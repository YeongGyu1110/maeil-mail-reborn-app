package com.maeilmail.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.maeilmail.app.account.GitHubApi
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
import com.maeilmail.app.account.Records
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
    val records by Records.state.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }
    var pickingRepo by remember { mutableStateOf(false) }
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
            InfoRow(
                "학습 기록",
                when {
                    records.loading && !records.loaded -> "읽는 중…"
                    records.loaded -> "이해도 ${records.items.values.count { it.level != Records.Level.NEW }}개 · " +
                        "별표 ${records.items.values.count { it.starred }}개"
                    else -> "다시 읽기"
                },
                onClick = { Records.refresh(force = true) },
                valueColor = if (records.loaded) colors.textSub else null,
            )
            RowRule()
            // 메모를 커밋할 저장소. 웹과 같은 설정이라 여기서 바꾸면 웹도 바뀐다
            InfoRow(
                "메모 저장소",
                records.memoRepo?.fullName ?: "고르기",
                sub = if (records.memoRepo == null) "고르면 메모를 저장할 때 GitHub에 커밋해요" else null,
                onClick = { pickingRepo = true },
            )
            Spacer(Modifier.height(4.dp))
            Btn(
                "로그아웃",
                onClick = { confirmSignOut = true },
                modifier = Modifier.padding(horizontal = PaperPadding - 14.dp),
            )
        }
    }
    (account.error ?: records.error)?.let {
        Text(
            it,
            style = TextStyles.small,
            color = colors.danger,
            modifier = Modifier.padding(horizontal = PaperPadding, vertical = 8.dp),
        )
    }

    if (pickingRepo) RepoDialog(selected = records.memoRepo, onDismiss = { pickingRepo = false })

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

/**
 * 메모 저장소 고르기 (웹의 저장소 선택 창과 같다): GitHub App을 설치한 저장소 중 포크·보관 저장소를 뺀 목록.
 * 고르면 계정의 memoRepo를 바꾼다 (웹과 함께 쓰는 설정).
 */
@Composable
private fun RepoDialog(selected: Records.MemoRepo?, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    var repos by remember { mutableStateOf<List<GitHubApi.Repo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        try {
            repos = GitHubApi.installedRepos()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = GitHubApi.errorMessage(e)
        }
    }

    fun choose(repo: GitHubApi.Repo) {
        if (saving) return
        saving = true
        Records.launch {
            try {
                Records.setMemoRepo(repo)
                onDismiss()
            } catch (e: Exception) {
                error = "저장소를 바꾸지 못했어요. ${Account.errorMessage(e)}"
            } finally {
                saving = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(colors.bgPaper, Radius.control),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                Text("메모 저장소 선택", style = TextStyles.dialogTitle, color = colors.textMain)
                Spacer(Modifier.height(4.dp))
                Text(
                    "메모를 커밋할 저장소를 골라 주세요. GitHub 앱에 추가한 저장소만 보여요. 웹과 같은 설정이라 웹도 함께 바뀌어요.",
                    style = TextStyles.small,
                    color = colors.textMuted,
                )
            }
            Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                val list = repos
                val message = error
                when {
                    message != null && list == null -> RepoState(message, "다시 시도") { attempt++ }
                    list == null -> RepoState("저장소를 불러오는 중…")
                    list.isEmpty() -> RepoState("아직 앱에 추가한 저장소가 없어요. 메모를 남길 저장소를 앱에 추가해 주세요.")
                    else -> LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                        items(list, key = { it.id }) { repo ->
                            // 같은 이름으로 저장소를 다시 만들면 번호가 바뀌므로 이름으로도 본다 (커밋은 이름으로 한다)
                            val on = selected != null &&
                                (repo.id == selected.id || repo.fullName.equals(selected.fullName, ignoreCase = true))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(Radius.control)
                                    .background(if (on) colors.primaryLight else Color.Transparent)
                                    .clickable(enabled = !saving) { choose(repo) }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        repo.fullName,
                                        style = TextStyles.button,
                                        color = colors.textMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (repo.description.isNotEmpty()) {
                                        Text(
                                            repo.description,
                                            style = TextStyles.small,
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (on) Text("선택됨", style = TextStyles.num, color = colors.primary)
                            }
                        }
                    }
                }
            }
            val message = error
            if (message != null && repos != null) {
                Text(
                    message,
                    style = TextStyles.small,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            HorizontalDivider(color = colors.rule)
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Btn(
                    "GitHub에서 저장소 추가",
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(GitHubApi.INSTALL_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    contentColor = colors.primary,
                )
                Spacer(Modifier.weight(1f))
                Btn("닫기", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun RepoState(message: String, action: String? = null, onAction: () -> Unit = {}) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = TextStyles.small, color = colors.textMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            Btn(action, onClick = onAction, kind = BtnKind.Soft)
        }
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
