package com.mibot.ui.hq

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 颗粒化动效（借鉴 Dissolve）：内容消散成颗粒向四周飞散。
 * 放在被删除元素的位置，播完回调 [onDone]。
 */
@Composable
fun DissolveParticles(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Hq.accent,
    count: Int = 26,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        onDone()
    }
    val p = progress.value
    val density = LocalDensity.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val maxDist = (w * 0.45f).coerceAtLeast(40f)
        val rpx = with(density) { ((1f - p) * 5f + 0.5f).dp.toPx() }
        repeat(count) { i ->
            val angle = (i / count.toFloat()) * 2f * PI.toFloat()
            val dist = p * maxDist * (0.7f + 0.3f * ((i * 7919) % 100) / 100f)
            val x = w / 2 + cos(angle) * dist
            val y = h / 2 + sin(angle) * dist
            drawCircle(
                color = color.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                radius = rpx,
                center = Offset(x, y),
            )
        }
    }
}
