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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.maeilmail.app.R

/**
 * 웹 서비스 base.css의 색 변수를 그대로 옮겼다 (라이트 :root, 다크 [data-theme="dark"]).
 * 이름도 웹과 맞춰 두어 웹에서 색을 바꾸면 같은 이름을 찾아 고친다.
 *
 * 쓰임은 웹과 같다: 캔버스(bgCanvas)는 책상, 그 위의 지면(bgPaper)이 문서다.
 * 상단 바·필터·아래 막대는 캔버스에, 내용은 지면에 둔다.
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

/**
 * 모서리: 웹 base.css와 같은 값. 웹은 알약·원 모양을 쓰지 않고 거의 각진 모서리로 문서 느낌을 낸다.
 * - control: 버튼·입력칸·메뉴·창·토스트 (--radius 4px)
 * - small: 메뉴 항목·작은 표시 (2px)
 * - dot: 학습 수준 점·로고 점·진행 막대 (1px)
 */
object Radius {
    val control = RoundedCornerShape(4.dp)
    /** 칸 여럿을 묶은 틀 (.level-control 6px) */
    val group = RoundedCornerShape(6.dp)
    val small = RoundedCornerShape(2.dp)
    val dot = RoundedCornerShape(1.dp)
}

/**
 * 웹과 같은 글꼴 Pretendard 1.3.9 (SIL OFL, assets/licenses/pretendard-OFL.txt).
 * 웹은 CDN의 pretendard.min.css로 불러오고, 앱은 같은 판의 OTF 파일 4개(400·500·600·700)를 res/font에 넣었다.
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/**
 * 웹의 글자 단계. 크기는 웹의 좁은 화면 값을 따르고, 이름은 웹 클래스에서 따왔다.
 * 웹처럼 글자 사이를 -0.015em 좁히고, 줄 높이는 CSS처럼 위아래로 고르게 나눈다.
 */
object TextStyles {
    private val base = TextStyle(
        fontFamily = Pretendard,
        letterSpacing = (-0.015).em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    /** 상단 바의 서비스 이름과 돌아갈 곳 (.nav-brand, .nav-current. 누르는 자리라 조금 크게) */
    val pageTitle = base.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

    /** 문서 머리의 분류·날짜 (.doc-meta) */
    val meta = base.copy(fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em)

    /** 문서 제목 (h1, 좁은 화면 23px) */
    val h1 = base.copy(fontSize = 23.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.025).em)

    /** 구분 제목 (.list-divider) */
    val divider = base.copy(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)

    /** 첫 화면의 오늘의 질문 제목 (.today-title) */
    val todayTitle = base.copy(fontSize = 17.sp, lineHeight = 25.5.sp, fontWeight = FontWeight.Bold)

    /** 목록 행의 질문 제목 (.q-title) */
    val rowTitle = base.copy(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)

    /** 번호·개수처럼 자릿수를 맞추는 작은 글자 (.q-num, .list-head) */
    val num = base.copy(fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

    /** 보통 글 (설정 항목, 안내) */
    val body = base.copy(fontSize = 15.sp, lineHeight = 24.sp)

    /** 작은 안내·분류 (.q-meta, .progress-text) */
    val small = base.copy(fontSize = 12.5.sp, lineHeight = 19.sp)

    /** 버튼 (.btn. 손가락으로 누르므로 웹의 13px보다 조금 크게) */
    val button = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    /** 토스트 (.toast) */
    val toast = base.copy(fontSize = 13.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    /** 창 제목 (.confirm-dialog h2) */
    val dialogTitle = base.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)

    /** Material 컴포넌트(펼침 메뉴, 시각 입력 등)가 쓰는 글자 */
    internal val material = Typography().let { t ->
        t.copy(
            headlineSmall = h1,
            titleLarge = dialogTitle,
            titleMedium = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = body,
            bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = small,
            labelLarge = button,
            labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            labelSmall = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) Dark else Light
    // Material 컴포넌트(메뉴, 시각 입력 등)도 웹 색을 쓰도록 맞춘다. 동적 색상(Material You)은 쓰지 않는다
    val scheme = (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = c.primary, onPrimary = c.primaryInk,
        primaryContainer = c.primaryLight, onPrimaryContainer = c.primary,
        secondary = c.textMuted, onSecondary = c.bgPaper,
        background = c.bgPaper, onBackground = c.textMain,
        surface = c.bgPaper, onSurface = c.textMain,
        surfaceVariant = c.bgSubtle, onSurfaceVariant = c.textMuted,
        surfaceContainer = c.bgPaper, surfaceContainerLow = c.bgPaper,
        surfaceContainerHigh = c.bgPaper, surfaceContainerHighest = c.bgHover,
        inverseSurface = c.textMain, inverseOnSurface = c.bgPaper, inversePrimary = c.primary,
        outline = c.ruleStrong, outlineVariant = c.rule,
        error = c.danger, errorContainer = c.dangerLight,
    )
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TextStyles.material,
            // Material의 모서리 단계를 모두 웹의 4px로 둔다 (메뉴·창·입력칸이 이 값을 쓴다)
            shapes = Shapes(
                extraSmall = Radius.control,
                small = Radius.control,
                medium = Radius.control,
                large = Radius.control,
                extraLarge = Radius.control,
            ),
            content = content,
        )
    }
}
