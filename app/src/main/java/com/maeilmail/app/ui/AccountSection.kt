package com.maeilmail.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.maeilmail.app.account.Account
import com.maeilmail.app.account.GitHubAuth
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles
import java.time.Instant
import java.time.ZoneId

/**
 * 설정의 계정 칸: GitHub로 로그인(Device Flow)하고, 웹과 같은 학습 기록을 읽는지 보여준다.
 * 로그인하면 웹과 같은 Firebase 계정(uid)으로 들어가므로 웹의 이해도·별표·메모 저장소가 그대로 보인다.
 */
@Composable
fun AccountSection() {
    val colors = AppTheme.colors
    val account by Account.state.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    val user = account.user
    val pending = account.pending

    DividerHead("계정")
    Spacer(Modifier.height(4.dp))
    when {
        pending != null -> DeviceCodeBox(pending)
        user == null -> {
            Text(
                "GitHub로 로그인하면 웹과 같은 학습 기록(이해도·별표·메모)을 함께 써요. 처음 한 번만 하면 되고, 로그인은 알아서 이어져요.",
                style = TextStyles.small,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = PaperPadding, vertical = 10.dp),
            )
            Btn(
                if (account.busy) "코드를 받는 중…" else "GitHub로 로그인",
                onClick = Account::signIn,
                kind = BtnKind.Primary,
                enabled = !account.busy,
                modifier = Modifier.padding(horizontal = PaperPadding),
            )
        }
        else -> {
            InfoRow("GitHub", user.login)
            RowRule()
            val record = account.record
            InfoRow(
                "학습 기록",
                when {
                    account.busy -> "읽는 중…"
                    record != null -> "이해도 ${record.levels}개 · 별표 ${record.starred}개"
                    else -> "다시 읽기"
                },
                onClick = if (account.busy) null else Account::checkRecord,
                valueColor = if (record != null && !account.busy) colors.textSub else null,
            )
            RowRule()
            InfoRow("메모 저장소", record?.memoRepo ?: if (record != null) "고르지 않음" else "-")
            Spacer(Modifier.height(4.dp))
            Btn(
                "로그아웃",
                onClick = { confirmSignOut = true },
                modifier = Modifier.padding(horizontal = PaperPadding - 14.dp),
            )
        }
    }
    account.error?.let {
        Text(
            it,
            style = TextStyles.small,
            color = colors.danger,
            modifier = Modifier.padding(horizontal = PaperPadding, vertical = 8.dp),
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "로그아웃할까요?",
            body = "이 휴대폰에서만 로그아웃해요. 계정에 저장한 학습 기록과 메모는 그대로 남아요.",
            confirm = "로그아웃",
            danger = true,
            onConfirm = {
                confirmSignOut = false
                Account.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

/** github.com에 적을 코드. 복사하고 GitHub를 열면, 승인하는 대로 저절로 로그인된다 */
@Composable
private fun DeviceCodeBox(code: GitHubAuth.DeviceCode) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val until = Instant.ofEpochMilli(code.expiresAt).atZone(ZoneId.systemDefault()).toLocalTime()

    Column(Modifier.padding(horizontal = PaperPadding, vertical = 10.dp)) {
        Text("GitHub에서 이 코드를 적어 주세요", style = TextStyles.body, color = colors.textMain)
        Spacer(Modifier.height(12.dp))
        Text(
            code.userCode,
            style = TextStyles.h1.copy(fontSize = 28.sp, letterSpacing = 0.08.em, fontFeatureSettings = "tnum"),
            color = colors.textMain,
            modifier = Modifier
                .background(colors.bgSubtle, Radius.control)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Btn(
                "코드 복사하고 GitHub 열기",
                onClick = {
                    clipboard.setText(AnnotatedString(code.userCode))
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                kind = BtnKind.Primary,
            )
            Btn("취소", onClick = Account::cancelSignIn)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "승인하면 이 화면으로 돌아와 저절로 로그인돼요. 코드는 ${Format.time(until)}까지 쓸 수 있어요.",
            style = TextStyles.small,
            color = colors.textMuted,
        )
    }
}
