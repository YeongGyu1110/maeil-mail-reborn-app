package com.maeilmail.app.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 화면에 보일 날짜·시각 형식 (한국어) */
object Format {
    private val day = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val dayLong = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    /** 11월 23일 (월) */
    fun day(date: LocalDate): String = date.format(day)

    /** 11월 21일 토요일 */
    fun dayLong(date: LocalDate): String = date.format(dayLong)

    /** 07:00 */
    fun time(t: LocalTime): String = t.format(time)

    /** 오늘 07:00 / 내일 07:00 / 11월 23일 (월) 07:00 */
    fun relative(at: LocalDateTime, today: LocalDate): String {
        val d = at.toLocalDate()
        val prefix = when (d) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            else -> day(d)
        }
        return "$prefix ${time(at.toLocalTime())}"
    }
}
