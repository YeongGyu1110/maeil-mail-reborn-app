package com.maeilmail.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maeilmail.app.data.Library

private val ListFilterSaver: Saver<ListFilter, Any> = listSaver(
    save = { listOf(it.query, it.category) },
    restore = { ListFilter(it[0], it[1]) },
)

/** 앱의 화면 전환: 질문 목록 → 질문 화면. 화면을 돌려도 보던 화면과 검색어가 남는다 */
@Composable
fun App() {
    val state by Library.state.collectAsState()
    val filter = rememberSaveable(saver = ListFilterSaver) { ListFilter() }
    val listState = rememberLazyListState()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var order by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val snackbar = remember { SnackbarHostState() }

    // 이미 질문이 있을 때 맞춘 결과나 실패를 짧게 알린다 (처음 받을 때는 목록 자리에 보여준다)
    LaunchedEffect(state.notice, state.error) {
        val message = state.notice ?: state.error?.takeIf { state.catalog.questions.isNotEmpty() } ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        Library.clearNotice()
    }

    Box(Modifier.fillMaxSize()) {
        val id = openId
        if (id == null) {
            ListScreen(
                state = state,
                filter = filter,
                listState = listState,
                onOpen = { q, ids ->
                    order = ArrayList(ids)
                    openId = q.id
                },
            )
        } else {
            QuestionScreen(
                state = state,
                id = id,
                order = order,
                onNavigate = { openId = it },
                onBack = { openId = null },
            )
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (id == null) 8.dp else 56.dp),
        )
    }
}
