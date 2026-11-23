package com.mibot.ui.hq

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * [openbots/avatar] 每个 bot 的 blob 形状+表情都不同(按名字哈希稳定分配):
 * 5 种形状(圆/椭圆/扁豆/水滴/三角圆) × 5 种表情(两眼/眯眼/眨眼/圆眼笑/豆眼)。
 * 同名的 bot 永远同一个样子;形状和表情互不绑定,可自由组合。
 */
@Composable
fun PebbleAvatar(
    name: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val c = color ?: Hq.pebble(name)
    val h = name.hashCode()
    val shape = (h and Int.MAX_VALUE) % 5
    val expr = ((h ushr 3) and Int.MAX_VALUE) % 5
    Canvas(modifier) {
        val w = size.width
        val hh = size.height
        drawBody(w, hh, shape, c)
        drawFace(w, hh, expr)
    }
}

/** 主体形状:5 种,全部落在 w×h 内(高约 0.92h,宽约 0.94w,垂直居中)。 */
private fun DrawScope.drawBody(w: Float, h: Float, shape: Int, c: Color) {
    val path = Path()
    when (shape) {
        0 -> { // 正圆
            drawOval(color = c, topLeft = Offset(w * 0.03f, h * 0.05f), size = Size(w * 0.94f, h * 0.90f))
            return
        }
        1 -> { // 宽椭圆(原样)
            path.moveTo(w * 0.08f, h * 0.46f)
            path.cubicTo(w * 0.03f, h * 0.24f, w * 0.20f, h * 0.11f, w * 0.50f, h * 0.11f)
            path.cubicTo(w * 0.80f, h * 0.11f, w * 0.97f, h * 0.24f, w * 0.92f, h * 0.46f)
            path.cubicTo(w * 0.97f, h * 0.68f, w * 0.80f, h * 0.81f, w * 0.50f, h * 0.81f)
            path.cubicTo(w * 0.20f, h * 0.81f, w * 0.03f, h * 0.68f, w * 0.08f, h * 0.46f)
        }
        2 -> { // 扁豆:左圆右平
            path.moveTo(w * 0.10f, h * 0.50f)
            path.cubicTo(w * 0.04f, h * 0.22f, w * 0.28f, h * 0.08f, w * 0.54f, h * 0.15f)
            path.cubicTo(w * 0.74f, h * 0.19f, w * 0.86f, h * 0.34f, w * 0.86f, h * 0.50f)
            path.cubicTo(w * 0.86f, h * 0.66f, w * 0.74f, h * 0.81f, w * 0.54f, h * 0.85f)
            path.cubicTo(w * 0.28f, h * 0.92f, w * 0.04f, h * 0.78f, w * 0.10f, h * 0.50f)
        }
        3 -> { // 水滴:顶尖底圆
            path.moveTo(w * 0.50f, h * 0.10f)
            path.cubicTo(w * 0.78f, h * 0.30f, w * 0.90f, h * 0.52f, w * 0.76f, h * 0.72f)
            path.cubicTo(w * 0.66f, h * 0.87f, w * 0.34f, h * 0.87f, w * 0.24f, h * 0.72f)
            path.cubicTo(w * 0.10f, h * 0.52f, w * 0.22f, h * 0.30f, w * 0.50f, h * 0.10f)
        }
        4 -> { // 圆角三角:底边圆
            path.moveTo(w * 0.50f, h * 0.12f)
            path.cubicTo(w * 0.80f, h * 0.30f, w * 0.88f, h * 0.62f, w * 0.72f, h * 0.80f)
            path.cubicTo(w * 0.62f, h * 0.91f, w * 0.38f, h * 0.91f, w * 0.28f, h * 0.80f)
            path.cubicTo(w * 0.12f, h * 0.62f, w * 0.20f, h * 0.30f, w * 0.50f, h * 0.12f)
        }
    }
    path.close()
    drawPath(path, color = c)
}

/** 表情:5 种,画在主体上部(约 0.28h),嘴在下(约 0.62h)。 */
private fun DrawScope.drawFace(w: Float, h: Float, expr: Int) {
    val eyeColor = Color(0xFF000000).copy(alpha = 0.55f)
    val eyeY = h * 0.30f
    val gap = w * 0.11f
    when (expr) {
        0 -> { // 两眼(原样)
            drawOval(color = eyeColor, topLeft = Offset(w * 0.5f - gap - w * 0.09f, eyeY), size = Size(w * 0.09f, h * 0.15f))
            drawOval(color = eyeColor, topLeft = Offset(w * 0.5f + gap, eyeY), size = Size(w * 0.09f, h * 0.15f))
        }
        1 -> { // 眯眼:两条短横线
            val stroke = Stroke(width = h * 0.055f, cap = StrokeCap.Round)
            drawLine(eyeColor, Offset(w * 0.5f - gap - w * 0.055f, eyeY + h * 0.07f), Offset(w * 0.5f - gap + w * 0.055f, eyeY + h * 0.07f), strokeWidth = h * 0.055f, cap = StrokeCap.Round)
            drawLine(eyeColor, Offset(w * 0.5f + gap - w * 0.055f, eyeY + h * 0.07f), Offset(w * 0.5f + gap + w * 0.055f, eyeY + h * 0.07f), strokeWidth = h * 0.055f, cap = StrokeCap.Round)
        }
        2 -> { // 眨眼:左眼、右横线
            drawOval(color = eyeColor, topLeft = Offset(w * 0.5f - gap - w * 0.09f, eyeY), size = Size(w * 0.09f, h * 0.15f))
            drawLine(eyeColor, Offset(w * 0.5f + gap - w * 0.055f, eyeY + h * 0.07f), Offset(w * 0.5f + gap + w * 0.055f, eyeY + h * 0.07f), strokeWidth = h * 0.055f, cap = StrokeCap.Round)
        }
        3 -> { // 圆眼 + 微笑嘴
            drawOval(color = eyeColor, topLeft = Offset(w * 0.5f - gap - w * 0.045f, eyeY), size = Size(w * 0.09f, h * 0.09f))
            drawOval(color = eyeColor, topLeft = Offset(w * 0.5f + gap, eyeY), size = Size(w * 0.09f, h * 0.09f))
            val smile = Path().apply {
                moveTo(w * 0.5f - w * 0.10f, h * 0.60f)
                cubicTo(w * 0.5f - w * 0.05f, h * 0.66f, w * 0.5f + w * 0.05f, h * 0.66f, w * 0.5f + w * 0.10f, h * 0.60f)
            }
            drawPath(smile, color = eyeColor, style = Stroke(width = h * 0.045f, cap = StrokeCap.Round))
        }
        4 -> { // 豆眼(竖椭圆) + 平嘴线
            drawOval(color = eyeColor, topLeft = Offset(w * 0.5f - gap - w * 0.06f, eyeY - h * 0.02f), size = Size(w * 0.07f, h * 0.17f))
            drawOval(color = eyeColor, topLeft = Offset(w * 0.5f + gap, eyeY - h * 0.02f), size = Size(w * 0.07f, h * 0.17f))
            drawLine(eyeColor, Offset(w * 0.5f - w * 0.08f, h * 0.64f), Offset(w * 0.5f + w * 0.08f, h * 0.64f), strokeWidth = h * 0.04f, cap = StrokeCap.Round)
        }
    }
}