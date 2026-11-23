package com.maeilmail.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.maeilmail.app.account.Account
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Settings
import com.maeilmail.app.data.WebLink
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.ui.App
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.work.SyncWorker
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    /** 알림을 눌러 열 질문 ID */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 상태 표시줄·내비게이션 바 아이콘 색은 휴대폰의 다크 모드를 따른다
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // 앱을 새로 열 때만 시작 화면의 아이콘 애니메이션을 끝까지 보여준다 (화면을 돌려 다시 만들 때는 바로)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && savedInstanceState == null) holdSplash()
        Library.init(this)
        Account.init(this)
        Records.init(this)
        // 화면을 돌려 다시 만들 때는 이미 연 질문을 또 열지 않는다
        if (savedInstanceState == null) readOpenRequest(intent)
        // 앱을 열지 않아도 6시간마다 발송 기록을 받아 알림이 빠지지 않게 한다
        SyncWorker.schedulePeriodic(this)
        thread(name = "schedule") { Reminders.schedule(applicationContext) }
        val settings = Settings.get(this)
        setContent {
            val theme by settings.theme.collectAsState()
            val dark = when (theme) {
                Settings.Theme.SYSTEM -> isSystemInDarkTheme()
                Settings.Theme.LIGHT -> false
                Settings.Theme.DARK -> true
            }
            // 상태 표시줄·내비게이션 바 아이콘 색도 고른 테마를 따른다 (안드로이드 12 미만에서는 휴대폰 설정과 다를 수 있다)
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }
            AppTheme(darkTheme = dark) {
                App(openRequest = openRequest.value, onOpenHandled = { openRequest.value = null })
            }
        }
    }

    /**
     * 시작 화면(안드로이드 12 이상): 아이콘 애니메이션(점이 튀어나오고 줄이 그어진다, 약 0.7초)이 끝날 때까지
     * 첫 화면을 그리지 않고 기다렸다가, 시작 화면이 살짝 흐려지며 사라지게 한다.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun holdSplash() {
        val start = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (SystemClock.uptimeMillis() - start < SPLASH_HOLD_MS) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        splashScreen.setOnExitAnimationListener { view ->
            view.animate().alpha(0f).setDuration(220).withEndAction { view.remove() }.start()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readOpenRequest(intent)
    }

    override fun onResume() {
        super.onResume()
        // 앱을 열 때마다 오래됐으면 웹 서비스 저장소와 맞춘다
        Library.syncIfStale()
        // 브라우저에서 GitHub 승인을 마치고 돌아왔으면 바로 로그인을 마저 한다
        Account.onResume()
        // 웹에서 바꾼 학습 기록을 가져온다 (1분이 지났으면)
        Records.refresh()
    }

    /** 열 질문: 알림·위젯이 넘긴 ID, 또는 웹 주소(메일의 "답변 확인하기" 버튼)의 ?q= */
    private fun readOpenRequest(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_QUESTION)
            ?: intent?.takeIf { it.action == Intent.ACTION_VIEW }?.let { WebLink.questionId(it.data) }
        id?.let { openRequest.value = it }
    }

    companion object {
        const val EXTRA_QUESTION = "question"

        /** 시작 화면을 붙잡아 두는 시간: 아이콘 애니메이션(0.7초)에서 시작 화면이 뜨기까지 걸린 시간을 뺀 정도 */
        private const val SPLASH_HOLD_MS = 650L
    }
}
