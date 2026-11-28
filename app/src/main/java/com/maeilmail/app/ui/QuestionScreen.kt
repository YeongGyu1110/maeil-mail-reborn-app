package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.TextStyles
import org.json.JSONArray
import org.json.JSONObject

/**
 * 질문 화면: 답변을 한 장의 문서로 읽고, 본문 끝의 학습 기록에서 이해도를 고르고 메모를 본다.
 * 위는 돌아갈 목록의 이름, 순서, 별표다. 아래 막대는 없고, 본문이 화면 아래 끝까지 닿는다. 이전·다음 질문의 제목은 본문 끝에 있다.
 * 메모는 메모 창에서 고친다: 본문 위에 겹쳐 여는 한 화면으로, 열려 있는 동안 위 막대를 숨기고 뒤로 가기는 메모 창이 받는다.
 * 메모 창을 연 채 떠나려 하면(알림으로 다른 질문 열기) 메모 창이 먼저 저장하지 않은 변경을 어떻게 할지 묻는다.
 *
 * origin: 돌아갈 목록의 이름 (예: 질문 목록, 복습 필요)
 * order: 이전·다음으로 넘길 순서. 질문 목록에서 열었으면 그 목록에 보이던 순서다.
 * interrupt: 앱 밖(알림, 위젯, 메일)에서 다른 화면을 열어 달라고 했을 때 할 일. 그대로 있기로 하면 onStay를 부른다.
 */
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
    // 메모 창이 열려 있는지 (본문 스크립트가 알려 준다)
    var memoOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    var pickingRepo by remember { mutableStateOf(false) }
    // 메모 창에 닫아도 되는지 물은 것: 번호 → (닫았을 때 할 일, 그대로 있을 때 할 일)
    val leaving = remember { HashMap<Int, Pair<() -> Unit, () -> Unit>>() }
    var leaveSeq by remember { mutableIntStateOf(0) }

    /** 메모 창이 열려 있으면 메모 창이 닫아도 되는지 물은 뒤에 떠난다 */
    fun leave(stay: () -> Unit = {}, action: () -> Unit) {
        if (!memoOpen) return action()
        val seq = ++leaveSeq
        leaving[seq] = action to stay
        viewer.requestLeave(seq)
    }
    // 메모 창이 열려 있으면 뒤로 가기는 메모 창의 ←와 같다
    BackHandler { if (memoOpen) viewer.back() else onBack() }
    LaunchedEffect(interrupt) { interrupt?.let { leave(stay = onStay, action = it) } }

    // 읽어 둔 답변 본문 (질문 ID, 본문). 질문이 바뀌거나 새로 받으면 다시 읽는다. 본문이 ""이면 받아 둔 파일이 없다.
    // 다음 질문을 읽는 동안에는 보던 본문을 그대로 두어, 본문 화면(WebView)을 새로 만들지 않고 내용만 바꾼다
    var loaded by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(id, state.catalog) {
        loaded = id to (question?.let { Library.content(it) } ?: "")
    }

    val index = order.indexOf(id)

    // 좌우로 밀면 함께 보일 앞뒤 두 질문씩의 본문 (질문 ID → 본문). 보던 질문이나 순서가 바뀌면 다시 읽는다.
    // 빠르게 여러 번 넘겨도 본문 스크립트가 앱을 기다리지 않고 이어서 넘기도록, 바로 옆 너머의 질문까지 넘긴다
    val shownId = loaded?.first
    // 목차가 바뀌면(답변을 새로 받음) 읽어 둔 본문을 버리고 새로 읽는다
    var nearContent by remember(state.catalog) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(shownId, order, state.catalog) {
        val at = order.indexOf(shownId)
        if (at < 0) return@LaunchedEffect
        val loadedNow = HashMap<String, String>()
        // 바로 옆 질문을 먼저 넘기고, 그 너머는 뒤이어 넘긴다. 이미 읽어 둔 것은 다시 읽지 않는다
        for (step in listOf(listOf(-1, 1), listOf(-2, 2))) {
            for (near in step.mapNotNull { order.getOrNull(at + it) }) {
                loadedNow[near] = nearContent[near] ?: state.catalog.question(near)?.let { Library.content(it) }.orEmpty()
            }
            nearContent = HashMap(loadedNow)
        }
    }

    /** 순서에서 at번째 질문을 옆 질문으로 (없으면 null) */
    fun neighborAt(at: Int): Neighbor? {
        val q = order.getOrNull(at)?.let(state.catalog::question) ?: return null
        return Neighbor(
            question = q,
            meta = metaOf(q),
            markdown = nearContent[q.id].orEmpty(),
            study = if (records.signedIn) studyOf(records, q.id) else null,
            prev = order.getOrNull(at - 1)?.let(state.catalog::question),
            next = order.getOrNull(at + 1)?.let(state.catalog::question),
        )
    }

    // 화면 끝까지 그리므로(edge-to-edge) 키보드가 올라와도 창이 줄지 않는다. 키보드만큼 직접 줄여 메모 칸과 버튼이 가리지 않게 한다
    Column(Modifier.fillMaxSize().background(colors.bgCanvas).imePadding()) {
        // 메모 창은 본문(WebView) 안에서 화면 전체를 쓰므로, 위 막대를 숨기고 상태 표시줄과 아래 손잡이 자리만 비운다.
        // 메모 창을 닫은 동안에는 본문이 아래 손잡이 뒤까지 닿고, 본문의 끝만 그만큼 띄운다 (bottomInset)
        if (!memoOpen) PageBar(origin, onBack = onBack) {
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

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (memoOpen) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
                .background(colors.bgPaper),
        ) {
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
                    prev = order.indexOf(shownQuestion.id).takeIf { it >= 0 }?.let { neighborAt(it - 1) },
                    next = order.indexOf(shownQuestion.id).takeIf { it >= 0 }?.let { neighborAt(it + 1) },
                    prev2 = order.indexOf(shownQuestion.id).takeIf { it >= 0 }?.let { neighborAt(it - 2) },
                    next2 = order.indexOf(shownQuestion.id).takeIf { it >= 0 }?.let { neighborAt(it + 2) },
                    study = if (records.signedIn) studyOf(records, shownQuestion.id) else null,
                    bottomInset = if (memoOpen) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    controller = viewer,
                    onRequest = { name, payload, reply ->
                        val memoId = payload.optString("id")
                        // 저장 결과와 함께 넘기는 이 질문의 최신 기록 (본문 스크립트가 setStudy를 기다리지 않고 바로 그린다)
                        fun study() = studyOf(Records.state.value, memoId).toJson()
                        when (name) {
                            "memoWindow" -> memoOpen = payload.optBoolean("open")
                            "leave" -> leaving.remove(payload.optInt("seq"))?.let { (action, stay) ->
                                if (payload.optBoolean("left")) action() else stay()
                            }
                            "toast" -> payload.optString("text").takeIf { it.isNotEmpty() }?.let(Records::notify)
                            "setLevel" -> Records.setLevel(memoId, Records.Level.of(payload.optString("level")))
                            // 메모 창을 열면 기록을 새로 읽는다 (다른 기기에서 방금 저장한 초안이나 메모로 열도록). 바뀌면 setStudy로 간다
                            "refreshRecords" -> Records.refresh(force = true)
                            "navigate" -> memoId.takeIf { it in order }?.let { to -> leave { onNavigate(to) } }
                            "pickRepo" -> pickingRepo = true
                            "confirm" -> {
                                // 떠 있던 확인 창이 있으면(그사이 알림으로 다른 질문을 열려 할 때 등) '아니오'로 답하고 바꾼다.
                                // 답하지 않으면 본문 스크립트가 그 답을 끝없이 기다려 메모 창이 '저장 중'에 멈출 수 있다
                                confirm?.onResult("no")
                                confirm = ConfirmRequest(
                                    title = payload.optString("title"),
                                    body = payload.optString("body"),
                                    yes = payload.optString("yes"),
                                    no = payload.optString("no"),
                                    alt = payload.optString("alt").ifEmpty { null },
                                    danger = payload.optString("tone") != "primary",
                                ) { answer -> reply(JSONObject().put("answer", answer)) }
                            }
                            // 저장은 화면을 떠나도 끝까지 하도록 기록 저장소의 범위에서 돌린다
                            "saveMemo" -> Records.launch {
                                val result = Records.saveMemo(
                                    id = memoId,
                                    raw = payload.optString("text"),
                                    title = state.catalog.question(memoId)?.title.orEmpty(),
                                    dropDraft = payload.optBoolean("dropDraft"),
                                )
                                reply(
                                    JSONObject()
                                        .put("saved", result.saved)
                                        .put("changed", result.changed)
                                        .put("committed", result.committed ?: JSONObject.NULL)
                                        .put("study", study()),
                                )
                            }
                            "saveDraft" -> Records.launch {
                                val result = Records.saveDraft(memoId, payload.optString("text"), payload.optString("base"))
                                reply(
                                    JSONObject()
                                        .put("saved", result.saved)
                                        .put("draft", result.draft?.toJson() ?: JSONObject.NULL)
                                        .put("study", study()),
                                )
                            }
                            "deleteDraft" -> Records.launch {
                                val saved = Records.deleteDraft(memoId)
                                reply(JSONObject().put("saved", saved).put("study", study()))
                            }
                            // 저장 직전에 다른 곳에서 바뀌었는지 보려고 서버에서 새로 읽는다. 읽지 못하면 failed
                            "latest" -> Records.launch {
                                val latest = Records.latest(memoId)
                                reply(
                                    if (latest == null) JSONObject().put("failed", true)
                                    else JSONObject().put("memo", latest.memo).put("draft", latest.draft?.toJson() ?: JSONObject.NULL),
                                )
                            }
                            "moveOldDrafts" -> Records.launch {
                                val list = payload.optJSONArray("drafts")
                                val old = (0 until (list?.length() ?: 0)).mapNotNull { i ->
                                    val o = list?.optJSONObject(i) ?: return@mapNotNull null
                                    Records.OldDraft(o.optString("id"), o.optString("text"), o.optLong("at"))
                                }
                                val ready = Records.state.value.signedIn && Records.state.value.loaded
                                val result = Records.moveOldDrafts(old)
                                reply(
                                    JSONObject()
                                        .put("ready", ready)
                                        .put("moved", JSONArray(result.moved))
                                        .put("done", JSONArray(result.done)),
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    // 메모 칸의 "저장소 고르기": 읽던 자리와 쓰던 메모를 잃지 않게 이 화면 위에서 고른다
    if (pickingRepo) RepoDialog(selected = records.memoRepo, onDismiss = { pickingRepo = false })

    confirm?.let { request ->
        val answer = { value: String ->
            confirm = null
            request.onResult(value)
        }
        ConfirmDialog(
            title = request.title,
            body = request.body,
            confirm = request.yes,
            dismiss = request.no,
            danger = request.danger,
            alt = request.alt,
            onConfirm = { answer("yes") },
            onDismiss = { answer("no") },
            onAlt = { answer("alt") },
        )
    }
}

/** 본문 스크립트가 부탁한 확인 창 하나. 답은 'yes' | 'no' | 'alt'(세 번째 버튼) */
private class ConfirmRequest(
    val title: String,
    val body: String,
    val yes: String,
    val no: String,
    val alt: String? = null,
    val danger: Boolean = true,
    val onResult: (String) -> Unit,
)

/** 본문 학습 기록 칸에 넘길 이 질문의 기록 */
private fun studyOf(records: Records.State, id: String) = StudyState(
    item = records.item(id),
    canCommit = records.signedIn && records.memoRepo != null,
    repo = records.memoRepo?.fullName,
)

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
