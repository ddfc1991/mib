package com.mibot.ui.hq

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 行交错入场（ui-interactions P4「列表交错入场」）：
 * 首组成后 index*40ms（上限 200ms）延迟，从下方 12dp 滑入 + 淡入（240ms FastOutSlowIn）。
 * 用法：行 composable 外包 [HqStaggerIn](index = i) { row }。
 */
@Composable
fun HqStaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    val dy = remember { Animatable(12f) }
    LaunchedEffect(Unit) {
        delay(((index.coerceAtMost(5) * 40L)).coerceAtMost(200L))
        alpha.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        dy.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier.graphicsLayer(
            alpha = alpha.value,
            translationY = dy.value,
        ),
    ) {
        content()
    }
}
