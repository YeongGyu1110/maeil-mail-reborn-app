package com.maeilmail.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 웹 index.html의 searchMatcher와 같게 동작하는지 */
class SearchTest {
    private val search = Search(
        listOf(
            listOf("react", "리액트"),
            listOf("javascript", "js", "자바스크립트"),
            listOf("closure", "클로저"),
            listOf("event", "이벤트"),
            listOf("event loop", "이벤트 루프"),
            listOf("server", "서버"),
        ),
    )

    private fun q(title: String, id: String = "fe-1") = Question(id, "fe", title, "", "")

    private fun finds(query: String, title: String) = search.matches(search.matcher(query), q(title))

    @Test
    fun `빈 검색어는 모두 보인다`() {
        assertNull(search.matcher("  "))
        assertTrue(finds("", "아무 제목"))
    }

    @Test
    fun `대소문자와 띄어쓰기를 가리지 않는다`() {
        assertTrue(finds("이벤트루프", "이벤트 루프에 대해서 설명해주세요."))
        assertTrue(finds("REACT", "react의 렌더링"))
    }

    @Test
    fun `동의어의 다른 표기로도 찾는다`() {
        assertTrue(finds("react", "리액트의 props와 state"))
        assertTrue(finds("리액트", "React 18의 변화"))
        assertTrue(finds("event loop", "이벤트 루프에 대해서 설명해주세요."))
    }

    @Test
    fun `영어 표기는 단어 중간에서 바꿔 찾지 않는다`() {
        // "서버"를 "server"로 바꿔 찾을 때 "Observer" 안의 server는 걸리지 않는다
        assertFalse(finds("서버", "Observer 패턴"))
        assertTrue(finds("서버", "server 컴포넌트"))
    }

    @Test
    fun `두 글자 영어 표기는 검색어로만 쓰고 바꿔 찾지 않는다`() {
        assertTrue(finds("js", "자바스크립트 함수"))
        // "자바스크립트"로 검색해도 "elements" 속의 "js"... 가 아니라 js라는 두 글자 표기는 찾는 대상에서 빠진다
        assertFalse(finds("자바스크립트", "jsx 문법"))
    }

    @Test
    fun `입력 중인 마지막 단어로 시작하는 표기의 동의어로도 찾는다`() {
        assertTrue(finds("clo", "클로저에 대해서 설명해주세요."))
        assertTrue(finds("리액", "React hooks"))
        // 영어는 3글자부터
        assertFalse(finds("cl", "클로저에 대해서 설명해주세요."))
    }

    @Test
    fun `질문 번호로도 찾는다`() {
        assertTrue(search.matches(search.matcher("fe-12"), q("아무 제목", id = "fe-12")))
    }

    @Test
    fun `정규식 특수 문자를 그대로 찾는다`() {
        assertTrue(finds("0.1 + 0.2", "자바스크립트에서 \"0.1 + 0.2 === 0.3\"의 실행 결과"))
        assertFalse(finds("0.1 + 0.2", "0a1 x 0b2"))
    }
}
