package com.mibot.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mibot.settings.ThemeAccent
import com.openminis.app.ui.theme.AppTokens

/**
 * 界面用到的全部颜色。页面一律从 [MikaTheme.colors] 取,不写死色值。
 * 实色只用在强调(主按钮、选中、开关、用户消息)和状态(成功、需要你、出错);图标块一律中性。
 */
@Immutable
class MikaColors(
    /** 页面背景。 */
    val bg: Color,
    /** 卡片、分组。 */
    val surface: Color,
    /** 浅填充:次按钮、分段控件底、代码块底。 */
    val fill: Color,
    /** 文字三级:正文、次要、辅助。 */
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    /** 正文与辅助之间:比正文淡一点点的字(插话收成小字后用它)。 */
    val inkSoft: Color,
    /** 分隔线。 */
    val hair: Color,
    /** 卡片描边。 */
    val edge: Color,
    val accent: Color,
    val accentSoft: Color,
    /** 强调色上的文字和图标。 */
    val onAccent: Color,
    val ok: Color,
    val okSoft: Color,
    /** 「需要你」:[warn] 用于文字,[warnSolid] 用于圆点、描边。 */
    val warn: Color,
    val warnSolid: Color,
    val warnSoft: Color,
    val bad: Color,
    val badSoft: Color,
    /** 图标块的底。 */
    val neutralSoft: Color,
    /** Mika 回答的底:比页面背景深一点点的灰,和用户的蓝色气泡区分开。 */
    val answer: Color,
    /** 灰色胶囊、关着的开关。 */
    val greySoft: Color,
    /** 悬浮玻璃(输入栏、浮在内容上的按钮)的底和描边。 */
    val glass: Color,
    val glassEdge: Color,
    /** 卡片阴影;深色下卡片只靠描边,为透明。 */
    val cardShadow: Color,
    /** 悬浮元素的阴影。 */
    val floatShadow: Color,
    /** 开关的圆钮。 */
    val thumb: Color,
    /**
     * 图表里分类的颜色(例如每家服务商一种),按固定的先后取;超出的归到 [seriesOther]。
     * 只给图形上色,文字一律用 ink 三级。
     */
    val series: List<Color>,
    val seriesOther: Color,
    /** 图表的网格线。 */
    val grid: Color,
    val isDark: Boolean,
) {
    /** 换一种强调色,其余不变;淡底的透明度和原来一样(浅色 10%、深色 16%)。 */
    fun withAccent(accent: Color, onAccent: Color) = MikaColors(
        bg = bg, surface = surface, fill = fill, ink = ink, ink2 = ink2, ink3 = ink3, inkSoft = inkSoft, hair = hair, edge = edge,
        accent = accent, accentSoft = accent.copy(alpha = if (isDark) 0x29 / 255f else 0x1A / 255f), onAccent = onAccent,
        ok = ok, okSoft = okSoft, warn = warn, warnSolid = warnSolid, warnSoft = warnSoft, bad = bad, badSoft = badSoft,
        neutralSoft = neutralSoft, answer = answer, greySoft = greySoft, glass = glass, glassEdge = glassEdge,
        cardShadow = cardShadow, floatShadow = floatShadow, thumb = thumb, series = series, seriesOther = seriesOther,
        grid = grid, isDark = isDark,
    )
}

val LightColors = MikaColors(
    bg = AppTokens.light.bg,
    surface = AppTokens.light.surface,
    fill = AppTokens.light.surface2.copy(alpha = 0.4f),
    ink = AppTokens.light.ink,
    ink2 = AppTokens.light.ink2,
    ink3 = AppTokens.light.ink3,
    inkSoft = AppTokens.light.ink2,
    hair = AppTokens.light.border.copy(alpha = 0.4f),
    edge = AppTokens.light.border.copy(alpha = 0.3f),
    accent = AppTokens.light.accent,
    accentSoft = AppTokens.light.accent.copy(alpha = 0.12f),
    onAccent = AppTokens.light.onAccent,
    ok = AppTokens.light.success,
    okSoft = AppTokens.light.success.copy(alpha = 0.14f),
    warn = AppTokens.light.warn,
    warnSolid = AppTokens.light.warn,
    warnSoft = AppTokens.light.warn.copy(alpha = 0.14f),
    bad = AppTokens.light.danger,
    badSoft = AppTokens.light.danger.copy(alpha = 0.12f),
    neutralSoft = AppTokens.light.surface2.copy(alpha = 0.5f),
    answer = AppTokens.light.surface2.copy(alpha = 0.4f),
    greySoft = AppTokens.light.ink3.copy(alpha = 0.2f),
    glass = AppTokens.light.surface.copy(alpha = 0.85f),
    glassEdge = AppTokens.light.border.copy(alpha = 0.3f),
    cardShadow = Color(0xFF000000),
    floatShadow = Color(0xFF000000),
    thumb = AppTokens.light.surface,
    series = listOf(AppTokens.light.accent, AppTokens.light.warn, AppTokens.light.success),
    seriesOther = AppTokens.light.ink3,
    grid = AppTokens.light.border.copy(alpha = 0.3f),
    isDark = false,
)

val DarkColors = MikaColors(
    bg = AppTokens.dark.bg,
    surface = AppTokens.dark.surface,
    fill = AppTokens.dark.surface2.copy(alpha = 0.4f),
    ink = AppTokens.dark.ink,
    ink2 = AppTokens.dark.ink2,
    ink3 = AppTokens.dark.ink3,
    inkSoft = AppTokens.dark.ink2,
    hair = AppTokens.dark.border.copy(alpha = 0.5f),
    edge = AppTokens.dark.border,
    accent = AppTokens.dark.accent,
    accentSoft = AppTokens.dark.accent.copy(alpha = 0.16f),
    onAccent = AppTokens.dark.onAccent,
    ok = AppTokens.dark.success,
    okSoft = AppTokens.dark.success.copy(alpha = 0.16f),
    warn = AppTokens.dark.warn,
    warnSolid = AppTokens.dark.warn,
    warnSoft = AppTokens.dark.warn.copy(alpha = 0.16f),
    bad = AppTokens.dark.danger,
    badSoft = AppTokens.dark.danger.copy(alpha = 0.16f),
    neutralSoft = AppTokens.dark.surface2.copy(alpha = 0.5f),
    answer = AppTokens.dark.surface2.copy(alpha = 0.4f),
    greySoft = AppTokens.dark.ink3.copy(alpha = 0.2f),
    glass = AppTokens.dark.surface2.copy(alpha = 0.85f),
    glassEdge = AppTokens.dark.border.copy(alpha = 0.5f),
    cardShadow = Color.Transparent,
    floatShadow = Color(0xFF000000),
    thumb = AppTokens.dark.ink,
    series = listOf(AppTokens.dark.accent, AppTokens.dark.warn, AppTokens.dark.success),
    seriesOther = AppTokens.dark.ink3,
    grid = AppTokens.dark.border.copy(alpha = 0.5f),
    isDark = true,
)

/** 按深浅色和强调色取整套颜色。蓝色就是 [LightColors] / [DarkColors] 本身;别的颜色只换强调色那三项。 */
fun palette(dark: Boolean, accent: ThemeAccent): MikaColors {
    val base = if (dark) DarkColors else LightColors
    if (accent == ThemeAccent.Blue) return base
    val (color, on) = accentTone(accent, dark)
    return base.withAccent(color, on)
}

/**
 * 强调色在浅色 / 深色下的取值,和它上面的文字、图标色。
 * 文字的对比度不低于默认蓝色,和页面背景的对比度不低于 3:1(见 `AccentPaletteTest`)。
 */
fun accentTone(accent: ThemeAccent, dark: Boolean): Pair<Color, Color> = when (accent) {
    ThemeAccent.Blue -> (if (dark) DarkColors else LightColors).let { it.accent to it.onAccent }
    ThemeAccent.Indigo -> Color(if (dark) 0xFF7D7AFF else 0xFF5856D6) to Color.White
    ThemeAccent.Purple -> Color(if (dark) 0xFFBF5AF2 else 0xFF9B3FD9) to Color.White
    ThemeAccent.Pink -> Color(if (dark) 0xFFFF4F80 else 0xFFE0306A) to Color.White
    ThemeAccent.Teal -> Color(if (dark) 0xFF1E9E97 else 0xFF0A8F8A) to Color.White
    // 深色下的石墨是浅灰,上面配深色字
    ThemeAccent.Graphite -> if (dark) Color(0xFFE5E5EA) to Color(0xFF1C1C1E) else Color(0xFF3A3A3C) to Color.White
}

/** 没有包在 [MikaTheme] 里时(比如界面测试)按浅色。 */
val LocalMikaColors = staticCompositionLocalOf { LightColors }
