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
    fun `메일 버튼 주소에서 질문 ID를 꺼낸다`() {
        assertEquals("be-100", WebLink.questionId("maeil-mail-reborn.pages.dev", "/questions/", "BE-100"))
        assertEquals("fe-3", WebLink.questionId("Maeil-Mail-Reborn.pages.dev", "/questions", "3"))
    }

    @Test
    fun `다른 호스트나 질문 화면이 아닌 주소에는 열 질문이 없다`() {
        // 옛 GitHub Pages 주소는 앱 링크로 받지 않는다 (브라우저가 연다)
        assertNull(WebLink.questionId("yeonggyu1110.github.io", "/maeil-mail-reborn/questions/", "be-100"))
        // 미리보기 배포 주소도 받지 않는다
        assertNull(WebLink.questionId("feat-x.maeil-mail-reborn.pages.dev", "/questions/", "be-100"))
        assertNull(WebLink.questionId("maeil-mail-reborn.pages.dev", "/", "be-100"))
        assertNull(WebLink.questionId(null, null, null))
    }

    @Test
    fun `알 수 없는 값이면 열지 않는다`() {
        assertNull(WebLink.normalizeId(null))
        assertNull(WebLink.normalizeId(""))
        assertNull(WebLink.normalizeId("xx-1"))
    }
}
