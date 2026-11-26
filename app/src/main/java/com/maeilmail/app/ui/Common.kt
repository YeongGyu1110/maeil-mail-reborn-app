package com.maeilmail.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Settings
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles

/*
 * 여러 화면이 같이 쓰는 웹식 부품. 웹 base.css의 상단 바·버튼·토스트를 휴대폰 손가락 크기에 맞춰 옮겼다.
 * 모서리는 4dp 이하, 구분은 면보다 선(구분선)으로 한다.
 */

/** 지면 안쪽 여백 (웹 좁은 화면의 문서 여백 20px) */
val PaperPadding: Dp = 20.dp

/**
 * 버튼·칩처럼 면이 보이는 것의 높이 (웹 .scope-filter·.cat-trigger 34px).
 * 손가락으로 누르는 자리 48dp는 면을 키우지 않고 둘레의 투명한 자리로 둔다 (minimumInteractiveComponentSize)
 */
val ControlHeight: Dp = 34.dp

/**
 * 글자 옆에 서는 아이콘의 크기. 휴대폰의 글자 크기 설정을 따라 글자와 같은 비율로 바뀐다 (0.8~1.3배).
 * 글자만 줄고 아이콘이 그대로면 아이콘이 글자보다 커 보인다
 */
@Composable
fun scaledIcon(base: Dp): Dp = base * LocalDensity.current.fontScale.coerceIn(0.8f, 1.3f)

/** 누를 수 있는 행의 바탕을 글자보다 양옆으로 넓히는 폭 (웹 .q-row::before inset 0 -8px). 행은 지면 여백에서 이만큼 안쪽에서 시작한다 */
private val RowBleed: Dp = 8.dp
val RowInset: Dp = PaperPadding - RowBleed

/**
 * 상단 바: 캔버스와 같은 면, 구분선 없음 (웹 .top-nav). 어느 화면에서나 누르는 것만 둔다.
 * 첫 화면은 로고·검색·설정, 그 밖의 화면은 뒤로 가기와 그 화면의 버튼이다.
 */
@Composable
fun TopBar(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.bgCanvas)
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 첫 화면이 아닌 화면의 상단 바: 돌아갈 곳의 이름까지 하나로 누르는 뒤로 가기 */
@Composable
fun PageBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = AppTheme.colors
    TopBar {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(Radius.control)
                    .clickable(role = Role.Button, onClickLabel = "뒤로", onClick = onBack)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(LineIcons.ArrowLeft, contentDescription = "뒤로", tint = colors.textSub, modifier = Modifier.size(scaledIcon(20.dp)))
                Spacer(Modifier.width(8.dp))
                Text(title, style = TextStyles.pageTitle, color = colors.textMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

/** 로고: 각진 초록 점 + 서비스 이름 (웹 .nav-brand). 누르면 처음 목록으로 돌아간다 */
@Composable
fun Brand(onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(Radius.control)
            .clickable(role = Role.Button, onClickLabel = "처음 목록으로", onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(colors.primary, Radius.dot))
        Spacer(Modifier.width(8.dp))
        Text("매일메일", style = TextStyles.pageTitle.copy(fontWeight = FontWeight.Bold), color = colors.textMain)
    }
}

/** 아이콘만 있는 버튼 (웹 .btn-icon). 누르면 각진 바탕이 깔린다 */
@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.textSub,
    enabled: Boolean = true,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(Radius.control)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(scaledIcon(iconSize)))
    }
}

enum class BtnKind {
    /** 테두리·바탕 없는 글자형 (웹 .btn 기본) */
    Text,

    /** 옅은 면 (웹 .btn-soft) */
    Soft,

    /** 초록 면 (웹 .btn-primary) */
    Primary,

    /** 빨간 면: 지우기·로그아웃처럼 되돌리기 어려운 일 (웹 .btn-danger) */
    Danger,
}

/** 버튼 글자의 양옆 여백. 글자형 버튼의 글자를 옆의 글과 같은 세로줄에 맞출 때 이만큼 당긴다 */
val BtnPadding: Dp = 14.dp

/** 버튼 (웹 .btn): 모서리 4dp, 굵은 글자. 보이는 높이는 ControlHeight이고 누르는 자리는 48dp다 */
@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Text,
    enabled: Boolean = true,
    contentColor: Color? = null,
    icon: ImageVector? = null,
) {
    val colors = AppTheme.colors
    val background = when (kind) {
        BtnKind.Text -> Color.Transparent
        BtnKind.Soft -> colors.bgSubtle
        BtnKind.Primary -> colors.primary
        BtnKind.Danger -> colors.danger
    }
    val color = contentColor ?: when (kind) {
        BtnKind.Primary -> colors.primaryInk
        // 웹과 같다: 라이트는 흰 글자, 다크는 짙은 글자
        BtnKind.Danger -> if (colors.isDark) Color(0xFF1F0D0D) else Color.White
        else -> colors.textSub
    }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .minimumInteractiveComponentSize()
            .heightIn(min = ControlHeight)
            .clip(Radius.control)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = BtnPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(scaledIcon(16.dp)))
        Text(text, style = TextStyles.button, color = color, maxLines = 1)
    }
}

/** 글자 버튼의 누르는 자리를 글자보다 양옆으로 넓히는 폭. 글자를 여백에 맞출 때 이만큼 당긴다 */
val LinkBtnPadding: Dp = 8.dp

/** 글자처럼 보이는 작은 버튼 (웹 .link-btn) */
@Composable
fun LinkBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TextStyles.small,
        fontWeight = FontWeight.SemiBold,
        color = AppTheme.colors.primary,
        maxLines = 1,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(Radius.control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LinkBtnPadding, vertical = 4.dp),
    )
}

/**
 * 켜고 끄는 표시. 알약 모양 스위치 대신 웹의 각진 모서리(4dp)에 맞춘 네모 스위치다.
 * 누르는 일은 이 표시를 품은 행이 맡는다 (행 전체가 스위치).
 */
@Composable
fun Toggle(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val offset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "toggle")
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .background(if (checked) colors.primary else colors.bgHover, Radius.control)
            .then(if (checked) Modifier else Modifier.border(1.dp, colors.ruleStrong, Radius.control))
            .padding(5.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .background(if (checked) colors.primaryInk else colors.textMuted, Radius.small),
        )
    }
}

/** 화면 아래 잠깐 뜨는 알림 (웹 .toast: 진한 면에 밝은 글자, 모서리 4dp) */
@Composable
fun Toast(message: String) {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = TextStyles.toast,
            color = colors.bgPaper,
            modifier = Modifier
                .background(colors.textMain, Radius.control)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** 받는 중임을 알리는 얇은 막대. progress가 null이면 끝없이 흐른다 (모서리 없이 각진 끝) */
@Composable
fun ProgressLine(progress: Float?, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(2.dp),
            color = colors.primary,
            trackColor = colors.rule,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxWidth().height(2.dp),
            color = colors.primary,
            trackColor = colors.rule,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** 구분 제목: 작은 굵은 제목 + 개수, 아래에 진한 선 하나 (웹 .list-divider) */
@Composable
fun DividerHead(title: String, modifier: Modifier = Modifier, count: String? = null) {
    val colors = AppTheme.colors
    Column(modifier.padding(horizontal = PaperPadding)) {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = TextStyles.divider, color = colors.textMain)
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text(count, style = TextStyles.num, color = colors.textMuted)
            }
        }
        HorizontalDivider(color = colors.ruleStrong)
    }
}

/** 행 사이의 옅은 구분선 (웹 .q-row border-top) */
@Composable
fun RowRule() {
    HorizontalDivider(Modifier.padding(horizontal = PaperPadding), color = AppTheme.colors.rule)
}

/**
 * 이름과 값을 나란히 두는 행. 누를 수 있으면 값을 초록 글자로 보이고, 누를 때 각진 바탕이 글자보다 조금 넓게 깔린다.
 * trailing이 있으면 값 대신 그것을 둔다.
 */
@Composable
fun InfoRow(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
    valueColor: Color? = null,
    labelColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = RowInset)
            .clip(Radius.control)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = RowBleed, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyles.body, color = labelColor ?: if (enabled) colors.textMain else colors.textMuted)
            if (sub != null) Text(sub, style = TextStyles.small, color = colors.textMuted)
        }
        if (trailing != null) {
            trailing()
        } else if (value != null) {
            Text(
                value,
                style = TextStyles.body,
                color = when {
                    !enabled -> colors.textMuted
                    valueColor != null -> valueColor
                    onClick != null -> colors.primary
                    else -> colors.textSub
                },
                fontWeight = if (onClick != null && enabled) FontWeight.SemiBold else null,
            )
        }
    }
}

/** 창 안쪽 여백 (웹 .confirm-dialog 24px). 아래는 버튼 둘레의 누르는 자리(7dp)만큼 뺀다 */
val DialogPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 17.dp)

/**
 * 확인 창 (웹 .confirm-dialog): 모서리 4dp, 제목·설명, 오른쪽 아래에 글자 버튼(취소)과 면 버튼(확인).
 * danger면 확인 버튼을 빨간 면으로 둔다 (웹 .btn-danger).
 * alt를 주면 세 번째 버튼(예: 변경 버리기)을 왼쪽 끝에 빨간 글자로 둔다. 셋이 한 줄에 들어가지 않으면(글자를 키웠을 때)
 * 왼쪽부터의 순서 그대로 위에서 아래로 쌓는다 (웹 확인 창과 같다).
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "취소",
    danger: Boolean = false,
    alt: String? = null,
    onAlt: () -> Unit = {},
) {
    val colors = AppTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.bgPaper, Radius.control)
                .padding(DialogPadding),
        ) {
            Text(title, style = TextStyles.dialogTitle, color = colors.textMain)
            Spacer(Modifier.height(8.dp))
            Text(body, style = TextStyles.body.copy(fontSize = 14.sp, lineHeight = 22.sp), color = colors.textSub)
            DialogButtons(Modifier.padding(top = 13.dp), leading = alt != null) {
                if (alt != null) Btn(alt, onClick = onAlt, contentColor = colors.danger)
                Btn(dismiss, onClick = onDismiss)
                Btn(confirm, onClick = onConfirm, kind = if (danger) BtnKind.Danger else BtnKind.Primary)
            }
        }
    }
}

/**
 * 확인 창의 버튼 줄. 한 줄에 들어가면 오른쪽 끝에 모으고, leading이면 첫 버튼만 왼쪽 끝에 둔다
 * (글자 버튼의 글자가 창의 글과 같은 세로줄에 오도록 버튼 안쪽 여백만큼 왼쪽으로 당긴다).
 * 한 줄에 들어가지 않으면 창의 폭으로 늘려 차례로 쌓는다.
 */
@Composable
private fun DialogButtons(modifier: Modifier, leading: Boolean, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 4.dp.roundToPx()
        val width = constraints.maxWidth
        val fits = measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } + gap * (measurables.size - 1) <= width
        if (fits) {
            val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                var x = width
                for (i in placeables.indices.reversed()) {
                    val p = placeables[i]
                    val y = (height - p.height) / 2
                    if (i == 0 && leading) {
                        p.place(-BtnPadding.roundToPx(), y)
                    } else {
                        x -= p.width
                        p.place(x, y)
                        x -= gap
                    }
                }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            layout(width, placeables.sumOf { it.height }) {
                var y = 0
                for (p in placeables) {
                    p.place(0, y)
                    y += p.height
                }
            }
        }
    }
}

/** 학습 수준의 색: (면, 글자, 점). 웹 .level-badge의 data-level별 색 */
@Composable
fun levelColors(level: Records.Level): Triple<Color, Color, Color> {
    val c = AppTheme.colors
    return when (level) {
        Records.Level.NEW -> Triple(c.bgSubtle, c.textMuted, c.ruleStrong)
        Records.Level.REVIEW -> Triple(c.reviewLight, c.review, c.reviewBar)
        Records.Level.DONE -> Triple(c.primaryLight, c.primary, c.primary)
    }
}

/** 학습 수준 표시: 각진 점 + 이름 (웹 .level-badge, 높이 24, 모서리 4) */
@Composable
fun LevelBadge(level: Records.Level, modifier: Modifier = Modifier) {
    val (background, text, dot) = levelColors(level)
    Row(
        modifier
            .heightIn(min = 24.dp)
            .background(background, Radius.control)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(dot, Radius.dot))
        Spacer(Modifier.width(6.dp))
        Text(level.label, style = TextStyles.num, color = text)
    }
}

/** 별표 버튼 (웹 .star-btn): 누르면 별표하고, 다시 누르면 뗀다. 별표한 질문은 노란 칠한 별 */
@Composable
fun StarButton(starred: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier
            .size(48.dp)
            .clip(Radius.control)
            .toggleable(value = starred, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (starred) LineIcons.StarFilled else LineIcons.Star,
            contentDescription = if (starred) "별표 떼기" else "별표하기",
            tint = if (starred) colors.star else colors.textMuted,
            modifier = Modifier.size(scaledIcon(18.dp)),
        )
    }
}

/** 여러 가지 중 하나를 그 자리에서 고르는 칸 (웹 .level-control): 고른 칸만 지면처럼 떠오른다 */
@Composable
fun Segments(options: List<String>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .border(1.dp, colors.rule, Radius.group)
            .background(colors.bgSubtle, Radius.group)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(Radius.control)
                    .then(if (on) Modifier.background(colors.bgPaper).border(1.dp, colors.rule, Radius.control) else Modifier)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(i) })
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = TextStyles.button.copy(fontSize = 13.5.sp),
                    color = if (on) colors.textMain else colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 화면에 돌아올 때마다(onResume) 바뀌는 값. 권한처럼 앱 밖에서 바뀌는 상태를 다시 읽는 데 쓴다 */
@Composable
fun rememberResumeCount(): Int {
    var count by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) count++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}

/**
 * 알림 권한을 묻는다. 이미 거절해 다시 물을 수 없으면 이 앱의 알림 설정 화면을 연다.
 * 반환한 함수를 부르면 묻는다.
 */
@Composable
fun rememberNotificationPermissionRequest(onResult: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }
    // 두 번 거절하면 시스템은 창을 띄우지 않고 바로 거절로 답한다. 그렇게 곧바로 거절되면 설정 화면을 연다
    var askedAt by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && SystemClock.elapsedRealtime() - askedAt < 500) {
            context.startActivity(notificationSettingsIntent(context.packageName))
        }
        onResult()
    }
    return {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            settings.askedNotificationPermission = true
            askedAt = SystemClock.elapsedRealtime()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // 권한은 있는데 알림을 꺼 둔 경우: 이 앱의 알림 설정 화면을 연다
            context.startActivity(notificationSettingsIntent(context.packageName))
        }
    }
}

/** 알림이 오지 않거나 늦을 수 있는 문제를 고치러 간다: 권한을 묻거나, 그 문제를 바꾸는 휴대폰 설정 화면을 연다 */
@Composable
fun rememberProblemFix(): (Reminders.Problem) -> Unit {
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermissionRequest()
    return { problem ->
        when (problem) {
            Reminders.Problem.PERMISSION -> requestPermission()
            Reminders.Problem.CHANNEL -> context.startActivity(
                Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(AndroidSettings.EXTRA_CHANNEL_ID, Reminders.CHANNEL)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            Reminders.Problem.BACKGROUND -> context.startActivity(appDetailsIntent(context.packageName))
            Reminders.Problem.BATTERY -> context.startActivity(batteryIntent(context.packageName))
        }
    }
}

/** 배터리 최적화 예외를 묻는 시스템 창 (사용자가 직접 허용한다) */
@SuppressLint("BatteryLife")
private fun batteryIntent(packageName: String): Intent =
    Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))

/** 휴대폰 설정의 이 앱 화면 (알림, 배터리, 기본으로 열기) */
fun appDetailsIntent(packageName: String): Intent =
    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun notificationSettingsIntent(packageName: String): Intent =
    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
