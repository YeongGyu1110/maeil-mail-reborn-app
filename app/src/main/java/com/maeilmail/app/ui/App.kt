package com.maeilmail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maeilmail.app.data.Library
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.TextStyles

private val ListFilterSaver: Saver<ListFilter, Any> = listSaver(
    save = { listOf(it.query, it.category) },
    restore = { ListFilter(it[0], it[1]) },
)

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("오늘", LineIcons.Calendar),
    Questions("질문", LineIcons.List),
    Settings("설정", LineIcons.Sliders),
}

/**
 * 앱의 화면 전환: 아래 탭(오늘 · 질문 · 설정)과 그 위에 여는 질문 화면.
 * 화면을 돌려도 보던 탭·질문·검색어가 남는다.
 *
 * openRequest: 알림을 눌러 열 질문 ID. 열고 나면 onOpenHandled로 지운다.
 */
@Composable
fun App(openRequest: String?, onOpenHandled: () -> Unit) {
    val colors = AppTheme.colors
    val state by Library.state.collectAsState()
    val filter = rememberSaveable(saver = ListFilterSaver) { ListFilter() }
    val listState = rememberLazyListState()
    var tab by rememberSaveable { mutableIntStateOf(Tab.Today.ordinal) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var order by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    // 질문 화면 경로에 보일, 질문을 연 곳 (질문 목록 / 오늘)
    var origin by rememberSaveable { mutableStateOf(Tab.Questions.pageName) }
    val snackbar = remember { SnackbarHostState() }

    /** 질문 목록 밖(오늘, 알림)에서 열면 이전·다음은 전체 목록 순서를 따른다 */
    fun openInFullOrder(id: String) {
        order = ArrayList(state.catalog.questions.map { it.id })
        origin = Tab.Today.pageName
        openId = id
    }

    LaunchedEffect(openRequest, state.loaded) {
        if (openRequest != null && state.loaded) {
            openInFullOrder(openRequest)
            onOpenHandled()
        }
    }

    // 이미 질문이 있을 때 맞춘 결과나 실패를 짧게 알린다 (처음 받을 때는 목록 자리에 보여준다)
    LaunchedEffect(state.notice, state.error) {
        val message = state.notice ?: state.error?.takeIf { state.catalog.questions.isNotEmpty() } ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        Library.clearNotice()
    }

    val id = openId
    if (id != null) {
        Box(Modifier.fillMaxSize()) {
            QuestionScreen(
                state = state,
                id = id,
                origin = origin,
                order = order,
                onNavigate = { openId = it },
                onBack = { openId = null },
            )
            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 64.dp),
            ) { Toast(it.visuals.message) }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(colors.bgCanvas)) {
        Box(Modifier.weight(1f)) {
            when (Tab.entries[tab]) {
                Tab.Today -> HomeScreen(
                    state = state,
                    onOpen = { openInFullOrder(it.id) },
                    onOpenSettings = { tab = Tab.Settings.ordinal },
                )
                Tab.Questions -> ListScreen(
                    state = state,
                    filter = filter,
                    listState = listState,
                    onOpen = { q, ids ->
                        order = ArrayList(ids)
                        origin = Tab.Questions.pageName
                        openId = q.id
                    },
                )
                Tab.Settings -> SettingsScreen(state)
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter)) { Toast(it.visuals.message) }
        }
        TabBar(Tab.entries[tab], onSelect = { tab = it.ordinal })
    }
}

/** 화면 이름 (상단 바 경로에 보인다) */
private val Tab.pageName: String
    get() = when (this) {
        Tab.Today -> "오늘"
        Tab.Questions -> "질문 목록"
        Tab.Settings -> "설정"
    }

/**
 * 아래 탭: 캔버스 면에 선 아이콘과 이름. 알약 모양 표시 대신, 고른 탭 위쪽(지면과 맞닿은 곳)에 짧은 초록 선을 긋는다.
 */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgCanvas)
            .navigationBarsPadding()
            .height(58.dp)
            .selectableGroup(),
    ) {
        for (t in Tab.entries) {
            val on = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(t) }),
                contentAlignment = Alignment.Center,
            ) {
                if (on) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .width(28.dp)
                            .height(2.dp)
                            .background(colors.primary),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        t.icon,
                        contentDescription = null,
                        tint = if (on) colors.primary else colors.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.label,
                        style = TextStyles.num.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                        color = if (on) colors.textMain else colors.textMuted,
                    )
                }
            }
        }
    }
}
