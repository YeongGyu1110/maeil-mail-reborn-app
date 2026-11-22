package com.maeilmail.app.account

import android.content.Context
import android.content.SharedPreferences
import com.maeilmail.app.data.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 로그인한 계정. GitHub 토큰(메모 커밋용)과 Firebase 세션(학습 기록용)을 함께 들고, 만료되기 전에 알아서 갱신한다.
 * 한 번 로그인하면 다시 로그인하라고 하지 않는다. GitHub 갱신 토큰(6개월)까지 만료되면 그때만 다시 로그인한다.
 *
 * 토큰은 앱 전용 저장소(SharedPreferences "account")에 두고, 기기 백업에서는 뺀다 (res/xml의 백업 규칙).
 */
object Account {
    private const val PREFS = "account"
    /** 만료 이만큼 전에 미리 갱신한다 */
    private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L

    data class User(val login: String, val uid: String, val githubId: String?)

    /** 웹과 같은 기록을 읽었는지 확인한 결과 */
    data class RecordCheck(val levels: Int, val starred: Int, val memoRepo: String?)

    data class State(
        val user: User? = null,
        /** 사용자가 github.com에 코드를 적기를 기다리는 중 */
        val pending: GitHubAuth.DeviceCode? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val record: RecordCheck? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private lateinit var prefs: SharedPreferences
    private var signInJob: Job? = null
    /** 승인을 기다리는 중에 바로 다시 묻게 깨운다 */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val githubLock = Mutex()
    private val firebaseLock = Mutex()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = State(user = readUser())
    }

    // ---------- 로그인 ----------

    /** Device Flow 로그인을 시작한다. 코드를 받으면 state.pending에 두고, 승인될 때까지 기다린다 */
    fun signIn() {
        if (signInJob?.isActive == true) return
        signInJob = scope.launch {
            _state.update { it.copy(busy = true, error = null, pending = null) }
            try {
                val code = io { GitHubAuth.requestDeviceCode() }
                _state.update { it.copy(pending = code) }
                val tokens = waitForApproval(code)
                val signIn = retryOnNetworkError { io { FirebaseAuthRest.signInWithGitHub(tokens.accessToken) } }
                val user = User(
                    login = signIn.githubLogin ?: "GitHub",
                    uid = signIn.session.uid,
                    githubId = signIn.githubId,
                )
                io {
                    saveGitHub(tokens)
                    saveFirebase(signIn.session)
                    saveUser(user)
                }
                _state.update { it.copy(user = user, pending = null) }
                checkRecord()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = errorMessage(e), pending = null) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** 코드를 적는 중에 그만둔다 */
    fun cancelSignIn() {
        signInJob?.cancel()
        _state.update { it.copy(pending = null, busy = false) }
    }

    /**
     * 사용자가 github.com에서 승인할 때까지 interval초마다 묻는다.
     * 안드로이드는 뒤로 간 앱의 인터넷을 막으므로(브라우저에서 승인하는 동안), 연결 오류는 실패로 보지 않고 계속 기다린다.
     * 앱으로 돌아오면(onResume) 다음 차례를 기다리지 않고 바로 묻는다.
     */
    private suspend fun waitForApproval(code: GitHubAuth.DeviceCode): GitHubAuth.TokenSet {
        var interval = code.interval
        var lastPollAt = System.currentTimeMillis()
        // 취소(cancelSignIn)하면 기다리는 자리에서 멈춘다
        while (true) {
            if (System.currentTimeMillis() > code.expiresAt) throw GitHubAuth.AuthException("expired_token", null)
            withTimeoutOrNull(interval * 1000L) { wake.receive() }
            // 앱으로 돌아와 일찍 깼어도 GitHub가 정한 간격보다 자주 묻지 않는다
            val wait = lastPollAt + interval * 1000L - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastPollAt = System.currentTimeMillis()
            val r = try {
                io { GitHubAuth.poll(code.deviceCode, interval) }
            } catch (e: IOException) {
                if (isNetworkError(e)) continue else throw e
            }
            when (r) {
                is GitHubAuth.Poll.Success -> return r.tokens
                GitHubAuth.Poll.Pending -> Unit
                is GitHubAuth.Poll.SlowDown -> interval = r.interval
                is GitHubAuth.Poll.Failed -> throw GitHubAuth.AuthException(r.error, r.description)
            }
        }
    }

    /** 앱이 다시 앞에 왔을 때 (MainActivity.onResume). 승인을 기다리는 중이면 바로 다시 묻는다 */
    fun onResume() {
        if (_state.value.pending != null) wake.trySend(Unit)
    }

    /** 연결 오류면 잠깐 쉬었다가 몇 번 더 해 본다 (앱이 막 앞에 와서 인터넷이 아직 풀리지 않았을 때) */
    private suspend fun <T> retryOnNetworkError(times: Int = 4, block: suspend () -> T): T {
        var wait = 1000L
        repeat(times - 1) {
            try {
                return block()
            } catch (e: IOException) {
                if (!isNetworkError(e)) throw e
                delay(wait)
                wait *= 2
            }
        }
        return block()
    }

    fun signOut() {
        cancelSignIn()
        prefs.edit().clear().apply()
        _state.value = State()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** 웹과 같은 기록(users/{uid})을 읽어 몇 개인지 센다. 로그인이 제대로 됐는지 확인하는 용도 */
    fun checkRecord() {
        val user = _state.value.user ?: return
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val idToken = firebaseIdToken()
                val doc = io { FirestoreRest.get(idToken, "users/${user.uid}") }
                val items = (doc?.get("items") as? Map<*, *>).orEmpty().values.filterIsInstance<Map<*, *>>()
                val repo = (doc?.get("memoRepo") as? Map<*, *>)?.get("fullName") as? String
                _state.update {
                    it.copy(
                        record = RecordCheck(
                            levels = items.count { item -> item["level"] != null },
                            starred = items.count { item -> item["starred"] == true },
                            memoRepo = repo,
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = errorMessage(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    // ---------- 토큰 ----------

    /**
     * 쓸 수 있는 GitHub 토큰. 만료가 가까우면 갱신 토큰으로 먼저 새로 받는다.
     * 갱신 토큰은 한 번 쓰면 무효가 되므로 한 번에 하나만 갱신한다.
     * rejected: GitHub가 401로 거절한 토큰. 주면 만료 시각과 상관없이 갱신한다.
     */
    suspend fun githubToken(rejected: String? = null): String = githubLock.withLock {
        val token = prefs.getString("gh_access", null) ?: throw NotSignedIn()
        if (rejected != null && token != rejected) return token
        val expiresAt = prefs.getLong("gh_access_exp", 0)
        val due = rejected != null || (expiresAt > 0 && System.currentTimeMillis() > expiresAt - REFRESH_MARGIN_MS)
        if (!due) return token
        val refresh = prefs.getString("gh_refresh", null) ?: throw NotSignedIn("GitHub 로그인이 만료됐어요. 다시 로그인해 주세요")
        when (val r = io { GitHubAuth.refresh(refresh) }) {
            is GitHubAuth.Refresh.Refreshed -> {
                io { saveGitHub(r.tokens) }
                r.tokens.accessToken
            }
            is GitHubAuth.Refresh.Invalid -> throw NotSignedIn(GitHubAuth.message(r.error, r.description))
        }
    }

    /** 쓸 수 있는 Firebase idToken. 만료가 가까우면 새로 받는다 */
    suspend fun firebaseIdToken(): String = firebaseLock.withLock {
        val session = readFirebase() ?: throw NotSignedIn()
        if (System.currentTimeMillis() < session.expiresAt - REFRESH_MARGIN_MS) return session.idToken
        val fresh = io { FirebaseAuthRest.refresh(session) }
        io { saveFirebase(fresh) }
        fresh.idToken
    }

    /** 로그인하지 않았거나, 로그인이 풀려 다시 해야 할 때 */
    class NotSignedIn(message: String = "로그인하지 않았어요") : IOException(message)

    // ---------- 저장 ----------

    private fun readUser(): User? {
        val uid = prefs.getString("uid", null) ?: return null
        return User(prefs.getString("login", null) ?: "GitHub", uid, prefs.getString("github_id", null))
    }

    private fun saveUser(user: User) = prefs.edit()
        .putString("uid", user.uid).putString("login", user.login).putString("github_id", user.githubId)
        .commit()

    private fun saveGitHub(t: GitHubAuth.TokenSet) = prefs.edit()
        .putString("gh_access", t.accessToken)
        .putString("gh_refresh", t.refreshToken)
        .putLong("gh_access_exp", t.accessExpiresAt ?: 0)
        .putLong("gh_refresh_exp", t.refreshExpiresAt ?: 0)
        .commit()

    private fun readFirebase(): FirebaseSession? {
        val uid = prefs.getString("uid", null) ?: return null
        return FirebaseSession(
            uid = uid,
            idToken = prefs.getString("fb_id", null) ?: return null,
            refreshToken = prefs.getString("fb_refresh", null) ?: return null,
            expiresAt = prefs.getLong("fb_exp", 0),
        )
    }

    private fun saveFirebase(s: FirebaseSession) = prefs.edit()
        .putString("uid", s.uid)
        .putString("fb_id", s.idToken)
        .putString("fb_refresh", s.refreshToken)
        .putLong("fb_exp", s.expiresAt)
        .commit()

    /** GitHub 갱신 토큰이 언제 만료되는지 (0: 모름 또는 만료 없음) */
    val githubRefreshExpiresAt: Long get() = prefs.getLong("gh_refresh_exp", 0)

    /** GitHub access token이 언제 만료되는지 (0: 만료 없음) */
    val githubAccessExpiresAt: Long get() = prefs.getLong("gh_access_exp", 0)

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    fun errorMessage(e: Exception): String = when (e) {
        is UnknownHostException -> "인터넷에 연결되지 않았어요"
        is SocketTimeoutException -> "응답이 너무 늦어요. 잠시 후 다시 시도해 주세요"
        is FirebaseAuthRest.FirebaseException -> when {
            e.code == 403 || e.message == "PERMISSION_DENIED" -> "이 계정으로는 학습 기록을 읽을 수 없어요 (웹 주인 계정인지 확인해 주세요)"
            e.message?.startsWith("INVALID_IDP_RESPONSE") == true -> "Firebase가 GitHub 로그인을 받지 않았어요 (${e.message})"
            else -> "Firebase 오류: ${e.message}"
        }
        is Http.StatusException -> "GitHub 응답 오류 (HTTP ${e.code})"
        else -> e.message ?: e.javaClass.simpleName
    }
}

/** 인터넷 연결 문제로 생긴 오류인지 (잠깐 뒤 다시 해 볼 만한 오류) */
fun isNetworkError(e: Throwable): Boolean = e is UnknownHostException || e is ConnectException ||
    e is SocketTimeoutException || e is NoRouteToHostException || e is SocketException ||
    e is SSLException || e is InterruptedIOException
