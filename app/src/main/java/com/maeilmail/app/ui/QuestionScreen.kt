package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.data.areaOf
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Mono

/**
 * 질문 화면: 답변을 읽는다 (이해도·메모는 뒤 단계에서 더한다).
 * order: 이전·다음으로 넘길 순서. 질문 목록에서 열었으면 그 목록에 보이던 순서다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionScreen(
    state: Library.State,
    id: String,
    order: List<String>,
    onNavigate: (id: String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val question = state.catalog.question(id)
    BackHandler(onBack = onBack)

    // 읽어 둔 답변 본문 (질문 ID, 본문). 질문이 바뀌거나 새로 받으면 다시 읽는다. 본문이 ""이면 받아 둔 파일이 없다.
    // 다음 질문을 읽는 동안에는 보던 본문을 그대로 두어, 본문 화면(WebView)을 새로 만들지 않고 내용만 바꾼다
    var loaded by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(id, state.catalog) {
        loaded = id to (question?.let { Library.content(it) } ?: "")
    }

    val index = order.indexOf(id)
    val prev = order.getOrNull(index - 1)
    val next = order.getOrNull(index + 1)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgPaper),
    ) {
        TopAppBar(
            title = { Text(id.uppercase(), fontFamily = Mono, fontSize = 16.sp, color = colors.textMain) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "질문 목록으로", tint = colors.textMain)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bgPaper),
        )
        HorizontalDivider(color = colors.rule)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val shown = loaded
            val shownQuestion = shown?.let { state.catalog.question(it.first) }
            when {
                question == null -> Missing("이 질문을 찾지 못했어요", "목차에서 빠졌을 수 있어요.")
                shown == null || shownQuestion == null -> Unit
                shown.second.isEmpty() -> Missing("답변을 아직 받지 못했어요", "인터넷에 연결된 뒤 질문 목록을 당겨서 새로고침해 주세요.")
                else -> MarkdownView(
                    key = shownQuestion.id,
                    meta = metaOf(shownQuestion),
                    title = shownQuestion.title,
                    markdown = shown.second,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (order.size > 1 && index >= 0) {
            HorizontalDivider(color = colors.rule)
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { prev?.let(onNavigate) }, enabled = prev != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    Text("이전")
                }
                Spacer(Modifier.weight(1f))
                Text("${index + 1} / ${order.size}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { next?.let(onNavigate) }, enabled = next != null) {
                    Text("다음")
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** 제목 위에 보일 영역·카테고리. 예: 프론트엔드 · JavaScript 기초와 런타임 */
private fun metaOf(q: Question): String {
    val area = areaOf(q.id)?.name.orEmpty()
    return if (q.categoryName.isEmpty()) area else "$area · ${q.categoryName}"
}

@Composable
private fun Missing(title: String, hint: String) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textMain)
        Spacer(Modifier.height(6.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = Library::syncNow) { Text("지금 받기") }
    }
}
