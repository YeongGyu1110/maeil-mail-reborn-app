package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import org.json.JSONObject
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.data.areaOf
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles

/**
 * 질문 화면: 답변을 한 장의 문서로 읽고, 본문 끝의 학습 기록에서 이해도를 고르고 메모를 쓴다.
 * 위는 경로(어디서 왔는지 / 질문 번호)와 별표, 아래는 웹 문서 꼬리처럼 이전·다음 질문의 제목을 보인다.
 * 메모를 쓰다가 떠나려 하면(뒤로 가기, 이전·다음) 쓰던 내용을 버릴지 묻는다.
 *
 * origin: 어디서 열었는지 (경로에 보일 이름. 예: 질문 목록, 오늘)
 * order: 이전·다음으로 넘길 순서. 질문 목록에서 열었으면 그 목록에 보이던 순서다.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val viewer = remember { ViewerController() }
    // 본문의 메모 편집기에 저장하지 않은 글이 있는지 (본문 스크립트가 알려 준다)
    var memoDirty by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }

    /** 쓰던 메모가 있으면 버릴지 물은 뒤에 떠난다 */
    fun leave(action: () -> Unit) {
        if (!memoDirty) return action()
        confirm = ConfirmRequest("쓰던 내용을 버릴까요?", "저장하지 않은 변경은 사라져요.", "버리기", "계속 쓰기") { yes ->
            if (yes) {
                viewer.discardMemo()
                memoDirty = false
                action()
            }
        }
    }
    BackHandler { leave(onBack) }

    // 읽어 둔 답변 본문 (질문 ID, 본문). 질문이 바뀌거나 새로 받으면 다시 읽는다. 본문이 ""이면 받아 둔 파일이 없다.
    // 다음 질문을 읽는 동안에는 보던 본문을 그대로 두어, 본문 화면(WebView)을 새로 만들지 않고 내용만 바꾼다
    var loaded by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(id, state.catalog) {
        loaded = id to (question?.let { Library.content(it) } ?: "")
    }

    val index = order.indexOf(id)
    val prev = order.getOrNull(index - 1)?.let { state.catalog.question(it) }
    val next = order.getOrNull(index + 1)?.let { state.catalog.question(it) }

    // 화면 끝까지 그리므로(edge-to-edge) 키보드가 올라와도 창이 줄지 않는다. 키보드만큼 직접 줄여 메모 칸과 버튼이 가리지 않게 한다
    val imeVisible = WindowInsets.isImeVisible
    Column(Modifier.fillMaxSize().background(colors.bgCanvas).imePadding()) {
        TopNav(
            current = id.uppercase(),
            parent = origin,
            onBack = { leave(onBack) },
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
                    study = if (records.signedIn) studyOf(records, shownQuestion.id) else null,
                    controller = viewer,
                    onLevel = { Records.setLevel(shownQuestion.id, it) },
                    onRequest = { name, payload, reply ->
                        when (name) {
                            "editing" -> memoDirty = payload.optBoolean("dirty")
                            "confirm" -> confirm = ConfirmRequest(
                                title = payload.optString("title"),
                                body = payload.optString("body"),
                                yes = payload.optString("yes"),
                                no = payload.optString("no"),
                                danger = payload.optString("tone") != "primary",
                            ) { yes -> reply(JSONObject().put("yes", yes)) }
                            // 저장은 화면을 떠나도 끝까지 하도록 기록 저장소의 범위에서 돌린다
                            "saveMemo" -> Records.launch {
                                val memoId = payload.optString("id")
                                val result = Records.saveMemo(
                                    id = memoId,
                                    raw = payload.optString("text"),
                                    title = state.catalog.question(memoId)?.title.orEmpty(),
                                    commit = payload.optBoolean("commit", true),
                                )
                                reply(
                                    JSONObject()
                                        .put("saved", result.saved)
                                        .put("changed", result.changed)
                                        .put("committed", result.committed ?: JSONObject.NULL)
                                        .put("study", studyOf(Records.state.value, memoId).toJson()),
                                )
                            }
                            "latestMemo" -> Records.launch {
                                reply(JSONObject().put("memo", Records.latestMemo(payload.optString("id"))))
                            }
                        }
                    },
                )
            }
        }

        // 메모를 쓰느라 키보드가 올라와 있으면 이전·다음 막대를 숨겨 쓰는 칸을 넓힌다
        if (imeVisible) {
            Unit
        } else if (order.size > 1 && index >= 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bgCanvas)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                PageLink(prev, isNext = false, onClick = { leave { onNavigate(it) } }, modifier = Modifier.weight(1f))
                PageLink(next, isNext = true, onClick = { leave { onNavigate(it) } }, modifier = Modifier.weight(1f))
            }
        } else {
            Spacer(Modifier.fillMaxWidth().background(colors.bgCanvas).navigationBarsPadding())
        }
    }

    confirm?.let { request ->
        ConfirmDialog(
            title = request.title,
            body = request.body,
            confirm = request.yes,
            dismiss = request.no,
            danger = request.danger,
            onConfirm = {
                confirm = null
                request.onResult(true)
            },
            onDismiss = {
                confirm = null
                request.onResult(false)
            },
        )
    }
}

/** 확인 창 하나 (본문 스크립트가 부탁한 것과 화면을 떠날 때 묻는 것) */
private class ConfirmRequest(
    val title: String,
    val body: String,
    val yes: String,
    val no: String,
    val danger: Boolean = true,
    val onResult: (Boolean) -> Unit,
)

/** 본문 학습 기록 칸에 넘길 이 질문의 기록 */
private fun studyOf(records: Records.State, id: String) = StudyState(
    item = records.item(id),
    canCommit = records.signedIn && records.memoRepo != null,
    repo = records.memoRepo?.fullName,
)

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
