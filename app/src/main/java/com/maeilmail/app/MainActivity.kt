package com.maeilmail.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.maeilmail.app.account.Account
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.Library
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
        Library.init(this)
        Account.init(this)
        Records.init(this)
        // 화면을 돌려 다시 만들 때는 이미 연 질문을 또 열지 않는다
        if (savedInstanceState == null) readOpenRequest(intent)
        // 앱을 열지 않아도 6시간마다 발송 기록을 받아 알림이 빠지지 않게 한다
        SyncWorker.schedulePeriodic(this)
        thread(name = "schedule") { Reminders.schedule(applicationContext) }
        setContent {
            AppTheme {
                App(openRequest = openRequest.value, onOpenHandled = { openRequest.value = null })
            }
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

    private fun readOpenRequest(intent: Intent?) {
        intent?.getStringExtra(EXTRA_QUESTION)?.let { openRequest.value = it }
    }

    companion object {
        const val EXTRA_QUESTION = "question"
    }
}
