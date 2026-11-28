package com.maeilmail.app.widget

import com.maeilmail.app.reminder.Today
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 위젯 문구: 첫 화면과 같은 규칙 */
class TodayWidgetTest {
    private val seven = LocalTime.of(7, 0)
    private val monday = LocalDate.of(2026, 11, 23)
    private val titles = mapOf("be-100" to ("트랜잭션이란?" to "데이터베이스 기초"))
    private val rest = Today.Idle("오늘은 쉬는 날이에요", "다음 질문은 11월 23일 (월) 07:00에 열려요", rest = true)

    private fun content(today: Today, opened: String? = null) = TodayWidget.content(today, monday, opened) { titles[it] }

    @Test
    fun `오늘 질문이면 번호와 제목과 분류`() {
        val c = content(Today("be-100", "오늘의 질문", true, null))
        assertEquals("오늘의 질문", c.label)
        assertEquals("BE-100", c.meta)
        assertEquals("트랜잭션이란?", c.title)
        assertEquals("데이터베이스 기초", c.sub)
        assertEquals("be-100", c.questionId)
    }

    @Test
    fun `열어 본 오늘 질문도 그대로 보인다`() {
        assertEquals("be-100", content(Today("be-100", "오늘의 질문", true, null), opened = "be-100").questionId)
    }

    @Test
    fun `알림 시각 전에는 여는 시각만 보인다`() {
        val c = content(Today("be-100", "금요일에 받은 질문", false, Today.Idle("오늘의 질문은 07:00에 열려요")))
        assertEquals("오늘의 질문은 07:00에 열려요", c.title)
        assertEquals("11월 23일 (월)", c.meta)
        assertNull(c.questionId)
    }

    @Test
    fun `쉬는 날에는 아직 열지 않은 지난 질문`() {
        val c = content(Today("be-100", "금요일에 받은 질문", false, rest))
        assertEquals("금요일에 받은 질문", c.label)
        assertEquals("트랜잭션이란?", c.title)
        assertEquals("다음 질문은 11월 23일 (월) 07:00에 열려요", c.sub)
    }

    @Test
    fun `쉬는 날 지난 질문을 이미 열었으면 다음 날짜`() {
        val c = content(Today("be-100", "금요일에 받은 질문", false, rest), opened = "be-100")
        assertEquals("오늘은 쉬는 날이에요", c.title)
        assertNull(c.questionId)
    }

    @Test
    fun `목차에 없는 질문이면 앱을 열라고 한다`() {
        assertEquals("앱을 열면 질문을 받아 와요", content(Today("fe-999", "오늘의 질문", true, null)).title)
    }

    @Test
    fun `다음에 다시 그릴 때는 알림 시각 전이면 알림 시각, 지났으면 자정`() {
        assertEquals(monday.atTime(7, 0), TodayWidget.nextChange(monday.atTime(6, 30), seven))
        assertEquals(monday.plusDays(1).atStartOfDay(), TodayWidget.nextChange(monday.atTime(7, 0), seven))
    }
}
