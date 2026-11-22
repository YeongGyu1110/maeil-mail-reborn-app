package com.maeilmail.app.account

import android.content.Context
import com.maeilmail.app.work.RecordsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 학습 기록(이해도·별표·메모)과 메모 저장소 설정. 웹과 같은 Firestore 문서를 함께 쓴다.
 *   users/{uid}            items: { "fe-1": { level, starred } }, memoRepo: { id, fullName }
 *   users/{uid}/memos/{n}  { "fe-1": { memo, memoAt, memoUncommitted } }  (n = 질문 번호 % 4)
 *
 * - 읽기: 로그인한 뒤, 그리고 앱을 열 때 1분이 지났으면 다시 읽는다. 마지막으로 읽은 기록은 기기에 두어 인터넷 없이도 보인다.
 * - 쓰기: 바꾼 것은 바로 화면에 보이고, 잠깐 모아 한 번에 보낸다 (웹과 같은 필드 단위 merge 쓰기를 한 번의 batch로).
 *   보내지 못한 변경은 기기에 보관했다가 연결되면 보낸다. 다시 읽어도 보관한 변경을 위에 덮어, 이 기기에서 나중에 바꾼 것이 이긴다.
 * - 메모는 화면에서는 학습 수준·별표와 한 항목으로 다루고, 쓸 때만 묶음 문서로 나눈다 (웹 createFirestoreBackend와 같다).
 */
object Records {
    const val MEMO_MAX = 3000

    /** 메모 묶음 문서 수. 바꾸면 웹의 MEMO_SHARDS와 firestore.rules도 같이 바꾼다 */
    private const val MEMO_SHARDS = 4
    private val MEMO_FIELDS = setOf("memo", "memoAt", "memoUncommitted")

    /** 다시 읽기까지 기다리는 시간 */
    private const val STALE_MS = 60_000L

    /** 메모를 저장하기 전에, 기록을 읽은 지 이만큼 지났으면 다시 읽어 다른 곳에서 바뀌었는지 본다 (웹 STALE_AFTER) */
    private const val CONFLICT_STALE_MS = 5 * 60_000L

    /** 이해도·별표를 바꾼 뒤 이만큼 모았다가 보낸다 */
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

    data class Item(
        val level: Level = Level.NEW,
        val starred: Boolean = false,
        val memo: String? = null,
        /** 메모를 저장한 시각 (epoch ms) */
        val memoAt: Long? = null,
        /** 계정에는 저장했지만 GitHub에는 아직 올리지 않은 메모 (임시저장, 커밋 실패) */
        val memoUncommitted: Boolean = false,
    )

    data class MemoRepo(val id: Long, val fullName: String)

    data class State(
        val items: Map<String, Item> = emptyMap(),
        /** 메모를 커밋할 저장소 (고르지 않았으면 null) */
        val memoRepo: MemoRepo? = null,
        /** 로그인해 기록을 쓸 수 있는지 */
        val signedIn: Boolean = false,
        /** 기록을 한 번이라도 읽었는지 (기기에 둔 사본 포함) */
        val loaded: Boolean = false,
        val loading: Boolean = false,
        /** 보내지 못한(보내는 중인) 변경 수 */
        val pending: Int = 0,
        val error: String? = null,
        /** 한 번 보여주고 지우는 알림 (메모 저장 결과 등) */
        val notice: String? = null,
    ) {
        fun item(id: String): Item = items[id] ?: Item()
        fun level(id: String): Level = item(id).level
    }

    /** 메모 저장 결과. saved: 계정에 썼는지, committed: GitHub에 올렸는지 (올리지 않았으면 null) */
    data class MemoResult(val saved: Boolean, val changed: Boolean, val committed: Boolean?)

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var dir: File
    private lateinit var appContext: Context

    /** 서버에서 읽은 기록: 질문 ID → 필드 (메모 필드 포함) */
    private var server: Map<String, Map<String, Any?>> = emptyMap()
    private var memoRepo: MemoRepo? = null

    /** 묶음으로 옮기지 못하고 기본 문서에 남아 있는 예전 메모의 질문 ID (그 메모를 바꿀 때 기본 문서에서도 지운다) */
    internal val legacy = HashSet<String>()

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
        loadJob = scope.launch { load() }
    }

    private suspend fun load() {
        _state.update { it.copy(loading = true) }
        try {
            val uid = Account.state.value.user?.uid ?: return
            val token = Account.firebaseIdToken()
            val (doc, shards) = coroutineScope {
                val main = async { io { FirestoreRest.get(token, "users/$uid") } }
                val memos = async { io { FirestoreRest.list(token, "users/$uid/memos") } }
                main.await() to memos.await()
            }
            applyLoaded(doc, shards)
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

    /** 기본 문서와 메모 묶음을 합친다. 기본 문서에 남은 예전 메모는 더 나중에 저장한 쪽을 쓴다 (웹 load와 같다) */
    private fun applyLoaded(doc: Map<String, Any?>?, shards: Map<String, Map<String, Any?>>) {
        val memos = HashMap<String, Map<String, Any?>>()
        for ((_, fields) in shards) {
            for ((id, memo) in fields) {
                val map = (memo as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }.orEmpty()
                if (map.isNotEmpty()) memos[id] = map
            }
        }
        val items = LinkedHashMap<String, Map<String, Any?>>()
        legacy.clear()
        for ((id, value) in (doc?.get("items") as? Map<*, *>).orEmpty()) {
            if (id !is String) continue
            val fields = (value as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v }.orEmpty()
            val rest = fields.filterKeys { it !in MEMO_FIELDS }
            val memo = fields.filterKeys { it in MEMO_FIELDS }
            if (rest.isNotEmpty()) items[id] = rest
            if (memo.isEmpty()) continue
            legacy.add(id)
            if (memos[id] == null || memo.at() > memos[id].orEmpty().at()) memos[id] = memo
        }
        for ((id, memo) in memos) items[id] = items[id].orEmpty() + memo
        server = items
        memoRepo = (doc?.get("memoRepo") as? Map<*, *>)?.let { repo ->
            val name = repo["fullName"] as? String ?: return@let null
            MemoRepo((repo["id"] as? Number)?.toLong() ?: 0, name)
        }
    }

    private fun Map<String, Any?>.at(): Long = (this["memoAt"] as? Number)?.toLong() ?: 0

    /**
     * 메모를 저장하기 직전에 다른 곳에서 바뀌었는지 보려고 부른다 (웹 confirmNoConflict).
     * 기록을 읽은 지 5분이 지났으면 다시 읽고(보낼 변경이 남아 있으면 읽지 않는다), 지금 이 질문의 메모를 돌려준다
     */
    suspend fun latestMemo(id: String): String {
        if (signedIn() && pending.isEmpty() && System.currentTimeMillis() - loadedAt >= CONFLICT_STALE_MS) {
            loadJob?.join()
            if (System.currentTimeMillis() - loadedAt >= CONFLICT_STALE_MS) load()
        }
        return _state.value.item(id).memo.orEmpty()
    }

    // ---------- 쓰기: 이해도·별표 ----------

    fun setLevel(id: String, level: Level) = update(id, mapOf("level" to level.key))

    fun setStarred(id: String, starred: Boolean) = update(id, mapOf("starred" to if (starred) true else null))

    private fun update(id: String, fields: Map<String, Any?>, flushSoon: Boolean = true) {
        if (!signedIn()) return
        pending.getOrPut(id) { LinkedHashMap() }.putAll(fields)
        publish()
        scope.launch { io { writePending() } }
        if (flushSoon) scheduleFlush(FLUSH_DELAY_MS)
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
    }

    /** 대기열을 보낸다. 다 보냈으면 true */
    private suspend fun flush(): Boolean = flushLock.withLock {
        if (pending.isEmpty()) return@withLock true
        if (!signedIn()) return@withLock false
        val uid = Account.state.value.user?.uid ?: return@withLock false
        // 보내는 동안 또 바꿀 수 있으니, 보낼 것을 떼어 두고 보낸 뒤 그대로인 것만 대기열에서 지운다
        val patch = pending.mapValues { (_, fields) -> fields.toMap() }
        try {
            val token = Account.firebaseIdToken()
            val writes = writesFor(uid, patch)
            io { FirestoreRest.commit(token, writes) }
            for ((id, fields) in patch) if (fields.keys.any { it in MEMO_FIELDS }) legacy.remove(id)
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
            pending.isEmpty()
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
            false
        }
    }

    /**
     * 변경을 Firestore 쓰기로 나눈다: 학습 수준·별표는 기본 문서, 메모는 묶음 문서 (웹 write와 같다).
     * 메모를 지우면 묶음에서 질문 항목째 지우고, 기본 문서에 예전 메모가 남아 있으면 그것도 지운다.
     */
    internal fun writesFor(uid: String, patch: Map<String, Map<String, Any?>>): List<FirestoreRest.Write> {
        val main = FirestoreRest.Write("users/$uid")
        val shards = sortedMapOf<Int, FirestoreRest.Write>()
        for ((id, fields) in patch) {
            for ((key, value) in fields) if (key !in MEMO_FIELDS) main.set("items", id, key, value = value)
            val memoFields = fields.filterKeys { it in MEMO_FIELDS }
            if (memoFields.isEmpty()) continue
            val shard = shards.getOrPut(memoShard(id)) { FirestoreRest.Write("users/$uid/memos/${memoShard(id)}") }
            if (memoFields.containsKey("memo") && memoFields["memo"] == null) {
                shard.set(id, value = null)
            } else {
                for ((key, value) in memoFields) shard.set(id, key, value = value)
            }
            if (id in legacy) {
                main.set("items", id, "memo", value = null)
                main.set("items", id, "memoAt", value = null)
            }
        }
        return listOf(main) + shards.values
    }

    /** 메모가 들어갈 묶음 문서: 질문 번호를 묶음 수로 나눈 나머지 (fe-17 → 17 % 4 → memos/1) */
    internal fun memoShard(id: String): Int = (Regex("\\d+$").find(id)?.value?.toIntOrNull() ?: 0) % MEMO_SHARDS

    // ---------- 쓰기: 메모 ----------

    /** 저장할 메모의 모양: 앞의 빈 줄과 끝의 공백만 걷어 낸다. 첫 줄의 들여쓰기는 남긴다 (웹 normalizeMemo) */
    fun normalizeMemo(text: String): String = text.replace(Regex("^(?:[ \\t]*\\n)+"), "").trimEnd()

    /** 저장할 때 GitHub에도 커밋하는지 (메모 저장소를 골랐으면) */
    fun canCommitMemo(): Boolean = signedIn() && memoRepo != null

    /**
     * 메모를 저장한다 (웹 saveMemo와 같다). 빈 글이면 메모를 지우고, 저장소의 {id}.md 파일도 지운다.
     * commit이 false면 임시저장이다: 계정에만 쓰고 GitHub에는 올리지 않으며 알림도 띄우지 않는다.
     * 바뀐 것이 없어도 아직 올리지 않은 메모면 커밋한다. title은 커밋 메시지에 붙일 질문 제목이다.
     */
    suspend fun saveMemo(id: String, raw: String, title: String, commit: Boolean = true): MemoResult {
        val text = normalizeMemo(raw)
        val item = _state.value.item(id)
        val changed = text != item.memo.orEmpty()
        val repo = memoRepo?.fullName
        val committable = text.isNotEmpty() && repo != null && signedIn()
        val shouldCommit = commit && committable && (changed || item.memoUncommitted)
        val shouldRemoveFile = commit && text.isEmpty() && changed && repo != null && signedIn()
        if (!changed && !shouldCommit) return MemoResult(saved = true, changed = false, committed = null)

        if (changed) {
            // 커밋할 때는 성공했다고 보고 표시를 지워 둔다 (실패하면 아래에서 다시 켠다). 임시저장이면 켠다
            val uncommitted = if (!commit && committable) true else null
            update(
                id,
                if (text.isNotEmpty()) mapOf("memo" to text, "memoAt" to System.currentTimeMillis(), "memoUncommitted" to uncommitted)
                else mapOf("memo" to null, "memoAt" to null, "memoUncommitted" to null),
                flushSoon = false,
            )
        }

        // 계정 저장과 GitHub 커밋(또는 파일 삭제)을 함께 한다
        val (saved, github) = coroutineScope {
            val save = async { if (changed) flushNow() else true }
            val git = async {
                runCatching {
                    when {
                        shouldCommit -> GitHubApi.commitMemo(repo!!, id, text, title).let { true }
                        shouldRemoveFile -> GitHubApi.deleteMemoFile(repo!!, id, title)
                        else -> null
                    }
                }
            }
            save.await() to git.await()
        }

        // 커밋 결과를 계정에 남긴다 (바뀔 때만 쓴다)
        var flagged = true
        if (shouldCommit) {
            val failed = github.isFailure
            if (failed != _state.value.item(id).memoUncommitted) {
                update(id, mapOf("memoUncommitted" to if (failed) true else null), flushSoon = false)
                flagged = flushNow()
            }
        }

        val error = github.exceptionOrNull()
        val message = when {
            !saved || !flagged -> "저장하지 못했어요. 바꾼 내용은 이 기기에 보관했다가 연결되면 저장할게요."
            error != null && shouldRemoveFile -> "메모는 지웠지만 GitHub의 $id.md 파일을 지우지 못했어요. ${GitHubApi.errorMessage(error)}"
            error != null -> "메모는 저장했지만 GitHub에 올리지 못했어요. ${GitHubApi.errorMessage(error)}"
            !commit -> null
            shouldCommit -> "메모를 저장하고 ${repo}에 커밋했어요."
            shouldRemoveFile && github.getOrNull() == true -> "메모를 지우고 ${repo}의 $id.md 파일도 삭제했어요."
            text.isNotEmpty() -> "메모를 저장했어요."
            else -> "메모를 지웠어요."
        }
        if (message != null) _state.update { it.copy(notice = message) }
        return MemoResult(
            saved = saved && flagged,
            changed = changed,
            committed = if (shouldCommit || shouldRemoveFile) github.isSuccess else null,
        )
    }

    /**
     * 화면을 떠나도 끝까지 해야 하는 일(메모 저장 등)을 기록 저장소의 범위에서 돌린다.
     * 화면의 범위에서 돌리면 저장 중에 뒤로 가면 저장이 중간에 멈춘다.
     */
    fun launch(block: suspend () -> Unit): Job = scope.launch { block() }

    /** 메모 저장소를 바꾼다. 웹과 같은 설정(memoRepo)이라 웹에서도 바뀐다. 실패하면 예외 */
    suspend fun setMemoRepo(repo: GitHubApi.Repo) {
        val uid = Account.state.value.user?.uid ?: throw Account.NotSignedIn()
        val token = Account.firebaseIdToken()
        val write = FirestoreRest.Write("users/$uid").set("memoRepo", value = mapOf("id" to repo.id, "fullName" to repo.fullName))
        io { FirestoreRest.commit(token, listOf(write)) }
        memoRepo = MemoRepo(repo.id, repo.fullName)
        io { writeSnapshot() }
        publish()
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
        legacy.clear()
        pending.clear()
        loadedAt = 0
        scope.launch { io { dir.listFiles()?.forEach(File::delete) } }
        _state.value = State()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    // ---------- 화면에 보일 상태 ----------

    private fun signedIn() = Account.state.value.user != null

    private fun publish(loaded: Boolean = _state.value.loaded) {
        val merged = applyPatch(server, pending)
        _state.update {
            it.copy(
                items = merged.mapValues { (_, f) ->
                    Item(
                        level = Level.of(f["level"]),
                        starred = f["starred"] == true,
                        memo = (f["memo"] as? String)?.takeIf { memo -> memo.isNotEmpty() },
                        memoAt = (f["memoAt"] as? Number)?.toLong(),
                        memoUncommitted = f["memoUncommitted"] == true,
                    )
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
            memoRepo = o.optJSONObject("memoRepo")?.let { MemoRepo(it.optLong("id"), it.getString("fullName")) }
                ?: o.optString("memoRepo").takeIf { it.contains('/') }?.let { MemoRepo(0, it) }
            o.optJSONArray("legacy")?.let { a -> for (i in 0 until a.length()) legacy.add(a.getString(i)) }
        }
        runCatching {
            val o = JSONObject(File(dir, "pending.json").readText())
            for ((id, fields) in o.toFieldMap()) pending[id] = LinkedHashMap(fields)
        }
    }

    private fun writeSnapshot() {
        val o = JSONObject()
            .put("items", server.toJson())
            .put("legacy", JSONArray(legacy.toList()))
        memoRepo?.let { o.put("memoRepo", JSONObject().put("id", it.id).put("fullName", it.fullName)) }
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
