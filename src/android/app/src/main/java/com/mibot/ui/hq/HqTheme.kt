package com.mibot.ui.hq

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hermes HQ 复刻设计系统 —— OLED 黑 + iOS 蓝 + 彩色 pebble blob 头像 + 悬浮毛玻璃胶囊。
 * 全部色值来自真机截图逐屏提取（见 workspace/hermes-hq-replica/规范.md）。
 */
object Hq {
    /** 页面背景：OLED 近纯黑。 */
    val bg = Color(0xFF0A0A0A)
    /** 表面/卡片：iOS secondarySystemBackground dark。 */
    val surface = Color(0xFF1C1C1E)
    /** 更深表面（bot 气泡、审批卡）。 */
    val surface2 = Color(0xFF1E1E1E)
    /** 卡片/胶囊描边。 */
    val edge = Color(0xFF2C2C2E)
    /** 主文字。 */
    val ink = Color(0xFFFFFFFF)
    /** 次文字（辅助灰）。 */
    val ink2 = Color(0xFF8E8E93)
    /** 三级（描述、更淡）。 */
    val ink3 = Color(0xFFD1D1D6)
    /** 强调蓝（选中 tab、主按钮、链接）。 */
    val accent = Color(0xFF0A84FF)
    /** 用户气泡蓝。 */
    val bubbleUser = Color(0xFF3478F6)
    /** bot 气泡深灰。 */
    val bubbleBot = Color(0xFF1E1E1E)
    /** 毛玻璃底/边（输入框）。 */
    val glass = Color(0x0FFFFFFF)
    val glassEdge = Color(0x1FFFFFFF)
    /** 成功绿 / 警告橙 / 危险红。 */
    val ok = Color(0xFF30D158)
    val warn = Color(0xFFFF9F0A)
    val bad = Color(0xFFFF453A)

    /** Pebble（blob）头像色板 —— HQ 视觉锚点，按索引循环取。 */
    val pebbles = listOf(
        Color(0xFFFF9500), // 橙  Hermes
        Color(0xFFFF6B4A), // 珊瑚红  Deploys
        Color(0xFFA78BFA), // 紫  Research
        Color(0xFF3B82F6), // 蓝  Inbox
        Color(0xFF10B981), // 绿  Writing
        Color(0xFFFFCC00), // 黄  Finance
        Color(0xFF06B6D4), // 青  Travel
        Color(0xFFEC4899), // 粉  Home
        Color(0xFF84CC16), // 黄绿  Garden
    )
    /** 按名字稳定取一个 pebble 色（名字哈希）。 */
    fun pebble(name: String): Color = pebbles[(name.hashCode() and Int.MAX_VALUE) % pebbles.size]

    /** 等宽字体族（bot 命令、命令行）。 */
    const val MonoFamily = "monospace"
}

/** 字号。 */
object HqType {
    /** 大标题（Bots/More）。 */
    val LargeTitle = TextStyle(fontSize = 30.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold)
    /** 屏标题（Sessions/Kanban）。 */
    val ScreenTitle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    /** 行标题、导航胶囊名称。 */
    val RowTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    /** 正文、消息。 */
    val Body = TextStyle(fontSize = 15.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    /** 说明文字。 */
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    /** TabBar 标签。 */
    val Tab = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
    /** 分组标题。 */
    val Section = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    /** 等宽命令。 */
    val Mono = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
}

/** 圆角 / 尺寸。 */
object HqShape {
    val Bubble = RoundedCornerShape(18.dp)
    val Card = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(10.dp)
    val Chip = RoundedCornerShape(16.dp)
    /** 悬浮胶囊（导航/输入/tab）：全圆。 */
    val Capsule = RoundedCornerShape(26.dp)
    /** 消息气泡尾角。 */
    val UserBubble = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
    val BotBubble = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
}

/** 间距。 */
object HqSpace {
    val S4 = 4.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val Page = 16.dp
}
