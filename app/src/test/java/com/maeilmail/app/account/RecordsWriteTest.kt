package com.maeilmail.app.account

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** 기록 변경을 Firestore 쓰기로 나누는 규칙 (웹 memo-core.js의 write와 같아야 한다) */
class RecordsWriteTest {
    @After
    fun clearLegacy() = Records.legacy.clear()

    @Test
    fun `메모 묶음은 질문 번호를 4로 나눈 나머지`() {
        assertEquals(1, Records.memoShard("fe-17"))
        assertEquals(0, Records.memoShard("be-120"))
        assertEquals(3, Records.memoShard("fe-3"))
    }

    @Test
    fun `학습 수준과 별표는 기본 문서에 필드 단위로 쓴다`() {
        val writes = Records.writesFor("u1", mapOf("fe-1" to mapOf("level" to "review", "starred" to null)))
        assertEquals(1, writes.size)
        assertEquals("users/u1", writes[0].path)
        assertEquals(listOf("items.`fe-1`.level", "items.`fe-1`.starred"), writes[0].mask)
    }

    @Test
    fun `메모는 묶음 문서에 쓰고 기본 문서는 건드리지 않는다`() {
        val writes = Records.writesFor(
            "u1",
            mapOf("fe-17" to mapOf("memo" to "글", "memoAt" to 1L, "memoUncommitted" to null)),
        )
        assertEquals(emptyList<String>(), writes[0].mask)
        assertEquals("users/u1/memos/1", writes[1].path)
        assertEquals(listOf("`fe-17`.memo", "`fe-17`.memoAt", "`fe-17`.memoUncommitted"), writes[1].mask)
    }

    @Test
    fun `메모를 지우면 묶음에서 질문 항목째 지운다`() {
        val writes = Records.writesFor(
            "u1",
            mapOf("fe-2" to mapOf("memo" to null, "memoAt" to null, "memoUncommitted" to null)),
        )
        assertEquals("users/u1/memos/2", writes[1].path)
        assertEquals(listOf("`fe-2`"), writes[1].mask)
    }

    @Test
    fun `기본 문서에 예전 메모가 남아 있으면 같이 지운다`() {
        Records.legacy.add("fe-5")
        val writes = Records.writesFor("u1", mapOf("fe-5" to mapOf("memo" to "새 글", "memoAt" to 2L)))
        assertEquals(listOf("items.`fe-5`.memo", "items.`fe-5`.memoAt"), writes[0].mask)
        assertEquals(listOf("`fe-5`.memo", "`fe-5`.memoAt"), writes[1].mask)
    }

    @Test
    fun `메모 모양은 앞의 빈 줄과 끝의 공백만 걷어 낸다`() {
        assertEquals("  - 항목\n본문", Records.normalizeMemo("\n  \n  - 항목\n본문  \n\n"))
        assertEquals("", Records.normalizeMemo("   \n\t\n"))
    }
}
