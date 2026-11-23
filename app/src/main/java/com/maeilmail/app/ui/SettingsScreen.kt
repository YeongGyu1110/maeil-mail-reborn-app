package com.maeilmail.app.ui

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.maeilmail.app.BuildConfig
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Settings
import com.maeilmail.app.data.WebLink
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.widget.TodayWidgetProvider
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles
import com.maeilmail.app.ui.theme.ThemeSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

/** 설정: 계정, 알림, 질문 저장소 상태 */
@Composable
fun SettingsScreen(state: Library.State) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { Settings.get(context) }
    val notify by settings.notify.collectAsState()
    val nextAlarm by settings.nextAlarm.collectAsState()
    val resumed = rememberResumeCount()
    val canNotify = remember(resumed) { Reminders.canNotify(context) }
    val batteryFree = remember(resumed) { Reminders.isIgnoringBatteryOptimizations(context) }
    val requestPermission = rememberNotificationPermissionRequest()
    var pickingTime by remember { mutableStateOf(false) }
    var pickingTheme by remember { mutableStateOf(false) }
    val theme by settings.theme.collectAsState()

    fun update(value: Settings.Notify) {
        settings.setNotify(value)
        scope.launch { withContext(Dispatchers.IO) { Reminders.schedule(context) } }
    }

    Column(Modifier.fillMaxSize().background(colors.bgCanvas)) {
        TopNav("설정")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.bgPaper)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp, bottom = 32.dp),
        ) {
            AccountSection()

            Spacer(Modifier.height(36.dp))
            DividerHead("알림")
            Spacer(Modifier.height(4.dp))
            InfoRow(
                "오늘의 질문 알림",
                sub = "메일로 받는 날 정한 시각에 알려요",
                modifier = Modifier.toggleable(
                    value = notify.enabled,
                    role = Role.Switch,
                    onValueChange = { update(notify.copy(enabled = it)) },
                ),
                trailing = { Toggle(notify.enabled) },
            )
            RowRule()
            InfoRow("알림 시각", Format.time(notify.time), enabled = notify.enabled, onClick = { pickingTime = true })
            RowRule()
            InfoRow(
                "다음 알림",
                when {
                    !notify.enabled -> "꺼져 있음"
                    nextAlarm != null -> Format.relative(nextAlarm!!, Reminders.nowKst().toLocalDate())
                    else -> "정해진 질문 없음"
                },
            )
            RowRule()
            InfoRow(
                "알림 권한",
                if (canNotify) "허용됨" else "꺼져 있음",
                valueColor = if (canNotify) null else colors.review,
                onClick = if (canNotify) null else requestPermission,
            )
            RowRule()
            InfoRow(
                "배터리 사용량",
                if (batteryFree) "제한 없음" else "최적화됨",
                valueColor = if (batteryFree) null else colors.review,
                onClick = if (batteryFree) null else { { context.startActivity(batteryIntent(context.packageName)) } },
            )
            Text(
                if (batteryFree) {
                    "절전 중에도 알림이 늦지 않아요. 삼성 휴대폰은 설정 > 배터리 > 백그라운드 사용 제한에서 이 앱이 '사용하지 않는 앱'이나 '잠자는 앱'에 들어가 있지 않은지도 확인해 주세요."
                } else {
                    "배터리 최적화를 끄면(제한 없음) 절전 중에도 알림이 늦거나 빠지지 않아요. 눌러서 바꿀 수 있어요."
                },
                style = TextStyles.small,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = PaperPadding).padding(top = 2.dp, bottom = 12.dp),
            )
            Btn(
                "알림 미리 보기",
                onClick = { Reminders.preview(context) },
                kind = BtnKind.Soft,
                enabled = canNotify,
                modifier = Modifier.padding(horizontal = PaperPadding),
            )

            Spacer(Modifier.height(36.dp))
            DividerHead("질문")
            Spacer(Modifier.height(4.dp))
            InfoRow("받아 둔 질문", "${state.catalog.questions.size}개")
            RowRule()
            InfoRow("마지막으로 맞춘 시각", if (state.lastSyncAt > 0) syncTime(state.lastSyncAt) else "아직 없음")
            RowRule()
            InfoRow("저장소 커밋", state.commit.take(7).ifEmpty { "모름" })
            Spacer(Modifier.height(8.dp))
            Btn(
                if (state.syncing) "받는 중…" else "지금 받기",
                onClick = Library::syncNow,
                kind = BtnKind.Soft,
                enabled = !state.syncing,
                modifier = Modifier.padding(horizontal = PaperPadding),
            )

            Spacer(Modifier.height(36.dp))
            DividerHead("앱")
            Spacer(Modifier.height(4.dp))
            // 메일의 "답변 확인하기" 버튼(웹 주소)을 이 앱에서 열지. 안드로이드가 허락해야 하므로 그 설정 화면으로 보낸다
            val linkOpen = remember(resumed) { WebLink.isOpenedByApp(context) }
            if (linkOpen != null) {
                InfoRow(
                    "메일 링크를 앱에서 열기",
                    if (linkOpen) "켜짐" else "켜기",
                    sub = "메일의 '답변 확인하기'를 누르면 웹 대신 이 앱의 질문 화면이 열려요",
                    valueColor = if (linkOpen) colors.textSub else null,
                    onClick = { context.startActivity(WebLink.settingsIntent(context)) },
                )
                RowRule()
                // (레거시) 11/23까지 받은 메일의 옛 주소(GitHub Pages). 증명 파일이 없어 사용자가 휴대폰 설정에서 직접 켜야 한다
                val legacyOpen = remember(resumed) { WebLink.isOpenedByApp(context, WebLink.LEGACY_HOST) }
                InfoRow(
                    "이전 메일 링크도 앱에서 열기",
                    if (legacyOpen == true) "켜짐" else "켜기",
                    sub = "11월 23일까지 받은 메일은 옛 주소예요. 휴대폰 설정에서 ${WebLink.LEGACY_HOST}를 직접 켜야 앱으로 열려요",
                    valueColor = if (legacyOpen == true) colors.textSub else null,
                    onClick = { context.startActivity(WebLink.settingsIntent(context)) },
                )
                RowRule()
            }
            // 화면 테마: 휴대폰 설정을 따르거나 라이트·다크로 고정한다 (상단 바의 해·달 버튼과 같은 설정)
            InfoRow("화면 테마", theme.label, onClick = { pickingTheme = true })
            RowRule()
            // 홈 화면에 오늘의 질문 위젯을 놓는다 (시스템이 놓을지 묻는다). 런처가 지원하지 않으면 감춘다
            val widgets = remember { AppWidgetManager.getInstance(context) }
            if (widgets.isRequestPinAppWidgetSupported) {
                InfoRow(
                    "홈 화면 위젯",
                    "놓기",
                    sub = "오늘의 질문 번호와 제목을 홈 화면에 보여요",
                    onClick = { widgets.requestPinAppWidget(ComponentName(context, TodayWidgetProvider::class.java), null, null) },
                )
                RowRule()
            }
            InfoRow("버전", BuildConfig.VERSION_NAME)
        }
    }

    if (pickingTheme) {
        ChoiceDialog(
            title = "화면 테마",
            options = Settings.Theme.entries.map { it.label },
            selected = theme.ordinal,
            onPick = {
                pickingTheme = false
                ThemeSwitch.set(context, Settings.Theme.entries[it])
            },
            onDismiss = { pickingTheme = false },
        )
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

/** 배터리 최적화 예외를 묻는 시스템 창 (사용자가 직접 허용한다) */
@SuppressLint("BatteryLife")
private fun batteryIntent(packageName: String): Intent =
    Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))

private fun syncTime(epochMs: Long): String =
    SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(Date(epochMs))
