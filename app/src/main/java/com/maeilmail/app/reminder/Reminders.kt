package com.maeilmail.app.reminder

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.maeilmail.app.MainActivity
import com.maeilmail.app.R
import com.maeilmail.app.data.ContentStore
import com.maeilmail.app.data.KST
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Progress
import com.maeilmail.app.data.Settings
import com.maeilmail.app.widget.TodayWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 오늘의 질문 알림.
 *
 * - 발송 기록(progress.json)의 next에는 앞으로 받을 질문이 발송일별로 미리 정해져 있고, 보낼 때까지 바뀌지 않는다.
 *   그래서 알림 시각에 인터넷이 없어도 받아 둔 기록으로 알릴 수 있다.
 * - 알람은 한 번에 하나만(가장 가까운 발송일) 걸어 두고, 울릴 때마다 다음 발송일을 다시 건다.
 * - 날짜와 알림 시각은 한국 시간 기준이다.
 * - 같은 발송일은 한 번만 알린다. 휴대폰이 꺼져 있다가 알림 시각이 지나 켜졌으면, 그날 안에서는 바로 알린다.
 */
object Reminders {
    const val ACTION_REMIND = "com.maeilmail.app.action.REMIND"
    const val EXTRA_DATE = "date"
    const val CHANNEL = "today_question"
    private const val NOTIFICATION_ID = 3001

    data class Alarm(val date: LocalDate, val at: LocalDateTime)

    // ------------------------------------------------------------ 시각 계산 (순수 함수, 테스트 대상)

    /**
     * 다음에 알릴 발송일과 시각. now·time은 한국 시간.
     * 이미 알린 날(lastNotified 이전)과 지난 날은 건너뛰고, 오늘 알림 시각이 지났는데 아직 안 알렸으면 지금 알린다.
     */
    fun nextAlarm(progress: Progress?, now: LocalDateTime, time: LocalTime, lastNotified: LocalDate?): Alarm? {
        val today = now.toLocalDate()
        val date = progress?.scheduled()?.keys
            ?.filter { !it.isBefore(today) && (lastNotified == null || it.isAfter(lastNotified)) }
            ?.minOrNull() ?: return null
        val at = date.atTime(time)
        return Alarm(date, if (at.isBefore(now)) now else at)
    }

    /** 첫 화면에 오늘의 질문을 보여줄지: 알림 시각이 지났거나 이미 알렸으면. 알림 시각 전에는 미리 보여주지 않는다 */
    fun isRevealed(date: LocalDate, now: LocalDateTime, time: LocalTime, lastNotified: LocalDate?): Boolean =
        (lastNotified != null && !lastNotified.isBefore(date)) || !now.isBefore(date.atTime(time))

    fun nowKst(): LocalDateTime = LocalDateTime.now(KST)

    // ------------------------------------------------------------ 알람 예약

    /**
     * 설정과 받아 둔 발송 기록에 맞춰 다음 알람을 건다 (꺼져 있거나 알릴 날이 없으면 취소). 여러 번 불러도 안전.
     * 발송 기록·알림 시각·알린 날이 바뀔 때마다 불리므로, 같은 것을 보여주는 홈 화면 위젯도 여기서 다시 그린다.
     */
    fun schedule(context: Context) {
        val ctx = context.applicationContext
        runCatching { TodayWidget.refresh(ctx) }
        val settings = Settings.get(ctx)
        val notify = settings.notify.value
        val am = ctx.getSystemService(AlarmManager::class.java)
        val alarm = if (notify.enabled) {
            nextAlarm(Library.readProgress(ContentStore(ctx.filesDir)), nowKst(), notify.time, settings.lastNotified.value)
        } else {
            null
        }
        if (alarm == null) {
            am.cancel(alarmIntent(ctx, null))
            settings.setNextAlarm(null)
            return
        }
        val at = alarm.at.atZone(KST).toInstant().toEpochMilli()
        val pi = alarmIntent(ctx, alarm.date)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        settings.setNextAlarm(alarm.at)
    }

    /** 알람은 하나만 둔다: 같은 요청 번호로 덮어쓴다 */
    private fun alarmIntent(ctx: Context, date: LocalDate?): PendingIntent = PendingIntent.getBroadcast(
        ctx, 20,
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_REMIND).apply {
            if (date != null) putExtra(EXTRA_DATE, date.toString())
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // ------------------------------------------------------------ 알림

    /** 이 앱의 알림이 허용됐는지 (알림 종류 하나만 꺼 둔 것은 problems가 본다) */
    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** 배터리 최적화에서 빠져 있는지 (빠져 있어야 절전 중에도 알림이 늦지 않다) */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** 알림이 오지 않거나 늦을 수 있는 까닭 */
    enum class Problem {
        /** 이 앱의 알림이 꺼져 있다 */
        PERMISSION,

        /** '오늘의 질문' 알림 종류만 꺼져 있다 */
        CHANNEL,

        /** 휴대폰이 이 앱의 백그라운드 동작을 막았다 */
        BACKGROUND,

        /** 배터리 최적화 대상이라 절전 중에 늦을 수 있다 */
        BATTERY;

        /** 알림이 아예 보이지 않는 문제인지 (나머지는 늦거나 빠질 수 있는 문제) */
        val blocks: Boolean get() = this == PERMISSION || this == CHANNEL
    }

    fun problems(context: Context): List<Problem> = buildList {
        if (!canNotify(context)) {
            add(Problem.PERMISSION)
        } else {
            val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) add(Problem.CHANNEL)
        }
        val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
        if (restricted) add(Problem.BACKGROUND)
        if (!isIgnoringBatteryOptimizations(context)) add(Problem.BATTERY)
    }

    /** 알람이 울렸을 때: date에 받는 질문을 알린다. 받아 둔 기록에 없으면 질문 없이 알린다 */
    fun notifyDate(context: Context, date: LocalDate) {
        val ctx = context.applicationContext
        val store = ContentStore(ctx.filesDir)
        val id = Library.readProgress(store)?.questionOn(date)
        val question = id?.let { store.loadCatalog().question(it) }
        if (question != null) {
            post(ctx, "오늘의 질문 · ${question.label}", question.title, question.id)
        } else {
            post(ctx, "오늘의 질문이 도착했어요", "앱을 열어 오늘의 질문을 확인해 보세요", null)
        }
    }

    /** 알림 종류를 만들어 둔다 (이미 있으면 그대로). 만들어 두어야 사용자가 꺼 두었는지 알 수 있다 */
    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "오늘의 질문", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "매일 정한 시각에 오늘의 면접 질문을 알려요"
            },
        )
    }

    private fun post(ctx: Context, title: String, text: String, questionId: String?) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        ensureChannel(ctx)
        // 누르면 첫 화면을 연다. 맨 위에 이 질문이 있고 거기서 답변을 확인한다 (MainActivity.readOpenRequest)
        val open = PendingIntent.getActivity(
            ctx, 21,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_HOME, true)
                .apply { if (questionId != null) putExtra(MainActivity.EXTRA_QUESTION, questionId) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF007D53.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }
}
