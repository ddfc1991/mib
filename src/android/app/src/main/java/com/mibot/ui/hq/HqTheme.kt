package com.mibot.ui.hq

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.theme.AppTokens

/**
 * Hermes HQ 设计系统 —— 已收敛到全 App 唯一视觉真源 [AppTokens]。
 *
 * 旧常量名全部保留为 alias，页面代码无需改动；
 * 明暗与皮肤强调色由 AppTokens.current 统一决定，HQ 不再自带一套 iOS 蓝。
 */
object Hq {
    /** 当前有效配色（明暗 + 皮肤强调色）。 */
    private val tokens: com.openminis.app.ui.theme.OmegaPalette
        @Composable
        @ReadOnlyComposable
        get() = AppTokens.current

    val bg: Color
        @Composable @ReadOnlyComposable get() = tokens.bg
    val surface: Color
        @Composable @ReadOnlyComposable get() = tokens.surface
    val surface2: Color
        @Composable @ReadOnlyComposable get() = tokens.surface2
    val edge: Color
        @Composable @ReadOnlyComposable get() = tokens.border
    val ink: Color
        @Composable @ReadOnlyComposable get() = tokens.ink
    val ink2: Color
        @Composable @ReadOnlyComposable get() = tokens.ink2
    val ink3: Color
        @Composable @ReadOnlyComposable get() = tokens.ink3
    val accent: Color
        @Composable @ReadOnlyComposable get() = tokens.accent
    val bubbleUser: Color
        @Composable @ReadOnlyComposable get() = tokens.bubbleUser
    val bubbleBot: Color
        @Composable @ReadOnlyComposable get() = tokens.surface2
    val glass: Color
        @Composable @ReadOnlyComposable get() = tokens.surface2.copy(alpha = 0.90f)
    val glassEdge: Color
        @Composable @ReadOnlyComposable get() = tokens.border.copy(alpha = 0.6f)
    val ok: Color
        @Composable @ReadOnlyComposable get() = tokens.success
    val warn: Color
        @Composable @ReadOnlyComposable get() = tokens.warn
    val bad: Color
        @Composable @ReadOnlyComposable get() = tokens.danger

    /** Pebble（blob）头像色板 —— 头像身份色，不属于页面主题，保留。 */
    val pebbles = listOf(
        Color(0xFFFF9500),
        Color(0xFFFF6B4A),
        Color(0xFFA78BFA),
        Color(0xFF3B82F6),
        Color(0xFF10B981),
        Color(0xFFFFCC00),
        Color(0xFF06B6D4),
        Color(0xFFEC4899),
        Color(0xFF84CC16),
    )

    fun pebble(name: String): Color = pebbles[(name.hashCode() and Int.MAX_VALUE) % pebbles.size]

    /** 等宽字体族（bot 命令、命令行）。 */
    const val MonoFamily = "monospace"
}

/** 字号 —— 使用统一字号标尺。 */
object HqType {
    // 字号全部取自 AppTokens，避免 HQ 自己再维护一套标尺。
    val LargeTitle = TextStyle(fontSize = AppTokens.display, lineHeight = AppTokens.display * 1.25f, fontWeight = FontWeight.Bold)
    val ScreenTitle = TextStyle(fontSize = AppTokens.title, lineHeight = AppTokens.title * 1.25f, fontWeight = FontWeight.Bold)
    val RowTitle = TextStyle(fontSize = AppTokens.section, lineHeight = AppTokens.section * 1.25f, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = AppTokens.body, lineHeight = AppTokens.body * 1.4f, fontWeight = FontWeight.Normal)
    val Caption = TextStyle(fontSize = AppTokens.body2, lineHeight = AppTokens.body2 * 1.4f, fontWeight = FontWeight.Normal)
    val Tab = TextStyle(fontSize = AppTokens.micro, lineHeight = AppTokens.micro * 1.3f, fontWeight = FontWeight.Medium)
    val Section = TextStyle(fontSize = AppTokens.body2, lineHeight = AppTokens.body2 * 1.4f, fontWeight = FontWeight.SemiBold)
    val Mono = TextStyle(fontSize = AppTokens.mono, lineHeight = AppTokens.mono * 1.4f, fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace)
}

/** 圆角 / 尺寸 —— 使用统一圆角标尺。 */
object HqShape {
    val Bubble = RoundedCornerShape(AppTokens.radiusMd)
    val Card = RoundedCornerShape(AppTokens.radiusLg)
    val Button = RoundedCornerShape(AppTokens.radiusSm)
    val Chip = RoundedCornerShape(AppTokens.radiusMd)
    val Capsule = RoundedCornerShape(AppTokens.radiusPill)
    val UserBubble = RoundedCornerShape(AppTokens.radiusMd, AppTokens.radiusMd, AppTokens.radiusXs, AppTokens.radiusMd)
    val BotBubble = RoundedCornerShape(AppTokens.radiusMd, AppTokens.radiusMd, AppTokens.radiusMd, AppTokens.radiusXs)
}

/** 间距。 */
object HqSpace {
    val S4 = AppTokens.spaceXs
    val S8 = AppTokens.spaceSm
    val S12 = AppTokens.spaceMd
    val S16 = AppTokens.spaceLg
    val Page = AppTokens.page
}
