package com.mibot.ui.hq

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mibot.ui.theme.MikaIcons
import com.openminis.app.ui.glass.FrostLevel
import com.openminis.app.ui.glass.frosted
import dev.chrisbanes.haze.HazeState

/** HQ 底部 5 个 tab。 */
enum class HqTab(val label: String, val icon: ImageVector) {
    Bots("对话", MikaIcons.Chats),
    Sessions("会话", MikaIcons.Route),
    Jobs("任务", MikaIcons.Clock),
    Kanban("动态", MikaIcons.Apps),
    More("更多", MikaIcons.More),
}

/**
 * 悬浮毛玻璃胶囊 TabBar：底距 12dp、左右 8dp、高 44dp、全圆。
 * 未选中 `#8E8E93`，选中 `#0A84FF`。
 */
@Composable
fun HqTabBar(
    current: HqTab,
    onSelect: (HqTab) -> Unit,
    modifier: Modifier = Modifier,
    /** Page-local Haze source: blur the content the bar floats over. */
    haze: HazeState? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(52.dp)
            // [fork/glass-depth] True frosted capsule: blur + lit rim + sheen.
            // Falls back to a denser flat tint on API < 31 or without a source.
            .frosted(
                shape = RoundedCornerShape(26.dp),
                level = FrostLevel.Bar,
                border = true,
                elevation = 8.dp,
                haze = haze,
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HqTab.entries.forEach { tab ->
            val selected = tab == current
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.90f else 1f, label = "tabScale")
            val color by animateColorAsState(if (selected) Hq.accent else Hq.ink2, label = "tabColor")
            Column(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(interactionSource = interaction, indication = null) { onSelect(tab) }
                    .then(
                        if (selected) Modifier.background(Hq.accent.copy(alpha = 0.14f)) else Modifier
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(21.dp))
                Text(tab.label, style = HqType.Tab, color = color)
            }
        }
    }
}
