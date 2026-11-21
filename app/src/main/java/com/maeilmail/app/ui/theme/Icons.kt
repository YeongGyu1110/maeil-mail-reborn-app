package com.maeilmail.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 웹과 같은 선 아이콘 (Feather 아이콘 모양: 24칸, 선 굵기 2, 둥근 선 끝).
 * 웹의 <svg>에 있는 path·line·polyline을 path 문자열로 옮겼다. 색은 Icon의 tint가 칠한다.
 */
object LineIcons {
    val Search = icon("Search", "M19 11a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M21 21l-4.35-4.35")
    val Close = icon("Close", "M18 6L6 18", "M6 6l12 12")
    val ChevronDown = icon("ChevronDown", "M6 9l6 6 6-6")
    val ChevronLeft = icon("ChevronLeft", "M15 18l-6-6 6-6")
    val ChevronRight = icon("ChevronRight", "M9 18l6-6-6-6")
    val ArrowLeft = icon("ArrowLeft", "M19 12H5", "M12 19l-7-7 7-7")
    val ArrowRight = icon("ArrowRight", "M5 12h14", "M12 5l7 7-7 7")

    /** 오늘 탭 */
    val Calendar = icon(
        "Calendar",
        "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M16 2v4", "M8 2v4", "M3 10h18",
    )

    /** 질문 탭 */
    val List = icon("List", "M8 6h13", "M8 12h13", "M8 18h13", "M3 6h0.01", "M3 12h0.01", "M3 18h0.01")

    /** 설정 탭 */
    val Sliders = icon(
        "Sliders",
        "M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3",
        "M1 14h6", "M9 8h6", "M17 16h6",
    )

    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
