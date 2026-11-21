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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maeilmail.app.data.AREAS
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles

/** 질문 목록의 검색어와 카테고리. 질문을 보고 돌아와도 남도록 위에서 들고 있는다 */
class ListFilter(query: String = "", category: String = "") {
    var query by mutableStateOf(query)
    var category by mutableStateOf(category)
    val active: Boolean get() = query.isNotBlank() || category.isNotEmpty()

    fun reset() {
        query = ""
        category = ""
    }
}

/**
 * 질문 목록: 웹의 좁은 화면 배치를 따른다.
 * 캔버스에는 상단 바와 필터(카테고리)를, 지면(문서)에는 검색창·목록 머리·질문 색인을 둔다.
 * 행을 누르면 질문 화면으로 간다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
    state: Library.State,
    filter: ListFilter,
    listState: LazyListState,
    onOpen: (question: Question, order: List<String>) -> Unit,
) {
    val colors = AppTheme.colors
    val catalog = state.catalog
    val search = remember(catalog.synonyms) { Library.search() }
    val matcher = remember(search, filter.query) { search.matcher(filter.query) }
    val visible = remember(catalog, matcher, filter.category) {
        catalog.questions.filter { q ->
            (filter.category.isEmpty() || q.category == filter.category) && search.matches(matcher, q)
        }
    }
    val order = remember(visible) { visible.map { it.id } }
    val counts = remember(catalog) { catalog.questions.groupingBy { it.category }.eachCount() }

    Column(Modifier.fillMaxSize().background(colors.bgCanvas)) {
        TopNav("질문 목록")
        CategorySelect(
            categories = catalog.categories,
            counts = counts,
            category = filter.category,
            onCategory = { filter.category = it },
            modifier = Modifier.padding(start = PaperPadding, end = PaperPadding, bottom = 12.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth().background(colors.bgPaper)) {
            if (catalog.questions.isEmpty()) {
                FirstLoad(state)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(20.dp))
                    SearchField(filter.query, onChange = { filter.query = it })
                    ListHead(
                        total = catalog.questions.size,
                        shown = visible.size,
                        filtered = filter.active,
                        onReset = filter::reset,
                    )
                    val pull = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = state.syncing,
                        onRefresh = Library::syncNow,
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
                            QuestionIndex(visible, listState, onOpen = { onOpen(it, order) })
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

/** 질문 색인: 영역마다 구분 제목(목록을 내려도 위에 붙어 있다) 아래에 질문 행을 둔다 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionIndex(visible: List<Question>, listState: LazyListState, onOpen: (Question) -> Unit) {
    val colors = AppTheme.colors
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
                QuestionRow(q, first = i == 0, onClick = { onOpen(q) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
private fun ListHead(total: Int, shown: Int, filtered: Boolean, onReset: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(start = PaperPadding, end = PaperPadding - 6.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (filtered) "질문 ${total}개 중 ${shown}개" else "질문 ${total}개",
            style = TextStyles.num,
            color = colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (filtered) LinkBtn("필터 초기화", onClick = onReset)
    }
}

/** 질문 행: 번호 → 제목 → 카테고리. 행 사이는 옅은 선, 누르면 글자보다 조금 넓게 각진 바탕 (웹 .q-row 좁은 화면) */
@Composable
private fun QuestionRow(q: Question, first: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    if (!first) RowRule()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset)
            .clip(Radius.control)
            .clickable(onClick = onClick)
            .padding(horizontal = PaperPadding - RowInset, vertical = 14.dp),
    ) {
        Text(q.label, style = TextStyles.num, color = colors.textMuted)
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
