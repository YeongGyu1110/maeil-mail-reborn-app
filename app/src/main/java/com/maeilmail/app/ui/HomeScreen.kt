package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.AREAS
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
 * 캔버스에는 상단 바(로고·검색·설정)와 자주 쓰는 필터 칩을, 지면에는 오늘의 질문과 목록을 둔다.
 * 걸린 필터는 칩과 목록 머리에 늘 보이고, 목록 머리는 목록을 내려도 위에 붙어 있다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: Library.State,
    today: Today,
    filter: ListFilter,
    listState: LazyListState,
    onOpen: (question: Question, order: List<String>, from: String) -> Unit,
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

    val search = remember(catalog.synonyms) { Library.search() }
    val matcher = remember(search, filter.query) { search.matcher(filter.query) }
    val base = remember(catalog, received, filter.received) {
        if (filter.received) received.mapNotNull(catalog::question) else catalog.questions
    }
    val visible = remember(base, matcher, filter.category, filter.level, filter.starred, records.items) {
        base.filter { q ->
            (filter.category.isEmpty() || q.category == filter.category) &&
                (filter.level == null || records.level(q.id) == filter.level) &&
                (!filter.starred || records.item(q.id).starred) &&
                search.matches(matcher, q, records.item(q.id).memo.orEmpty())
        }
    }
    val order = remember(visible) { visible.map { it.id } }
    val fullOrder = remember(catalog) { catalog.questions.map { it.id } }

    // 검색어나 필터를 바꾸면 목록 맨 위로
    LaunchedEffect(filter) {
        snapshotFlow { listOf(filter.query, filter.category, filter.level, filter.starred, filter.received) }
            .drop(1)
            .collect { listState.scrollToItem(0) }
    }

    // 뒤로 가기: 검색 → 필터 → 앱 나가기
    BackHandler(enabled = filter.searching || filter.active) {
        if (filter.searching) filter.closeSearch() else filter.reset()
    }

    Column(Modifier.fillMaxSize().background(colors.bgCanvas).imePadding()) {
        HomeBar(filter, signedIn = records.signedIn, onSettings = onSettings)
        FilterChips(filter, received.size, records)

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
                            } else if (filter.query.isBlank() && !filter.received && todayQuestion != null && !opened) {
                                // 필터가 남아 있어도 오늘의 질문을 놓치지 않게 한 줄은 남긴다
                                item(key = "today") {
                                    TodayLine(today.label, todayQuestion) { onOpen(todayQuestion, fullOrder, ListFilter.DEFAULT_NAME) }
                                }
                            }
                            stickyHeader(key = "head") {
                                ListHead(state, filter, shown = visible.size, base = base, records = records)
                            }
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
}

/** 상단 바: 평소에는 로고·검색·설정, 검색 아이콘을 누르면 그 자리가 검색창이 된다 */
@Composable
private fun HomeBar(filter: ListFilter, signedIn: Boolean, onSettings: () -> Unit) {
    val focus = remember { FocusRequester() }
    // 아이콘을 눌러 열 때만 자판을 올린다 (질문을 보고 돌아왔을 때는 올리지 않는다)
    var takeFocus by remember { mutableStateOf(false) }
    TopBar {
        if (filter.searching) {
            IconBtn(LineIcons.ArrowLeft, contentDescription = "검색 닫기", onClick = filter::closeSearch)
            SearchField(
                filter.query,
                placeholder = if (signedIn) "질문, 키워드, 내 메모 검색" else "질문, 키워드 검색",
                onChange = { filter.query = it },
                focus = focus,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            LaunchedEffect(Unit) {
                if (takeFocus) focus.requestFocus()
                takeFocus = false
            }
        } else {
            Brand(onClick = filter::reset)
            Spacer(Modifier.weight(1f))
            IconBtn(
                LineIcons.Search,
                contentDescription = "질문 검색",
                onClick = {
                    takeFocus = true
                    filter.searching = true
                },
            )
            IconBtn(LineIcons.Sliders, contentDescription = "설정", onClick = onSettings)
        }
    }
}

/** 검색창: 지면색 면, 모서리 4dp, 적는 중이면 초록 테두리 (웹 .search input) */
@Composable
private fun SearchField(
    query: String,
    placeholder: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .heightIn(min = 48.dp)
            .background(colors.bgPaper, Radius.control)
            .then(if (focused) Modifier.border(1.5.dp, colors.primary, Radius.control) else Modifier)
            .padding(start = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LineIcons.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (query.isEmpty()) {
                Text(placeholder, style = TextStyles.body, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyles.body.copy(color = colors.textMain),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            IconBtn(LineIcons.Close, contentDescription = "검색어 지우기", onClick = { onChange("") }, tint = colors.textMuted)
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

/** 자주 쓰는 필터: 늘 보이고 한 번 눌러 걸고 다시 눌러 푼다. 고른 칩은 진하게 칠한다. 학습 수준은 목록 머리에서 고르므로 여기 두지 않는다 */
@Composable
private fun FilterChips(filter: ListFilter, receivedCount: Int, records: Records.State) {
    val colors = AppTheme.colors
    ChipRow(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Chip("전체", count = null, pressed = !filter.narrowed, onToggle = filter::clearScopes)
        if (receivedCount > 0) {
            Chip("받은 질문", receivedCount, filter.received, onToggle = { filter.received = !filter.received })
        }
        if (records.signedIn) {
            Chip("별표", records.items.values.count { it.starred }, filter.starred, onToggle = { filter.starred = !filter.starred }) { tint ->
                Icon(
                    if (filter.starred) LineIcons.StarFilled else LineIcons.Star,
                    contentDescription = null,
                    tint = if (filter.starred) colors.star else tint,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(
    label: String,
    count: Int?,
    pressed: Boolean,
    onToggle: () -> Unit,
    icon: (@Composable RowScope.(tint: Color) -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val ink = if (pressed) colors.bgPaper else colors.textMain
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(Radius.control)
            .background(if (pressed) colors.textMain else colors.bgPaper)
            .toggleable(value = pressed, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        icon?.invoke(this, ink)
        Text(
            label,
            style = TextStyles.button.copy(fontSize = 13.5.sp),
            fontWeight = if (pressed) FontWeight.Bold else FontWeight.Medium,
            color = ink,
            maxLines = 1,
        )
        if (count != null) Text("$count", style = TextStyles.num, color = if (pressed) ink else colors.textMuted, maxLines = 1)
    }
}

/** 칩을 한 줄에 채워 놓는다. 남는 폭은 고르게 나누고, 다 들어가지 않으면(좁은 화면, 큰 글자) 줄을 바꾼다 */
@Composable
private fun ChipRow(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = 6.dp.roundToPx()
        val width = constraints.maxWidth
        val wants = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(width) }
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
            val extra = width - row.sumOf { wants[it] } - gap * (row.size - 1)
            row.mapIndexed { n, i ->
                val w = wants[i] + extra / row.size + if (n == row.lastIndex) extra % row.size else 0
                measurables[i].measure(Constraints.fixedWidth(w))
            }
        }
        val heights = placed.map { row -> row.maxOf { it.height } }
        layout(width, heights.sum() + gap * (rows.size - 1).coerceAtLeast(0)) {
            var y = 0
            placed.forEachIndexed { r, row ->
                var x = 0
                for (p in row) {
                    p.placeRelative(x, y)
                    x += p.width + gap
                }
                y += heights[r] + gap
            }
        }
    }
}

/**
 * 목록 머리: 왼쪽에 개수, 오른쪽에 학습 수준과 카테고리를 고르는 버튼.
 * 고른 것은 초록 이름으로 바뀌어, 목록을 내려도 무엇이 걸렸는지 보인다.
 */
@Composable
private fun ListHead(state: Library.State, filter: ListFilter, shown: Int, base: List<Question>, records: Records.State) {
    val colors = AppTheme.colors
    val catalog = state.catalog
    Column(Modifier.fillMaxWidth().background(colors.bgPaper)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = PaperPadding, end = PaperPadding - 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (shown == base.size) "${if (filter.received) "받은 질문" else "질문"} ${shown}개" else "${base.size}개 중 ${shown}개",
                style = TextStyles.num,
                color = colors.textMuted,
                maxLines = 1,
            )
            Row(Modifier.weight(1f).padding(start = 8.dp), horizontalArrangement = Arrangement.End) {
                if (records.signedIn) {
                    Picker(filter.level?.label, "학습 수준") { close ->
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
                Picker(filter.category.ifEmpty { null }, "카테고리", Modifier.weight(1f, fill = false)) { close ->
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
        HorizontalDivider(color = colors.rule)
    }
}

/** 누르면 아래로 펼쳐지는 고르기 버튼 (웹 .cat-trigger + .cat-menu) */
@Composable
private fun Picker(value: String?, label: String, modifier: Modifier = Modifier, menu: @Composable (close: () -> Unit) -> Unit) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .clip(Radius.control)
                .clickable(role = Role.DropdownList, onClickLabel = "$label 고르기") { open = true }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                value ?: label,
                style = TextStyles.button.copy(fontSize = 13.5.sp),
                fontWeight = if (value != null) FontWeight.Bold else FontWeight.Medium,
                color = if (value != null) colors.primary else colors.textSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                LineIcons.ChevronDown,
                contentDescription = null,
                tint = if (value != null) colors.primary else colors.textMuted,
                modifier = Modifier.size(15.dp),
            )
        }
        // 지면과 같은 색이라 옅은 테두리와 그림자로 떠 있는 것을 드러낸다 (웹 .cat-menu)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = colors.bgPaper,
            border = BorderStroke(1.dp, colors.rule),
            shadowElevation = 8.dp,
            shape = Radius.control,
            modifier = Modifier.widthIn(min = 200.dp),
        ) { menu { open = false } }
    }
}

@Composable
private fun Option(name: String, count: Int, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val colors = AppTheme.colors
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dot != null) {
                    Box(Modifier.size(7.dp).background(dot, Radius.dot))
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    name,
                    style = TextStyles.button,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) colors.primary else colors.textSub,
                )
            }
        },
        trailingIcon = {
            Text(
                "$count",
                style = TextStyles.num,
                color = colors.textMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 28.dp),
            )
        },
        onClick = onClick,
    )
}
