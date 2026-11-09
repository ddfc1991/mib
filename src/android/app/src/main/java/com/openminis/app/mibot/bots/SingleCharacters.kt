package com.openminis.app.mibot.bots

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [fork/mibot-destiny] Bots "外形" = Single Characters（参考 Hermes Look & Feel 风格）:
 * 每个 Bot 默认头像是一个单色 blob（圆/幽灵/太阳/花/心/叶/三角/水滴/云/圆角方…）
 * 加两个黑点眼睛。按 index 在 [SingleCharacters] 里循环取，稳定一致。
 * 上传了自定义头像的 Bot 仍显示位图（优先）。
 */

enum class SingleShape { CIRCLE, SQUARE, STAR, HEART, LEAF, TRIANGLE, DROP, CLOUD, GHOST, BLOB }

data class SingleCharacter(val name: String, val shape: SingleShape, val color: Color)

/** Hermes Look & Feel "Single Characters" 16 个默认角色。 */
val SingleCharacters = listOf(
    SingleCharacter("Lagoon", SingleShape.CIRCLE, Color(0xFF6FBF9E)),
    SingleCharacter("Sky Ghost", SingleShape.GHOST, Color(0xFF9EC3E8)),
    SingleCharacter("Citrus Sun", SingleShape.STAR, Color(0xFFF5A623)),
    SingleCharacter("Dusk Bloom", SingleShape.BLOB, Color(0xFFD8A6C8)),
    SingleCharacter("Candy Heart", SingleShape.HEART, Color(0xFFF2A6B8)),
    SingleCharacter("Mint Clover", SingleShape.LEAF, Color(0xFF9AD6B0)),
    SingleCharacter("Golden Tri", SingleShape.TRIANGLE, Color(0xFFE8C84A)),
    SingleCharacter("Twilight Drop", SingleShape.DROP, Color(0xFF8A8CD8)),
    SingleCharacter("Coral Pebble", SingleShape.BLOB, Color(0xFFF08A7A)),
    SingleCharacter("Violet Quad", SingleShape.SQUARE, Color(0xFF9A8CD8)),
    SingleCharacter("Rose Pear", SingleShape.CIRCLE, Color(0xFFF2A6C0)),
    SingleCharacter("Cloud Nine", SingleShape.CLOUD, Color(0xFF8FD0D4)),
    SingleCharacter("Ink Ghost", SingleShape.GHOST, Color(0xFF3A3A44)),
    SingleCharacter("Chalk Heart", SingleShape.HEART, Color(0xFFE8E6E0)),
    SingleCharacter("Silver Bloom", SingleShape.BLOB, Color(0xFFB8BEC8)),
    SingleCharacter("Ember Dot", SingleShape.SQUARE, Color(0xFFE08A6A)),
)

fun singleCharacterAt(index: Int): SingleCharacter =
    SingleCharacters[((index % SingleCharacters.size) + SingleCharacters.size) % SingleCharacters.size]

/** 画一个 Single Character blob + 两个黑点眼睛，填满 [size] 方块。 */
@Composable
fun SingleCharacterGlyph(c: SingleCharacter, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Canvas(modifier) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.42f
        drawBlob(c.shape, c.color, Offset(cx, cy), r)
        // eyes: two dots, upper-middle
        val eyeR = r * 0.13f
        val eyeY = cy - r * 0.30f
        val eyeDX = r * 0.38f
        drawCircle(Color(0xFF1A1A22), eyeR, Offset(cx - eyeDX, eyeY))
        drawCircle(Color(0xFF1A1A22), eyeR, Offset(cx + eyeDX, eyeY))
    }
}

private fun DrawScope.drawBlob(shape: SingleShape, color: Color, c: Offset, r: Float) {
    when (shape) {
        SingleShape.CIRCLE -> drawCircle(color, r, c)
        SingleShape.SQUARE -> drawRoundRect(color, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), cornerRadius = CornerRadius(r * 0.45f))
        SingleShape.STAR -> drawStar(color, c, r, points = 8, inner = 0.78f) // sun-like
        SingleShape.HEART -> drawHeart(color, c, r)
        SingleShape.LEAF -> { // three-leaf clover
            val d = r * 0.62f
            drawCircle(color, d, Offset(c.x, c.y - r * 0.45f))
            drawCircle(color, d, Offset(c.x - r * 0.55f, c.y + r * 0.35f))
            drawCircle(color, d, Offset(c.x + r * 0.55f, c.y + r * 0.35f))
        }
        SingleShape.TRIANGLE -> drawTriangle(color, c, r)
        SingleShape.DROP -> drawDrop(color, c, r)
        SingleShape.CLOUD -> {
            val d = r * 0.6f
            drawCircle(color, d, Offset(c.x - r * 0.5f, c.y))
            drawCircle(color, d, Offset(c.x, c.y - r * 0.35f))
            drawCircle(color, d, Offset(c.x + r * 0.5f, c.y))
            drawRoundRect(color, topLeft = Offset(c.x - r * 0.9f, c.y - r * 0.1f), size = Size(r * 1.8f, r * 0.9f), cornerRadius = CornerRadius(r * 0.3f))
        }
        SingleShape.GHOST -> drawGhost(color, c, r)
        SingleShape.BLOB -> {
            // soft organic blob: ellipse with slight offset lobes
            drawCircle(color, r, Offset(c.x - r * 0.18f, c.y))
            drawCircle(color, r * 0.94f, Offset(c.x + r * 0.22f, c.y - r * 0.10f))
            drawCircle(color, r * 0.9f, Offset(c.x, c.y + r * 0.18f))
        }
    }
}

private fun DrawScope.drawStar(color: Color, c: Offset, r: Float, points: Int, inner: Float) {
    val path = Path()
    for (i in 0 until points * 2) {
        val ang = Math.PI * i / points - Math.PI / 2
        val rad = if (i % 2 == 0) r else r * inner
        val x = c.x + (Math.cos(ang) * rad).toFloat()
        val y = c.y + (Math.sin(ang) * rad).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawHeart(color: Color, c: Offset, r: Float) {
    val path = Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.2f, c.y + r * 0.1f, c.x - r * 0.9f, c.y - r * 0.9f, c.x, c.y - r * 0.3f)
        cubicTo(c.x + r * 0.9f, c.y - r * 0.9f, c.x + r * 1.2f, c.y + r * 0.1f, c.x, c.y + r * 0.9f)
    }
    drawPath(path, color)
}

private fun DrawScope.drawTriangle(color: Color, c: Offset, r: Float) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        lineTo(c.x + r, c.y + r * 0.8f)
        lineTo(c.x - r, c.y + r * 0.8f)
        close()
    }
    // round the corners a touch
    drawPath(path, color)
}

private fun DrawScope.drawDrop(color: Color, c: Offset, r: Float) {
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        cubicTo(c.x + r * 0.9f, c.y + r * 0.2f, c.x + r * 0.6f, c.y + r, c.x, c.y + r * 0.85f)
        cubicTo(c.x - r * 0.6f, c.y + r, c.x - r * 0.9f, c.y + r * 0.2f, c.x, c.y - r)
    }
    drawPath(path, color)
}

private fun DrawScope.drawGhost(color: Color, c: Offset, r: Float) {
    val path = Path().apply {
        moveTo(c.x - r * 0.8f, c.y + r * 0.9f)
        lineTo(c.x - r * 0.8f, c.y - r * 0.2f)
        arcTo(Rect(c.x - r * 0.8f, c.y - r * 0.9f, c.x + r * 0.8f, c.y + r * 0.5f), 180f, 180f, false)
        lineTo(c.x + r * 0.8f, c.y + r * 0.9f)
        // wavy bottom
        for (i in 1..3) {
            val x0 = c.x + r * 0.8f - (r * 1.6f / 3f) * i
            val x1 = x0 - r * 1.6f / 6f
            lineTo(x0, c.y + r * 0.6f)
            lineTo(x1, c.y + r * 0.9f)
        }
        close()
    }
    drawPath(path, color)
}
