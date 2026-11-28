package com.maeilmail.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.maeilmail.app.OpenRequest
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Settings
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.widget.TodayWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 화면. 뒤에 있는 것이 위에 쌓이는 화면이다 */
private enum class Route { Home, Search, Question, Settings }

/** 받은 질문을 이만큼 열어 두어야 열어 본 것으로 친다 (옆으로 넘기다 스친 질문은 치지 않는다) */
private const val OPENED_AFTER_MS = 1500L

/**
 * 앱의 화면 전환: 첫 화면(오늘의 질문과 목록) 위에 검색·질문·설정을 연다.
 * 화면을 돌려도 보던 화면·질문·검색어가 남고, 검색 결과에서 연 질문은 뒤로 가면 그 결과로 돌아온다.
 *
 * openRequest: 알림·위젯·메일 링크로 열어 달라고 한 것. 열고 나면 onOpenHandled로 지운다.
 */
@Composable
fun App(openRequest: OpenRequest?, onOpenHandled: () -> Unit) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings.get(context) }
    val state by Library.state.collectAsState()
    val today = rememberToday(state)
    val filter = rememberSaveable(saver = ListFilter.Saver) { ListFilter() }
    val listState = rememberLazyListState()
    var route by rememberSaveable { mutableStateOf(Route.Home) }
    var questionId by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    // 질문 화면의 뒤로 가기에 보일, 질문을 연 목록의 이름
    var origin by rememberSaveable { mutableStateOf(ListFilter.DEFAULT_NAME) }
    // 질문 화면에서 뒤로 가면 돌아갈 화면 (첫 화면 또는 검색)
    var questionBack by rememberSaveable { mutableStateOf(Route.Home) }
    var query by rememberSaveable { mutableStateOf("") }
    val resultState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    // 질문 화면에서 돌아갈 때 마지막에 본 질문. 돌아간 목록이 그 질문이 보이게 옮긴다 (옆으로 넘기며 여러 질문을 봤을 때)
    var follow by rememberSaveable { mutableStateOf<String?>(null) }

    fun openQuestion(id: String, ids: List<String>, from: String, back: Route = Route.Home) {
        order = ArrayList(ids)
        origin = from
        questionId = id
        questionBack = back
        route = Route.Question
    }

    /** 검색어를 바꾸면 결과를 맨 위부터 보인다 */
    fun setQuery(value: String) {
        if (value.trim() != query.trim()) scope.launch { resultState.scrollToItem(0) }
        query = value
    }

    /**
     * 앱 밖에서 열어 달라고 한 화면으로 간다. 질문 화면으로 바로 열면, 받은 질문은 받은 순서(밀면 지난번에 받은 질문)를,
     * 아니면 전체 목록 순서를 따른다 (첫 화면의 오늘의 질문과 같다)
     */
    fun handle(request: OpenRequest) {
        onOpenHandled()
        val ids = state.catalog.questions.map { it.id }
        val received = state.progress?.received(today.id.takeIf { today.isToday }, ids.toHashSet()).orEmpty()
        fun orderFor(id: String) = if (id in received) received else ids
        when (request) {
            is OpenRequest.Question -> openQuestion(request.id, orderFor(request.id), ListFilter.DEFAULT_NAME)
            is OpenRequest.Home -> {
                filter.show(request.filter)
                scope.launch { listState.scrollToItem(0) }
                // 지난 알림을 눌러 첫 화면에 그 질문이 없으면(더 앞의 질문이거나 이미 열어 본 지난 질문) 질문 화면을 바로 연다
                val onHome = request.id == today.id && (today.isToday || request.id != settings.opened.value)
                if (request.id != null && !onHome) openQuestion(request.id, orderFor(request.id), ListFilter.DEFAULT_NAME) else route = Route.Home
            }
        }
    }

    // 질문 화면에서는 쓰던 메모를 버릴지 먼저 묻는다 (interrupt)
    LaunchedEffect(openRequest, state.loaded) {
        if (openRequest != null && state.loaded && route != Route.Question) handle(openRequest)
    }

    // 받은 질문의 답변을 어느 길로든 열었으면 기억해 둔다 (첫 화면과 위젯에서 지난 질문으로 다시 보이지 않게).
    // 옆으로 넘기다 스치기만 한 질문은 연 것으로 치지 않는다: 잠깐 머문 뒤에 기억한다 (그 전에 넘기면 이 효과가 취소된다)
    LaunchedEffect(route, questionId, today.id) {
        if (route == Route.Question && questionId == today.id && settings.opened.value != questionId) {
            delay(OPENED_AFTER_MS)
            settings.setOpened(questionId)
            withContext(Dispatchers.IO) { TodayWidget.refresh(context) }
        }
    }

    // 이미 질문이 있을 때 맞춘 결과나 실패를 짧게 알린다 (처음 받을 때는 목록 자리에 보여준다)
    LaunchedEffect(state.notice, state.error) {
        val message = state.notice ?: state.error?.takeIf { state.catalog.questions.isNotEmpty() } ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        Library.clearNotice()
    }

    // 메모 저장 결과 (저장했어요, 커밋했어요, 올리지 못했어요 등)
    val records by Records.state.collectAsState()
    LaunchedEffect(records.notice) {
        val message = records.notice ?: return@LaunchedEffect
        // 보여준 뒤에 지운다. 먼저 지우면 notice가 바뀌어 이 효과가 다시 시작되며, 띄우던 알림이 곧바로 닫힌다
        snackbar.showSnackbar(message)
        Records.clearNotice()
    }

    // 이해도·별표를 저장하지 못했을 때 (설정에도 남아 있다)
    LaunchedEffect(records.error) { records.error?.let { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(colors.bgCanvas)) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(200)) { w -> if (forward) w / 12 else -w / 16 } + fadeIn(tween(200)))
                    .togetherWith(fadeOut(tween(100)))
            },
            label = "route",
        ) { shown ->
            when (shown) {
                Route.Home -> HomeScreen(
                    state = state,
                    today = today,
                    filter = filter,
                    listState = listState,
                    onOpen = { q, ids, from -> openQuestion(q.id, ids, from) },
                    onSearch = {
                        query = ""
                        route = Route.Search
                    },
                    onSettings = { route = Route.Settings },
                    follow = follow,
                    onFollowed = { follow = null },
                )
                Route.Search -> SearchScreen(
                    state = state,
                    query = query,
                    onQuery = ::setQuery,
                    listState = resultState,
                    onOpen = { q, ids, from -> openQuestion(q.id, ids, from, back = Route.Search) },
                    onBack = { route = Route.Home },
                    follow = follow,
                    onFollowed = { follow = null },
                )
                Route.Question -> QuestionScreen(
                    state = state,
                    id = questionId,
                    origin = origin,
                    order = order,
                    onNavigate = { questionId = it },
                    onBack = {
                        follow = questionId
                        route = questionBack
                    },
                    interrupt = if (openRequest != null && state.loaded) ({ handle(openRequest) }) else null,
                    onStay = onOpenHandled,
                )
                Route.Settings -> SettingsScreen(state, today, onBack = { route = Route.Home })
            }
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding(),
        ) { Toast(it.visuals.message) }
    }
}
