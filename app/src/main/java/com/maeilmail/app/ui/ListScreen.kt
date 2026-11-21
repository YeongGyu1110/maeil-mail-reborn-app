package com.maeilmail.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maeilmail.app.data.AREAS
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Mono
import com.maeilmail.app.ui.theme.Radius

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

/** 질문 목록: 질문을 찾아 고르는 화면. 행을 누르면 질문 화면으로 간다 */
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

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgPaper)
            .statusBarsPadding(),
    ) {
        Header(state)
        SearchField(filter.query, onChange = { filter.query = it })
        FilterRow(
            categories = catalog.categories,
            category = filter.category,
            onCategory = { filter.category = it },
            total = catalog.questions.size,
            shown = visible.size,
            filtered = filter.active,
            onReset = filter::reset,
        )
        HorizontalDivider(color = colors.rule)

        when {
            catalog.questions.isEmpty() -> FirstLoad(state)
            else -> PullToRefreshBox(
                isRefreshing = state.syncing,
                onRefresh = Library::syncNow,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (visible.isEmpty()) {
                    EmptyResult(onReset = filter::reset)
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        // 번호순 목록에서 영역이 시작하는 자리에 구분 제목을 둔다
                        for (area in AREAS) {
                            val inArea = visible.filter { it.area == area.prefix }
                            if (inArea.isEmpty()) continue
                            stickyHeader(key = "area-${area.prefix}") {
                                AreaHeader(area.name, inArea.size)
                            }
                            items(inArea, key = { it.id }) { q ->
                                QuestionRow(q, onClick = { onOpen(q, order) })
                            }
                        }
                        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars).height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: Library.State) {
    val colors = AppTheme.colors
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp)) {
        Text("매일메일", style = MaterialTheme.typography.labelMedium, color = colors.primary)
        Spacer(Modifier.height(2.dp))
        Text("질문 목록", style = MaterialTheme.typography.headlineSmall, color = colors.textMain)
    }
    // 이미 질문이 있을 때 맞추는 중이면 얇은 막대로만 알린다 (처음 받을 때는 아래 FirstLoad가 보여준다)
    if (state.syncing && state.catalog.questions.isNotEmpty()) {
        val p = state.progress
        if (p != null && p.second > 0) {
            LinearProgressIndicator(
                progress = { p.first.toFloat() / p.second },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = colors.primary,
                trackColor = colors.rule,
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = AppTheme.colors
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .background(colors.bgSubtle, Radius.control)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("질문, 키워드 검색", style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textMain),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "검색어 지우기", tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun FilterRow(
    categories: List<String>,
    category: String,
    onCategory: (String) -> Unit,
    total: Int,
    shown: Int,
    filtered: Boolean,
    onReset: () -> Unit,
) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier
                    .clickable(onClickLabel = "카테고리 고르기") { open = true }
                    .background(if (category.isEmpty()) colors.bgPaper else colors.primaryLight, Radius.control)
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (category.isEmpty()) "모든 카테고리" else category,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (category.isEmpty()) colors.textSub else colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.textMuted)
            }
            // 바탕과 같은 색이라 테두리와 그림자로 떠 있는 것을 드러낸다 (웹의 펼침 메뉴와 같다)
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = colors.bgPaper,
                border = BorderStroke(1.dp, colors.rule),
                shadowElevation = 12.dp,
                shape = Radius.control,
            ) {
                DropdownMenuItem(text = { Text("모든 카테고리") }, onClick = { onCategory(""); open = false })
                for (c in categories) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                c,
                                color = if (c == category) colors.primary else colors.textMain,
                                fontWeight = if (c == category) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = { onCategory(c); open = false },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (filtered) "${total}개 중 ${shown}개" else "${total}개",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        if (filtered) {
            TextButton(onClick = onReset) { Text("초기화", color = colors.primary) }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun AreaHeader(name: String, count: Int) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgSubtle)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = colors.textSub)
        Spacer(Modifier.width(6.dp))
        Text("${count}개", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
    }
}

@Composable
private fun QuestionRow(q: Question, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.label, fontFamily = Mono, fontSize = 12.sp, color = colors.textMuted)
            if (q.categoryName.isNotEmpty()) {
                Text(" · ", fontSize = 12.sp, color = colors.textMuted)
                Text(
                    q.categoryName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(q.title, style = MaterialTheme.typography.bodyLarge, color = colors.textMain, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.rule)
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
            Text("질문을 받지 못했어요", style = MaterialTheme.typography.titleMedium, color = colors.textMain)
            Spacer(Modifier.height(6.dp))
            Text(state.error, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = Library::syncNow) { Text("다시 시도") }
        } else {
            Text("질문을 받고 있어요", style = MaterialTheme.typography.titleMedium, color = colors.textMain)
            Spacer(Modifier.height(4.dp))
            Text("처음 한 번만 받아 두면 인터넷 없이도 볼 수 있어요", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            val p = state.progress
            if (p != null && p.second > 0) {
                LinearProgressIndicator(
                    progress = { p.first.toFloat() / p.second },
                    modifier = Modifier.fillMaxWidth(0.7f),
                    color = colors.primary,
                    trackColor = colors.rule,
                )
                Spacer(Modifier.height(6.dp))
                Text("${p.first} / ${p.second}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.7f), color = colors.primary, trackColor = colors.rule)
            }
        }
    }
}

@Composable
private fun EmptyResult(onReset: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(PaddingValues(32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("맞는 질문이 없어요", style = MaterialTheme.typography.titleMedium, color = colors.textMain)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onReset) { Text("검색과 필터 지우기") }
    }
}
