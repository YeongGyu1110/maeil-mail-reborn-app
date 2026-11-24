package com.maeilmail.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 최근 검색어: 차례, 겹침, 개수, 오래된 것 지우기 */
class SearchHistoryTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_000 * day
    private fun texts(entries: List<SearchHistory.Entry>) = entries.map { it.text }

    @Test
    fun `최근에 찾은 것이 앞에 온다`() {
        val list = SearchHistory.merge(listOf(SearchHistory.Entry("클로저", now - day)), "react", now)
        assertEquals(listOf("react", "클로저"), texts(list))
    }

    @Test
    fun `띄어쓰기와 대소문자만 다르면 하나로 보고 새로 쓴 모양을 남긴다`() {
        val old = listOf(SearchHistory.Entry("클로저", now - 2), SearchHistory.Entry("이벤트루프", now - 3), SearchHistory.Entry("React", now - 4))
        assertEquals(listOf("이벤트 루프", "클로저", "React"), texts(SearchHistory.merge(old, " 이벤트  루프 ", now)))
        assertEquals(listOf("react", "클로저", "이벤트루프"), texts(SearchHistory.merge(old, "react", now)))
    }

    @Test
    fun `빈 검색어와 너무 긴 글은 남기지 않는다`() {
        assertEquals(emptyList<String>(), texts(SearchHistory.merge(emptyList(), "   ", now)))
        assertEquals(emptyList<String>(), texts(SearchHistory.merge(emptyList(), "가".repeat(101), now)))
    }

    @Test
    fun `30일이 지난 것은 지운다`() {
        val old = listOf(SearchHistory.Entry("29일", now - 29 * day), SearchHistory.Entry("30일", now - 30 * day))
        assertEquals(listOf("29일"), texts(SearchHistory.prune(old, now)))
        assertEquals(listOf("새 검색", "29일"), texts(SearchHistory.merge(old, "새 검색", now)))
    }

    @Test
    fun `다시 찾으면 기간이 새로 시작된다`() {
        val list = SearchHistory.merge(listOf(SearchHistory.Entry("클로저", now - 29 * day)), "클로저", now)
        assertEquals(listOf(SearchHistory.Entry("클로저", now)), list)
    }

    @Test
    fun `20개까지만 둔다`() {
        var list = emptyList<SearchHistory.Entry>()
        for (i in 1..25) list = SearchHistory.merge(list, "검색 $i", now + i)
        assertEquals(20, list.size)
        assertEquals("검색 25", list.first().text)
        assertEquals("검색 6", list.last().text)
    }
}
