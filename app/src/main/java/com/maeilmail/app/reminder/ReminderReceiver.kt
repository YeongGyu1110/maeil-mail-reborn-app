package com.maeilmail.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.maeilmail.app.data.Settings
import com.maeilmail.app.work.SyncWorker
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * - 알람: 그날 질문을 알리고, 다음 발송일 알람을 건 뒤, 저장소와 맞추는 작업을 건다
 * - 재부팅·앱 업데이트·시간 변경: 알람이 사라지거나 어긋나므로 다시 건다
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        // 받아 둔 파일을 읽으므로 메인 스레드 밖에서 한다
        thread(name = "reminder") {
            try {
                when (intent.action) {
                    Reminders.ACTION_REMIND -> {
                        val date = intent.getStringExtra(Reminders.EXTRA_DATE)?.let(LocalDate::parse)
                        val settings = Settings.get(app)
                        val last = settings.lastNotified.value
                        if (date != null && settings.notify.value.enabled && (last == null || date.isAfter(last))) {
                            Reminders.notifyDate(app, date)
                            settings.setLastNotified(date)
                        }
                        Reminders.schedule(app)
                        // 다음 발송일이 기록에 더 생겼을 수 있으니 인터넷이 되면 저장소와 맞춘다 (맞춘 뒤 알람을 다시 건다)
                        SyncWorker.runOnce(app)
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        Reminders.schedule(app)
                        SyncWorker.schedulePeriodic(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
