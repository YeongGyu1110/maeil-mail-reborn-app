package com.maeilmail.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maeilmail.app.data.Library
import com.maeilmail.app.ui.App
import com.maeilmail.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 상태 표시줄·내비게이션 바 아이콘 색은 휴대폰의 다크 모드를 따른다
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Library.init(this)
        setContent {
            AppTheme {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 앱을 열 때마다 오래됐으면 웹 서비스 저장소와 맞춘다
        Library.syncIfStale()
    }
}
