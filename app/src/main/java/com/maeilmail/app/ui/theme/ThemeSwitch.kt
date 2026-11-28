package com.maeilmail.app.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.maeilmail.app.data.Settings

/**
 * 화면 테마(시스템 설정 / 라이트 / 다크)를 바꾼다.
 * 안드로이드 12 이상은 앱별 다크 모드(UiModeManager.setApplicationNightMode)로 바꾼다.
 * 그러면 앱 화면뿐 아니라 시작 화면·창 배경·상태 표시줄 아이콘처럼 리소스(values-night)를 쓰는 곳까지 함께 바뀌고,
 * 시스템이 이 값을 기억해 다음에 앱을 열 때도 그대로 시작한다. 바꾸면 화면을 새로 그린다(보던 탭·질문은 남는다).
 * 그보다 낮은 판에서는 앱 화면(Compose)만 바뀐다.
 */
object ThemeSwitch {
    fun set(context: Context, theme: Settings.Theme) {
        Settings.get(context).setTheme(theme)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
                when (theme) {
                    Settings.Theme.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    Settings.Theme.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    Settings.Theme.DARK -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
    }
}
