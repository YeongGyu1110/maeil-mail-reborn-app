package com.maeilmail.app.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.AREAS
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.data.Settings
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles

/**
 * 질문 목록의 검색어와 필터. 질문을 보고 돌아와도 남도록 위에서 들고 있는다.
 * level: 학습 수준 (null이면 전체), starred: 별표한 질문만, received: 받은 질문만 최근에 받은 순서로.
 */
class ListFilter(
    query: String = "",
    category: String = "",
    level: Records.Level? = null,
    starred: Boolean = false,
    received: Boolean = false,
) {
    var query by mutableStateOf(query)
    var category by mutableStateOf(category)
    var level by mutableStateOf(level)
    var starred by mutableStateOf(starred)
    var received by mutableStateOf(received)
    val active: Boolean get() = query.isNotBlank() || category.isNotEmpty() || level != null || starred || received

    fun reset() {
        query = ""
        category = ""
        level = null
        starred = false
        received = false
    }
}

/**
 * 질문 목록: 웹의 좁은 화면 배치를 따른다.
 * 캔버스에는 상단 바와 필터(학습 수준, 받은 질문·별표, 카테고리)를, 지면(문서)에는 검색창·목록 머리·질문 색인을 둔다.
 * 행을 누르면 질문 화면으로 간다. 로그인하면 행에 학습 수준과 별표가 보이고, 별표는 바로 누를 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    state: Library.State,
    filter: ListFilter,
    listState: LazyListState,
    onOpen: (question: Question, order: List<String>) -> Unit,
) {
    val colors = AppTheme.colors
    val catalog = state.catalog
    val records by Records.state.collectAsState()
    val todayId = rememberRevealedTodayId(state)
    val received = remember(state.progress, todayId, catalog) {
        state.progress?.received(todayId, catalog.questions.mapTo(HashSet()) { it.id }).orEmpty()
    }
    // 받은 질문이 없어지면 받은 질문 필터를, 로그아웃하면 학습 기록 필터를 푼다
    if (filter.received && received.isEmpty() && state.progress != null) filter.received = false
    if (!records.signedIn && (filter.level != null || filter.starred)) {
        filter.level = null
        filter.starred = false
    }

    val search = remember(catalog.synonyms) { Library.search() }
    val matcher = remember(search, filter.query) { search.matcher(filter.query) }
    val visible = remember(catalog, matcher, filter.category, filter.level, filter.starred, filter.received, received, records.items) {
        val base = if (filter.received) received.mapNotNull(catalog::question) else catalog.questions
        base.filter { q ->
            (filter.category.isEmpty() || q.category == filter.category) &&
                (filter.level == null || records.level(q.id) == filter.level) &&
                (!filter.starred || records.item(q.id).starred) &&
                search.matches(matcher, q)
        }
    }
    val order = remember(visible) { visible.map { it.id } }
    val categoryCounts = remember(catalog) { catalog.questions.groupingBy { it.category }.eachCount() }
    val levelCounts = remember(catalog, records.items) { catalog.questions.groupingBy { records.level(it.id) }.eachCount() }

    Column(Modifier.fillMaxSize().background(colors.bgCanvas)) {
        TopNav("질문 목록")
        Column(
            Modifier.padding(start = PaperPadding, end = PaperPadding, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (records.signedIn) {
                LevelStats(
                    counts = levelCounts,
                    total = catalog.questions.size,
                    selected = filter.level,
                    onSelect = { filter.level = it },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (received.isNotEmpty()) {
                    ScopeChip(
                        icon = LineIcons.Inbox,
                        label = "받은 질문",
                        count = received.size,
                        pressed = filter.received,
                        onToggle = { filter.received = !filter.received },
                        activeTint = colors.primary,
                    )
                }
                if (records.signedIn) {
                    ScopeChip(
                        icon = if (filter.starred) LineIcons.StarFilled else LineIcons.Star,
                        label = "별표",
                        count = records.items.values.count { it.starred },
                        pressed = filter.starred,
                        onToggle = { filter.starred = !filter.starred },
                        activeTint = colors.star,
                    )
                }
                CategorySelect(
                    categories = catalog.categories,
                    counts = categoryCounts,
                    category = filter.category,
                    onCategory = { filter.category = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().background(colors.bgPaper)) {
            if (catalog.questions.isEmpty()) {
                FirstLoad(state)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(20.dp))
                    SearchField(filter.query, onChange = { filter.query = it })
                    ListHead(
                        total = if (filter.received) received.size else catalog.questions.size,
                        shown = visible.size,
                        received = filter.received,
                        filtered = filter.active,
                        onReset = filter::reset,
                    )
                    val pull = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = state.syncing,
                        onRefresh = {
                            Library.syncNow()
                            Records.refresh(force = true)
                        },
                        state = pull,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        // 둥근 새로고침 표시 대신, 당긴 만큼 차오르는 얇은 막대를 목록 위에 둔다
                        indicator = {
                            val f = pull.distanceFraction
                            if (f > 0f && !state.syncing) {
                                ProgressLine(f.coerceAtMost(1f), Modifier.align(Alignment.TopCenter))
                            }
                        },
                    ) {
                        if (visible.isEmpty()) {
                            EmptyResult(onReset = filter::reset)
                        } else {
                            QuestionIndex(
                                visible = visible,
                                grouped = !filter.received,
                                records = records,
                                listState = listState,
                                onOpen = { onOpen(it, order) },
                            )
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

/** 지금 볼 수 있는 오늘 질문: 알림 시각이 지났으면 오늘 질문 ID, 아니면 null (알림 전에는 받은 질문에도 넣지 않는다) */
@Composable
private fun rememberRevealedTodayId(state: Library.State): String? {
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }
    val notify by settings.notify.collectAsState()
    val lastNotified by settings.lastNotified.collectAsState()
    rememberResumeCount() // 앱으로 돌아올 때 시각을 새로 읽는다
    val now = Reminders.nowKst()
    val today = now.toLocalDate()
    val id = state.progress?.questionOn(today) ?: return null
    return id.takeIf { Reminders.isRevealed(today, now, notify.time, lastNotified) }
}

/**
 * 질문 색인. grouped면 영역마다 구분 제목(목록을 내려도 위에 붙어 있다) 아래에 질문 행을 두고,
 * 아니면(받은 순서로 볼 때) 영역이 섞이므로 구분 제목 없이 한 줄로 둔다 (웹과 같다).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionIndex(
    visible: List<Question>,
    grouped: Boolean,
    records: Records.State,
    listState: LazyListState,
    onOpen: (Question) -> Unit,
) {
    val colors = AppTheme.colors
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (!grouped) {
            item { Spacer(Modifier.height(4.dp)) }
            itemsIndexed(visible, key = { _, q -> q.id }) { i, q ->
                QuestionRow(q, first = i == 0, records = records, onClick = { onOpen(q) })
            }
        } else {
            var firstArea = true
            for (area in AREAS) {
                val inArea = visible.filter { it.area == area.prefix }
                if (inArea.isEmpty()) continue
                // 앞 영역의 마지막 행과 다음 구분 제목 사이를 띄운다 (웹 .list-divider margin-top)
                if (!firstArea) item(key = "gap-${area.prefix}") { Spacer(Modifier.height(20.dp)) }
                firstArea = false
                stickyHeader(key = "area-${area.prefix}") {
                    DividerHead(
                        area.name,
                        count = "${inArea.size}개",
                        modifier = Modifier.background(colors.bgPaper).padding(top = 12.dp),
                    )
                }
                itemsIndexed(inArea, key = { _, q -> q.id }) { i, q ->
                    QuestionRow(q, first = i == 0, records = records, onClick = { onOpen(q) })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * 학습 수준 필터 (웹 좁은 화면의 .stats: 네 칸 격자, 이름 아래에 개수).
 * 고른 칸만 지면색으로 떠오르고 글자가 굵어진다. 고른 칸을 다시 누르면 전체로 돌아간다.
 */
@Composable
private fun LevelStats(
    counts: Map<Records.Level, Int>,
    total: Int,
    selected: Records.Level?,
    onSelect: (Records.Level?) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        StatCell("전체", total, dot = null, pressed = selected == null, onClick = { onSelect(null) })
        for (level in Records.Level.entries) {
            StatCell(
                level.label,
                counts[level] ?: 0,
                dot = levelColors(level).third,
                pressed = selected == level,
                onClick = { onSelect(if (selected == level) null else level) },
            )
        }
    }
}

@Composable
private fun RowScope.StatCell(label: String, count: Int, dot: Color?, pressed: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .weight(1f)
            .height(52.dp)
            .clip(Radius.control)
            .background(if (pressed) colors.bgPaper else Color.Transparent)
            .selectable(selected = pressed, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(7.dp).background(dot, Radius.dot))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = TextStyles.small,
                fontWeight = if (pressed) FontWeight.Bold else FontWeight.Medium,
                color = if (pressed) colors.textMain else colors.textSub,
                maxLines = 1,
            )
        }
        Text(
            "$count",
            style = TextStyles.body.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = colors.textMain,
        )
    }
}

/** 받은 질문·별표처럼 학습 수준과 겹쳐 거르는 범위 (웹 .scope-filter). 켜면 테두리가 생기고 아이콘에 색이 든다 */
@Composable
private fun ScopeChip(
    icon: ImageVector,
    label: String,
    count: Int,
    pressed: Boolean,
    onToggle: () -> Unit,
    activeTint: Color,
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .height(40.dp)
            .clip(Radius.control)
            .background(colors.bgPaper)
            .then(if (pressed) Modifier.border(1.dp, colors.textMuted, Radius.control) else Modifier)
            .toggleable(value = pressed, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (pressed) activeTint else colors.textMuted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = TextStyles.button.copy(fontSize = 13.5.sp),
            fontWeight = if (pressed) FontWeight.Bold else FontWeight.Medium,
            color = colors.textMain,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text("$count", style = TextStyles.num, color = if (pressed) colors.textMain else colors.textMuted)
    }
}

/** 카테고리 고르기 (웹 .cat-trigger + .cat-menu). 캔버스 위에 지면색 칸으로 둔다 */
@Composable
private fun CategorySelect(
    categories: List<String>,
    counts: Map<String, Int>,
    category: String,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(Radius.control)
                .background(colors.bgPaper)
                .clickable(onClickLabel = "카테고리 고르기") { open = true }
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (category.isEmpty()) "모든 카테고리" else category,
                style = TextStyles.button,
                fontWeight = if (category.isEmpty()) FontWeight.Medium else FontWeight.SemiBold,
                color = if (category.isEmpty()) colors.textMain else colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                LineIcons.ChevronDown,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp).rotate(if (open) 180f else 0f),
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
        ) {
            CategoryOption("모든 카테고리", counts.values.sum(), selected = category.isEmpty()) { onCategory(""); open = false }
            for (c in categories) {
                CategoryOption(c, counts[c] ?: 0, selected = c == category) { onCategory(c); open = false }
            }
        }
    }
}

@Composable
private fun CategoryOption(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                name,
                style = TextStyles.button,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.primary else colors.textSub,
            )
        },
        trailingIcon = { Text("$count", style = TextStyles.num, color = colors.textMuted) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
    )
}

/** 검색창: 옅은 면, 모서리 4dp, 누르면 초록 테두리 (웹 .search input) */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = AppTheme.colors
    val focus = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .padding(horizontal = PaperPadding)
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.bgSubtle, Radius.control)
            .then(if (focused) Modifier.border(1.5.dp, colors.primary, Radius.control) else Modifier)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LineIcons.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("질문, 키워드 검색", style = TextStyles.body, color = colors.textMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyles.body.copy(color = colors.textMain),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconBtn(LineIcons.Close, contentDescription = "검색어 지우기", onClick = { onChange("") }, tint = colors.textMuted)
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

/** 목록 머리: 개수와 필터 초기화 (웹 .list-head) */
@Composable
private fun ListHead(total: Int, shown: Int, received: Boolean, filtered: Boolean, onReset: () -> Unit) {
    val colors = AppTheme.colors
    val noun = if (received) "받은 질문" else "질문"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(start = PaperPadding, end = PaperPadding - 6.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (shown == total) "$noun ${total}개" else "$noun ${total}개 중 ${shown}개",
            style = TextStyles.num,
            color = colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (filtered) LinkBtn("필터 초기화", onClick = onReset)
    }
}

/**
 * 질문 행: 번호(+ 학습 수준, 별표) → 제목 → 카테고리 (웹 .q-row 좁은 화면).
 * 행 사이는 옅은 선, 누르면 글자보다 조금 넓게 각진 바탕이 깔린다. 별표는 엄지가 닿는 오른쪽 끝에 둔다.
 */
@Composable
private fun QuestionRow(q: Question, first: Boolean, records: Records.State, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val item = records.item(q.id)
    if (!first) RowRule()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset)
            .clip(Radius.control)
            .clickable(onClick = onClick)
            .padding(horizontal = PaperPadding - RowInset, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(q.label, style = TextStyles.num, color = colors.textMuted, modifier = Modifier.weight(1f))
            if (item.level != Records.Level.NEW) LevelBadge(item.level)
            if (records.signedIn) {
                StarButton(
                    starred = item.starred,
                    onToggle = { Records.setStarred(q.id, !item.starred) },
                    modifier = Modifier.offset(x = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(q.title, style = TextStyles.rowTitle, color = colors.textMain, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (q.categoryName.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                q.categoryName,
                style = TextStyles.small,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 처음 열어 받아 둔 질문이 없을 때: 받는 중이면 진행을, 실패했으면 다시 시도를 보여준다 */
@Composable
private fun FirstLoad(state: Library.State) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.error != null && !state.syncing) {
            Text("질문을 받지 못했어요", style = TextStyles.rowTitle, color = colors.textMain)
            Spacer(Modifier.height(6.dp))
            Text(state.error, style = TextStyles.small, color = colors.textMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Btn("다시 시도", onClick = Library::syncNow, kind = BtnKind.Soft)
        } else {
            Text("질문을 받고 있어요", style = TextStyles.rowTitle, color = colors.textMain)
            Spacer(Modifier.height(4.dp))
            Text("처음 한 번만 받아 두면 인터넷 없이도 볼 수 있어요", style = TextStyles.small, color = colors.textMuted)
            Spacer(Modifier.height(20.dp))
            val p = state.progressCount
            ProgressLine(
                progress = p?.takeIf { it.second > 0 }?.let { it.first.toFloat() / it.second },
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            if (p != null && p.second > 0) {
                Spacer(Modifier.height(8.dp))
                Text("${p.first} / ${p.second}", style = TextStyles.num, color = colors.textMuted)
            }
        }
    }
}

/** 맞는 질문이 없을 때 (웹 .empty) */
@Composable
private fun EmptyResult(onReset: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("맞는 질문이 없어요", style = TextStyles.body, color = colors.textMuted)
        Spacer(Modifier.height(10.dp))
        Btn("검색과 필터 지우기", onClick = onReset, kind = BtnKind.Soft)
    }
}
