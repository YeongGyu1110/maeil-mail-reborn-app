package com.maeilmail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.data.Settings
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.reminder.Today
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles
import kotlinx.coroutines.delay

/** 지금의 오늘의 질문. 알림 시각이나 자정이 지나면 바뀌도록 1분마다 시각을 새로 읽는다 */
@Composable
fun rememberToday(state: Library.State): Today {
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }
    val notify by settings.notify.collectAsState()
    val lastNotified by settings.lastNotified.collectAsState()
    var now by remember { mutableStateOf(Reminders.nowKst()) }
    val resumed = rememberResumeCount()
    LaunchedEffect(resumed) {
        while (true) {
            now = Reminders.nowKst()
            delay((60 - now.second) * 1000L)
        }
    }
    return remember(state.progress, now, notify.time, lastNotified) { Today.of(state.progress, now, notify.time, lastNotified) }
}

/**
 * 첫 화면 맨 위의 오늘의 질문 (웹 .today: 왼쪽 세로줄 하나로만 표시하는 머리글).
 * 아직 답변을 열지 않았으면 "답변 확인하기" 버튼을 두고, 열어 본 뒤에는 제목만 남겨 목록을 끌어올린다.
 * 오늘 질문이 없을 때(알림 시각 전, 쉬는 날)는 그 까닭을 보이고, 열지 않은 지난 질문이 있으면 그것을 위에 둔다.
 */
@Composable
fun TodayBlock(today: Today, question: Question?, opened: Boolean, records: Records.State, onOpen: (Question) -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.padding(top = 24.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (question != null && opened && today.isToday) {
            Column(
                Modifier
                    .padding(horizontal = PaperPadding)
                    .fillMaxWidth()
                    .clip(Radius.small)
                    .clickable(role = Role.Button, onClickLabel = "질문 열기") { onOpen(question) }
                    .sideLine(colors.primary),
            ) {
                TodayTop(today.label, question, records)
                Text(
                    buildAnnotatedString {
                        append(question.title)
                        withStyle(SpanStyle(color = colors.textMuted, fontWeight = FontWeight.Medium)) { append("  →") }
                    },
                    style = TextStyles.todayTitle,
                    color = colors.textMain,
                )
            }
        } else if (question != null && !opened) {
            Column(Modifier.padding(horizontal = PaperPadding).fillMaxWidth().sideLine(colors.primary)) {
                TodayTop(today.label, question, records)
                Text(question.title, style = TextStyles.todayTitle, color = colors.textMain)
                if (question.categoryName.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(question.categoryName, style = TextStyles.small, color = colors.textMuted)
                }
                Spacer(Modifier.height(14.dp))
                Btn("답변 확인하기 →", onClick = { onOpen(question) }, kind = BtnKind.Primary)
            }
        }
        val idle = today.idle
            ?: if (question == null && today.id != null) Today.Idle("오늘의 질문 · ${today.id.uppercase()}", "질문 목록을 아직 받지 못했어요") else null
        if (idle != null) {
            Column(Modifier.padding(horizontal = PaperPadding).fillMaxWidth().sideLine(colors.ruleStrong)) {
                Text(idle.title, style = TextStyles.button, color = colors.textSub)
                if (idle.body != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(idle.body, style = TextStyles.small, color = colors.textMuted)
                }
            }
        }
    }
}

/** 필터를 건 동안의 오늘의 질문: 목록을 밀어내지 않게 한 줄로 */
@Composable
fun TodayLine(label: String, question: Question, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .padding(horizontal = PaperPadding, vertical = 8.dp)
            .fillMaxWidth()
            .clip(Radius.small)
            .clickable(role = Role.Button, onClickLabel = "질문 열기", onClick = onClick)
            .sideLine(colors.primary)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = TextStyles.num, color = colors.primary)
        Text(
            question.title,
            style = TextStyles.button.copy(fontSize = 13.5.sp),
            color = colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("→", style = TextStyles.button, color = colors.textMuted)
    }
}

/** 윗줄: 이름과 번호, 오른쪽에 지금 학습 수준 (미학습이면 보이지 않는다) */
@Composable
private fun TodayTop(label: String, question: Question, records: Records.State) {
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label · ${question.label}", style = TextStyles.num, color = AppTheme.colors.primary, modifier = Modifier.weight(1f))
        val level = records.level(question.id)
        if (records.signedIn && level != Records.Level.NEW) LevelBadge(level)
    }
    Spacer(Modifier.height(2.dp))
}

/** 왼쪽 세로줄 (웹 .today의 border-left 2px) */
private fun Modifier.sideLine(color: Color): Modifier = this
    .drawBehind { drawRect(color, size = Size(2.dp.toPx(), size.height)) }
    .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)

/**
 * 첫 화면의 안내: 알림이 꺼져 있어 질문이 와도 알 수 없을 때. 닫을 수 있고, 알림을 다시 켜면 닫은 것을 잊는다.
 * 웹의 복습 표시처럼 옅은 주황 면, 모서리 4dp.
 */
@Composable
fun NoticeBar(text: String, action: String, onAction: () -> Unit, onClose: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .padding(start = PaperPadding, end = PaperPadding, top = 16.dp)
            .fillMaxWidth()
            .background(colors.reviewLight, Radius.control)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = TextStyles.button.copy(fontSize = 13.5.sp),
            color = colors.review,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        Btn(action, onClick = onAction, contentColor = colors.review)
        IconBtn(LineIcons.Close, contentDescription = "이 안내 닫기", onClick = onClose, tint = colors.review)
    }
}
