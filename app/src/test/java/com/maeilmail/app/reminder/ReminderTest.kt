package com.maeilmail.app.reminder

import com.maeilmail.app.data.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderTest {
    private val seven = LocalTime.of(7, 0)
    private fun d(day: Int) = LocalDate.of(2026, 11, day) // 11/20 금, 11/21 토, 11/23 월
    private fun at(day: Int, h: Int, m: Int = 0) = d(day).atTime(h, m)

    /** 11/20(금) 실행 뒤: 오늘 보낸 것은 fe-78, 다음은 월·화·수 */
    private val afterFriday = Progress(
        sent = listOf("fe-54", "be-22", "fe-108", "fe-78"),
        today = Progress.Sent("2026-11-20", "fe-78"),
        next = mapOf("2026-11-23" to "be-100", "2026-11-24" to "fe-118", "2026-11-25" to "be-123"),
        hasNext = true,
    )

    // ---------- 오늘의 질문 (웹 todayQuestionId와 같은 규칙)

    @Test
    fun `발송 전에는 next, 발송 뒤에는 today, 둘 다 아니면 쉬는 날`() {
        assertEquals("be-100", afterFriday.questionOn(d(23)))
        assertEquals("fe-78", afterFriday.questionOn(d(20)))
        assertNull(afterFriday.questionOn(d(21)))
    }

    @Test
    fun `예전 형식이면 마지막으로 보낸 질문`() {
        val old = Progress(listOf("fe-1", "fe-5"), today = null, next = emptyMap(), hasNext = false)
        assertEquals("fe-5", old.questionOn(d(21)))
    }

    @Test
    fun `쉬는 날에는 다음 발송일을 안다`() {
        assertEquals(d(23), afterFriday.nextDateAfter(d(21)))
        assertNull(afterFriday.nextDateAfter(d(25)))
    }

    // ---------- 다음 알림

    @Test
    fun `쉬는 날에는 다음 발송일 알림 시각에 건다`() {
        val alarm = Reminders.nextAlarm(afterFriday, at(21, 10), seven, lastNotified = d(20))
        assertEquals(Reminders.Alarm(d(23), at(23, 7)), alarm)
    }

    @Test
    fun `발송일 아침 알림 시각 전이면 그 시각에 건다`() {
        val alarm = Reminders.nextAlarm(afterFriday, at(23, 6), seven, lastNotified = d(20))
        assertEquals(Reminders.Alarm(d(23), at(23, 7)), alarm)
    }

    @Test
    fun `휴대폰이 꺼져 있어 알림 시각이 지났으면 그날 안에서는 바로 알린다`() {
        val now = at(23, 8, 30)
        assertEquals(Reminders.Alarm(d(23), now), Reminders.nextAlarm(afterFriday, now, seven, lastNotified = d(20)))
    }

    @Test
    fun `이미 알린 날은 건너뛰고 다음 발송일에 건다`() {
        val alarm = Reminders.nextAlarm(afterFriday, at(23, 8), seven, lastNotified = d(23))
        assertEquals(Reminders.Alarm(d(24), at(24, 7)), alarm)
    }

    @Test
    fun `메일이 먼저 나간 뒤 알림 시각을 늦췄으면 today 질문으로 알린다`() {
        // 11/23 10:40에 Actions가 보내 today로 옮겨졌고, 알림 시각을 13:00으로 바꾼 경우
        val afterMonday = Progress(
            sent = afterFriday.sent + "be-100",
            today = Progress.Sent("2026-11-23", "be-100"),
            next = mapOf("2026-11-24" to "fe-118", "2026-11-25" to "be-123", "2026-11-26" to "fe-9"),
            hasNext = true,
        )
        val alarm = Reminders.nextAlarm(afterMonday, at(23, 11), LocalTime.of(13, 0), lastNotified = d(20))
        assertEquals(Reminders.Alarm(d(23), at(23, 13)), alarm)
        assertEquals("be-100", afterMonday.questionOn(d(23)))
    }

    @Test
    fun `알릴 날이 없거나 기록이 없으면 걸지 않는다`() {
        assertNull(Reminders.nextAlarm(afterFriday, at(26, 9), seven, lastNotified = d(25)))
        assertNull(Reminders.nextAlarm(null, at(21, 9), seven, lastNotified = null))
    }

    // ---------- 홈 화면에 오늘의 질문을 보일지

    @Test
    fun `알림 시각 전에는 오늘의 질문을 숨긴다`() {
        assertFalse(Reminders.isRevealed(d(23), at(23, 6, 59), seven, lastNotified = d(20)))
        assertTrue(Reminders.isRevealed(d(23), at(23, 7), seven, lastNotified = d(20)))
        // 알림이 먼저 울렸으면(시각을 바꾸기 전에 알린 경우 등) 보인다
        assertTrue(Reminders.isRevealed(d(23), at(23, 6), seven, lastNotified = d(23)))
    }
}
