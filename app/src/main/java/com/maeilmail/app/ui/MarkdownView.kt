package com.maeilmail.app.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.maeilmail.app.account.Records
import com.maeilmail.app.ui.theme.AppTheme
import org.json.JSONObject

/**
 * 마크다운 본문을 웹 서비스와 같은 방식(marked + Prism)으로 그린다. assets/viewer/viewer.html 참고.
 * 한 화면에서 질문을 넘길 때는 WebView를 새로 만들지 않고 render()로 내용만 바꾼다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownView(
    /** 바뀌면 다시 그리고 맨 위로 올린다 (질문 ID) */
    key: String,
    meta: String,
    title: String,
    markdown: String,
    modifier: Modifier = Modifier,
    /** 이 질문의 이해도. null이면 학습 기록 칸을 감춘다 (로그인하지 않음) */
    level: Records.Level? = null,
    /** 본문 끝의 이해도 버튼을 눌렀을 때 */
    onLevel: (Records.Level) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val fontScale = LocalDensity.current.fontScale
    var pageReady by remember { mutableStateOf(false) }
    val currentOnLevel by rememberUpdatedState(onLevel)

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // 휴대폰의 글자 크기 설정을 따른다
            settings.textZoom = (fontScale * 100).toInt()
            isVerticalScrollBarEnabled = false
            setBackgroundColor(colors.bgPaper.toArgb())
            addJavascriptInterface(Bridge(context) { key -> currentOnLevel(Records.Level.of(key)) }, "MaeilApp")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    pageReady = true
                }

                // 본문 안의 링크는 앱 밖(브라우저)에서 연다. 그 밖의 이동은 막는다
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme in setOf("http", "https", "mailto")) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "링크를 열 앱이 없어요", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }
            }
            loadUrl("file:///android_asset/viewer/viewer.html")
        }
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    LaunchedEffect(pageReady, key, markdown) {
        if (!pageReady) return@LaunchedEffect
        val payload = JSONObject()
            .put("meta", meta)
            .put("title", title)
            .put("markdown", markdown)
            .put("dark", colors.isDark)
        webView.evaluateJavascript("render($payload)", null)
    }

    // 이해도는 본문을 다시 그리지 않고 학습 기록 칸만 바꾼다 (웹에서 바뀐 기록을 받아 와도 읽던 자리가 그대로다)
    LaunchedEffect(pageReady, key, level) {
        if (!pageReady) return@LaunchedEffect
        val state = if (level == null) "null" else JSONObject().put("signedIn", true).put("level", level.key ?: "new").toString()
        webView.evaluateJavascript("setStudy($state)", null)
    }

    LaunchedEffect(pageReady, colors.isDark) {
        if (!pageReady) return@LaunchedEffect
        webView.setBackgroundColor(colors.bgPaper.toArgb())
        webView.evaluateJavascript("setTheme(${colors.isDark})", null)
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

/** 본문 스크립트가 부르는 앱 기능 */
private class Bridge(private val context: Context, private val onLevel: (String?) -> Unit) {
    private val main = android.os.Handler(context.mainLooper)

    /** 이해도 버튼: "new" | "review" | "done" */
    @JavascriptInterface
    fun setLevel(level: String) {
        main.post { onLevel(level.takeIf { it != "new" }) }
    }

    @JavascriptInterface
    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("코드", text))
        // Android 13부터는 시스템이 복사했다고 알려 준다
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            android.os.Handler(context.mainLooper).post {
                Toast.makeText(context, "코드를 복사했어요", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
