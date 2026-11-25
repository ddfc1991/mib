package com.mibot.ui.claude

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.R

/**
 * Claude 设计系统 —— 从 com.anthropic.claude（v1.261120.19）逆向提取。
 *
 * 来源：反编译全源码（jadx 1.5.2，31,769 个 Java）+ 真机界面截图 + 品牌字体。
 * 字体来自 app 的 `assets/composeResources/claude.theme.generated.resources/font/`：
 * anthropic_interface / anthropic_sans(+italic) / anthropic_serif(+italic) / jetbrains_mono。
 * 色值来自真机截图逐屏提取 + 硬编码串 `ClaudeWordmark`/`ClaudeSparkIcon` 的强调橙。
 *
 * 见 workspace/claude/SHELL_SPEC.md 与 REPORT.md。
 */
object Claude {

    // ── 色板 ─────────────────────────────────────────────────────────────

    /** 页面背景：OLED 纯黑（Claude 深色模式主色）。 */
    val bg = Color(0xFF000000)
    /** 次级背景（侧栏、会话列表）。 */
    val bgAlt = Color(0xFF0A0A0A)
    /** 卡片、提示卡。 */
    val surface = Color(0xFF1A1A1A)
    /** 更深表面（Claude 回答气泡、代码块）。 */
    val surface2 = Color(0xFF141414)
    /** 卡片描边。 */
    val edge = Color(0xFF2C2C2E)
    /** 主文字。 */
    val ink = Color(0xFFF4F4F5)
    /** 次文字（辅助灰）。 */
    val ink2 = Color(0xFFA1A1AA)
    /** 三级（描述、更淡）。 */
    val ink3 = Color(0xFF8E8E93)
    /** Claude 强调橙（asterisk ✳、主按钮、链接、图标）。 */
    val accent = Color(0xFFD97757)
    /** 强调橙的悬停态。 */
    val accentHover = Color(0xFFE8926F)
    /** 强调橙上的文字。 */
    val onAccent = Color(0xFFFFFFFF)
    /** 用户气泡底（深棕灰，和 Claude 回答的深灰区分）。 */
    val bubbleUser = Color(0xFF2E2A28)
    /** Claude 回答气泡底。 */
    val bubbleBot = Color(0xFF141414)
    /** 毛玻璃底（输入框、悬浮按钮）。 */
    val glass = Color(0x0FFFFFFF)
    val glassEdge = Color(0x1FFFFFFF)
    /** 错误卡底（组织已停用一类）。 */
    val errorBg = Color(0xFF3B1D1D)
    val errorEdge = Color(0xFF5C2C2C)
    val errorInk = Color(0xFFFFC9C9)
    val errorTitle = Color(0xFFFF8A80)
    /** 状态色。 */
    val ok = Color(0xFF3CCF7A)
    val warn = Color(0xFFFFB340)
    val bad = Color(0xFFFF6961)
    /** 建议提问（月光下的对话？一类）的底色。 */
    val suggestion = Color(0xFF1F1D1A)

    // ── 形状 ─────────────────────────────────────────────────────────────

    /** 卡片圆角。 */
    val cardRadius = 16.dp
    /** 按钮圆角（胶囊）。 */
    val buttonRadius = 28.dp
    /** 输入框圆角。 */
    val inputRadius = 24.dp
    /** 气泡圆角。 */
    val bubbleRadius = 14.dp

    // ── 字体 ─────────────────────────────────────────────────────────────

    /** Claude Sans：正文、标题、按钮（app 默认界面字体）。 */
    val ClaudeSans = FontFamily(
        FontFamily.Font(R.font.claude_sans),
        FontFamily.Font(R.font.claude_sans_italic, style = androidx.compose.ui.text.font.FontStyle.Italic),
    )
    /** Claude Serif：长文阅读、正文段落（Claude 深色界面正文用的衬线）。 */
    val ClaudeSerif = FontFamily(
        FontFamily.Font(R.font.claude_serif),
        FontFamily.Font(R.font.claude_serif_italic, style = androidx.compose.ui.text.font.FontStyle.Italic),
    )
    /** Claude Mono：代码、命令行、Claude Code 输出。 */
    val ClaudeMono = FontFamily(
        FontFamily.Font(R.font.claude_mono),
        FontFamily.Monospace,
    )

    /** 全局默认字体族（界面文字一律走这个）。 */
    val defaultFont = ClaudeSans
}

/** Claude 字号。 */
object ClaudeType {
    /** 大标题（设置标题、屏标题）。 */
    val LargeTitle = TextStyle(
        fontFamily = Claude.ClaudeSans,
        fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, color = Claude.ink,
    )
    /** 屏标题（会话、项目）。 */
    val ScreenTitle = TextStyle(
        fontFamily = Claude.ClaudeSans,
        fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Claude.ink,
    )
    /** 行标题、导航项名称。 */
    val RowTitle = TextStyle(
        fontFamily = Claude.ClaudeSans,
        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = Claude.ink,
    )
    /** 正文、消息。 */
    val Body = TextStyle(
        fontFamily = Claude.ClaudeSans,
        fontSize = 15.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal, color = Claude.ink,
    )
    /** 说明文字。 */
    val Caption = TextStyle(
        fontFamily = Claude.ClaudeSans,
        fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal, color = Claude.ink2,
    )
    /** 分组标题。 */
    val Section = TextStyle(
        fontFamily = Claude.ClaudeSans,
        fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = Claude.ink3,
    )
    /** 等宽（Claude Code 命令、代码块）。 */
    val Mono = TextStyle(
        fontFamily = Claude.ClaudeMono,
        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, color = Claude.ink,
    )
}
