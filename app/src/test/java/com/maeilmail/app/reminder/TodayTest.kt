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

/** 첫 화면과 위젯에 보일 오늘의 질문 */
class TodayTest {
    private val seven = LocalTime.of(7, 0)
    private val monday = LocalDate.of(2026, 11, 23)
    private val progress = Progress(
        sent = listOf("fe-7", "fe-1"),
        today = Progress.Sent("2026-11-20", "fe-1"),
        next = mapOf("2026-11-23" to "be-100", "2026-11-24" to "fe-118"),
        hasNext = true,
    )

    private fun today(now: LocalDateTime, p: Progress? = progress, lastNotified: LocalDate? = null) =
        Today.of(p, now, seven, lastNotified)

    @Test
    fun `알림 시각이 지나면 오늘 질문`() {
        val t = today(monday.atTime(7, 0))
        assertEquals("be-100", t.id)
        assertEquals("오늘의 질문", t.label)
        assertTrue(t.isToday)
        assertNull(t.idle)
    }

    @Test
    fun `이미 알렸으면 알림 시각 전이라도 오늘 질문`() {
        assertEquals("be-100", today(monday.atTime(6, 0), lastNotified = monday).id)
    }

    @Test
    fun `알림 시각 전에는 오늘 질문 대신 앞서 받은 질문과 여는 시각`() {
        val t = today(monday.atTime(6, 59))
        assertEquals("fe-1", t.id)
        assertEquals("금요일에 받은 질문", t.label)
        assertFalse(t.isToday)
        assertEquals("오늘의 질문은 07:00에 열려요", t.idle?.title)
        assertFalse(t.idle!!.rest)
    }

    @Test
    fun `쉬는 날에는 앞서 받은 질문과 다음 날짜`() {
        val t = today(LocalDate.of(2026, 11, 21).atTime(12, 0))
        assertEquals("fe-1", t.id)
        assertEquals("금요일에 받은 질문", t.label)
        assertEquals("오늘은 쉬는 날이에요", t.idle?.title)
        assertEquals("다음 질문은 11월 23일 (월) 07:00에 열려요", t.idle?.body)
        assertTrue(t.idle!!.rest)
    }

    @Test
    fun `메일이 먼저 나가 어제 날짜를 모르면 지난 질문`() {
        val sentToday = Progress(
            sent = listOf("fe-7", "fe-1", "be-100"),
            today = Progress.Sent("2026-11-23", "be-100"),
            next = mapOf("2026-11-24" to "fe-118"),
            hasNext = true,
        )
        val t = today(monday.atTime(6, 30), sentToday)
        assertEquals("fe-1", t.id)
        assertEquals("지난 질문", t.label)
    }

    @Test
    fun `기록을 받지 못한 동안 지나간 발송일의 질문도 앞서 받은 질문이다`() {
        // 11월 24일(화)까지만 받아 둔 채로 25일(수)이 됐다
        val t = today(LocalDate.of(2026, 11, 25).atTime(12, 0))
        assertEquals("fe-118", t.id)
        assertEquals("화요일에 받은 질문", t.label)
        // 앞으로 받을 질문을 하나도 모르므로 쉬는 날이라고 하지 않는다
        assertEquals("다음 질문을 아직 받지 못했어요", t.idle?.title)
        assertFalse(t.idle!!.rest)
    }

    @Test
    fun `오래 지난 질문은 날짜로 부른다`() {
        assertEquals("11월 24일 (화)에 받은 질문", today(LocalDate.of(2026, 12, 5).atTime(12, 0)).label)
    }

    @Test
    fun `발송 기록이 없으면 받지 못했다고 한다`() {
        val t = today(monday.atTime(12, 0), p = null)
        assertNull(t.id)
        assertEquals("오늘의 질문을 아직 받지 못했어요", t.idle?.title)
    }
}
