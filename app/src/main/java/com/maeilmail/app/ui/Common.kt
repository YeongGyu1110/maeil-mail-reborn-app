package com.maeilmail.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.maeilmail.app.data.Settings
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

/** 누를 수 있는 행의 바탕을 글자보다 양옆으로 넓히는 폭 (웹 .q-row::before inset 0 -8px). 행은 지면 여백에서 이만큼 안쪽에서 시작한다 */
private val RowBleed: Dp = 8.dp
val RowInset: Dp = PaperPadding - RowBleed

/**
 * 상단 바: 캔버스와 같은 면, 구분선 없음 (웹 .top-nav).
 * 탭 화면은 "■ 매일메일 / 지금 화면", 질문 화면은 "← 질문 목록 / FE-1"처럼 경로로 보인다.
 */
@Composable
fun TopNav(
    current: String,
    parent: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgCanvas)
            .statusBarsPadding()
            .height(52.dp)
            .padding(start = if (onBack != null) 8.dp else PaperPadding, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconBtn(LineIcons.ArrowLeft, contentDescription = "뒤로", onClick = onBack)
            Spacer(Modifier.width(4.dp))
        } else {
            Brand()
            NavSeparator()
        }
        if (parent != null) {
            Text(
                parent,
                style = TextStyles.nav,
                color = colors.textMuted,
                maxLines = 1,
                modifier = if (onBack != null) Modifier.clip(Radius.small).clickable(onClick = onBack) else Modifier,
            )
            NavSeparator()
        }
        Text(
            current,
            style = TextStyles.nav.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** 로고: 각진 초록 점 + 서비스 이름 (웹 .nav-brand) */
@Composable
private fun Brand() {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(colors.primary, Radius.dot))
        Spacer(Modifier.width(6.dp))
        Text("매일메일", style = TextStyles.navBrand, color = colors.textMain)
    }
}

@Composable
private fun NavSeparator() {
    Text("/", style = TextStyles.nav, color = AppTheme.colors.ruleStrong, modifier = Modifier.padding(horizontal = 8.dp))
}

/** 아이콘만 있는 버튼 (웹 .btn-icon). 누르면 각진 바탕이 깔린다 */
@Composable
fun IconBtn(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, tint: Color = AppTheme.colors.textSub) {
    Box(
        Modifier
            .size(40.dp)
            .clip(Radius.control)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
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

/** 버튼: 모서리 4dp, 굵은 글자. 웹 .btn보다 높이만 손가락에 맞게 키웠다 (32px → 40dp) */
@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Text,
    enabled: Boolean = true,
    contentColor: Color? = null,
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
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 40.dp)
            .clip(Radius.control)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyles.button, color = color, maxLines = 1)
    }
}

/** 글자처럼 보이는 작은 버튼 (웹 .link-btn) */
@Composable
fun LinkBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TextStyles.num,
        color = AppTheme.colors.primary,
        modifier = modifier
            .clip(Radius.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
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
            .size(width = 40.dp, height = 22.dp)
            .background(if (checked) colors.primary else colors.bgHover, Radius.control)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(16.dp)
                .background(if (checked) colors.primaryInk else colors.ruleStrong, Radius.small),
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
            .padding(horizontal = RowBleed, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TextStyles.body, color = if (enabled) colors.textMain else colors.textMuted)
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

/**
 * 확인 창 (웹 .confirm-dialog): 모서리 4dp, 제목·설명, 오른쪽 아래에 글자 버튼(취소)과 면 버튼(확인).
 * danger면 확인 버튼을 빨간 면으로 둔다 (웹 .btn-danger).
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
) {
    val colors = AppTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.bgPaper, Radius.control)
                .padding(24.dp),
        ) {
            Text(title, style = TextStyles.dialogTitle, color = colors.textMain)
            Spacer(Modifier.height(8.dp))
            Text(body, style = TextStyles.body.copy(fontSize = 14.sp, lineHeight = 22.sp), color = colors.textSub)
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                Btn(dismiss, onClick = onDismiss)
                Btn(confirm, onClick = onConfirm, kind = if (danger) BtnKind.Danger else BtnKind.Primary)
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

fun notificationSettingsIntent(packageName: String): Intent =
    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
