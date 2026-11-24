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

    /** 설정 */
    val Sliders = icon(
        "Sliders",
        "M4 21v-7", "M4 10V3", "M12 21v-9", "M12 8V3", "M20 21v-5", "M20 12V3",
        "M1 14h6", "M9 8h6", "M17 16h6",
    )

    private const val STAR = "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"

    /** 별표 (빈 별) */
    val Star = icon("Star", STAR)

    /** 별표한 질문 (칠한 별) */
    val StarFilled = icon("StarFilled", STAR, filled = true)

    /** 메모 (웹의 메모 표시 아이콘) */
    val Pencil = icon("Pencil", "M12 20h9", "M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z")

    /** GitHub 로고 (웹 GITHUB_ICON). 이것만 선이 아니라 칠한 모양이다 */
    val GitHub: ImageVector = ImageVector.Builder(
        name = "GitHub",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94" +
                "-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07" +
                "-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27" +
                ".68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95" +
                ".29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0 0 16 8c0-4.42-3.58-8-8-8z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()

    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector {
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
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}
