package com.maeilmail.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings

/**
 * 웹 서비스 주소를 앱이 받는다 (메일의 "답변 확인하기 →" 버튼 등).
 * 메일 버튼은 send_mail.py가 만든 {PAGES_URL}/questions/?q={id} 주소다.
 * 앱이 이 주소를 열도록 안드로이드가 허락하면(앱 링크), 휴대폰에서 누를 때 웹 대신 앱의 질문 화면이 열린다.
 * 허락의 근거(앱 증명 파일)는 주소 맨 앞(/.well-known/assetlinks.json)에 있어야 해서,
 * 웹을 GitHub Pages 프로젝트 사이트(yeonggyu1110.github.io/maeil-mail-reborn/) 대신 Cloudflare Pages 주소에서 받는다.
 */
object WebLink {
    const val HOST = "maeil-mail-reborn.pages.dev"
    private const val QUESTIONS_PATH = "/questions"

    /**
     * (레거시) 2026-11-23까지 받은 메일의 버튼 주소: GitHub Pages 프로젝트 사이트 yeonggyu1110.github.io/maeil-mail-reborn/.
     * 이 호스트의 맨 앞은 개인 사이트라 증명 파일을 두지 않았다. 그래서 저절로 허락되지 않고,
     * 사용자가 휴대폰 설정(지원되는 웹 주소)에서 직접 켜야 앱으로 열린다. 옛 메일을 더 열지 않게 되면 지운다.
     */
    const val LEGACY_HOST = "yeonggyu1110.github.io"
    private const val LEGACY_ROOT = "/maeil-mail-reborn"

    /**
     * 주소에서 열 질문 ID를 꺼낸다 (웹 questions/index.html과 같은 규칙).
     * 질문 화면 주소가 아니면 null (목록 주소 등은 앱만 연다). 접두어 없는 옛 링크(?q=1)는 프론트엔드 질문으로 본다.
     */
    fun questionId(uri: Uri?): String? = uri?.let { questionId(it.host, it.path, it.getQueryParameter("q")) }

    /** 주소의 호스트·경로·q 값으로 판단한다 (Uri 없이 시험하려고 나눴다) */
    internal fun questionId(host: String?, path: String?, q: String?): String? {
        val questions = when {
            host.equals(HOST, ignoreCase = true) -> QUESTIONS_PATH
            host.equals(LEGACY_HOST, ignoreCase = true) -> LEGACY_ROOT + QUESTIONS_PATH
            else -> return null
        }
        if (!path.orEmpty().startsWith(questions, ignoreCase = true)) return null
        return normalizeId(q)
    }

    /**
     * 안드로이드가 이 주소를 앱에서 열도록 허락했는지 (안드로이드 12 이상).
     * 웹 주소에 앱을 증명하는 파일(assetlinks.json)이 있으면 저절로 허락되고, 없으면 사용자가 설정에서 직접 허락한다.
     * 알 수 없으면(안드로이드 11 이하) null
     */
    fun isOpenedByApp(context: Context, host: String = HOST): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val state = context.getSystemService(DomainVerificationManager::class.java)
            ?.getDomainVerificationUserState(context.packageName) ?: return null
        if (!state.isLinkHandlingAllowed) return false
        val hostState = state.hostToStateMap[host]
        return hostState == DomainVerificationUserState.DOMAIN_STATE_VERIFIED ||
            hostState == DomainVerificationUserState.DOMAIN_STATE_SELECTED
    }

    /** 이 앱의 "기본으로 열기" 설정 화면 (여기서 웹 주소를 앱으로 열도록 허락한다) */
    fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(AndroidSettings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** "FE-1", "fe-1", "1" → "fe-1". 알 수 없는 모양이면 null */
    internal fun normalizeId(raw: String?): String? {
        val q = raw?.trim()?.lowercase().orEmpty()
        return when {
            Regex("(fe|be)-\\d+").matches(q) -> q
            Regex("\\d+").matches(q) -> "fe-$q"
            else -> null
        }
    }
}
