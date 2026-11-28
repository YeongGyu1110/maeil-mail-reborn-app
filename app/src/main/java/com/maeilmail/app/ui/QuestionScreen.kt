package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles
import org.json.JSONObject

/**
 * 질문 화면: 답변을 한 장의 문서로 읽고, 아래 막대에서 이해도를 고르고, 본문 끝에서 메모를 쓴다.
 * 위는 돌아갈 목록의 이름과 별표, 아래 막대는 이전 · 이해도 · 다음이다. 이전·다음 질문의 제목은 본문 끝에 있다.
 * 메모를 쓰다가 떠나려 하면(뒤로 가기, 이전·다음, 알림으로 열기) 쓰던 내용을 버릴지 묻는다.
 *
 * origin: 돌아갈 목록의 이름 (예: 질문 목록, 복습 필요)
 * order: 이전·다음으로 넘길 순서. 질문 목록에서 열었으면 그 목록에 보이던 순서다.
 * interrupt: 앱 밖(알림, 위젯, 메일)에서 다른 화면을 열어 달라고 했을 때 할 일. 그대로 있기로 하면 onStay를 부른다.
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
    interrupt: (() -> Unit)? = null,
    onStay: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val question = state.catalog.question(id)
    val records by Records.state.collectAsState()
    val viewer = remember { ViewerController() }
    // 본문의 메모 편집기에 저장하지 않은 글이 있는지 (본문 스크립트가 알려 준다)
    var memoDirty by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    var pickingRepo by remember { mutableStateOf(false) }

    /** 쓰던 메모가 있으면 버릴지 물은 뒤에 떠난다 */
    fun leave(stay: () -> Unit = {}, action: () -> Unit) {
        if (!memoDirty) return action()
        confirm = ConfirmRequest("쓰던 내용을 버릴까요?", "저장하지 않은 변경은 사라져요.", "버리기", "계속 쓰기") { yes ->
            if (yes) {
                viewer.discardMemo()
                memoDirty = false
                action()
            } else {
                stay()
            }
        }
    }
    BackHandler { leave(action = onBack) }
    LaunchedEffect(interrupt) { interrupt?.let { leave(stay = onStay, action = it) } }

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
        PageBar(origin, onBack = { leave(action = onBack) }) {
            if (order.size > 1 && index >= 0) {
                Text(
                    "${index + 1} / ${order.size}",
                    style = TextStyles.num,
                    color = colors.textMuted,
                    modifier = Modifier.padding(start = 8.dp, end = if (records.signedIn) 4.dp else 12.dp),
                )
            }
            // 별표 (웹은 문서 머리 오른쪽 위에 둔다)
            if (records.signedIn && question != null) {
                StarButton(
                    starred = records.item(id).starred,
                    onToggle = { Records.setStarred(id, !records.item(id).starred) },
                )
            }
        }

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
                    prev = order.getOrNull(order.indexOf(shownQuestion.id) - 1)?.let { state.catalog.question(it) },
                    next = order.getOrNull(order.indexOf(shownQuestion.id) + 1)?.let { state.catalog.question(it) },
                    study = if (records.signedIn) studyOf(records, shownQuestion.id) else null,
                    controller = viewer,
                    onRequest = { name, payload, reply ->
                        when (name) {
                            "editing" -> memoDirty = payload.optBoolean("dirty")
                            "navigate" -> payload.optString("id").takeIf { it in order }?.let { to -> leave { onNavigate(to) } }
                            "pickRepo" -> pickingRepo = true
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

        // 메모를 쓰느라 키보드가 올라와 있으면 아래 막대를 숨겨 쓰는 칸을 넓힌다
        if (!imeVisible) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bgCanvas)
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(
                    LineIcons.ChevronLeft,
                    contentDescription = "이전 질문",
                    enabled = prev != null,
                    onClick = { prev?.let { leave { onNavigate(it.id) } } },
                )
                if (records.signedIn && question != null) {
                    LevelBar(
                        records.level(id),
                        onPick = { Records.setLevel(id, it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconBtn(
                    LineIcons.ChevronRight,
                    contentDescription = "다음 질문",
                    enabled = next != null,
                    onClick = { next?.let { leave { onNavigate(it.id) } } },
                )
            }
        }
    }

    // 메모 칸의 "저장소 고르기": 읽던 자리와 쓰던 메모를 잃지 않게 이 화면 위에서 고른다
    if (pickingRepo) RepoDialog(selected = records.memoRepo, onDismiss = { pickingRepo = false })

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

/** 이해도: 셋 중 하나를 고르는 칸 (웹 .level-control). 고른 칸은 그 수준의 색을 띤다 */
@Composable
private fun LevelBar(selected: Records.Level, onPick: (Records.Level) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier
            .background(colors.bgPaper, Radius.group)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (level in Records.Level.entries) {
            val on = level == selected
            val (background, ink, dot) = levelColors(level)
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(Radius.control)
                    .background(if (!on) Color.Transparent else if (level == Records.Level.NEW) colors.bgHover else background)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(level) })
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            ) {
                Box(Modifier.size(7.dp).background(dot, Radius.dot))
                Text(
                    level.label,
                    style = TextStyles.button.copy(fontSize = 13.5.sp),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (!on) colors.textMuted else if (level == Records.Level.NEW) colors.textMain else ink,
                )
            }
        }
    }
}

/** 제목 위에 보일 번호와 카테고리. 예: FE-1 · JavaScript 기초와 런타임 */
private fun metaOf(q: Question): String = if (q.categoryName.isEmpty()) q.label else "${q.label} · ${q.categoryName}"

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
