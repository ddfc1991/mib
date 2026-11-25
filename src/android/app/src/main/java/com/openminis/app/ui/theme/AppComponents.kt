package com.openminis.app.ui.theme

import com.mibot.ui.theme.MikaIcons

import com.openminis.app.ui.glass.FrostLevel
import com.openminis.app.ui.glass.frosted
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 全 App 唯一顶栏实现。
 *
 * 规格：56dp 高 · 左对齐标题 · 44dp 返回热区 · 可选等宽副标题 · 1dp 发丝线。
 * 颜色全部来自 [AppTokens]，因此明暗切换与皮肤换色时顶栏自动跟随。
 */
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    /** 自定义左侧图标（例如设置齿轮）；为空且 [onBack] 非空时用标准返回箭头。 */
    navigationIcon: ImageVector? = null,
    navigationLabel: String = "返回",
    background: Color? = null,
) {
    val p = AppTokens.current
    val barModifier = if (background != null) {
        modifier.fillMaxWidth().background(background)
    } else {
        // [fork/opencode-ui] The shared top bar is transparent frosted glass,
        // not a second solid-colour surface. On API < 31 / non-glass screens
        // `frosted` falls back to an opaque tint so text stays readable.
        modifier.fillMaxWidth().frosted(level = FrostLevel.Bar, border = false)
    }
    Column(barModifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = AppTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val navIcon = navigationIcon ?: if (onBack != null) MikaIcons.Back else null
            if (navIcon != null && onBack != null) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .semantics { contentDescription = navigationLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        navIcon,
                        contentDescription = null,
                        tint = p.ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = AppTokens.spaceXs)) {
                Text(
                    title,
                    color = p.ink,
                    fontSize = AppTokens.section,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = p.ink3,
                        fontSize = AppTokens.micro,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HorizontalDivider(thickness = 1.dp, color = p.border.copy(alpha = 0.6f))
    }
}
