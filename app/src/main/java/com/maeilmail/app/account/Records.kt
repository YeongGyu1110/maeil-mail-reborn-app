package com.maeilmail.app.account

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.maeilmail.app.work.RecordsWorker
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 학습 기록(이해도·별표)과 메모 저장소 설정. 웹과 같은 Firestore 문서 users/{uid}를 함께 쓴다.
 *
 * - 읽기: 로그인한 뒤, 그리고 앱을 열 때 1분이 지났으면 다시 읽는다. 마지막으로 읽은 기록은 기기에 두어 인터넷 없이도 보인다.
 * - 쓰기: 바꾼 것은 바로 화면에 보이고, 잠깐 모아 한 번에 보낸다 (웹과 같은 필드 단위 merge 쓰기).
 *   보내지 못한 변경은 기기에 보관했다가 연결되면 보낸다. 다시 읽어도 보관한 변경을 위에 덮어, 이 기기에서 나중에 바꾼 것이 이긴다.
 */
object Records {
    /** 다시 읽기까지 기다리는 시간 */
    private const val STALE_MS = 60_000L
    /** 바꾼 뒤 이만큼 모았다가 보낸다 */
    private const val FLUSH_DELAY_MS = 600L
    private const val RETRY_BASE_MS = 2_000L
    private const val RETRY_MAX_MS = 60_000L

    enum class Level(val key: String?, val label: String) {
        NEW(null, "미학습"),
        REVIEW("review", "복습 필요"),
        DONE("done", "완벽 이해");

        companion object {
            fun of(key: Any?): Level = entries.firstOrNull { it.key != null && it.key == key } ?: NEW
        }
    }

    data class Item(val level: Level = Level.NEW, val starred: Boolean = false)

    data class State(
        val items: Map<String, Item> = emptyMap(),
        /** 메모 저장소 "owner/name" (고르지 않았으면 null) */
        val memoRepo: String? = null,
        /** 로그인해 기록을 쓸 수 있는지 */
        val signedIn: Boolean = false,
        /** 기록을 한 번이라도 읽었는지 (기기에 둔 사본 포함) */
        val loaded: Boolean = false,
        val loading: Boolean = false,
        /** 보내지 못한(보내는 중인) 변경 수 */
        val pending: Int = 0,
        val error: String? = null,
    ) {
        fun item(id: String): Item = items[id] ?: Item()
        fun level(id: String): Level = item(id).level
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var dir: File
    private lateinit var appContext: Context
    /** 서버에서 읽은 기록: 질문 ID → 필드 */
    private var server: Map<String, Map<String, Any?>> = emptyMap()
    private var memoRepo: String? = null
    /** 보내지 못한 변경: 질문 ID → 필드 → 값 (null이면 필드를 지운다) */
    private val pending = LinkedHashMap<String, MutableMap<String, Any?>>()
    private var loadedAt = 0L
    private var loadJob: Job? = null
    private var flushJob: Job? = null
    private val flushLock = Mutex()
    private var retryDelay = RETRY_BASE_MS

    @Synchronized
    fun init(context: Context) {
        if (::dir.isInitialized) return
        appContext = context.applicationContext
        dir = File(appContext.filesDir, "records").apply { mkdirs() }
        readFiles()
        publish(loaded = server.isNotEmpty() || memoRepo != null)
        refresh()
    }

    // ---------- 읽기 ----------

    /** 서버에서 다시 읽는다. force가 아니면 마지막으로 읽은 지 1분이 지났을 때만 */
    fun refresh(force: Boolean = false) {
        if (!signedIn()) return
        if (!force && System.currentTimeMillis() - loadedAt < STALE_MS) return
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val uid = Account.state.value.user?.uid ?: return@launch
                val token = Account.firebaseIdToken()
                val doc = io { FirestoreRest.get(token, "users/$uid") }
                server = (doc?.get("items") as? Map<*, *>).orEmpty().entries.mapNotNull { (id, fields) ->
                    val map = (fields as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }.orEmpty()
                    if (id is String) id to map else null
                }.toMap()
                memoRepo = (doc?.get("memoRepo") as? Map<*, *>)?.get("fullName") as? String
                loadedAt = System.currentTimeMillis()
                io { writeSnapshot() }
                _state.update { it.copy(error = null) }
                publish(loaded = true)
                if (pending.isNotEmpty()) scheduleFlush(0)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 인터넷이 없으면 기기에 둔 기록을 그대로 보여준다
                if (!isNetworkError(e)) _state.update { it.copy(error = Account.errorMessage(e)) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    // ---------- 쓰기 ----------

    fun setLevel(id: String, level: Level) = update(id, mapOf("level" to level.key))

    fun setStarred(id: String, starred: Boolean) = update(id, mapOf("starred" to if (starred) true else null))

    private fun update(id: String, fields: Map<String, Any?>) {
        if (!signedIn()) return
        pending.getOrPut(id) { LinkedHashMap() }.putAll(fields)
        publish()
        scope.launch { io { writePending() } }
        scheduleFlush(FLUSH_DELAY_MS)
    }

    private fun scheduleFlush(delayMs: Long) {
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(delayMs)
            flush()
        }
    }

    /** 보내지 못한 변경을 지금 보낸다 (백그라운드 작업에서도 부른다). 다 보냈거나 보낼 것이 없으면 true */
    suspend fun flushNow(): Boolean = withContext(Dispatchers.Main.immediate) {
        flushJob?.cancel()
        flush()
        pending.isEmpty()
    }

    private suspend fun flush() = flushLock.withLock {
        if (pending.isEmpty() || !signedIn()) return@withLock
        val uid = Account.state.value.user?.uid ?: return@withLock
        // 보내는 동안 또 바꿀 수 있으니, 보낼 것을 떼어 두고 보낸 뒤 그대로인 것만 대기열에서 지운다
        val patch = pending.mapValues { (_, fields) -> fields.toMap() }
        try {
            val token = Account.firebaseIdToken()
            io { FirestoreRest.updateItems(token, uid, patch) }
            server = applyPatch(server, patch)
            for ((id, fields) in patch) {
                val current = pending[id] ?: continue
                for ((key, value) in fields) if (current.containsKey(key) && current[key] == value) current.remove(key)
                if (current.isEmpty()) pending.remove(id)
            }
            retryDelay = RETRY_BASE_MS
            io {
                writePending()
                writeSnapshot()
            }
            _state.update { it.copy(error = null) }
            publish()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (isNetworkError(e)) {
                // 연결되면 보낸다: 간격을 늘려 가며 다시 해 본다 (앱을 닫아도 대기열은 기기에 남는다)
                _state.update { it.copy(error = "저장하지 못했어요. 이 기기에 보관했다가 연결되면 저장할게요") }
                // 앱이 뒤로 가 인터넷이 막혔을 수도 있으니, 인터넷이 될 때 시스템이 보내 주도록 맡겨 둔다
                RecordsWorker.enqueue(appContext)
                val wait = retryDelay
                retryDelay = (retryDelay * 2).coerceAtMost(RETRY_MAX_MS)
                scope.launch {
                    delay(wait)
                    if (pending.isNotEmpty()) scheduleFlush(0)
                }
            } else {
                // 규칙 거부 등 다시 보내도 같은 오류는 멈추고 다음에 앱을 열 때 다시 보낸다
                _state.update { it.copy(error = "저장이 거부됐어요 (${Account.errorMessage(e)}). 바꾼 내용은 이 기기에 보관해 두었어요") }
            }
        }
    }

    // ---------- 로그인·로그아웃 ----------

    fun onSignedIn() {
        loadedAt = 0
        publish()
        refresh(force = true)
    }

    /** 로그아웃: 기기에 둔 기록 사본과 보내지 못한 변경을 지운다 */
    fun onSignedOut() {
        loadJob?.cancel()
        flushJob?.cancel()
        server = emptyMap()
        memoRepo = null
        pending.clear()
        loadedAt = 0
        scope.launch { io { dir.listFiles()?.forEach(File::delete) } }
        _state.value = State()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    // ---------- 화면에 보일 상태 ----------

    private fun signedIn() = Account.state.value.user != null

    private fun publish(loaded: Boolean = _state.value.loaded) {
        val merged = applyPatch(server, pending)
        _state.update {
            it.copy(
                items = merged.mapValues { (_, fields) ->
                    Item(level = Level.of(fields["level"]), starred = fields["starred"] == true)
                }.filterValues { item -> item != Item() },
                memoRepo = memoRepo,
                signedIn = signedIn(),
                loaded = loaded,
                pending = pending.size,
            )
        }
    }

    /** 기록에 변경을 덮는다. 값이 null이면 그 필드를 지우고, 남은 필드가 없으면 질문 항목째 뺀다 */
    internal fun applyPatch(
        items: Map<String, Map<String, Any?>>,
        patch: Map<String, Map<String, Any?>>,
    ): Map<String, Map<String, Any?>> {
        val result = LinkedHashMap(items)
        for ((id, fields) in patch) {
            val item = LinkedHashMap(result[id].orEmpty())
            for ((key, value) in fields) if (value == null) item.remove(key) else item[key] = value
            if (item.isEmpty()) result.remove(id) else result[id] = item
        }
        return result
    }

    // ---------- 기기에 보관 ----------

    private fun readFiles() {
        runCatching {
            val o = JSONObject(File(dir, "snapshot.json").readText())
            server = o.optJSONObject("items").toFieldMap()
            memoRepo = o.optString("memoRepo").ifEmpty { null }
        }
        runCatching {
            val o = JSONObject(File(dir, "pending.json").readText())
            for ((id, fields) in o.toFieldMap()) pending[id] = LinkedHashMap(fields)
        }
    }

    private fun writeSnapshot() {
        val o = JSONObject().put("items", server.toJson()).put("memoRepo", memoRepo ?: "")
        writeAtomic(File(dir, "snapshot.json"), o.toString())
    }

    private fun writePending() {
        val file = File(dir, "pending.json")
        if (pending.isEmpty()) file.delete() else writeAtomic(file, pending.toJson().toString())
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("기록을 기기에 저장하지 못했어요")
        }
    }

    private fun Map<String, Map<String, Any?>>.toJson(): JSONObject {
        val o = JSONObject()
        for ((id, fields) in this) {
            val f = JSONObject()
            for ((k, v) in fields) f.put(k, v ?: JSONObject.NULL)
            o.put(id, f)
        }
        return o
    }

    private fun JSONObject?.toFieldMap(): Map<String, Map<String, Any?>> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { id ->
            val f = getJSONObject(id)
            f.keys().asSequence().associateWith { k -> f.get(k).takeIf { it != JSONObject.NULL } }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
