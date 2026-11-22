package com.maeilmail.app.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
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

/** 본문 끝 학습 기록 칸에 넘길 이 질문의 기록 */
data class StudyState(val item: Records.Item, val canCommit: Boolean, val repo: String?) {
    fun toJson(): JSONObject = JSONObject()
        .put("level", item.level.key ?: "new")
        .put("memo", item.memo ?: "")
        .put("memoAt", item.memoAt ?: JSONObject.NULL)
        .put("uncommitted", item.memoUncommitted)
        .put("canCommit", canCommit)
        .put("repo", repo ?: JSONObject.NULL)
}

/** 화면이 본문 스크립트에 시키는 일 */
class ViewerController {
    internal var webView: WebView? = null

    /** 쓰던 메모를 버리고 편집기를 닫는다 (초안도 지운다) */
    fun discardMemo() {
        webView?.evaluateJavascript("discardMemo()", null)
    }
}

/**
 * 마크다운 본문을 웹 서비스와 같은 방식(marked + Prism)으로 그린다. assets/viewer/viewer.html 참고.
 * 한 화면에서 질문을 넘길 때는 WebView를 새로 만들지 않고 render()로 내용만 바꾼다.
 * 본문 끝의 학습 기록(이해도·내 메모)도 이 안에서 그린다. 메모 편집기는 웹과 같은 memo-ui.js다.
 *
 * onRequest: 본문 스크립트가 앱에 부탁하는 일 (확인 창, 메모 저장 등). reply로 답하면 스크립트가 이어서 한다.
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
    /** 이 질문의 학습 기록. null이면 학습 기록 칸을 감춘다 (로그인하지 않음) */
    study: StudyState? = null,
    controller: ViewerController = remember { ViewerController() },
    /** 본문 끝의 이해도 버튼을 눌렀을 때 */
    onLevel: (Records.Level) -> Unit = {},
    onRequest: (name: String, payload: JSONObject, reply: (JSONObject) -> Unit) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val fontScale = LocalDensity.current.fontScale
    var pageReady by remember { mutableStateOf(false) }
    val currentOnLevel by rememberUpdatedState(onLevel)
    val currentOnRequest by rememberUpdatedState(onRequest)

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            // 메모 초안과 편집기 보기 설정을 이 기기에 둔다 (localStorage)
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            // 휴대폰의 글자 크기 설정을 따른다
            settings.textZoom = (fontScale * 100).toInt()
            isVerticalScrollBarEnabled = false
            setBackgroundColor(colors.bgPaper.toArgb())
            val view = this
            addJavascriptInterface(
                Bridge(
                    context,
                    onLevel = { key -> currentOnLevel(Records.Level.of(key)) },
                    onRequest = { name, id, payload ->
                        currentOnRequest(name, payload) { result ->
                            if (id != 0) view.evaluateJavascript("__reply($id, $result)", null)
                        }
                    },
                ),
                "MaeilApp",
            )
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
        controller.webView = webView
        onDispose {
            controller.webView = null
            webView.destroy()
        }
    }

    LaunchedEffect(pageReady, key, markdown) {
        if (!pageReady) return@LaunchedEffect
        val payload = JSONObject()
            .put("id", key)
            .put("meta", meta)
            .put("title", title)
            .put("markdown", markdown)
            .put("dark", colors.isDark)
        webView.evaluateJavascript("render($payload)", null)
    }

    // 학습 기록은 본문을 다시 그리지 않고 그 칸만 바꾼다 (웹에서 바뀐 기록을 받아 와도 읽던 자리가 그대로다)
    LaunchedEffect(pageReady, key, study) {
        if (!pageReady) return@LaunchedEffect
        webView.evaluateJavascript("setStudy(${study?.toJson() ?: "null"})", null)
    }

    LaunchedEffect(pageReady, colors.isDark) {
        if (!pageReady) return@LaunchedEffect
        webView.setBackgroundColor(colors.bgPaper.toArgb())
        webView.evaluateJavascript("setTheme(${colors.isDark})", null)
    }

    AndroidView(factory = { webView }, modifier = modifier)
}

/** 본문 스크립트가 부르는 앱 기능. 스크립트는 WebView의 별도 스레드에서 부르므로 화면 스레드로 넘긴다 */
private class Bridge(
    private val context: Context,
    private val onLevel: (String?) -> Unit,
    private val onRequest: (name: String, id: Int, payload: JSONObject) -> Unit,
) {
    private val main = Handler(context.mainLooper)

    /** 이해도 버튼: "new" | "review" | "done" */
    @JavascriptInterface
    fun setLevel(level: String) {
        main.post { onLevel(level.takeIf { it != "new" }) }
    }

    /** 앱에 부탁한다. id가 0이 아니면 앱이 __reply(id, 결과)로 답한다 */
    @JavascriptInterface
    fun request(name: String, id: Int, payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrDefault(JSONObject())
        main.post { onRequest(name, id, json) }
    }

    @JavascriptInterface
    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("코드", text))
        // Android 13부터는 시스템이 복사했다고 알려 준다
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            main.post { Toast.makeText(context, "코드를 복사했어요", Toast.LENGTH_SHORT).show() }
        }
    }
}
