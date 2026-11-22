package com.maeilmail.app.account

import org.junit.Assert.assertEquals
import org.junit.Test

/** Firestore 필드 경로: 질문 ID(fe-1)처럼 영문·숫자·밑줄이 아닌 이름은 역따옴표로 감싼다 */
class FieldPathTest {
    @Test
    fun `보통 이름은 그대로`() {
        assertEquals("items", FirestoreRest.fieldName("items"))
        assertEquals("level", FirestoreRest.fieldName("level"))
    }

    @Test
    fun `하이픈이 든 질문 ID는 감싼다`() {
        assertEquals("`fe-1`", FirestoreRest.fieldName("fe-1"))
        assertEquals("`be-120`", FirestoreRest.fieldName("be-120"))
    }

    @Test
    fun `역따옴표와 역슬래시는 이스케이프한다`() {
        assertEquals("""`a\`b`""", FirestoreRest.fieldName("a`b"))
        assertEquals("""`a\\b`""", FirestoreRest.fieldName("""a\b"""))
    }
}
