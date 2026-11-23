package com.maeilmail.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 이 기기에만 두는 설정 (계정과 상관없다).
 * - 알림: 켜기, 시각(한국 시간, 기본 07:00)
 * - 마지막으로 알린 발송일: 같은 날 두 번 알리지 않도록
 * - 화면 테마: 휴대폰 설정을 따르거나 라이트·다크로 고정
 */
class Settings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    data class Notify(val enabled: Boolean, val time: LocalTime)

    private val _notify = MutableStateFlow(readNotify())
    val notify: StateFlow<Notify> = _notify.asStateFlow()

    private fun readNotify() = Notify(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        time = LocalTime.of(prefs.getInt(KEY_HOUR, DEFAULT_TIME.hour), prefs.getInt(KEY_MINUTE, DEFAULT_TIME.minute)),
    )

    fun setNotify(value: Notify) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, value.enabled)
            .putInt(KEY_HOUR, value.time.hour)
            .putInt(KEY_MINUTE, value.time.minute)
            .apply()
        _notify.value = value
    }

    private val _lastNotified = MutableStateFlow(readLastNotified())
    /** 마지막으로 알린 발송일 */
    val lastNotified: StateFlow<LocalDate?> = _lastNotified.asStateFlow()

    private fun readLastNotified() = prefs.getString(KEY_LAST_NOTIFIED, null)?.let(LocalDate::parse)

    fun setLastNotified(date: LocalDate) {
        prefs.edit().putString(KEY_LAST_NOTIFIED, date.toString()).apply()
        _lastNotified.value = date
    }

    private val _nextAlarm = MutableStateFlow(prefs.getString(KEY_NEXT_ALARM, null)?.let(LocalDateTime::parse))
    /** 걸어 둔 다음 알림 시각 (한국 시간). 화면에 보여주는 용도 */
    val nextAlarm: StateFlow<LocalDateTime?> = _nextAlarm.asStateFlow()

    fun setNextAlarm(at: LocalDateTime?) {
        prefs.edit().putString(KEY_NEXT_ALARM, at?.toString()).apply()
        _nextAlarm.value = at
    }

    /** 화면 테마. SYSTEM이면 휴대폰의 다크 모드 설정을 따른다 */
    enum class Theme(val label: String) { SYSTEM("시스템 설정"), LIGHT("라이트"), DARK("다크") }

    private val _theme = MutableStateFlow(
        Theme.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: Theme.SYSTEM,
    )
    val theme: StateFlow<Theme> = _theme.asStateFlow()

    fun setTheme(value: Theme) {
        prefs.edit().putString(KEY_THEME, value.name).apply()
        _theme.value = value
    }

    /** 알림 권한을 처음 한 번 물었는지 (거절한 뒤로는 저절로 다시 묻지 않는다) */
    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_PERMISSION, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_PERMISSION, value).apply()

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(7, 0)
        private const val KEY_ENABLED = "notify_enabled"
        private const val KEY_HOUR = "notify_hour"
        private const val KEY_MINUTE = "notify_minute"
        private const val KEY_LAST_NOTIFIED = "last_notified_date"
        private const val KEY_ASKED_PERMISSION = "asked_notification_permission"
        private const val KEY_NEXT_ALARM = "next_alarm"
        private const val KEY_THEME = "theme"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) { instance ?: Settings(context).also { instance = it } }
    }
}
