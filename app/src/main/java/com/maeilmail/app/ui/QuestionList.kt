package com.maeilmail.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maeilmail.app.account.Records
import com.maeilmail.app.data.AREAS
import com.maeilmail.app.data.Library
import com.maeilmail.app.data.Question
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.LineIcons
import com.maeilmail.app.ui.theme.Radius
import com.maeilmail.app.ui.theme.TextStyles

/**
 * 질문 색인. grouped면 영역마다 구분 제목 아래에 질문 행을 두고,
 * 아니면(받은 순서로 볼 때) 영역이 섞이므로 구분 제목 없이 한 줄로 둔다 (웹과 같다).
 */
fun LazyListScope.questionIndex(
    visible: List<Question>,
    grouped: Boolean,
    records: Records.State,
    onOpen: (Question) -> Unit,
) {
    if (!grouped) {
        itemsIndexed(visible, key = { _, q -> q.id }) { i, q ->
            QuestionRow(q, first = i == 0, records = records, onClick = { onOpen(q) })
        }
        return
    }
    for (area in AREAS) {
        val inArea = visible.filter { it.area == area.prefix }
        if (inArea.isEmpty()) continue
        item(key = "area-${area.prefix}") {
            DividerHead(area.name, count = "${inArea.size}개", modifier = Modifier.padding(top = 18.dp))
        }
        itemsIndexed(inArea, key = { _, q -> q.id }) { i, q ->
            QuestionRow(q, first = i == 0, records = records, onClick = { onOpen(q) })
        }
    }
}

/**
 * 질문 행: 번호(+ 학습 수준, 별표) → 제목 → 카테고리 (웹 .q-row 좁은 화면).
 * 행 사이는 옅은 선, 누르면 글자보다 조금 넓게 각진 바탕이 깔린다. 별표는 행 위에 따로 선다.
 */
@Composable
private fun QuestionRow(q: Question, first: Boolean, records: Records.State, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val item = records.item(q.id)
    if (!first) RowRule()
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RowInset)
                .clip(Radius.control)
                .clickable(onClick = onClick)
                .padding(horizontal = PaperPadding - RowInset, vertical = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(end = if (records.signedIn) 40.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(q.label, style = TextStyles.num, color = colors.textMuted, modifier = Modifier.weight(1f))
                if (item.level != Records.Level.NEW) LevelBadge(item.level)
            }
            Spacer(Modifier.height(2.dp))
            Text(q.title, style = TextStyles.rowTitle, color = colors.textMain, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val memo = item.memo
            if (q.categoryName.isNotEmpty() || memo != null) {
                Spacer(Modifier.height(2.dp))
                // 분류 · ✎ 메모 한 줄 (웹 .q-meta: 마크다운 기호를 뺀 미리보기)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (q.categoryName.isNotEmpty()) {
                        Text(q.categoryName, style = TextStyles.small, color = colors.textMuted, maxLines = 1)
                    }
                    if (memo != null) {
                        if (q.categoryName.isNotEmpty()) Text(" · ", style = TextStyles.small, color = colors.textMuted)
                        Icon(LineIcons.Pencil, contentDescription = "메모", tint = colors.primary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            remember(memo) { memoPlainText(memo) },
                            style = TextStyles.small,
                            color = colors.textSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (records.signedIn) {
            StarButton(
                starred = item.starred,
                onToggle = { Records.setStarred(q.id, !item.starred) },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
            )
        }
    }
}

/** 한 줄 미리보기용: 마크다운 기호를 걷어 내고 읽을 글자만 남긴다 (웹 memoPlainText와 같다) */
internal fun memoPlainText(md: String): String = md
    .replace(Regex("""^\s*(```|~~~).*$""", RegexOption.MULTILINE), "") // 코드 블록 울타리
    .replace(Regex("""^[\s|:-]+$""", RegexOption.MULTILINE), "") // 표 구분선, 가로줄
    .replace(Regex("""!\[([^\]]*)]\([^)]*\)"""), "$1") // 이미지 → 대체 글
    .replace(Regex("""\[([^\]]*)]\([^)]*\)"""), "$1") // 링크 → 글자
    .replace(Regex("""^\s*(#{1,6}\s+|>\s?|[-*+]\s+\[[ xX]]\s+|[-*+]\s+|\d+[.)]\s+)""", RegexOption.MULTILINE), "") // 제목·인용·목록 기호
    .replace(Regex("""\*\*|__|~~|`"""), "") // 강조·코드 기호
    .replace(Regex("""\s*\|\s*"""), " ") // 표 칸 나눔
    .replace(Regex("""\s+"""), " ")
    .trim()
    .take(160)

/**
 * 학습 진도 (웹 .progress-group): 받은 질문을 볼 때 목록 위에 둔다.
 * 막대는 각진 4dp 띠에 완벽 이해(초록)와 복습 필요(주황)를 이어 칠한다.
 */
@Composable
fun StudyProgress(received: List<String>, records: Records.State) {
    val colors = AppTheme.colors
    val total = received.size
    if (total == 0) return
    val done = received.count { records.level(it) == Records.Level.DONE }
    val review = received.count { records.level(it) == Records.Level.REVIEW }
    val rest = total - done - review
    Column(Modifier.padding(start = PaperPadding, end = PaperPadding, top = 2.dp, bottom = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(Radius.dot)
                .background(colors.bgHover),
        ) {
            if (done > 0) Box(Modifier.fillMaxHeight().weight(done.toFloat()).background(colors.primary))
            if (review > 0) Box(Modifier.fillMaxHeight().weight(review.toFloat()).background(colors.reviewBar))
            if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${total}개 중 완벽 이해 ${done}개, 복습 필요 ${review}개, 아직 ${rest}개",
            style = TextStyles.small,
            color = colors.textMuted,
        )
    }
}

/** 처음 열어 받아 둔 질문이 없을 때: 받는 중이면 진행을, 실패했으면 다시 시도를 보여준다 */
@Composable
fun FirstLoad(state: Library.State) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.error != null && !state.syncing) {
            Text("질문을 받지 못했어요", style = TextStyles.rowTitle, color = colors.textMain)
            Spacer(Modifier.height(6.dp))
            Text(state.error, style = TextStyles.small, color = colors.textMuted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Btn("다시 시도", onClick = Library::syncNow, kind = BtnKind.Soft)
        } else {
            Text("질문을 받고 있어요", style = TextStyles.rowTitle, color = colors.textMain)
            Spacer(Modifier.height(4.dp))
            Text("처음 한 번만 받아 두면 인터넷 없이도 볼 수 있어요", style = TextStyles.small, color = colors.textMuted)
            Spacer(Modifier.height(20.dp))
            val p = state.progressCount
            ProgressLine(
                progress = p?.takeIf { it.second > 0 }?.let { it.first.toFloat() / it.second },
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            if (p != null && p.second > 0) {
                Spacer(Modifier.height(8.dp))
                Text("${p.first} / ${p.second}", style = TextStyles.num, color = colors.textMuted)
            }
        }
    }
}

/** 맞는 질문이 없을 때 (웹 .empty) */
@Composable
fun EmptyResult(onReset: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("맞는 질문이 없어요", style = TextStyles.body, color = colors.textMuted)
        Spacer(Modifier.height(10.dp))
        Btn("필터 초기화", onClick = onReset, kind = BtnKind.Soft)
    }
}
