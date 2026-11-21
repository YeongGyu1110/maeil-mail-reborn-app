package com.maeilmail.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParsersTest {
    private val frontend = AREAS.first { it.prefix == "fe" }
    private val backend = AREAS.first { it.prefix == "be" }

    @Test
    fun `목차에서 질문 번호와 제목을 읽고 이스케이프를 벗긴다`() {
        val toc = """
            # Frontend TOC

            - [클로저에 대해서 설명해주세요.](contents/fe-1.md)
            - [\<a> 태그의 target 속성](contents/fe-96.md)
            - [다른 영역](contents/be-3.md)
        """.trimIndent()
        val (questions, _) = Parsers.parseArea(frontend, toc, "")
        assertEquals(listOf("fe-1", "fe-96"), questions.map { it.id })
        assertEquals("<a> 태그의 target 속성", questions[1].title)
        assertEquals("FE-96", questions[1].label)
        assertEquals("frontend/contents/fe-96.md", questions[1].contentPath)
    }

    @Test
    fun `카테고리 목차로 카테고리를 붙이고 백엔드는 영역 이름을 앞에 붙인다`() {
        val toc = "- [PRG 패턴](contents/be-123.md)\n- [인덱스](contents/be-22.md)\n- [미분류](contents/be-9.md)"
        val categoryToc = """
            # Backend TOC by Category

            ## 웹 애플리케이션 구조
            설명 문장입니다.

            - [PRG 패턴](contents/be-123.md)

            ## 데이터베이스 기초
            - [인덱스](contents/be-22.md)
        """.trimIndent()
        val (questions, categories) = Parsers.parseArea(backend, toc, categoryToc)
        assertEquals(listOf("백엔드 · 웹 애플리케이션 구조", "백엔드 · 데이터베이스 기초"), categories)
        assertEquals("백엔드 · 웹 애플리케이션 구조", questions[0].category)
        assertEquals("웹 애플리케이션 구조", questions[0].categoryName)
        assertEquals("데이터베이스 기초", questions[1].categoryName)
        assertEquals("", questions[2].category)
    }

    @Test
    fun `동의어 파일에서 묶음을 읽고 주석은 건너뛴다`() {
        val js = """
            // 설명 주석 ['가짜', '묶음']
            export const SEARCH_SYNONYMS = [
              // 언어와 프레임워크
              ['react', '리액트'],
              ['javascript', 'js', '자바스크립트'],
              ['same-origin policy', 'sop', '동일 출처 정책'],
            ];
        """.trimIndent()
        assertEquals(
            listOf(
                listOf("react", "리액트"),
                listOf("javascript", "js", "자바스크립트"),
                listOf("same-origin policy", "sop", "동일 출처 정책"),
            ),
            Parsers.parseSynonyms(js),
        )
    }

    /** 웹 저장소가 이 PC에 있으면 실제 파일로도 확인한다 (없으면 건너뛴다) */
    @Test
    fun `실제 웹 저장소 파일을 모두 읽는다`() {
        val web = File("C:/.workspace/projects/service/maeil-meil-reborn")
        if (!web.isDirectory) return
        var total = 0
        for (area in AREAS) {
            val toc = File(web, "${area.dir}/toc.md").readText()
            val categoryToc = File(web, "${area.dir}/toc-category.md").readText()
            val (questions, categories) = Parsers.parseArea(area, toc, categoryToc)
            val files = File(web, "${area.dir}/contents").list()!!.count { it.endsWith(".md") }
            assertEquals("${area.dir}: 목차의 질문 수 = 답변 파일 수", files, questions.size)
            assertTrue("${area.dir}: 카테고리", categories.isNotEmpty())
            assertTrue("${area.dir}: 모든 질문에 카테고리", questions.all { it.category.isNotEmpty() })
            total += questions.size
        }
        assertTrue(total >= 220)

        val js = File(web, "search-synonyms.js").readText()
        val groups = Parsers.parseSynonyms(js)
        val expected = js.lines().count { it.trimStart().startsWith("[") }
        assertEquals(expected, groups.size)
    }
}
