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
 * - 답변을 열어 본 마지막 받은 질문: 첫 화면의 오늘의 질문을 작게 줄인다
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

    private val _opened = MutableStateFlow(prefs.getString(KEY_OPENED, null))
    /** 답변을 열어 본 마지막 받은 질문의 ID. 날짜가 아니라 질문으로 기억해 쉬는 날에 열어도 맞는다 */
    val opened: StateFlow<String?> = _opened.asStateFlow()

    fun setOpened(id: String) {
        prefs.edit().putString(KEY_OPENED, id).apply()
        _opened.value = id
    }

    private val _noticeHidden = MutableStateFlow(prefs.getBoolean(KEY_NOTICE_HIDDEN, false))
    /** 첫 화면에서 "알림이 꺼져 있어요" 안내를 닫았는지. 알림이 다시 켜지면 푼다 */
    val noticeHidden: StateFlow<Boolean> = _noticeHidden.asStateFlow()

    fun setNoticeHidden(value: Boolean) {
        if (_noticeHidden.value == value) return
        prefs.edit().putBoolean(KEY_NOTICE_HIDDEN, value).apply()
        _noticeHidden.value = value
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
        private const val KEY_OPENED = "opened_question"
        private const val KEY_NOTICE_HIDDEN = "notice_hidden"

        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) { instance ?: Settings(context).also { instance = it } }
    }
}
