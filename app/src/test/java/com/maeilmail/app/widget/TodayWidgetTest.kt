package com.maeilmail.app.widget

import com.maeilmail.app.data.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 위젯 문구: 오늘 탭과 같은 규칙 (알림 시각 전에는 질문을 보여주지 않는다) */
class TodayWidgetTest {
    private val seven = LocalTime.of(7, 0)
    private val monday = LocalDate.of(2026, 11, 23)
    private val progress = Progress(
        sent = listOf("fe-1"),
        today = Progress.Sent("2026-11-20", "fe-1"),
        next = mapOf("2026-11-23" to "be-100", "2026-11-24" to "fe-118"),
        hasNext = true,
    )
    private val titles = mapOf("be-100" to ("트랜잭션이란?" to "데이터베이스 기초"))

    private fun content(now: LocalDateTime, lastNotified: LocalDate? = null) =
        TodayWidget.content(progress, now, seven, lastNotified) { titles[it] }

    @Test
    fun `알림 시각 전에는 질문을 보여주지 않는다`() {
        val c = content(monday.atTime(6, 59))
        assertEquals("오늘의 질문은 07:00에 알려 드려요", c.title)
        assertNull(c.questionId)
    }

    @Test
    fun `알림 시각이 지나면 번호와 제목을 보이고 누르면 그 질문을 연다`() {
        val c = content(monday.atTime(7, 0))
        assertEquals("BE-100", c.meta)
        assertEquals("트랜잭션이란?", c.title)
        assertEquals("데이터베이스 기초", c.sub)
        assertEquals("be-100", c.questionId)
    }

    @Test
    fun `이미 알렸으면 알림 시각 전이라도 보인다`() {
        assertEquals("be-100", content(monday.atTime(6, 0), lastNotified = monday).questionId)
    }

    @Test
    fun `쉬는 날이면 다음 질문 날짜를 보인다`() {
        val c = content(LocalDate.of(2026, 11, 22).atTime(12, 0))
        assertEquals("오늘은 쉬는 날이에요", c.title)
        assertEquals("다음 질문 11월 23일 (월) 07:00", c.sub)
    }

    @Test
    fun `다음에 다시 그릴 때는 알림 시각 전이면 알림 시각, 지났으면 자정`() {
        assertEquals(monday.atTime(7, 0), TodayWidget.nextChange(monday.atTime(6, 30), seven))
        assertEquals(monday.plusDays(1).atStartOfDay(), TodayWidget.nextChange(monday.atTime(7, 0), seven))
    }
}
