package com.maeilmail.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.maeilmail.app.MainActivity
import com.maeilmail.app.R
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.ContentStore
import com.maeilmail.app.data.KST
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Settings
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.reminder.Today
import com.maeilmail.app.ui.Format
import com.maeilmail.app.ui.ListFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 홈 화면 위젯: 오늘의 질문 (첫 화면과 같은 규칙, reminder/Today). 누르면 앱의 첫 화면을 열어 거기서 답변을 확인한다.
 * - 알림 시각 전에는 질문을 보여주지 않고 여는 시각만 보인다
 * - 쉬는 날이면 아직 답변을 열지 않은 지난 질문을, 없으면 다음 질문이 오는 날을 보인다
 * - 두 칸 높이에서는 아래에 받은 질문·복습 필요·별표 개수를 두고, 누르면 그 목록을 연다
 *
 * 받아 둔 파일(목차·발송 기록·학습 기록 사본)과 설정만 읽으므로 인터넷이 없어도 그린다.
 * 알림 시각과 자정에 바뀌어야 하므로, 그때 다시 그리도록 알람을 하나 걸어 둔다 (scheduleNext).
 */
object TodayWidget {
    const val ACTION_TICK = "com.maeilmail.app.action.WIDGET_TICK"

    /** 이 높이(dp)부터 두 칸짜리 모양을 쓴다 */
    private const val LARGE_HEIGHT = 170

    /** 위젯에 보일 내용 */
    data class Content(
        /** 왼쪽 위 이름. 예: 오늘의 질문, 금요일에 받은 질문 */
        val label: String,
        /** 오른쪽 위 작은 글자 (질문 번호 또는 날짜) */
        val meta: String,
        val title: String,
        val sub: String?,
        /** 보여주는 질문 ID (질문이 없으면 null) */
        val questionId: String?,
    )

    /** 위젯 내용 (순수 함수, 테스트 대상). opened: 답변을 열어 본 질문, titleOf: 질문 ID → (제목, 분류) */
    fun content(today: Today, date: LocalDate, opened: String?, titleOf: (String) -> Pair<String, String>?): Content {
        val id = today.id
        val idle = today.idle
        val show = id != null && (today.isToday || (idle?.rest == true && id != opened))
        if (!show && idle != null) return Content(Today.LABEL, Format.day(date), idle.title, idle.body, null)
        if (id == null) return Content(Today.LABEL, Format.day(date), "오늘의 질문을 아직 받지 못했어요", null, null)
        val (title, category) = titleOf(id)
            ?: return Content(today.label, id.uppercase(), "앱을 열면 질문을 받아 와요", null, id)
        return Content(today.label, id.uppercase(), title, (idle?.body ?: idle?.title ?: category).ifEmpty { null }, id)
    }

    /** 다음에 위젯 내용이 바뀌는 시각: 오늘 알림 시각(아직 안 지났으면)과 다음 자정 중 이른 쪽 */
    fun nextChange(now: LocalDateTime, time: LocalTime): LocalDateTime {
        val reveal = now.toLocalDate().atTime(time)
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return if (now.isBefore(reveal)) reveal else midnight
    }

    /** 모든 위젯을 다시 그리고 다음에 다시 그릴 알람을 건다. 받아 둔 파일을 읽으므로 메인 스레드 밖에서 부른다 */
    fun refresh(context: Context) {
        val ctx = context.applicationContext
        val manager = AppWidgetManager.getInstance(ctx)
        val ids = manager.getAppWidgetIds(ComponentName(ctx, TodayWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancelNext(ctx)
            return
        }
        val (small, large) = views(ctx)
        for (id in ids) {
            // 안드로이드 12부터는 크기마다 쓸 모양을 함께 넘기면 런처가 고른다
            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(mapOf(SizeF(100f, 40f) to small, SizeF(180f, LARGE_HEIGHT.toFloat()) to large))
            } else {
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                if (height >= LARGE_HEIGHT) large else small
            }
            manager.updateAppWidget(id, views)
        }
        scheduleNext(ctx)
    }

    /** 한 칸 높이와 두 칸 높이 */
    private fun views(ctx: Context): Pair<RemoteViews, RemoteViews> {
        val store = ContentStore(ctx.filesDir)
        val settings = Settings.get(ctx)
        val catalog = store.loadCatalog()
        val progress = Library.readProgress(store)
        val now = Reminders.nowKst()
        val today = Today.of(progress, now, settings.notify.value.time, settings.lastNotified.value)
        val content = content(today, now.toLocalDate(), settings.opened.value) { id ->
            catalog.question(id)?.let { it.title to it.categoryName }
        }
        val known = catalog.questions.mapTo(HashSet()) { it.id }
        val received = progress?.received(today.id.takeIf { today.isToday }, known).orEmpty().size
        val records = Records.peek(ctx)

        fun fill(views: RemoteViews) = views.apply {
            setTextViewText(R.id.widget_label, content.label)
            setTextViewText(R.id.widget_meta, content.meta)
            setTextViewText(R.id.widget_title, content.title)
            setOnClickPendingIntent(R.id.widget_main, openIntent(ctx, 30) { putExtra(MainActivity.EXTRA_QUESTION, content.questionId) })
        }

        val small = fill(RemoteViews(ctx.packageName, R.layout.widget_today_small))
        val large = fill(RemoteViews(ctx.packageName, R.layout.widget_today)).apply {
            setTextViewText(R.id.widget_sub, content.sub.orEmpty())
            setViewVisibility(R.id.widget_sub, if (content.sub == null) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_strip, if (records.signedIn) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_received, "$received")
            setTextViewText(R.id.widget_review, "${records.items.values.count { it.level == Records.Level.REVIEW }}")
            setTextViewText(R.id.widget_starred, "${records.items.values.count { it.starred }}")
            setOnClickPendingIntent(R.id.widget_received_cell, filterIntent(ctx, 32, ListFilter.SHOW_RECEIVED))
            setOnClickPendingIntent(R.id.widget_review_cell, filterIntent(ctx, 33, ListFilter.SHOW_REVIEW))
            setOnClickPendingIntent(R.id.widget_starred_cell, filterIntent(ctx, 34, ListFilter.SHOW_STARRED))
        }
        return small to large
    }

    /** 누르면 앱의 첫 화면을 연다 (MainActivity.readOpenRequest) */
    private fun openIntent(ctx: Context, requestCode: Int, extras: Intent.() -> Unit): PendingIntent = PendingIntent.getActivity(
        ctx, requestCode,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_HOME, true)
            .apply(extras),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 숫자 칸: 그 필터를 건 첫 화면을 연다 */
    private fun filterIntent(ctx: Context, requestCode: Int, filter: String) =
        openIntent(ctx, requestCode) { putExtra(MainActivity.EXTRA_FILTER, filter) }

    private fun scheduleNext(ctx: Context) {
        val at = nextChange(Reminders.nowKst(), Settings.get(ctx).notify.value.time)
        val millis = at.atZone(KST).toInstant().toEpochMilli()
        ctx.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, tickIntent(ctx))
    }

    private fun cancelNext(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(tickIntent(ctx))
    }

    private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 31,
        Intent(ctx, TodayWidgetProvider::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
