package com.mibot.life.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mibot 虚拟形象 —— 纯 Compose 画的"表情化"脸。
 *
 * 不依赖图片资产，表情由真实状态驱动：
 *   mood 0..1（愉悦/能量/挫败综合）→ 眼睛弧度 + 嘴型
 *   talking → 嘴一张一合（说话）
 *   blink 由内部动画驱动（自动眨眼）
 */
@Composable
fun MibotAvatar(
    size: Dp = 96.dp,
    mood: Float = 0.5f,
    talking: Boolean = false,
    sleeping: Boolean = false,
) {
    val infinite = rememberInfiniteTransition(label = "avatar")
    // 自动眨眼
    val blink by infinite.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(90), RepeatMode.Reverse,
            initialStartOffset = StartOffset(2400),   // 每 ~2.5s 眨一下
        ), label = "blink"
    )
    // 说话动嘴
    val talk by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "talk"
    )
    // 呼吸
    val breathe by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breathe"
    )

    val m = mood.coerceIn(0f, 1f)
    val eyeH = if (sleeping) 0.08f else (0.30f * blink * (0.6f + 0.4f * m))   // 眼睛高度
    val mouthOpen = if (talking) talk else (0.10f + 0.12f * m)                // 嘴开合

    Box(
        Modifier.size(size).scale(breathe),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val cx = size.toPx() / 2f
            val cy = size.toPx() / 2f
            val r = s * 0.46f

            // 身体/头（暖瓷圆）
            drawCircle(Color(0xFFF6E3CE), radius = r, center = Offset(cx, cy))
            // 底部阴影
            drawCircle(Color(0xFFE8D2B8), radius = r, center = Offset(cx, cy + r * 0.16f))
            // 高光（左上）
            drawCircle(Color(0x66FFFFFF), radius = r * 0.36f, center = Offset(cx - r * 0.3f, cy - r * 0.36f))

            // 耳朵（两个小圆）
            drawCircle(Color(0xFFF0D8BE), radius = r * 0.16f, center = Offset(cx - r * 0.95f, cy))
            drawCircle(Color(0xFFF0D8BE), radius = r * 0.16f, center = Offset(cx + r * 0.95f, cy))

            // 眼睛
            val eyeY = cy - r * 0.16f
            val eyeDX = r * 0.34f
            val eyeW = r * 0.18f
            if (sleeping) {
                // 闭眼：一条弧线
                drawArc(
                    color = Color(0xFF6B625A),
                    startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(cx - eyeDX - eyeW, eyeY - eyeW * 0.5f),
                    size = androidx.compose.ui.geometry.Size(eyeW * 2, eyeW),
                    style = Stroke(width = r * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF6B625A),
                    startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(cx + eyeDX - eyeW, eyeY - eyeW * 0.5f),
                    size = androidx.compose.ui.geometry.Size(eyeW * 2, eyeW),
                    style = Stroke(width = r * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            } else {
                // 睁眼：椭圆（高度随眨眼/心情）
                val h = r * 0.22f * eyeH.coerceIn(0.08f, 1f)
                drawOval(
                    color = Color(0xFF2E2A26),
                    topLeft = Offset(cx - eyeDX - eyeW / 2, eyeY - h / 2),
                    size = androidx.compose.ui.geometry.Size(eyeW, h)
                )
                drawOval(
                    color = Color(0xFF2E2A26),
                    topLeft = Offset(cx + eyeDX - eyeW / 2, eyeY - h / 2),
                    size = androidx.compose.ui.geometry.Size(eyeW, h)
                )
                // 高光点
                drawCircle(Color(0x88FFFFFF), radius = eyeW * 0.12f,
                    center = Offset(cx - eyeDX, eyeY - h * 0.22f))
                drawCircle(Color(0x88FFFFFF), radius = eyeW * 0.12f,
                    center = Offset(cx + eyeDX, eyeY - h * 0.22f))
            }

            // 嘴：微笑弧度 / 开口（说话）
            val mouthY = cy + r * 0.28f
            val mouthW = r * 0.36f
            val smile = (m - 0.5f) * 0.8f + 0.3f   // 心情→嘴角弧度
            if (talking) {
                // 说话：椭圆开合
                val h = r * 0.16f * mouthOpen
                drawOval(
                    color = Color(0xFF2E2A26),
                    topLeft = Offset(cx - mouthW / 2, mouthY - h / 2),
                    size = androidx.compose.ui.geometry.Size(mouthW, h)
                )
                drawOval(Color(0xFFE8736B),
                    topLeft = Offset(cx - mouthW / 2, mouthY - h * 0.1f),
                    size = androidx.compose.ui.geometry.Size(mouthW, h * 0.4f))
            } else {
                // 微笑弧线（心情越高越弯）
                drawArc(
                    color = Color(0xFF2E2A26),
                    startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(cx - mouthW / 2, mouthY - mouthW * 0.5f * smile),
                    size = androidx.compose.ui.geometry.Size(mouthW, mouthW * smile),
                    style = Stroke(width = r * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }

            // 腮红（心情好才有）
            if (m > 0.4f) {
                val blush = (m - 0.4f) * 0.5f
                drawCircle(Color(0x66E8825A), radius = r * 0.12f * (0.6f + blush),
                    center = Offset(cx - r * 0.62f, cy + r * 0.12f))
                drawCircle(Color(0x66E8825A), radius = r * 0.12f * (0.6f + blush),
                    center = Offset(cx + r * 0.62f, cy + r * 0.12f))
            }
        }
    }
}
