package com.maeilmail.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 앱 전체가 함께 보는 질문 목록. 화면을 돌리거나 다시 그려도 유지되도록 앱 프로세스에 하나만 둔다.
 * 처음엔 받아 둔 파일에서 읽고, 웹 서비스 저장소와 맞추면 다시 읽는다.
 */
object Library {
    /** 앱을 열 때 이보다 오래 지났으면 저장소와 다시 맞춘다 */
    private const val SYNC_INTERVAL_MS = 30 * 60 * 1000L

    data class State(
        val catalog: Catalog = Catalog.EMPTY,
        /** 받아 둔 파일을 읽었는지 (읽기 전에는 빈 목록이 "질문 없음"이 아니다) */
        val loaded: Boolean = false,
        val syncing: Boolean = false,
        /** 받는 중인 파일 수 (받은 수, 전체) */
        val progress: Pair<Int, Int>? = null,
        val lastSyncAt: Long = 0,
        /** 마지막으로 맞추지 못한 이유 (화면에 보일 문구) */
        val error: String? = null,
        /** 마지막으로 맞춘 결과를 알릴 문구 (한 번 보여주고 지운다) */
        val notice: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var store: ContentStore
    private lateinit var sync: ContentSync
    private var syncJob: Job? = null

    /** 검색은 동의어가 바뀔 때만 새로 만든다 */
    @Volatile
    private var search: Pair<List<List<String>>, Search>? = null

    fun init(context: Context) {
        if (::store.isInitialized) return
        store = ContentStore(context.applicationContext.filesDir)
        sync = ContentSync(store)
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
        val (catalog, manifest) = withContext(Dispatchers.IO) { store.loadCatalog() to store.manifest() }
        _state.update { it.copy(catalog = catalog, loaded = true, lastSyncAt = manifest?.syncedAt ?: 0) }
    }

    /** 앱을 열 때: 오래됐거나 받아 둔 질문이 없으면 맞춘다 */
    fun syncIfStale() {
        scope.launch {
            if (!_state.value.loaded) reload()
            val s = _state.value
            if (s.catalog.questions.isEmpty() || System.currentTimeMillis() - s.lastSyncAt > SYNC_INTERVAL_MS) syncNow()
        }
    }

    /** 지금 저장소와 맞춘다 (당겨서 새로고침). 이미 맞추는 중이면 그 작업을 기다린다 */
    fun syncNow() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            _state.update { it.copy(syncing = true, error = null, progress = null) }
            try {
                val result = sync.sync { done, total ->
                    _state.update { it.copy(progress = done to total) }
                }
                val hadQuestions = _state.value.catalog.questions.isNotEmpty()
                reload()
                val notice = when (result) {
                    is ContentSync.Result.Updated -> if (hadQuestions && result.downloaded > 0) "질문 ${result.downloaded}개를 새로 받았어요" else null
                    ContentSync.Result.UpToDate -> null
                }
                _state.update { it.copy(notice = notice) }
            } catch (e: Exception) {
                _state.update { it.copy(error = errorMessage(e)) }
            } finally {
                _state.update { it.copy(syncing = false, progress = null) }
            }
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
}
