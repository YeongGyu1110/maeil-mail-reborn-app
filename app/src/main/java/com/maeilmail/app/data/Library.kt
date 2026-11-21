package com.maeilmail.app.data

import android.content.Context
import com.maeilmail.app.reminder.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 앱 전체가 함께 보는 질문 목록과 발송 기록. 화면을 돌리거나 다시 그려도 유지되도록 앱 프로세스에 하나만 둔다.
 * 처음엔 받아 둔 파일에서 읽고, 웹 서비스 저장소와 맞추면 다시 읽는다.
 * 맞춘 뒤에는 발송 기록(progress.json)에 맞춰 오늘의 질문 알림을 다시 건다.
 */
object Library {
    /** 앱을 열 때 이보다 오래 지났으면 저장소와 다시 맞춘다 */
    private const val SYNC_INTERVAL_MS = 30 * 60 * 1000L

    data class State(
        val catalog: Catalog = Catalog.EMPTY,
        /** 발송 기록. 아직 받지 못했으면 null */
        val progress: Progress? = null,
        /** 받아 둔 파일을 읽었는지 (읽기 전에는 빈 목록이 "질문 없음"이 아니다) */
        val loaded: Boolean = false,
        val syncing: Boolean = false,
        /** 받는 중인 파일 수 (받은 수, 전체) */
        val progressCount: Pair<Int, Int>? = null,
        val lastSyncAt: Long = 0,
        /** 받아 둔 질문의 저장소 커밋 (모르면 "") */
        val commit: String = "",
        /** 마지막으로 맞추지 못한 이유 (화면에 보일 문구) */
        val error: String? = null,
        /** 마지막으로 맞춘 결과를 알릴 문구 (한 번 보여주고 지운다) */
        val notice: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var appContext: Context
    private lateinit var store: ContentStore
    private lateinit var contentSync: ContentSync
    private var syncJob: Job? = null
    private val syncLock = Mutex()

    /** 검색은 동의어가 바뀔 때만 새로 만든다 */
    @Volatile
    private var search: Pair<List<List<String>>, Search>? = null

    @Synchronized
    fun init(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = ContentStore(appContext.filesDir)
        contentSync = ContentSync(store)
        scope.launch { reload() }
    }

    fun search(): Search {
        val synonyms = _state.value.catalog.synonyms
        search?.let { (s, built) -> if (s === synonyms) return built }
        return Search(synonyms).also { search = synonyms to it }
    }

    /** 답변 본문 (받아 두지 않았으면 null) */
    suspend fun content(question: Question): String? = withContext(Dispatchers.IO) { store.read(question.contentPath) }

    private suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            Triple(store.loadCatalog(), readProgress(store), store.manifest())
        }
        val (catalog, progress, manifest) = loaded
        _state.update {
            it.copy(
                catalog = catalog,
                progress = progress,
                loaded = true,
                lastSyncAt = manifest?.syncedAt ?: 0,
                commit = manifest?.commit.orEmpty(),
            )
        }
    }

    /** 앱을 열 때: 오래됐거나 받아 둔 질문·발송 기록이 없으면 맞춘다 */
    fun syncIfStale() {
        scope.launch {
            if (!_state.value.loaded) reload()
            val s = _state.value
            val missing = s.catalog.questions.isEmpty() || s.progress == null
            if (missing || System.currentTimeMillis() - s.lastSyncAt > SYNC_INTERVAL_MS) syncNow()
        }
    }

    /** 지금 저장소와 맞춘다 (당겨서 새로고침). 이미 맞추는 중이면 그 작업에 맡긴다 */
    fun syncNow() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch { sync() }
    }

    /**
     * 저장소와 맞추고 알림을 다시 건다. 화면(syncNow)과 백그라운드 작업(SyncWorker)이 함께 쓰며, 한 번에 하나씩만 돈다.
     * 성공하면 true
     */
    suspend fun sync(): Boolean = syncLock.withLock {
        _state.update { it.copy(syncing = true, error = null, progressCount = null) }
        try {
            val before = _state.value.catalog.questions.map { it.id }.toSet()
            contentSync.sync { done, total -> _state.update { it.copy(progressCount = done to total) } }
            reload()
            // 새로 생긴 질문이 있으면 알린다 (처음 받을 때는 알리지 않는다)
            val added = _state.value.catalog.questions.count { it.id !in before }
            val notice = if (before.isNotEmpty() && added > 0) "새 질문 ${added}개를 받았어요" else null
            _state.update { it.copy(notice = notice) }
            true
        } catch (e: Exception) {
            _state.update { it.copy(error = errorMessage(e)) }
            false
        } finally {
            _state.update { it.copy(syncing = false, progressCount = null) }
            // 발송 기록이 바뀌었을 수 있으니 알림을 다시 건다 (맞추지 못했어도 받아 둔 기록으로 건다)
            withContext(Dispatchers.IO) { Reminders.schedule(appContext) }
        }
    }

    fun clearNotice() = _state.update { it.copy(notice = null, error = null) }

    private fun errorMessage(e: Exception): String = when (e) {
        is UnknownHostException -> "인터넷에 연결되지 않았어요"
        is SocketTimeoutException -> "응답이 너무 늦어요. 잠시 후 다시 시도해 주세요"
        is Http.StatusException -> when (e.code) {
            403, 429 -> "GitHub 요청 한도에 걸렸어요. 잠시 후 다시 시도해 주세요"
            404 -> "질문 저장소를 찾지 못했어요"
            else -> "GitHub 응답 오류 (HTTP ${e.code})"
        }
        is IOException -> e.message ?: "질문을 받지 못했어요"
        else -> e.message ?: e.javaClass.simpleName
    }

    /** 받아 둔 발송 기록. 없거나 읽지 못하면 null */
    fun readProgress(store: ContentStore): Progress? =
        store.read(Progress.PATH)?.let { runCatching { Progress.parse(it) }.getOrNull() }
}
