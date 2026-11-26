package com.maeilmail.app.account

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `초안은 질문마다 초안 문서를 통째로 바꾸고 기본 문서와 묶음은 건드리지 않는다`() {
        val draft = Records.Draft("쓰던 글", 3L, "메모")
        val writes = Records.writesFor("u1", mapOf("fe-3" to mapOf(Records.DRAFT_FIELD to draft.toMap())))
        assertEquals(listOf("users/u1", "users/u1/drafts/fe-3"), writes.map { it.path })
        assertTrue(writes[0].isEmpty)
        assertTrue(writes[1].whole)
        assertEquals(mapOf("text" to "쓰던 글", "at" to 3L, "base" to "메모"), writes[1].fields)
    }

    @Test
    fun `초안을 지우면 초안 문서를 지우고, 메모와 함께 저장하면 한 번에 보낸다`() {
        val writes = Records.writesFor(
            "u1",
            mapOf("fe-6" to mapOf("memo" to "새 글", "memoAt" to 4L, "memoUncommitted" to null, Records.DRAFT_FIELD to null)),
        )
        assertEquals(listOf("users/u1", "users/u1/memos/2", "users/u1/drafts/fe-6"), writes.map { it.path })
        assertEquals(listOf("`fe-6`.memo", "`fe-6`.memoAt", "`fe-6`.memoUncommitted"), writes[1].mask)
        assertTrue(writes[2].remove)
    }

    @Test
    fun `초안은 규칙과 같이 글과 기준 글이 3,000자까지일 때만 저장한다`() {
        assertTrue(Records.Draft("가".repeat(3000), 1L, "나".repeat(3000)).storable())
        assertFalse(Records.Draft("가".repeat(3001), 1L, "").storable())
        assertFalse(Records.Draft("글", 1L, "나".repeat(3001)).storable())
    }

    @Test
    fun `읽은 초안의 모양이 다르면 없는 것으로 본다`() {
        assertEquals(Records.Draft("글", 5L, ""), Records.Draft.from(mapOf("text" to "글", "at" to 5L)))
        assertEquals(null, Records.Draft.from(mapOf("at" to 5L)))
        assertEquals(null, Records.Draft.from("글"))
    }

    @Test
    fun `메모 모양은 앞의 빈 줄과 끝의 공백만 걷어 낸다`() {
        assertEquals("  - 항목\n본문", Records.normalizeMemo("\n  \n  - 항목\n본문  \n\n"))
        assertEquals("", Records.normalizeMemo("   \n\t\n"))
    }
}
