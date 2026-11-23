package com.maeilmail.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 메일 버튼 주소의 ?q= 값 → 질문 ID (웹 questions/index.html과 같은 규칙) */
class WebLinkTest {
    @Test
    fun `질문 ID는 소문자로 맞춘다`() {
        assertEquals("be-100", WebLink.normalizeId("be-100"))
        assertEquals("fe-1", WebLink.normalizeId("FE-1"))
    }

    @Test
    fun `접두어 없는 옛 링크는 프론트엔드 질문`() {
        assertEquals("fe-12", WebLink.normalizeId("12"))
    }

    @Test
    fun `알 수 없는 값이면 열지 않는다`() {
        assertNull(WebLink.normalizeId(null))
        assertNull(WebLink.normalizeId(""))
        assertNull(WebLink.normalizeId("xx-1"))
    }
}
