package com.maeilmail.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.maeilmail.app.account.Records

/**
 * 첫 화면 질문 목록의 필터. 질문을 보고 돌아와도 남도록 위에서 들고 있는다.
 * level: 학습 수준 (null이면 전체), starred: 별표한 질문만, received: 받은 질문만 최근에 받은 순서로.
 * 검색은 따로 된 화면(SearchScreen)에서 전체 질문을 대상으로 한다.
 */
class ListFilter(
    category: String = "",
    level: Records.Level? = null,
    starred: Boolean = false,
    received: Boolean = false,
) {
    var category by mutableStateOf(category)
    var level by mutableStateOf(level)
    var starred by mutableStateOf(starred)
    var received by mutableStateOf(received)

    /** 걸린 필터가 있는지 */
    val active: Boolean get() = category.isNotEmpty() || level != null || starred || received

    /** 지금 보는 목록의 이름. 질문 화면의 뒤로 가기에 보인다. 예: 받은 질문 · 복습 필요 */
    val name: String
        get() = listOfNotNull(
            "받은 질문".takeIf { received },
            "별표".takeIf { starred },
            level?.label,
            category.ifEmpty { null },
        ).joinToString(" · ").ifEmpty { DEFAULT_NAME }

    fun reset() {
        category = ""
        level = null
        starred = false
        received = false
    }

    /** 위젯의 숫자 칸으로 열었을 때: 그 필터 하나만 건다 */
    fun show(kind: String?) {
        reset()
        when (kind) {
            SHOW_RECEIVED -> received = true
            SHOW_REVIEW -> level = Records.Level.REVIEW
            SHOW_STARRED -> starred = true
        }
    }

    companion object {
        const val DEFAULT_NAME = "질문 목록"
        const val SHOW_RECEIVED = "received"
        const val SHOW_REVIEW = "review"
        const val SHOW_STARRED = "starred"

        val Saver: Saver<ListFilter, Any> = listSaver(
            save = { listOf(it.category, it.level?.name.orEmpty(), it.starred.toString(), it.received.toString()) },
            restore = {
                ListFilter(
                    category = it[0],
                    level = Records.Level.entries.firstOrNull { level -> level.name == it[1] },
                    starred = it[2] == "true",
                    received = it[3] == "true",
                )
            },
        )
    }
}
