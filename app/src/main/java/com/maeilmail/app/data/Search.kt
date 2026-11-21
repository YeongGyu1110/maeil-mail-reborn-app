package com.maeilmail.app.data

/**
 * 질문 검색. 웹 index.html의 searchMatcher를 그대로 옮겼다.
 * - 대소문자와 띄어쓰기는 구분하지 않는다 ("이벤트루프" = "이벤트 루프")
 * - 동의어 묶음의 다른 표기로도 찾는다 ("react" ↔ "리액트")
 * - 입력 중인 마지막 단어는 그 단어로 시작하는 표기의 동의어로도 찾는다 ("clo" → 클로저)
 */
class Search(synonyms: List<List<String>>) {

    /** 표기(공백 없는 소문자) → 같은 묶음의 모든 표기 */
    private val synonymsOf = LinkedHashMap<String, List<String>>()

    /** 검색어에서 묶음의 표기를 찾는 정규식. 긴 표기부터 찾아 "이벤트 루프"가 "이벤트"보다 먼저 잡히게 한다 */
    private val synonymPattern: Regex?

    init {
        for (group in synonyms) {
            val words = group.map(::normalize)
            // "ts", "js"처럼 두 글자 영어 표기는 다른 단어 속("elements")에도 흔해서, 검색어로만 쓰고 바꿔 찾는 대상에서는 뺀다
            val targets = words.filterNot { Regex("^[a-z0-9]{1,2}$").matches(it) }
            for (word in words) synonymsOf[word] = (listOf(word) + targets).distinct()
        }
        val alternatives = synonyms.flatten()
            .map { it.lowercase().trim() }
            .sortedByDescending { normalize(it).length }
            .map { word ->
                val pattern = word.split(Regex("\\s+")).joinToString("\\s*") { escape(it) }
                if (isAscii(word)) "(?<![a-z0-9])$pattern(?![a-z0-9])" else pattern
            }
        synonymPattern = if (alternatives.isEmpty()) null else Regex(alternatives.joinToString("|"))
    }

    /** 검색어에 맞는 글인지 검사하는 정규식. 검색어가 비어 있으면 null (모두 보인다) */
    fun matcher(text: String): Regex? {
        if (normalize(text).isEmpty()) return null
        val lower = text.lowercase()
        var variants: List<List<String>> = listOf(emptyList())
        fun addSegment(before: String, options: List<String>) {
            variants = variants.flatMap { parts -> options.map { option -> parts + before + option } }.take(200)
        }

        var last = 0
        synonymPattern?.findAll(lower)?.forEach { match ->
            val typed = normalize(match.value)
            val options = mutableListOf(spacedPattern(typed))
            for (word in synonymsOf[typed].orEmpty()) {
                if (word != typed) options += synonymOption(word)
            }
            addSegment(spacedPattern(lower.substring(last, match.range.first)), options)
            last = match.range.last + 1
        }

        // 마지막 단어는 입력 중일 수 있으므로, 그 단어로 시작하는 표기의 동의어로도 찾는다
        val rest = lower.substring(last)
        val tail = Regex("\\S+$").find(rest)?.value.orEmpty()
        val prefixWords = if (tail.isNotEmpty()) prefixSynonyms(normalize(tail)) else emptyList()
        if (prefixWords.isNotEmpty()) {
            addSegment(spacedPattern(rest.substring(0, rest.length - tail.length)), listOf(spacedPattern(tail)) + prefixWords.map(::synonymOption))
        } else {
            addSegment(spacedPattern(rest), listOf(""))
        }
        return Regex(variants.joinToString("|") { parts -> parts.filter { it.isNotEmpty() }.joinToString("\\s*") })
    }

    /** 검색 대상 글. 웹과 같이 번호, 제목 (메모는 메모 단계에서 더한다) */
    fun matches(matcher: Regex?, question: Question, memo: String = ""): Boolean =
        matcher == null || matcher.containsMatchIn("${question.id} ${question.title} $memo".lowercase())

    // 아직 다 입력하지 않은 단어("clo", "리액")로 시작하는 표기들의 동의어. 너무 짧으면 엉뚱한 묶음까지 걸리므로
    // 영어는 3글자, 한글은 2글자부터 찾는다.
    private fun prefixSynonyms(prefix: String): List<String> {
        if (prefix.codePointCount(0, prefix.length) < (if (isAscii(prefix)) 3 else 2)) return emptyList()
        val words = LinkedHashSet<String>()
        for ((word, targets) in synonymsOf) {
            if (word != prefix && word.startsWith(prefix)) words += targets
        }
        return words.take(40)
    }

    // 바꿔 넣은 영어 표기는 단어 전체로만 찾는다("server"가 "Observer"에 걸리지 않도록)
    private fun synonymOption(word: String): String =
        if (isAscii(word)) "(?<![a-z0-9])${spacedPattern(word)}(?![a-z0-9])" else spacedPattern(word)

    companion object {
        fun normalize(text: String): String = text.lowercase().replace(Regex("\\s+"), "")

        private fun isAscii(word: String): Boolean = word.all { it.code < 0x80 }

        // 웹의 escapeRegExp와 같은 글자를 이스케이프한다
        private fun escape(text: String): String = buildString {
            for (c in text) {
                if (c in ".*+?^\${}()|[]\\") append('\\')
                append(c)
            }
        }

        // 글자 사이에 띄어쓰기가 있어도 없어도 맞는 패턴. "이벤트루프"로 "이벤트 루프"를 찾을 수 있게 한다
        private fun spacedPattern(text: String): String {
            val normalized = normalize(text)
            val parts = ArrayList<String>()
            var i = 0
            while (i < normalized.length) {
                val cp = normalized.codePointAt(i)
                parts += escape(String(Character.toChars(cp)))
                i += Character.charCount(cp)
            }
            return parts.joinToString("\\s*")
        }
    }
}
