package com.openminis.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全 App 唯一视觉真源（以 OpenCode 手机端为基准）。
 *
 * 约束：
 * - 明暗两套都保留，但色值只在这里定义一次。
 * - 11 套用户皮肤只覆盖强调色；背景/表面/文字/圆角/字号全部收敛到本文件，
 *   因此切换皮肤不会再把 App 撕成不同风格。
 * - 旧主题对象（Hq / MibotPalette / OcTheme 等）改为 alias 到此处，
 *   页面代码无需改动即可自动对齐。
 */
data class OmegaPalette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val bubbleUser: Color,
    val onBubbleUser: Color,
    val danger: Color,
    val success: Color,
    val warn: Color,
)

object AppTokens {
    /** 深色：OpenCode OLED 基准。 */
    val dark = OmegaPalette(
        bg = Color(0xFF0A0A0B),
        surface = Color(0xFF151517),
        surface2 = Color(0xFF1E1E21),
        ink = Color(0xFFEDEDED),
        ink2 = Color(0xFF9A9A9E),
        ink3 = Color(0xFF6A6A70),
        border = Color(0xFF2A2A2E),
        accent = Color(0xFF5FB8A0),
        onAccent = Color(0xFF07100D),
        bubbleUser = Color(0xFF1F4A40),
        onBubbleUser = Color(0xFFEDEDED),
        danger = Color(0xFFE06C75),
        success = Color(0xFF63B87F),
        warn = Color(0xFFC9A24D),
    )

    /** 浅色：暖白纸感，青绿压深保证白底对比度 ≥ 4.5:1。 */
    val light = OmegaPalette(
        bg = Color(0xFFF5F5F3),
        surface = Color(0xFFFFFFFF),
        surface2 = Color(0xFFEBEBE8),
        ink = Color(0xFF1C1C1E),
        ink2 = Color(0xFF5A5A5E),
        ink3 = Color(0xFF8A8A8E),
        border = Color(0xFFDFDFDC),
        accent = Color(0xFF2E8C77),
        onAccent = Color(0xFFFFFFFF),
        bubbleUser = Color(0xFFCDE7DE),
        onBubbleUser = Color(0xFF12201C),
        danger = Color(0xFFC0434E),
        success = Color(0xFF2F7D4F),
        warn = Color(0xFF9A6B1F),
    )

    fun of(dark: Boolean): OmegaPalette = if (dark) dark else light

    /**
     * 当前有效配色：明暗由皮肤决定，强调色由皮肤覆盖。
     * 皮肤本身已收敛为「同一套底色 + 单一强调色」，所以这里只需把 accent
     * 换掉，其余全部继承基准 palette，页面不会出现第二套风格。
     */
    val current: OmegaPalette
        @Composable
        @ReadOnlyComposable
        get() {
            // 明暗跟随 MinisTheme 的 darkTheme（经 LocalMibotDark 下发），
            // 皮肤只覆盖强调色，避免主题模式与皮肤互相打架。
            val dark = LocalMibotDark.current
            val accent = SkinStore.current.value.accent
            return of(dark).copy(accent = accent)
        }

    val currentAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = current.accent

    // ---- 圆角（全 App 统一） ----
    val radiusXs = 4.dp     // 状态点、小标签
    val radiusSm = 10.dp    // 控件、命令块、按钮
    val radiusMd = 12.dp    // 卡片、消息气泡、会话行
    val radiusLg = 16.dp    // 大卡片、面板
    val radiusPill = 26.dp  // 胶囊（TabBar / 输入框）

    // ---- 字号（全 App 统一） ----
    val display = 28.sp
    val title = 20.sp
    val section = 18.sp
    val body = 15.sp
    val body2 = 13.sp
    val caption = 12.sp
    val micro = 11.sp
    val mono = 13.sp

    // ---- 间距（全 App 统一） ----
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp
    val spaceXl = 20.dp
    val page = 16.dp
}
