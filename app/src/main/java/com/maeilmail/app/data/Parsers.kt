package com.maeilmail.app.data

/** 웹 서비스의 목차·동의어 파일을 읽는다. 형식과 규칙은 웹 index.html의 loadArea, search-synonyms.js와 같다. */
object Parsers {
    // 제목의 마크다운 이스케이프(\<a> 등)를 벗긴다 (웹: title.replace(/\\([!-/:-@[-`{-~])/g, '$1'))
    private val escape = Regex("""\\([!-/:-@\[-`{-~])""")
    private val heading = Regex("""^##\s+(.+)""")

    /** 목차(toc.md)와 카테고리 목차(toc-category.md)로 한 영역의 질문과 카테고리 목록을 만든다 */
    fun parseArea(area: Area, toc: String, categoryToc: String): Pair<List<Question>, List<String>> {
        val categoryOf = HashMap<String, Pair<String, String>>()
        val categories = ArrayList<String>()
        var current = ""
        var currentName = ""
        val itemInCategory = Regex("""\(contents/(${area.prefix}-\d+)\.md\)""")
        for (line in categoryToc.lines()) {
            val h = heading.find(line)
            if (h != null) {
                currentName = h.groupValues[1].trim()
                current = area.categoryLabel + currentName
                categories += current
                continue
            }
            val item = itemInCategory.find(line)
            if (item != null && current.isNotEmpty()) categoryOf[item.groupValues[1]] = current to currentName
        }

        // send_mail.py와 같은 형식: - [질문](contents/fe-1.md)
        val itemInToc = Regex("""-\s*\[(.*?)]\(contents/(${area.prefix}-\d+)\.md\)""")
        val questions = itemInToc.findAll(toc).map { m ->
            val id = m.groupValues[2]
            val (category, categoryName) = categoryOf[id] ?: ("" to "")
            Question(
                id = id,
                area = area.prefix,
                title = escape.replace(m.groupValues[1], "$1"),
                category = category,
                categoryName = categoryName,
            )
        }.toList()
        return questions to categories
    }

    /**
     * search-synonyms.js의 SEARCH_SYNONYMS 배열을 읽는다. 한 줄에 묶음 하나: ['react', '리액트'],
     * 주석(//)은 건너뛴다.
     */
    fun parseSynonyms(js: String): List<List<String>> {
        val group = Regex("""^\s*\[(.*)]\s*,?\s*(//.*)?$""")
        val word = Regex("""'((?:[^'\\]|\\.)*)'""")
        return js.lines().mapNotNull { line ->
            if (line.trimStart().startsWith("//")) return@mapNotNull null
            val m = group.find(line) ?: return@mapNotNull null
            word.findAll(m.groupValues[1]).map { it.groupValues[1].replace("\\'", "'") }.toList()
                .takeIf { it.size >= 2 }
        }
    }
}
