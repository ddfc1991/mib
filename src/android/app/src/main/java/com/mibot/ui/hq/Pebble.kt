package com.mibot.ui.hq

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * Hermes HQ 的「鹅卵石」blob 头像：宽略大于高的圆角块 + 两枚小椭圆眼。
 * 非正圆，上下边缘平缓弧，是 HQ 全 app 的视觉锚点。
 */
@Composable
fun PebbleAvatar(
    name: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val c = color ?: Hq.pebble(name)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // 主体：宽椭圆 + 微调，上下弧平缓
        val body = Path().apply {
            moveTo(0f, h * 0.5f)
            cubicTo(0f, h * 0.18f, w * 0.18f, 0f, w * 0.5f, 0f)
            cubicTo(w * 0.82f, 0f, w, h * 0.18f, w, h * 0.5f)
            cubicTo(w, h * 0.82f, w * 0.82f, h, w * 0.5f, h)
            cubicTo(w * 0.18f, h, 0f, h * 0.82f, 0f, h * 0.5f)
            close()
        }
        drawPath(body, color = c)
        // 两枚小眼：上部偏中的深色小椭圆
        val eyeColor = Color(0xFF000000).copy(alpha = 0.55f)
        val eyeW = w * 0.10f
        val eyeH = h * 0.16f
        val eyeY = h * 0.32f
        val gap = w * 0.11f
        drawOval(
            color = eyeColor,
            topLeft = Offset(w * 0.5f - gap - eyeW, eyeY),
            size = Size(eyeW, eyeH),
        )
        drawOval(
            color = eyeColor,
            topLeft = Offset(w * 0.5f + gap, eyeY),
            size = Size(eyeW, eyeH),
        )
    }
}
