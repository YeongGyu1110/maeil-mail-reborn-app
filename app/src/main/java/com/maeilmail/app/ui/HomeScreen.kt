package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.AREAS
import com.maeilmail.app.data.Catalog
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.data.Settings
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.reminder.Today
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles
import kotlinx.coroutines.flow.drop

/**
 * 첫 화면: 오늘의 질문과 질문 목록을 한 화면에 둔다.
 * 캔버스에는 상단 바(로고·검색·설정)와 필터 한 줄을, 지면에는 오늘의 질문과 목록을 둔다.
 * 캔버스는 목록을 내려도 그대로라 걸린 필터가 늘 보인다. 목록 머리(개수, 필터 초기화)는 목록과 함께 올라간다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: Library.State,
    today: Today,
    filter: ListFilter,
    listState: LazyListState,
    onOpen: (question: Question, order: List<String>, from: String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = AppTheme.colors
    val catalog = state.catalog
    val records by Records.state.collectAsState()
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }
    val notify by settings.notify.collectAsState()
    val openedId by settings.opened.collectAsState()
    val noticeHidden by settings.noticeHidden.collectAsState()

    val resumed = rememberResumeCount()
    val blocked = remember(resumed) { Reminders.problems(context).firstOrNull { it.blocks } }
    val fix = rememberProblemFix()
    // 처음 열었을 때 한 번만 알림 권한을 묻는다
    val requestPermission = rememberNotificationPermissionRequest()
    LaunchedEffect(Unit) {
        if (notify.enabled && !Reminders.canNotify(context) && !settings.askedNotificationPermission) requestPermission()
    }
    LaunchedEffect(blocked) { if (blocked == null) settings.setNoticeHidden(false) }

    // 알림 시각 전에는 오늘 질문을 받은 질문에도 넣지 않는다
    val revealedId = today.id.takeIf { today.isToday }
    val received = remember(state.progress, revealedId, catalog) {
        state.progress?.received(revealedId, catalog.questions.mapTo(HashSet()) { it.id }).orEmpty()
    }
    // 받은 질문이 없어지면 받은 질문 필터를, 로그아웃하면 학습 기록 필터를 푼다
    if (filter.received && received.isEmpty() && state.progress != null) filter.received = false
    if (!records.signedIn && (filter.level != null || filter.starred)) {
        filter.level = null
        filter.starred = false
    }

    val base = remember(catalog, received, filter.received) {
        if (filter.received) received.mapNotNull(catalog::question) else catalog.questions
    }
    val visible = remember(base, filter.category, filter.level, filter.starred, records.items) {
        base.filter { q ->
            (filter.category.isEmpty() || q.category == filter.category) &&
                (filter.level == null || records.level(q.id) == filter.level) &&
                (!filter.starred || records.item(q.id).starred)
        }
    }
    val order = remember(visible) { visible.map { it.id } }
    val fullOrder = remember(catalog) { catalog.questions.map { it.id } }

    // 필터를 바꾸면 목록 맨 위로
    LaunchedEffect(filter) {
        snapshotFlow { listOf(filter.category, filter.level, filter.starred, filter.received) }
            .drop(1)
            .collect { listState.scrollToItem(0) }
    }

    // 뒤로 가기: 필터 → 앱 나가기
    BackHandler(enabled = filter.active, onBack = filter::reset)

    var menu by remember { mutableStateOf<OpenMenu?>(null) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.bgCanvas)) {
            HomeBar(onBrand = filter::reset, onSearch = onSearch, onSettings = onSettings)
            FilterBar(catalog, filter, received.size, base, records) { menu = it }

            Box(Modifier.weight(1f).fillMaxWidth().background(colors.bgPaper)) {
                when {
                    !state.loaded -> Unit
                    catalog.questions.isEmpty() -> FirstLoad(state)
                    else -> {
                        val pull = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = state.syncing,
                            onRefresh = {
                                Library.syncNow()
                                Records.refresh(force = true)
                            },
                            state = pull,
                            modifier = Modifier.fillMaxSize(),
                            // 둥근 새로고침 표시 대신, 당긴 만큼 차오르는 얇은 막대를 목록 위에 둔다
                            indicator = {
                                val f = pull.distanceFraction
                                if (f > 0f && !state.syncing) {
                                    ProgressLine(f.coerceAtMost(1f), Modifier.align(Alignment.TopCenter))
                                }
                            },
                        ) {
                            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            val todayQuestion = today.id?.let(catalog::question)
                            val opened = today.id != null && today.id == openedId
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = bottom + 24.dp),
                            ) {
                                if (blocked != null && notify.enabled && !noticeHidden && !filter.active) {
                                    item(key = "notice") {
                                        NoticeBar(
                                            text = "알림이 꺼져 있어 질문이 와도 알 수 없어요",
                                            action = "켜기",
                                            onAction = { fix(blocked) },
                                            onClose = { settings.setNoticeHidden(true) },
                                        )
                                    }
                                }
                                if (!filter.active) {
                                    item(key = "today") {
                                        TodayBlock(today, todayQuestion, opened, records) { onOpen(it, fullOrder, ListFilter.DEFAULT_NAME) }
                                    }
                                } else if (!filter.received && todayQuestion != null && !opened) {
                                    // 필터가 남아 있어도 오늘의 질문을 놓치지 않게 한 줄은 남긴다
                                    item(key = "today") {
                                        TodayLine(today.label, todayQuestion) { onOpen(todayQuestion, fullOrder, ListFilter.DEFAULT_NAME) }
                                    }
                                }
                                item(key = "head") { ListHead(filter, shown = visible.size, total = base.size) }
                                if (filter.received && filter.name == "받은 질문" && records.signedIn) {
                                    item(key = "progress") { StudyProgress(received, records) }
                                }
                                if (visible.isEmpty()) {
                                    item(key = "empty") { EmptyResult(onReset = filter::reset) }
                                } else {
                                    questionIndex(visible, grouped = !filter.received, records = records) { onOpen(it, order, filter.name) }
                                }
                            }
                        }
                    }
                }
                // 저장소와 맞추는 동안 지면 맨 위에 얇은 막대를 둔다
                if (state.syncing && catalog.questions.isNotEmpty()) {
                    val p = state.progressCount
                    ProgressLine(
                        progress = p?.takeIf { it.second > 0 }?.let { it.first.toFloat() / it.second },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        ScreenMenu(menu, onClose = { menu = null })
    }
}

/** 상단 바: 로고(누르면 필터를 풀고 처음 목록으로), 검색 화면, 설정 */
@Composable
private fun HomeBar(onBrand: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    TopBar {
        Brand(onClick = onBrand)
        Spacer(Modifier.weight(1f))
        IconBtn(LineIcons.Search, contentDescription = "질문 검색", onClick = onSearch)
        IconBtn(LineIcons.Sliders, contentDescription = "설정", onClick = onSettings)
    }
}

/**
 * 필터: 받은 질문 · 별표 · 학습 수준 · 카테고리를 한 줄에 둔다 (웹 좁은 화면의 .scopes + .cat-trigger).
 * 받은 질문과 별표는 한 번 눌러 걸고 다시 눌러 풀고, 학습 수준과 카테고리는 펼쳐서 고른다. 서로 겹쳐 거른다.
 */
@Composable
private fun FilterBar(
    catalog: Catalog,
    filter: ListFilter,
    receivedCount: Int,
    base: List<Question>,
    records: Records.State,
    onMenu: (OpenMenu) -> Unit,
) {
    val colors = AppTheme.colors
    ChipRow(Modifier.fillMaxWidth().padding(start = PaperPadding, end = PaperPadding, bottom = 5.dp)) {
        if (receivedCount > 0) {
            Chip(pressed = filter.received, onToggle = { filter.received = !filter.received }) {
                ChipText("받은 질문", filter.received)
                ChipCount(receivedCount, filter.received)
            }
        }
        if (records.signedIn) {
            Chip(pressed = filter.starred, onToggle = { filter.starred = !filter.starred }) {
                Icon(
                    if (filter.starred) LineIcons.StarFilled else LineIcons.Star,
                    contentDescription = null,
                    tint = if (filter.starred) colors.star else colors.textMuted,
                    modifier = Modifier.size(14.dp),
                )
                ChipText("별표", filter.starred)
                ChipCount(records.items.values.count { it.starred }, filter.starred)
            }
            Picker(filter.level?.label, "학습 수준", onMenu, dot = filter.level?.let { levelColors(it).third }) { close ->
                val counts = base.groupingBy { records.level(it.id) }.eachCount()
                Option("모든 수준", base.size, selected = filter.level == null) { filter.level = null; close() }
                for (level in Records.Level.entries) {
                    Option(level.label, counts[level] ?: 0, selected = filter.level == level, dot = levelColors(level).third) {
                        filter.level = level
                        close()
                    }
                }
            }
        }
        // 칩에는 영역 이름(백엔드)을 빼고 보인다. 행의 번호로 영역을 알 수 있고, 좁은 자리에 카테고리 이름이 더 들어간다
        val category = AREAS.fold(filter.category) { name, area -> name.removePrefix(area.categoryLabel) }
        Picker(category.ifEmpty { null }, "카테고리", onMenu) { close ->
            val counts = base.groupingBy { it.category }.eachCount()
            Option("모든 카테고리", base.size, selected = filter.category.isEmpty()) { filter.category = ""; close() }
            for (area in AREAS) {
                val names = catalog.categories.filter { c -> catalog.questions.any { it.area == area.prefix && it.category == c } }
                if (names.isEmpty()) continue
                Text(
                    area.name,
                    style = TextStyles.num,
                    color = colors.textMuted,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
                )
                for (c in names) {
                    Option(c.removePrefix(area.categoryLabel), counts[c] ?: 0, selected = c == filter.category) {
                        filter.category = c
                        close()
                    }
                }
            }
        }
    }
}

/** 칩의 면 (웹 .scope-filter): 캔버스보다 한 단계 진한 옅은 면. 고르면 면을 더 칠하지 않고 굵은 글자와 선 하나로 알린다 */
@Composable
private fun Modifier.chipSurface(pressed: Boolean): Modifier = this
    .minimumInteractiveComponentSize()
    .heightIn(min = ControlHeight)
    .clip(Radius.control)
    .background(AppTheme.colors.bgHover)
    .then(if (pressed) Modifier.border(1.dp, AppTheme.colors.textMuted, Radius.control) else Modifier)

private val ChipPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

/** 눌러 걸고 다시 눌러 푸는 칩 */
@Composable
private fun Chip(pressed: Boolean, onToggle: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .chipSurface(pressed)
            .toggleable(value = pressed, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(ChipPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

@Composable
private fun ChipText(label: String, pressed: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Text(
        label,
        style = TextStyles.button.copy(fontSize = 13.5.sp),
        fontWeight = if (pressed) FontWeight.Bold else FontWeight.Medium,
        color = if (pressed) colors.textMain else colors.textSub,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun ChipCount(count: Int, pressed: Boolean) {
    val colors = AppTheme.colors
    Text("$count", style = TextStyles.num, color = if (pressed) colors.textMain else colors.textMuted, maxLines = 1)
}

/**
 * 칩을 왼쪽부터 글자 폭만큼 놓고, 마지막 칩(카테고리)은 남는 폭을 채운다 (웹 grid-template-columns: auto minmax(0, 1fr)).
 * 다 들어가지 않으면(좁은 화면, 큰 글자) 줄을 바꾼다. 마지막 칩은 이름이 길어도 줄을 바꾸지 않고 줄여 보인다.
 */
@Composable
private fun ChipRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val width = constraints.maxWidth
        val natural = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(width) }
        val wants = natural.mapIndexed { i, w -> if (i == natural.lastIndex) w.coerceAtMost(88.sp.roundToPx()) else w }
        val rows = ArrayList<MutableList<Int>>()
        var used = 0
        wants.forEachIndexed { i, want ->
            if (rows.isEmpty() || used + gap + want > width) {
                rows += mutableListOf(i)
                used = want
            } else {
                rows.last() += i
                used += gap + want
            }
        }
        val placed = rows.map { row ->
            val extra = (width - row.sumOf { wants[it] } - gap * (row.size - 1)).coerceAtLeast(0)
            row.map { i -> measurables[i].measure(Constraints.fixedWidth(wants[i] + if (i == wants.lastIndex) extra else 0)) }
        }
        val heights = placed.map { row -> row.maxOf { it.height } }
        layout(width, heights.sum()) {
            var y = 0
            placed.forEachIndexed { r, row ->
                var x = 0
                for (p in row) {
                    p.placeRelative(x, y)
                    x += p.width + gap
                }
                // 칩 둘레에 누르는 자리(투명)가 있어 줄 사이를 더 띄우지 않는다
                y += heights[r]
            }
        }
    }
}

/** 목록 머리 (웹 .list-head): 왼쪽에 개수, 필터가 걸려 있으면 오른쪽에 필터 초기화 */
@Composable
private fun ListHead(filter: ListFilter, shown: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = PaperPadding, end = PaperPadding - LinkBtnPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (shown == total) "${if (filter.received) "받은 질문" else "질문"} ${shown}개" else "${total}개 중 ${shown}개",
            style = TextStyles.num,
            color = AppTheme.colors.textMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (filter.active) LinkBtn("필터 초기화", onClick = filter::reset)
    }
}

/** 눌러 펼쳐 고르는 칩 (웹 .cat-trigger). 고르면 그 이름을 보인다. 펼친 목록은 화면이 그린다 (ScreenMenu) */
@Composable
private fun Picker(
    value: String?,
    label: String,
    onMenu: (OpenMenu) -> Unit,
    dot: Color? = null,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    val colors = AppTheme.colors
    val bounds = remember { arrayOf(Rect.Zero) }
    Row(
        Modifier
            .chipSurface(pressed = value != null)
            .trackBounds { bounds[0] = it }
            .clickable(role = Role.DropdownList, onClickLabel = "$label 고르기") { onMenu(OpenMenu(label, bounds[0], menu)) }
            .padding(ChipPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(7.dp).background(dot, Radius.dot))
                Spacer(Modifier.width(6.dp))
            }
            ChipText(value ?: label, pressed = value != null, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
        }
        Icon(LineIcons.ChevronDown, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
    }
}

/** 펼친 목록의 한 줄 (웹 .cat-option). 줄이 붙어 있어 누르는 자리를 둘레로 넓힐 수 없으므로 웹의 32px보다 높은 40dp다 */
@Composable
private fun Option(name: String, count: Int, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(7.dp).background(dot, Radius.dot))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            name,
            style = TextStyles.button,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.primary else colors.textSub,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count",
            style = TextStyles.num,
            color = colors.textMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 20.dp).widthIn(min = 28.dp),
        )
    }
}
