package com.maeilmail.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSyncTest {
    @Test
    fun `git blob sha는 git hash-object와 같다`() {
        // printf 'hello\n' | git hash-object --stdin
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", gitBlobSha("hello\n".toByteArray()))
        // 빈 파일
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", gitBlobSha(ByteArray(0)))
    }

    @Test
    fun `sha가 다르거나 없는 파일만 받고 저장소에서 없어진 파일은 지운다`() {
        val remote = mapOf("frontend/toc.md" to "a", "frontend/contents/fe-1.md" to "b", "frontend/contents/fe-2.md" to "c")
        val local = mapOf("frontend/toc.md" to "a", "frontend/contents/fe-1.md" to "old", "frontend/contents/fe-9.md" to "d")
        val plan = ContentSync.plan(remote, local)
        assertEquals(setOf("frontend/contents/fe-1.md", "frontend/contents/fe-2.md"), plan.download.toSet())
        assertEquals(listOf("frontend/contents/fe-9.md"), plan.remove)
    }

    @Test
    fun `앱이 받는 파일만 고른다`() {
        assertTrue(Source.isTracked("frontend/toc.md"))
        assertTrue(Source.isTracked("backend/contents/be-22.md"))
        assertTrue(Source.isTracked("search-synonyms.js"))
        assertFalse(Source.isTracked("index.html"))
        assertFalse(Source.isTracked("progress.json"))
        assertFalse(Source.isTracked("frontend/notes.txt"))
    }
}
