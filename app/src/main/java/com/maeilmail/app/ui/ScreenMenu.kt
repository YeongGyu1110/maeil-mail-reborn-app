package com.maeilmail.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.maeilmail.app.ui.theme.AppTheme
import com.maeilmail.app.ui.theme.Radius
import kotlin.math.roundToInt

/** 펼친 목록 하나. anchor: 누른 버튼의 자리(루트 기준) */
class OpenMenu(val title: String, val anchor: Rect, val content: @Composable (close: () -> Unit) -> Unit)

/** 펼침 목록을 어느 버튼 아래에 둘지 알기 위해 버튼의 자리를 적어 둔다 */
fun Modifier.trackBounds(onBounds: (Rect) -> Unit): Modifier = onGloballyPositioned { onBounds(it.boundsInRoot()) }

/**
 * 펼침 목록 (웹 .cat-menu): 누른 버튼 아래에, 자리가 모자라면 위로 올려 둔다. 바깥을 누르거나 뒤로 가면 닫힌다.
 *
 * Material의 DropdownMenu처럼 창(팝업)을 따로 띄우지 않고 이 화면 위에 그린다. 팝업이 닫히면 초점이 앱 창으로 돌아오기 전
 * 잠깐 시스템이 키보드를 맡는데, 이때 예전의 "보임" 상태가 남아 있으면(안드로이드 16, One UI) 키보드가 열렸다 닫힌다.
 */
@Composable
fun ScreenMenu(menu: OpenMenu?, onClose: () -> Unit) {
    val colors = AppTheme.colors
    // 사라지는 동안에도 그릴 내용
    var last by remember { mutableStateOf(menu) }
    if (menu != null) last = menu
    BackHandler(enabled = menu != null, onBack = onClose)

    AnimatedVisibility(visible = menu != null, enter = fadeIn(tween(120)), exit = fadeOut(tween(80))) {
        val shown = last ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize()) {
            // 바깥: 누르는 순간 닫고, 그 누름이 뒤의 목록으로 넘어가지 않게 삼킨다
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            onClose()
                        }
                    }
                    .semantics { onClick(label = "${shown.title} 닫기") { onClose(); true } },
            )
            Layout(
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                content = {
                    Column(
                        Modifier
                            .widthIn(min = 200.dp)
                            .width(IntrinsicSize.Max)
                            .shadow(8.dp, Radius.control)
                            .background(colors.bgPaper, Radius.control)
                            .border(1.dp, colors.rule, Radius.control)
                            .semantics { paneTitle = shown.title }
                            // 목록의 빈 곳을 눌러도 닫히지 않게 한다
                            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown() } }
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) { shown.content(onClose) }
                },
            ) { measurables, constraints ->
                val edge = 12.dp.roundToPx()
                val placeable = measurables.single().measure(
                    Constraints(maxWidth = constraints.maxWidth - edge * 2, maxHeight = constraints.maxHeight - edge * 2),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // 버튼의 자리는 루트 기준이므로 시스템 막대만큼 뺀다
                    val top = coordinates?.boundsInRoot()?.top ?: 0f
                    val x = (shown.anchor.right.roundToInt() - placeable.width)
                        .coerceIn(edge, (constraints.maxWidth - edge - placeable.width).coerceAtLeast(edge))
                    // 버튼에서 4dp 띄운다 (웹 .cat-menu top: calc(100% + 4px))
                    val y = ((shown.anchor.bottom - top).roundToInt() + 4.dp.roundToPx())
                        .coerceIn(edge, (constraints.maxHeight - edge - placeable.height).coerceAtLeast(edge))
                    placeable.place(x, y)
                }
            }
        }
    }
}
