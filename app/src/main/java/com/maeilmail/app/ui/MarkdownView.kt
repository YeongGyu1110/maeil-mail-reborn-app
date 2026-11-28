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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Question
import com.maeilmail.app.ui.theme.AppTheme
import org.json.JSONObject

/** 본문 끝 학습 기록 칸(이해도 · 내 메모)에 넘길 이 질문의 기록 */
data class StudyState(val item: Records.Item, val canCommit: Boolean, val repo: String?) {
    fun toJson(): JSONObject = JSONObject()
        .put("level", item.level.key ?: "new")
        .put("memo", item.memo ?: "")
        .put("memoAt", item.memoAt ?: JSONObject.NULL)
        .put("uncommitted", item.memoUncommitted)
        .put("canCommit", canCommit)
        .put("repo", repo ?: JSONObject.NULL)
        .put("draft", item.draft?.toJson() ?: JSONObject.NULL)
}

/** 본문 스크립트에 넘기는 초안 { text, at, base } */
fun Records.Draft.toJson(): JSONObject = JSONObject().put("text", text).put("at", at).put("base", base)

/**
 * 좌우로 밀면 손가락을 따라 함께 보일 이전·다음 질문. 본문 스크립트가 이 질문의 문서를 미리 그려 두고,
 * 넘기면 이것으로 그 질문을 바로 그린다. markdown이 ""이면 아직 읽지 못했거나 받아 둔 답변이 없다.
 */
data class Neighbor(
    val question: Question,
    val meta: String,
    val markdown: String,
    val study: StudyState?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", question.id)
        .put("title", question.title)
        .put("meta", meta)
        .put("markdown", markdown)
        .put("study", study?.toJson() ?: JSONObject.NULL)
}

/** 화면이 본문 스크립트에 시키는 일 */
class ViewerController {
    internal var webView: WebView? = null

    /** 메모 창의 뒤로 가기: 펼친 목록을 닫거나, 겹친 곳 고르기를 그만두거나, 메모 창을 닫는다 (바뀐 것이 있으면 묻는다) */
    fun back() {
        webView?.evaluateJavascript("memoBack()", null)
    }

    /** 메모 창을 닫아도 되는지 묻는다 (저장하지 않은 변경이 있으면 초안 저장·버리기를 묻는다). 답은 본문 스크립트가 leave로 알린다 */
    fun requestLeave(seq: Int) {
        webView?.evaluateJavascript("requestLeave($seq)", null)
    }
}

/**
 * 마크다운 본문을 웹 서비스와 같은 방식(marked + Prism)으로 그린다. assets/viewer/viewer.html 참고.
 * 한 화면에서 질문을 넘길 때는 WebView를 새로 만들지 않고 render()로 내용만 바꾼다.
 * 본문 끝의 학습 기록도 이 안에서 그린다. 메모 편집기는 웹과 같은 memo-ui.js다.
 * 좌우로 밀어 옆 질문으로 넘기는 것도 본문 스크립트가 한다: 옆 질문을 미리 그려 두고 손가락을 따라 보이다가, 넘기면 navigate로 알린다.
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
    /** 이전·다음 질문: 좌우로 밀면 손가락을 따라 함께 보인다 */
    prev: Neighbor? = null,
    next: Neighbor? = null,
    /** 그 너머의 질문. 넘긴 직후 앱이 따라잡기 전에 한 번 더 넘겨도 옆 질문이 있게 미리 넘긴다 */
    prev2: Neighbor? = null,
    next2: Neighbor? = null,
    /** 이 질문의 학습 기록. null이면 학습 기록 칸을 감춘다 (로그인하지 않음) */
    study: StudyState? = null,
    /** 본문이 화면 아래 끝(손잡이 뒤)까지 닿을 때, 본문의 끝을 그만큼 더 띄운다 */
    bottomInset: Dp = 0.dp,
    controller: ViewerController = remember { ViewerController() },
    onRequest: (name: String, payload: JSONObject, reply: (JSONObject) -> Unit) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val fontScale = LocalDensity.current.fontScale
    var pageReady by remember { mutableStateOf(false) }
    val currentOnRequest by rememberUpdatedState(onRequest)

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            // 편집기 보기 설정을 이 기기에 둔다 (localStorage). 예전 앱이 여기에 보관하던 쓰던 메모는 처음 열 때 계정의 초안으로 옮긴다
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

    // 차례: render → setStudy → setNeighbors. 좌우로 넘기면 본문 스크립트가 먼저 그 질문을 그리고 navigate로 알리므로,
    // 여기서 넘기는 것은 뒤따라 맞추는 것이다. 빠르게 여러 번 넘기면 지나간 질문의 것이 늦게 가므로 질문 ID를 함께 넘긴다
    LaunchedEffect(pageReady, key, markdown, title) {
        if (!pageReady) return@LaunchedEffect
        val payload = JSONObject()
            .put("id", key)
            .put("meta", meta)
            .put("title", title)
            .put("markdown", markdown)
            .put("dark", colors.isDark)
            // 메모 창의 아이콘도 앱의 아이콘처럼 휴대폰 글자 크기를 따른다 (scaledIcon과 같은 비율)
            .put("iconScale", fontScale.coerceIn(0.8f, 1.3f).toDouble())
        webView.evaluateJavascript("render($payload)", null)
    }

    // 학습 기록은 본문을 다시 그리지 않고 그 칸만 바꾼다 (웹에서 바뀐 기록을 받아 와도 읽던 자리가 그대로다)
    LaunchedEffect(pageReady, key, study) {
        if (!pageReady) return@LaunchedEffect
        webView.evaluateJavascript("setStudy(${study?.toJson() ?: "null"}, ${JSONObject.quote(key)})", null)
    }

    // 옆 질문은 본문과 따로 넘긴다 (옆 질문의 기록이나 본문만 바뀌어도 지금 문서를 다시 그리지 않는다)
    LaunchedEffect(pageReady, key, prev, next, prev2, next2) {
        if (!pageReady) return@LaunchedEffect
        val payload = JSONObject()
            .put("id", key)
            .put("prev2", prev2?.toJson() ?: JSONObject.NULL)
            .put("prev", prev?.toJson() ?: JSONObject.NULL)
            .put("next", next?.toJson() ?: JSONObject.NULL)
            .put("next2", next2?.toJson() ?: JSONObject.NULL)
        webView.evaluateJavascript("setNeighbors($payload)", null)
    }

    // WebView의 CSS 픽셀은 dp와 같다
    LaunchedEffect(pageReady, bottomInset) {
        if (!pageReady) return@LaunchedEffect
        webView.evaluateJavascript("setInsets({ bottom: ${bottomInset.value} })", null)
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
    private val onRequest: (name: String, id: Int, payload: JSONObject) -> Unit,
) {
    private val main = Handler(context.mainLooper)

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
