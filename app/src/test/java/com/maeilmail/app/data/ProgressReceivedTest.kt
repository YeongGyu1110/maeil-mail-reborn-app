package com.maeilmail.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 받은 질문: 웹 index.html의 receivedIds와 같은 순서·규칙인지 */
class ProgressReceivedTest {
    private fun progress(vararg sent: String) = Progress(sent.toList(), today = null, next = emptyMap(), hasNext = true)
    private val known = setOf("fe-1", "fe-2", "fe-3", "be-1")

    @Test
    fun `최근에 받은 질문부터`() {
        assertEquals(listOf("be-1", "fe-2", "fe-1"), progress("fe-1", "fe-2", "be-1").received(null, known))
    }

    @Test
    fun `알림 시각이 지난 오늘 질문은 맨 앞에 넣는다`() {
        assertEquals(listOf("fe-3", "fe-2", "fe-1"), progress("fe-1", "fe-2").received("fe-3", known))
    }

    @Test
    fun `이미 보낸 오늘 질문은 두 번 넣지 않는다`() {
        assertEquals(listOf("fe-2", "fe-1"), progress("fe-1", "fe-2").received("fe-2", known))
    }

    @Test
    fun `목차에 없는 질문은 뺀다`() {
        assertEquals(listOf("fe-1"), progress("fe-1", "fe-99").received(null, known))
    }
}
