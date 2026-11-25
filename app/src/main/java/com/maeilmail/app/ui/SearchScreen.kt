package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.data.SearchHistory
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles

/**
 * 검색 화면: 검색어가 비어 있으면 최근 검색어를, 적으면 그에 맞는 질문을 바로 보인다.
 * 첫 화면의 필터와 상관없이 전체 질문에서 찾는다. 찾는 규칙(동의어 등)은 data/Search와 같다.
 *
 * 최근 검색어에는 적는 중간의 글자가 쌓이지 않게, 자판의 검색을 누르거나 결과를 열었을 때만 남긴다. 결과가 없는 검색어는 남기지 않는다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    state: Library.State,
    query: String,
    onQuery: (String) -> Unit,
    listState: LazyListState,
    onOpen: (question: Question, order: List<String>, from: String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val history = remember { SearchHistory.get(context) }
    val entries by history.entries.collectAsState()
    val records by Records.state.collectAsState()
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }

    val catalog = state.catalog
    val search = remember(catalog.synonyms) { Library.search() }
    val matcher = remember(search, query) { search.matcher(query) }
    val results = remember(catalog, matcher, records.items) {
        if (matcher == null) emptyList()
        else catalog.questions.filter { search.matches(matcher, it, records.item(it.id).memo.orEmpty()) }
    }
    val order = remember(results) { results.map { it.id } }

    fun keep() {
        if (results.isNotEmpty()) history.add(query)
    }
    fun open(q: Question) {
        keep()
        focusManager.clearFocus()
        onOpen(q, order, "'${query.trim()}' 검색")
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        history.prune()
        // 처음 열 때만 자판을 올린다 (결과를 보고 돌아왔을 때는 올리지 않는다)
        if (query.isEmpty()) focus.requestFocus()
    }
    // 결과를 손으로 밀면 자판을 내려 결과를 넓게 보인다
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) focusManager.clearFocus() }
    }

    Column(Modifier.fillMaxSize().background(colors.bgCanvas).imePadding()) {
        TopBar {
            IconBtn(LineIcons.ArrowLeft, contentDescription = "뒤로", onClick = onBack)
            SearchField(
                query,
                placeholder = if (records.signedIn) "질문, 키워드, 내 메모 검색" else "질문, 키워드 검색",
                onChange = onQuery,
                onSearch = {
                    keep()
                    focusManager.clearFocus()
                },
                onClear = {
                    onQuery("")
                    focus.requestFocus()
                },
                focus = focus,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
        }
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(Modifier.weight(1f).fillMaxWidth().background(colors.bgPaper)) {
            if (matcher == null) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
                    if (entries.isEmpty()) {
                        item { EmptyText("아직 검색한 것이 없어요", "번호, 제목, 내 메모에서 찾아요") }
                    } else {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(start = PaperPadding, end = PaperPadding - BtnPadding, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("최근 검색", style = TextStyles.divider, color = colors.textMain, modifier = Modifier.weight(1f))
                                Btn("모두 지우기", onClick = history::clear)
                            }
                            HorizontalDivider(Modifier.padding(horizontal = PaperPadding), color = colors.ruleStrong)
                        }
                        items(entries, key = { it.text }) { entry ->
                            HistoryRow(
                                entry.text,
                                onPick = {
                                    focusManager.clearFocus()
                                    history.add(entry.text)
                                    onQuery(entry.text)
                                },
                                onRemove = { history.remove(entry.text) },
                            )
                        }
                        item {
                            Text(
                                "${SearchHistory.KEEP_DAYS}일이 지난 검색어는 저절로 지워져요",
                                style = TextStyles.small,
                                color = colors.textMuted,
                                modifier = Modifier.padding(horizontal = PaperPadding, vertical = 16.dp),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
                    if (results.isEmpty()) {
                        item { EmptyText("맞는 질문이 없어요", "다른 낱말로 찾아보세요") }
                    } else {
                        stickyHeader(key = "head") {
                            Column(Modifier.fillMaxWidth().background(colors.bgPaper)) {
                                Text(
                                    "검색 결과 ${results.size}개",
                                    style = TextStyles.num,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(horizontal = PaperPadding, vertical = 14.dp),
                                )
                                HorizontalDivider(color = colors.rule)
                            }
                        }
                        questionIndex(results, grouped = true, records = records, onOpen = ::open)
                    }
                }
            }
        }
    }
}

/** 최근 검색어 한 줄: 누르면 다시 찾고, 오른쪽 X로 하나만 지운다 */
@Composable
private fun HistoryRow(text: String, onPick: () -> Unit, onRemove: () -> Unit) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = RowInset, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(Radius.control)
                .clickable(role = Role.Button, onClickLabel = "다시 검색", onClick = onPick)
                .padding(horizontal = PaperPadding - RowInset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(LineIcons.Clock, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
            Text(text, style = TextStyles.body, color = colors.textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconBtn(LineIcons.Close, contentDescription = "$text 지우기", onClick = onRemove, tint = colors.textMuted, iconSize = 16.dp)
    }
}

@Composable
private fun EmptyText(title: String, hint: String) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = TextStyles.body, color = colors.textSub, textAlign = TextAlign.Center)
        Text(hint, style = TextStyles.small, color = colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 검색창: 지면색 면, 모서리 4dp, 적는 중이면 초록 테두리 (웹 .search input) */
@Composable
private fun SearchField(
    query: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier
            .heightIn(min = 40.dp)
            .background(colors.bgPaper, Radius.control)
            .then(if (focused) Modifier.border(1.5.dp, colors.primary, Radius.control) else Modifier)
            .padding(start = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LineIcons.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
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
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            // 칸 높이에 맞춰 40dp로 둔다. 누르는 자리는 Compose가 48dp까지 넓혀 받는다
            IconBtn(LineIcons.Close, contentDescription = "검색어 지우기", onClick = onClear, modifier = Modifier.size(40.dp), tint = colors.textMuted)
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}
