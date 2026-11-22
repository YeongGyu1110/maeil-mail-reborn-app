package com.maeilmail.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/**
 * 오늘의 질문 위젯. 그리는 일은 TodayWidget이 한다.
 * 위젯을 놓거나 크기를 바꿀 때, 그리고 알림 시각·자정 알람(ACTION_TICK)에 다시 그린다.
 */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TodayWidget.ACTION_TICK) {
            refreshAsync(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAsync(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) =
        refreshAsync(context)

    override fun onDisabled(context: Context) = refreshAsync(context) // 위젯이 다 없어지면 알람을 거둔다

    /** 받아 둔 파일을 읽으므로 메인 스레드 밖에서 그린다 */
    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        thread(name = "widget") {
            try {
                TodayWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
