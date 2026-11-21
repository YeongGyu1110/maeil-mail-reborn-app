package com.maeilmail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.data.Settings
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles
import kotlinx.coroutines.delay

/**
 * 오늘: 오늘의 질문을 한 장의 문서 머리처럼 보인다 (날짜 → 큰 제목 → 설명 → 진한 구분선).
 * 알림 시각 전에는 질문을 미리 보여주지 않는다. 쉬는 날이면 다음 질문이 언제 오는지 알려준다.
 */
@Composable
fun HomeScreen(state: Library.State, onOpen: (Question) -> Unit, onOpenSettings: () -> Unit) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }
    val notify by settings.notify.collectAsState()
    val lastNotified by settings.lastNotified.collectAsState()
    val nextAlarm by settings.nextAlarm.collectAsState()

    // 알림 시각이 지나면 질문이 보이도록 1분마다 시각을 새로 읽는다
    var now by remember { mutableStateOf(Reminders.nowKst()) }
    val resumed = rememberResumeCount()
    LaunchedEffect(resumed) {
        while (true) {
            now = Reminders.nowKst()
            delay(60_000)
        }
    }
    val canNotify = remember(resumed) { Reminders.canNotify(context) }
    val requestPermission = rememberNotificationPermissionRequest()

    // 처음 열었을 때 한 번만 알림 권한을 묻는다
    LaunchedEffect(Unit) {
        if (notify.enabled && !Reminders.canNotify(context) && !settings.askedNotificationPermission) requestPermission()
    }

    val today = now.toLocalDate()
    val progress = state.progress
    val todayId = progress?.questionOn(today)
    val question = todayId?.let { state.catalog.question(it) }
    val revealed = todayId != null && Reminders.isRevealed(today, now, notify.time, lastNotified)

    Column(Modifier.fillMaxSize().background(colors.bgCanvas)) {
        TopNav("오늘")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.bgPaper)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 28.dp),
        ) {
            if (notify.enabled && !canNotify) {
                Notice(
                    title = "알림이 꺼져 있어요",
                    body = "오늘의 질문을 정한 시각에 알리려면 알림을 허용해 주세요.",
                    action = "허용하기",
                    onAction = requestPermission,
                )
                Spacer(Modifier.height(24.dp))
            }

            // 문서 머리: 날짜(+ 오늘의 질문 표시) → 제목 → 설명
            Column(Modifier.padding(horizontal = PaperPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.dayLong(today), style = TextStyles.meta, color = colors.textMuted)
                    if (revealed && question != null) {
                        Text(" · ", style = TextStyles.meta, color = colors.textMuted)
                        Text("오늘의 질문", style = TextStyles.meta, color = colors.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                when {
                    !state.loaded -> Spacer(Modifier.height(33.dp))
                    progress == null -> Headline(
                        "오늘의 질문을 아직 받지 못했어요",
                        "인터넷에 연결되면 발송 기록을 받아 와요.",
                    )
                    todayId == null -> {
                        val nextDate = progress.nextDateAfter(today)
                        Headline(
                            "오늘은 쉬는 날이에요",
                            nextDate?.let { "다음 질문은 ${Format.day(it)} ${Format.time(notify.time)}에 와요." }
                                ?: "다음 질문이 정해지면 알려 드릴게요.",
                        )
                    }
                    !revealed -> Headline(
                        "오늘의 질문은 ${Format.time(notify.time)}에 알려 드려요",
                        "알림 시각 전에는 질문을 미리 보여주지 않아요.",
                    )
                    question == null -> Headline(
                        "오늘의 질문 · ${todayId.uppercase()}",
                        "질문 목록을 아직 받지 못했어요. 인터넷에 연결된 뒤 다시 열어 주세요.",
                    )
                    else -> {
                        Text(question.title, style = TextStyles.h1, color = colors.textMain)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(question.label, style = TextStyles.num, color = colors.textMuted)
                            if (question.categoryName.isNotEmpty()) {
                                Text(" · ${question.categoryName}", style = TextStyles.small, color = colors.textMuted)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Btn("답변 보기", onClick = { onOpen(question) }, kind = BtnKind.Primary)
                    }
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = colors.ruleStrong)
            }

            Spacer(Modifier.height(8.dp))
            InfoRow(
                "다음 알림",
                when {
                    !notify.enabled -> "꺼져 있음"
                    nextAlarm != null -> Format.relative(nextAlarm!!, today)
                    else -> "정해진 질문 없음"
                },
                onClick = onOpenSettings,
            )
        }
    }
}

/** 문서 제목과 그 아래 한두 줄 설명 */
@Composable
private fun Headline(title: String, body: String) {
    val colors = AppTheme.colors
    Text(title, style = TextStyles.h1, color = colors.textMain)
    Spacer(Modifier.height(8.dp))
    Text(body, style = TextStyles.body, color = colors.textSub)
}

/** 해야 할 일을 알리는 칸 (권한 등). 웹의 복습 표시처럼 옅은 주황 면, 모서리 4dp */
@Composable
fun Notice(title: String, body: String, action: String, onAction: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .padding(horizontal = PaperPadding)
            .fillMaxWidth()
            .background(colors.reviewLight, Radius.control)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Text(title, style = TextStyles.button, color = colors.review)
        Spacer(Modifier.height(2.dp))
        Text(body, style = TextStyles.small, color = colors.textSub, modifier = Modifier.padding(end = 8.dp))
        Btn(action, onClick = onAction, contentColor = colors.review, modifier = Modifier.align(Alignment.End))
    }
}
