package com.mibot.ui.hq

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 弹性开关（ui-interactions W3「弹性开关」）：
 * 点击时 thumb spring 过冲回弹（dampingRatio 0.55 欠阻尼），滑动中拉长 1.25x 再弹回。
 * Hermes 配色：开=Hq.accent 底白 thumb / 关=#2C2C2E 底白 thumb。
 */
@Composable
fun HqSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackColor: Color = Hq.accent,
) {
    val thumbX = remember { Animatable(if (checked) 1f else 0f) }
    val thumbScale = remember { Animatable(1f) }
    val interaction = remember { MutableInteractionSource() }

    // 外部状态变更（非点击）直接对齐
    LaunchedEffect(checked) {
        if (!thumbX.isAnimating) thumbX.snapTo(if (checked) 1f else 0f)
    }

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(if (checked) trackColor else Color(0xFF2C2C2E))
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = null,
                    onClick = {
                        onCheckedChange(!checked)
                        thumbX.animateTo(if (checked) 0f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f))
                    },
                ) else Modifier,
            ),
    ) {
        LaunchedEffect(thumbX.isAnimating) {
            if (thumbX.isAnimating) {
                thumbScale.snapTo(1.22f)
                thumbScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f))
            }
        }
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .offset { IntOffset((thumbX.value * 18).toInt(), 0) },
        ) {
            Box(
                modifier = Modifier
                    .scale(thumbScale.value)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
