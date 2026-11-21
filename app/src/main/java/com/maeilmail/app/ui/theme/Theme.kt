package com.maeilmail.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 웹 서비스 base.css의 색 변수를 그대로 옮겼다 (라이트 :root, 다크 [data-theme="dark"]).
 * 이름도 웹과 맞춰 두어 웹에서 색을 바꾸면 같은 이름을 찾아 고친다.
 */
@Immutable
data class AppColors(
    val bgCanvas: Color,
    val bgPaper: Color,
    val bgSubtle: Color,
    val bgHover: Color,
    val rule: Color,
    val ruleStrong: Color,
    val textMain: Color,
    val textSub: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryLight: Color,
    val primaryInk: Color,
    val review: Color,
    val reviewBar: Color,
    val reviewLight: Color,
    val star: Color,
    val danger: Color,
    val dangerLight: Color,
    val isDark: Boolean,
)

private val Light = AppColors(
    bgCanvas = Color(0xFFE9ECEF),
    bgPaper = Color(0xFFFDFDFD),
    bgSubtle = Color(0xFFF3F4F6),
    bgHover = Color(0xFFE5E8EB),
    rule = Color(0xFFE1E5E9),
    ruleStrong = Color(0xFF8A939D),
    textMain = Color(0xFF14171A),
    textSub = Color(0xFF333D4B),
    textMuted = Color(0xFF5F6974),
    primary = Color(0xFF007D53),
    primaryLight = Color(0xFFE3F3EC),
    primaryInk = Color(0xFFFFFFFF),
    review = Color(0xFFA35C06),
    reviewBar = Color(0xFFE8960C),
    reviewLight = Color(0xFFFBF0DE),
    star = Color(0xFFD99400),
    danger = Color(0xFFC62828),
    dangerLight = Color(0xFFFBE9E9),
    isDark = false,
)

private val Dark = AppColors(
    bgCanvas = Color(0xFF111113),
    bgPaper = Color(0xFF1A1A1C),
    bgSubtle = Color(0xFF212124),
    bgHover = Color(0xFF29292D),
    rule = Color(0xFF2C2C31),
    ruleStrong = Color(0xFF6C727B),
    textMain = Color(0xFFF0F2F5),
    textSub = Color(0xFFC4C8CF),
    textMuted = Color(0xFF8A919B),
    primary = Color(0xFF00C47D),
    primaryLight = Color(0x2100C47D), // rgba(0, 196, 125, 0.13)
    primaryInk = Color(0xFF0C1512),
    review = Color(0xFFFBBF24),
    reviewBar = Color(0xFFF5A524),
    reviewLight = Color(0x21FBBF24), // rgba(251, 191, 36, 0.13)
    star = Color(0xFFFCC419),
    danger = Color(0xFFF87171),
    dangerLight = Color(0x1FF87171), // rgba(248, 113, 113, 0.12)
    isDark = true,
)

val LocalAppColors = staticCompositionLocalOf { Light }

object AppTheme {
    val colors: AppColors @Composable get() = LocalAppColors.current
}

/** 웹의 --radius(4px)와 같은 모서리 */
object Radius {
    val control = RoundedCornerShape(4.dp)
    val sheet = RoundedCornerShape(8.dp)
}

val Mono = FontFamily.Monospace

private val typography = Typography().let { t ->
    t.copy(
        headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) Dark else Light
    // Material 컴포넌트(새로고침 표시, 메뉴, 스낵바 등)도 웹 색을 쓰도록 맞춘다. 동적 색상(Material You)은 쓰지 않는다
    val scheme = (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = c.primary, onPrimary = c.primaryInk,
        primaryContainer = c.primaryLight, onPrimaryContainer = c.primary,
        secondary = c.textMuted, onSecondary = c.bgPaper,
        background = c.bgPaper, onBackground = c.textMain,
        surface = c.bgPaper, onSurface = c.textMain,
        surfaceVariant = c.bgSubtle, onSurfaceVariant = c.textMuted,
        surfaceContainer = c.bgPaper, surfaceContainerLow = c.bgPaper,
        surfaceContainerHigh = c.bgSubtle, surfaceContainerHighest = c.bgHover,
        inverseSurface = c.textMain, inverseOnSurface = c.bgPaper, inversePrimary = c.primary,
        outline = c.ruleStrong, outlineVariant = c.rule,
        error = c.danger, errorContainer = c.dangerLight,
    )
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = Shapes(extraSmall = Radius.control, small = Radius.control, medium = Radius.sheet, large = Radius.sheet),
            content = content,
        )
    }
}
