package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.data.areaOf
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles

/**
 * 질문 화면: 답변을 한 장의 문서로 읽는다 (이해도·메모는 뒤 단계에서 더한다).
 * 위는 경로(어디서 왔는지 / 질문 번호), 아래는 웹 문서 꼬리처럼 이전·다음 질문의 제목을 보인다.
 *
 * origin: 어디서 열었는지 (경로에 보일 이름. 예: 질문 목록, 오늘)
 * order: 이전·다음으로 넘길 순서. 질문 목록에서 열었으면 그 목록에 보이던 순서다.
 */
@Composable
fun QuestionScreen(
    state: Library.State,
    id: String,
    origin: String,
    order: List<String>,
    onNavigate: (id: String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val question = state.catalog.question(id)
    val records by Records.state.collectAsState()
    BackHandler(onBack = onBack)

    // 읽어 둔 답변 본문 (질문 ID, 본문). 질문이 바뀌거나 새로 받으면 다시 읽는다. 본문이 ""이면 받아 둔 파일이 없다.
    // 다음 질문을 읽는 동안에는 보던 본문을 그대로 두어, 본문 화면(WebView)을 새로 만들지 않고 내용만 바꾼다
    var loaded by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(id, state.catalog) {
        loaded = id to (question?.let { Library.content(it) } ?: "")
    }

    val index = order.indexOf(id)
    val prev = order.getOrNull(index - 1)?.let { state.catalog.question(it) }
    val next = order.getOrNull(index + 1)?.let { state.catalog.question(it) }

    Column(Modifier.fillMaxSize().background(colors.bgCanvas)) {
        TopNav(
            current = id.uppercase(),
            parent = origin,
            onBack = onBack,
            trailing = {
                if (order.size > 1 && index >= 0) {
                    Text("${index + 1} / ${order.size}", style = TextStyles.num, color = colors.textMuted)
                }
                // 별표 (웹은 문서 머리 오른쪽 위에 둔다)
                if (records.signedIn && question != null) {
                    Spacer(Modifier.width(4.dp))
                    StarButton(
                        starred = records.item(id).starred,
                        onToggle = { Records.setStarred(id, !records.item(id).starred) },
                        size = 40.dp,
                    )
                }
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth().background(colors.bgPaper)) {
            val shown = loaded
            val shownQuestion = shown?.let { state.catalog.question(it.first) }
            when {
                question == null -> Missing("이 질문을 찾지 못했어요", "목차에서 빠졌을 수 있어요.")
                shown == null || shownQuestion == null -> Unit
                shown.second.isEmpty() -> Missing("답변을 아직 받지 못했어요", "인터넷에 연결된 뒤 다시 받아 주세요.")
                else -> MarkdownView(
                    key = shownQuestion.id,
                    meta = metaOf(shownQuestion),
                    title = shownQuestion.title,
                    markdown = shown.second,
                    modifier = Modifier.fillMaxSize(),
                    level = if (records.signedIn) records.level(shownQuestion.id) else null,
                    onLevel = { Records.setLevel(shownQuestion.id, it) },
                )
            }
        }

        if (order.size > 1 && index >= 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bgCanvas)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                PageLink(prev, isNext = false, onClick = onNavigate, modifier = Modifier.weight(1f))
                PageLink(next, isNext = true, onClick = onNavigate, modifier = Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.fillMaxWidth().background(colors.bgCanvas).navigationBarsPadding())
        }
    }
}

/** 이전·다음 질문 (웹 .page-nav-card): 작은 안내 글자 아래에 질문 제목 한 줄. 없으면 자리만 비운다 */
@Composable
private fun PageLink(target: Question?, isNext: Boolean, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    if (target == null) {
        Spacer(modifier)
        return
    }
    val align = if (isNext) TextAlign.End else TextAlign.Start
    Column(
        modifier
            .clip(Radius.control)
            .clickable(onClickLabel = if (isNext) "다음 질문" else "이전 질문") { onClick(target.id) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = if (isNext) Alignment.End else Alignment.Start,
    ) {
        Text(
            if (isNext) "다음 질문 →" else "← 이전 질문",
            style = TextStyles.num,
            color = colors.textMuted,
            textAlign = align,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            target.title,
            style = TextStyles.button,
            color = colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 제목 위에 보일 영역·카테고리. 예: 프론트엔드 · JavaScript 기초와 런타임 */
private fun metaOf(q: Question): String {
    val area = areaOf(q.id)?.name.orEmpty()
    return if (q.categoryName.isEmpty()) area else "$area · ${q.categoryName}"
}

@Composable
private fun Missing(title: String, hint: String) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = TextStyles.rowTitle, color = colors.textMain)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = TextStyles.small, color = colors.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Btn("지금 받기", onClick = Library::syncNow, kind = BtnKind.Soft)
    }
}
