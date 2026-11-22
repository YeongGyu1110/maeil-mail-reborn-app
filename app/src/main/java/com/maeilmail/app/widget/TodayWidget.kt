package com.maeilmail.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.maeilmail.app.MainActivity
import com.maeilmail.app.R
import com.maeilmail.app.data.ContentStore
import com.maeilmail.app.data.KST
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Progress
import com.maeilmail.app.data.Settings
import com.maeilmail.app.reminder.Reminders
import com.maeilmail.app.ui.Format
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 홈 화면 위젯: 오늘의 질문 번호와 제목. 누르면 그 질문을 연다 (오늘 탭과 같은 규칙).
 * - 알림 시각 전에는 질문을 보여주지 않고 "07:00에 알려 드려요"만 보인다
 * - 쉬는 날이면 다음 질문이 오는 날을 보인다
 *
 * 받아 둔 파일(목차·발송 기록)과 설정만 읽으므로 인터넷이 없어도 그린다.
 * 알림 시각과 자정에 바뀌어야 하므로, 그때 다시 그리도록 알람을 하나 걸어 둔다 (scheduleNext).
 */
object TodayWidget {
    const val ACTION_TICK = "com.maeilmail.app.action.WIDGET_TICK"

    /** 위젯에 보일 내용 */
    data class Content(
        /** 오른쪽 위 작은 글자 (날짜 또는 질문 번호) */
        val meta: String,
        val title: String,
        val sub: String?,
        /** 오늘의 질문을 보여주는 중이면 그 ID (누르면 연다) */
        val questionId: String?,
    )

    /** 위젯 내용 (순수 함수, 테스트 대상). titleOf: 질문 ID → (제목, 분류) */
    fun content(
        progress: Progress?,
        now: LocalDateTime,
        time: LocalTime,
        lastNotified: LocalDate?,
        titleOf: (String) -> Pair<String, String>?,
    ): Content {
        val today = now.toLocalDate()
        val day = Format.day(today)
        if (progress == null) return Content(day, "오늘의 질문을 아직 받지 못했어요", "앱을 열면 발송 기록을 받아 와요", null)
        val id = progress.questionOn(today)
        if (id == null) {
            val next = progress.nextDateAfter(today)
            return Content(
                day,
                "오늘은 쉬는 날이에요",
                next?.let { "다음 질문 ${Format.day(it)} ${Format.time(time)}" } ?: "다음 질문이 정해지면 알려 드릴게요",
                null,
            )
        }
        if (!Reminders.isRevealed(today, now, time, lastNotified)) {
            return Content(day, "오늘의 질문은 ${Format.time(time)}에 알려 드려요", "알림 시각 전에는 미리 보여주지 않아요", null)
        }
        val (title, category) = titleOf(id) ?: return Content(id.uppercase(), "오늘의 질문 · ${id.uppercase()}", "앱을 열면 질문을 받아 와요", id)
        return Content(id.uppercase(), title, category.ifEmpty { null }, id)
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
        val views = views(ctx)
        ids.forEach { manager.updateAppWidget(it, views) }
        scheduleNext(ctx)
    }

    private fun views(ctx: Context): RemoteViews {
        val store = ContentStore(ctx.filesDir)
        val settings = Settings.get(ctx)
        val catalog = store.loadCatalog()
        val content = content(
            progress = Library.readProgress(store),
            now = Reminders.nowKst(),
            time = settings.notify.value.time,
            lastNotified = settings.lastNotified.value,
            titleOf = { id -> catalog.question(id)?.let { it.title to it.categoryName } },
        )
        return RemoteViews(ctx.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_meta, content.meta)
            setTextViewText(R.id.widget_title, content.title)
            setTextViewText(R.id.widget_sub, content.sub.orEmpty())
            setViewVisibility(R.id.widget_sub, if (content.sub == null) View.GONE else View.VISIBLE)
            // 질문을 보여주면 "오늘의 질문", 아니면 서비스 이름
            setTextViewText(R.id.widget_label, if (content.questionId != null) "오늘의 질문" else "매일메일")
            setOnClickPendingIntent(R.id.widget_root, openIntent(ctx, content.questionId))
        }
    }

    /** 누르면: 오늘의 질문이 보이면 그 질문을, 아니면 앱(오늘 탭)을 연다 */
    private fun openIntent(ctx: Context, questionId: String?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (questionId != null) intent.putExtra(MainActivity.EXTRA_QUESTION, questionId)
        return PendingIntent.getActivity(
            ctx, 30, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

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
