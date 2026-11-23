package com.mibot.ui.hq

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 填充进度条（ui-interactions C2「填充进度」）：
 * 宽度随 [fraction] spring 缓动，背景槽 + 前景填充，可选流光头。
 * 用于任务卡"运行中"进度、上传进度等。
 */
@Composable
fun HqProgressFill(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Hq.accent,
    trackColor: Color = Color(0xFF2C2C2E),
    height: androidx.compose.ui.unit.Dp = 6.dp,
    animate: Boolean = true,
) {
    val target = fraction.coerceIn(0f, 1f)
    val width = if (animate) {
        val anim = remember { Animatable(0f) }
        LaunchedEffect(target) {
            anim.animateTo(target, tween(600, easing = FastOutSlowInEasing))
        }
        anim.value
    } else target
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(width)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}
