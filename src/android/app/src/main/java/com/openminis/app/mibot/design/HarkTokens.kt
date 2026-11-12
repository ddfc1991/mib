package com.openminis.app.mibot.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hark 蒸馏风格 token（暖瓷毛玻璃 × Muse 结构）。
 * 从 Hark build-36 合并稿蒸馏：暖米白底 / 毛玻璃卡 90% / 陶土 accent / 墨文 / 绿状态点。
 * 质感关键：多层阴影 + 渐变背景 + 毛玻璃(blur) + 内高光，全部可量化，真机截图逐项核验。
 */
object HarkTokens {
    // ---- 色板（暖瓷）----
    val bgTop = Color(0xFFFAF6EF)          // 暖米白
    val bgBottom = Color(0xFFECE2D2)       // 米瓷深
    val glass = Color(0xF2FFFCF6)          // 毛玻璃卡 90% 不透明
    val glassStrong = Color(0xFFFDF8F0)    // 实卡
    val terracotta = Color(0xFFC2571F)     // 陶土 accent
    val terracottaSoft = Color(0xFFE8C4A8) // 陶土软
    val ink = Color(0xFF2B2620)            // 墨文（暖黑）
    val inkSub = Color(0xFF8A8178)         // 次文
    val hairline = Color(0x1F2B2620)       // hairline 边框 12%
    val okGreen = Color(0xFF3FA45B)        // 绿状态点
    val errRed = Color(0xFFD64545)         // 错误红
    val blue = Color(0xFF4C6FBF)           // 图标蓝
    val green = Color(0xFF3E8F5B)          // 图标绿
    val purple = Color(0xFF7A5FA8)         // 图标紫
    val amber = Color(0xFFC98A2E)          // 图标琥珀

    // ---- 毛玻璃背景（暖瓷渐变底）----
    val bgBrush = Brush.verticalGradient(listOf(bgTop, bgBottom))

    // ---- 三层阴影（软投影）----
    // 用 Modifier.shadow(elevation, shape, ambientColor, spotColor) 实现，见 DestinyHomeHeader。

    // ---- 字体（Muse 大标题：大字距 -0.02em，行高 1.3）----
    val museTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.02).em,
        lineHeight = 1.3.em,
        color = ink,
    )
    val museSection = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.02.em,
        color = inkSub,
    )

    // ---- 圆角（38 卡 / 28 组 / 16 内）----
    val cardRadius = RoundedCornerShape(38.dp)
    val groupRadius = RoundedCornerShape(28.dp)
    val innerRadius = RoundedCornerShape(16.dp)
}
