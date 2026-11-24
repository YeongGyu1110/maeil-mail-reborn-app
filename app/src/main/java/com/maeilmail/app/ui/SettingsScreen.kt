package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.maeilmail.app.BuildConfig
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Settings
import com.maeilmail.app.data.WebLink
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.reminder.Today
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles
import com.maeilmail.app.ui.theme.ThemeSwitch
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 설정: GitHub(계정과 메모 저장소), 알림, 화면 테마.
 * 고칠 것이 있는 항목(알림 권한, 배터리, 메일 링크 등)만 주황으로 나오고, 정상이면 "휴대폰의 앱 설정" 한 줄로 모은다.
 */
@Composable
fun SettingsScreen(state: Library.State, today: Today, onBack: () -> Unit) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings.get(context) }
    val notify by settings.notify.collectAsState()
    val nextAlarm by settings.nextAlarm.collectAsState()
    val theme by settings.theme.collectAsState()
    val resumed = rememberResumeCount()
    val problems = remember(resumed) { Reminders.problems(context) }
    val linkOpen = remember(resumed) { WebLink.isOpenedByApp(context) }
    val legacyOpen = remember(resumed) { WebLink.isOpenedByApp(context, WebLink.LEGACY_HOST) }
    val fix = rememberProblemFix()
    var pickingTime by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    fun update(value: Settings.Notify) {
        settings.setNotify(value)
        scope.launch { withContext(Dispatchers.IO) { Reminders.schedule(context) } }
    }

    Column(Modifier.fillMaxSize().background(colors.bgCanvas)) {
        PageBar("설정", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.bgPaper)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(top = 20.dp, bottom = 24.dp),
        ) {
            AccountSection()

            Spacer(Modifier.height(34.dp))
            DividerHead("알림")
            AlarmRow(
                notify = notify,
                status = when {
                    !notify.enabled -> "꺼져 있어요"
                    nextAlarm != null -> "받는 날 ${Format.time(notify.time)} · 다음은 ${Format.relative(nextAlarm!!, Reminders.nowKst().toLocalDate())}"
                    else -> "받는 날 ${Format.time(notify.time)}"
                },
                onTime = { pickingTime = true },
                onToggle = { update(notify.copy(enabled = it)) },
            )
            // 앞으로 받을 질문을 모르면 알림도 걸지 못한다 (발송 기록을 오래 받지 못했을 때)
            if (notify.enabled && nextAlarm == null && !state.syncing && today.idle != null) {
                RowRule()
                FixRow("다음 알림을 걸지 못했어요", "질문 저장소에서 다음 질문을 받지 못했어요", "다시 받기", Library::syncNow)
            }
            for (problem in problems) {
                RowRule()
                FixRow(problem.title, problem.hint, problem.action) { fix(problem) }
            }
            if (linkOpen == false) {
                RowRule()
                FixRow("메일 링크가 웹으로 열려요", "메일의 '답변 확인하기'를 이 앱에서 열 수 있어요", "켜기") {
                    context.startActivity(WebLink.settingsIntent(context))
                }
            }
            // (레거시) 11/23까지 받은 메일의 옛 주소. 증명 파일이 없어 사용자가 휴대폰 설정에서 직접 켜야 한다
            if (linkOpen == true && legacyOpen == false) {
                RowRule()
                FixRow("11월 23일까지 받은 메일은 웹으로 열려요", "휴대폰 설정에서 ${WebLink.LEGACY_HOST}를 켜면 앱으로 열려요", "켜기") {
                    context.startActivity(WebLink.settingsIntent(context))
                }
            }
            RowRule()
            InfoRow(
                "휴대폰의 앱 설정",
                "열기",
                sub = "알림, 배터리, 메일 링크를 휴대폰에서 바꿔요",
                onClick = { context.startActivity(appDetailsIntent(context.packageName)) },
            )

            Spacer(Modifier.height(34.dp))
            DividerHead("화면 테마")
            Segments(
                options = Settings.Theme.entries.map { it.label },
                selected = theme.ordinal,
                onPick = { ThemeSwitch.set(context, Settings.Theme.entries[it]) },
                modifier = Modifier.padding(start = PaperPadding, end = PaperPadding, top = 14.dp),
            )

            Text(
                "버전 ${BuildConfig.VERSION_NAME}\n" + when {
                    state.syncing -> "질문을 맞추고 있어요"
                    state.lastSyncAt > 0 -> "질문은 ${syncTime(state.lastSyncAt)}에 맞췄어요"
                    else -> "질문을 아직 맞추지 못했어요"
                },
                style = TextStyles.small,
                color = colors.textMuted,
                modifier = Modifier.padding(start = PaperPadding, end = PaperPadding, top = 34.dp),
            )
        }
    }

    if (pickingTime) {
        TimeDialog(
            initial = notify.time,
            onDismiss = { pickingTime = false },
            onConfirm = {
                pickingTime = false
                update(notify.copy(time = it))
            },
        )
    }
}

/** 오늘의 질문 알림: 왼쪽을 누르면 시각을 바꾸고, 오른쪽 스위치로 켜고 끈다. 이름 아래에 다음 알림을 보인다 */
@Composable
private fun AlarmRow(notify: Settings.Notify, status: String, onTime: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = RowInset, end = RowInset).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clip(Radius.control)
                .clickable(enabled = notify.enabled, role = Role.Button, onClickLabel = "알림 시각 바꾸기", onClick = onTime)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("오늘의 질문 알림", style = TextStyles.body, color = colors.textMain)
            Text(status, style = TextStyles.small, color = colors.textMuted)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 12.dp).background(colors.rule))
        Box(
            Modifier
                .size(width = 68.dp, height = 64.dp)
                .toggleable(value = notify.enabled, role = Role.Switch, onValueChange = onToggle)
                .semantics { contentDescription = "오늘의 질문 알림" },
            contentAlignment = Alignment.Center,
        ) { Toggle(notify.enabled) }
    }
}

/** 고칠 것이 있을 때만 나오는 행 (주황 글자) */
@Composable
private fun FixRow(title: String, hint: String, action: String, onFix: () -> Unit) {
    val colors = AppTheme.colors
    InfoRow(title, action, sub = hint, valueColor = colors.review, labelColor = colors.review, onClick = onFix)
}

private val Reminders.Problem.title: String
    get() = when (this) {
        Reminders.Problem.PERMISSION -> "알림 권한이 꺼져 있어요"
        Reminders.Problem.CHANNEL -> "'오늘의 질문' 알림이 꺼져 있어요"
        Reminders.Problem.BACKGROUND -> "백그라운드 사용이 제한돼 있어요"
        Reminders.Problem.BATTERY -> "배터리 최적화가 켜져 있어요"
    }

private val Reminders.Problem.hint: String
    get() = when (this) {
        Reminders.Problem.PERMISSION, Reminders.Problem.CHANNEL -> "질문이 와도 알 수 없어요"
        Reminders.Problem.BACKGROUND -> "알림이 오지 않을 수 있어요. 배터리에서 제한 없음으로 바꿔 주세요"
        Reminders.Problem.BATTERY -> "절전 중에 알림이 늦거나 빠질 수 있어요"
    }

private val Reminders.Problem.action: String
    get() = if (this == Reminders.Problem.BATTERY) "끄기" else "켜기".takeIf { blocks } ?: "바꾸기"

/**
 * 알림 시각 창 (웹 .confirm-dialog: 모서리 4dp, 오른쪽 아래에 글자 버튼과 초록 버튼).
 * 둥근 시계판 대신 시·분을 숫자로 적는 각진 칸 두 개를 둔다. 시를 두 자리 적으면 분 칸으로 넘어간다.
 */
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val colors = AppTheme.colors
    var hour by remember { mutableStateOf(TextFieldValue("%02d".format(initial.hour))) }
    var minute by remember { mutableStateOf(TextFieldValue("%02d".format(initial.minute))) }
    val hourFocus = remember { FocusRequester() }
    val minuteFocus = remember { FocusRequester() }
    val h = hour.text.toIntOrNull()?.takeIf { it in 0..23 }
    val m = minute.text.toIntOrNull()?.takeIf { it in 0..59 }
    val time = if (h != null && m != null) LocalTime.of(h, m) else null
    LaunchedEffect(Unit) { hourFocus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.bgPaper, Radius.control)
                .padding(24.dp),
        ) {
            Text("알림 시각", style = TextStyles.dialogTitle, color = colors.textMain)
            Spacer(Modifier.height(6.dp))
            Text("메일로 받는 날 이 시각에 알려요. 24시간제로 적어 주세요.", style = TextStyles.small, color = colors.textMuted)
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top,
            ) {
                TimeField(
                    value = hour,
                    onChange = {
                        // 시를 새로 두 자리 적었을 때만 분 칸으로 넘어간다 (칸에 들어와 숫자를 고르기만 한 때는 그대로)
                        val typed = it.text != hour.text
                        hour = it
                        if (typed && it.text.length == 2) minuteFocus.requestFocus()
                    },
                    label = "시 (0~23)",
                    valid = h != null,
                    focusRequester = hourFocus,
                    imeAction = ImeAction.Next,
                    onIme = { minuteFocus.requestFocus() },
                )
                Text(
                    ":",
                    style = TimeDigits,
                    color = colors.textMuted,
                    modifier = Modifier.padding(horizontal = 10.dp).height(72.dp).wrapContentHeight(),
                )
                TimeField(
                    value = minute,
                    onChange = { minute = it },
                    label = "분 (0~59)",
                    valid = m != null,
                    focusRequester = minuteFocus,
                    imeAction = ImeAction.Done,
                    onIme = { time?.let(onConfirm) },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                Btn("취소", onClick = onDismiss)
                Btn("저장", onClick = { time?.let(onConfirm) }, kind = BtnKind.Primary, enabled = time != null)
            }
        }
    }
}

private val TimeDigits = TextStyles.h1.copy(fontSize = 40.sp, lineHeight = 48.sp, fontFeatureSettings = "tnum")

/** 두 자리 숫자 칸: 옅은 면, 모서리 4dp, 적는 중이면 초록 테두리 (웹 검색창과 같은 표시). 칸에 들어오면 숫자를 모두 골라 바로 고쳐 쓸 수 있다 */
@Composable
private fun TimeField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    label: String,
    valid: Boolean,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onIme: () -> Unit,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        onChange(value.copy(selection = if (focused) TextRange(0, value.text.length) else TextRange(value.text.length)))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicTextField(
            value = value,
            onValueChange = { v ->
                val digits = v.text.filter(Char::isDigit).take(2)
                onChange(if (digits == v.text) v else TextFieldValue(digits, TextRange(digits.length)))
            },
            textStyle = TimeDigits.copy(color = if (valid) colors.textMain else colors.danger, textAlign = TextAlign.Center),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
            interactionSource = interaction,
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(width = 96.dp, height = 72.dp)
                .background(colors.bgSubtle, Radius.control)
                .border(1.5.dp, if (focused) colors.primary else Color.Transparent, Radius.control),
            decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } },
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = TextStyles.small, color = colors.textMuted)
    }
}

private fun syncTime(epochMs: Long): String =
    SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(Date(epochMs))
