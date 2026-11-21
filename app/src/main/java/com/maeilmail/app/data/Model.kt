package com.maeilmail.app.data

/** 질문이 있는 웹 서비스 저장소 */
object Source {
    const val OWNER = "YeongGyu1110"
    const val REPO = "maeil-mail-reborn"
    const val BRANCH = "master"
    const val SYNONYMS_PATH = "search-synonyms.js"

    /** 앱이 받아 두는 파일: 영역별 목차·카테고리 목차·답변, 검색 동의어 */
    fun isTracked(path: String): Boolean =
        path == SYNONYMS_PATH || AREAS.any { path.startsWith("${it.dir}/") && path.endsWith(".md") }
}

/**
 * 질문 영역. 웹 index.html의 AREAS와 같다.
 * categoryLabel: 카테고리 필터 값 앞에 붙여 프론트엔드·백엔드의 같은 이름을 가른다.
 */
data class Area(val prefix: String, val dir: String, val name: String, val categoryLabel: String)

val AREAS = listOf(
    Area(prefix = "fe", dir = "frontend", name = "프론트엔드", categoryLabel = ""),
    Area(prefix = "be", dir = "backend", name = "백엔드", categoryLabel = "백엔드 · "),
)

fun areaOf(id: String): Area? = AREAS.firstOrNull { id.startsWith("${it.prefix}-") }

/**
 * id: "fe-1", title: 질문 제목
 * category: 필터 값 (백엔드는 "백엔드 · " 붙음), categoryName: 화면에 보일 이름 (영역 없이)
 */
data class Question(
    val id: String,
    val area: String,
    val title: String,
    val category: String,
    val categoryName: String,
) {
    /** 화면에 보일 번호. 예: FE-46 */
    val label: String get() = id.uppercase()

    /** 답변 파일 경로. 예: frontend/contents/fe-1.md */
    val contentPath: String get() = "${areaOf(id)?.dir}/contents/$id.md"
}

/** 받아 둔 질문 전체 */
data class Catalog(
    val questions: List<Question>,
    val categories: List<String>,
    val synonyms: List<List<String>>,
) {
    private val byId = questions.associateBy { it.id }
    fun question(id: String): Question? = byId[id]

    companion object {
        val EMPTY = Catalog(emptyList(), emptyList(), emptyList())
    }
}
