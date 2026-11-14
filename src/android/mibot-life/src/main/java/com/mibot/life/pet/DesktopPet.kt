package com.mibot.life.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * 有动作的桌宠 —— 卡通猫（用户选猫/狗萌系）。
 * 圆脸 + 猫耳(随呼吸动) + 眼睛(眨眼/朝向) + 胡须 + 尾巴(摆) + 状态表情。
 * 由 PetState 驱动，主线程 30fps 刷新（减少动效时降帧）。
 *
 * 配色 Hark：暖瓷底 / 珊瑚橙身体 / 白肚皮 / 状态换色。
 */
@Composable
fun DesktopPet(
    state: PetState,
    modifier: Modifier = Modifier,
    sizeDp: Int = 120,
) {
    var now by remember { mutableStateOf(0.0) }

    // 动画循环：30fps
    LaunchedEffect(Unit) {
        val frameMs = 33L
        val start = System.nanoTime() / 1e9
        while (true) {
            now = (System.nanoTime() / 1e9) - start
            delay(frameMs)
        }
    }

    val frame = state.frame(now)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .rotate(frame.tilt),
            contentAlignment = Alignment.Center,
        ) {
            PetCat(frame, sizeDp)
        }
        Text(
            text = frame.phase.label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF3D2E26),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x33E8825A))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** 卡通猫身体：脸 + 猫耳 + 眼睛 + 嘴 + 胡须 + 尾巴 */
@Composable
private fun PetCat(frame: PetFrame, sizeDp: Int) {
    Canvas(modifier = Modifier.size(sizeDp.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.36f
        val bobOffset = frame.bob * h * 0.02f

        val bodyColor = when (frame.phase) {
            PetPhase.IDLE -> Color(0xFFE8825A)   // 珊瑚橙（Hark 强调）
            PetPhase.WORKING -> Color(0xFFD97757) // 陶土橙（工作）
            PetPhase.ASKING -> Color(0xFFF59E0B)  // 琥珀（待批准）
            PetPhase.DONE -> Color(0xFF10B981)    // 完成绿
        }
        val earY = cy - r * 0.9f + bobOffset

        // 尾巴（随呼吸摆动）
        val tailSwing = sin((frame.bob + 1f) * Math.PI).toFloat() * r * 0.15f
        drawTail(cx + r * 1.1f, cy + r * 0.6f, tailSwing, bodyColor, r)

        // 猫耳（随呼吸微动）
        drawEar(cx - r * 0.55f, earY, bodyColor, r, frame)
        drawEar(cx + r * 0.55f, earY, bodyColor, r, frame)

        // 脸（圆）
        drawCircle(bodyColor, radius = r, center = Offset(cx, cy + bobOffset))
        // 肚皮（下半白）
        drawCircle(Color(0xCCFFFFFF), radius = r * 0.5f, center = Offset(cx, cy + r * 0.45f + bobOffset))

        // 眼睛
        drawEyes(frame, cx, cy + bobOffset, r)
        // 嘴
        drawMouth(frame, cx, cy + bobOffset, r)
        // 胡须
        drawWhiskers(frame, cx, cy + bobOffset, r)
        // 状态装饰
        drawSign(frame, cx, cy, r)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEar(
    cx: Float, earY: Float, color: Color, r: Float, frame: PetFrame,
) {
    val earH = r * 0.5f
    val path = Path().apply {
        moveTo(cx - r * 0.35f, earY + r * 0.5f)
        lineTo(cx, earY)
        lineTo(cx + r * 0.35f, earY + r * 0.5f)
        close()
    }
    drawPath(path, color)
    // 内耳（粉）
    val inner = Path().apply {
        moveTo(cx - r * 0.18f, earY + r * 0.45f)
        lineTo(cx, earY + r * 0.2f)
        lineTo(cx + r * 0.18f, earY + r * 0.45f)
        close()
    }
    drawPath(inner, Color(0xFFF8B4A0))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTail(
    x: Float, y: Float, swing: Float, color: Color, r: Float,
) {
    val path = Path().apply {
        moveTo(x, y)
        cubicTo(
            x + r * 0.4f, y - r * 0.3f + swing,
            x + r * 0.5f, y - r * 0.9f + swing,
            x + r * 0.2f, y - r * 1.1f + swing,
        )
    }
    drawPath(path, color, style = Stroke(width = r * 0.22f))
    // 尾尖
    drawCircle(color, radius = r * 0.11f, center = Offset(x + r * 0.2f, y - r * 1.1f + swing))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyes(
    frame: PetFrame, cx: Float, cy: Float, r: Float,
) {
    val eyeY = cy - r * 0.15f
    val eyeDX = when (frame.facing) {
        PetFacing.LEFT -> -r * 0.08f
        PetFacing.RIGHT -> r * 0.08f
        else -> 0f
    }
    val eyeOffset = r * 0.3f
    val eyeR = r * 0.13f
    val blink = frame.blink.coerceIn(0f, 1f)
    val eyeRadius = eyeR * (1f - blink * 0.9f)

    listOf(-1, 1).forEach { side ->
        val ex = cx + side * eyeOffset + eyeDX
        if (frame.sign == PetSign.HAPPY) {
            val path = Path().apply {
                moveTo(ex - eyeR, eyeY)
                quadraticBezierTo(ex, eyeY - eyeR * 1.2f, ex + eyeR, eyeY)
            }
            drawPath(path, Color(0xFF3D2E26), style = Stroke(width = r * 0.05f))
        } else if (frame.sign == PetSign.SLEEP) {
            drawLine(Color(0xFF3D2E26), Offset(ex - eyeR, eyeY), Offset(ex + eyeR, eyeY), strokeWidth = r * 0.05f)
        } else {
            drawCircle(Color(0xFF3D2E26), radius = eyeRadius, center = Offset(ex, eyeY))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMouth(
    frame: PetFrame, cx: Float, cy: Float, r: Float,
) {
    val my = cy + r * 0.22f
    when (frame.phase) {
        PetPhase.DONE -> {
            val path = Path().apply {
                moveTo(cx - r * 0.2f, my)
                quadraticBezierTo(cx, my + r * 0.18f, cx + r * 0.2f, my)
            }
            drawPath(path, Color(0xFF3D2E26), style = Stroke(width = r * 0.045f))
        }
        PetPhase.WORKING -> drawCircle(Color(0xFF3D2E26), radius = r * 0.07f, center = Offset(cx, my))
        else -> drawLine(
            Color(0xFF3D2E26), Offset(cx - r * 0.1f, my), Offset(cx + r * 0.1f, my),
            strokeWidth = r * 0.04f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWhiskers(
    frame: PetFrame, cx: Float, cy: Float, r: Float,
) {
    val wy = cy + r * 0.05f
    val wc = Color(0x663D2E26)
    val strokeW = r * 0.025f
    // 左三根
    listOf(-1, 0, 1).forEach { i ->
        val yOff = i * r * 0.12f
        drawLine(wc, Offset(cx - r * 0.7f, wy + yOff), Offset(cx - r * 1.05f, wy + yOff - r * 0.05f), strokeW)
        drawLine(wc, Offset(cx + r * 0.7f, wy + yOff), Offset(cx + r * 1.05f, wy + yOff - r * 0.05f), strokeW)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSign(
    frame: PetFrame, cx: Float, cy: Float, r: Float,
) {
    when (frame.sign) {
        PetSign.QUESTION -> drawCircle(Color(0xFFFFF3CD), radius = r * 0.26f, center = Offset(cx + r * 0.72f, cy - r * 0.72f))
        PetSign.EXCLAIM -> drawCircle(Color(0xFFFEE2E2), radius = r * 0.26f, center = Offset(cx + r * 0.72f, cy - r * 0.72f))
        PetSign.THINKING -> {
            val bx = cx + r * 0.72f
            val by = cy - r * 0.72f
            for (i in 0..2) drawCircle(Color(0xFFBFDBFE), radius = r * 0.045f, center = Offset(bx - i * r * 0.12f, by))
        }
        PetSign.SLEEP -> drawCircle(Color(0xFFE2E8F0), radius = r * 0.26f, center = Offset(cx + r * 0.72f, cy - r * 0.72f))
        else -> {}
    }
}
