package com.openminis.app.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.R
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Top-level pages reached from the floating tab bar. */
enum class GlassTab(val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    CHATS(R.string.glass_tab_chats, Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
    FEED(R.string.glass_tab_feed, Icons.Outlined.DynamicFeed, Icons.Filled.DynamicFeed),
    IDEAS(R.string.glass_tab_ideas, Icons.Outlined.Lightbulb, Icons.Filled.Lightbulb),
    BOTS(R.string.glass_tab_bots, Icons.Outlined.SmartToy, Icons.Filled.SmartToy),
    SETTINGS(R.string.glass_tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** [fork/mibot-v4] Floating capsule height and its gap to the screen bottom. */
val GlassTabBarCapsule: Dp = 58.dp
val GlassTabBarMargin: Dp = 10.dp

/** Space the floating bar takes above the navigation-bar inset. */
val GlassTabBarHeight: Dp = GlassTabBarCapsule + GlassTabBarMargin

/**
 * Bottom space a page under the bar should leave (content padding for lists
 * that scroll beneath the glass, or plain padding for foreign screens).
 */
val LocalGlassBottomInset = compositionLocalOf { 0.dp }

/**
 * [fork/glass-ui] Hosts one tab page: backdrop + page as the Haze source, and
 * the docked glass bar (full width, hairline top border) above it. [scrollUnder] = true lets the page draw
 * beneath the bar (it must honour [LocalGlassBottomInset] as content
 * padding); false pads the page instead, for stock screens with their own
 * bottom FABs.
 */
@Composable
fun GlassTabShell(
    current: GlassTab,
    onSelect: (GlassTab) -> Unit,
    scrollUnder: Boolean,
    // [fork/mibot-v2] The shared backdrop now sits behind the whole NavHost.
    showBackdrop: Boolean = !com.openminis.app.ui.theme.LocalMibotActive.current,
    content: @Composable BoxScope.() -> Unit,
) {
  GlassStyleTheme {
    val haze = rememberHazeState()
    val style = LocalGlassStyle.current
    GlassSystemBars(lightIcons = style == GlassStyle.DUSK)
    val inset = GlassTabBarHeight
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            if (showBackdrop) GlassScreenBackdrop()
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (scrollUnder) Modifier
                        else Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = inset),
                    ),
            ) {
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                CompositionLocalProvider(LocalGlassBottomInset provides if (scrollUnder) inset + navBottom else 0.dp) {
                    content()
                }
            }
        }
        GlassTabBar(
            current = current,
            onSelect = onSelect,
            style = style,
            modifier = Modifier.align(Alignment.BottomCenter),
            haze = haze,
        )
    }
  }
}

/** Status/navigation bar icon colour while a glass page is showing; restored after. */
@Composable
private fun GlassSystemBars(lightIcons: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.DisposableEffect(lightIcons) {
        val window = (view.context as? android.app.Activity)?.window
        val ctl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val prevStatus = ctl?.isAppearanceLightStatusBars
        val prevNav = ctl?.isAppearanceLightNavigationBars
        ctl?.isAppearanceLightStatusBars = !lightIcons
        ctl?.isAppearanceLightNavigationBars = !lightIcons
        onDispose {
            if (prevStatus != null) ctl.isAppearanceLightStatusBars = prevStatus
            if (prevNav != null) ctl.isAppearanceLightNavigationBars = prevNav
        }
    }
}

/** The user's backdrop, dimmed toward the theme background for legibility. */
@Composable
fun GlassScreenBackdrop() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by remember(context) { GlassPrefs.flow(context) }.collectAsState()
    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(settings = settings)
        if (settings.mode != BackdropMode.PLAIN) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = settings.dim)))
        }
    }
}

/** [fork/mibot-v4] Refined line glyph for each tab. */
private fun GlassTab.glyph(): com.openminis.app.mibot.design.LineGlyph = with(com.openminis.app.mibot.design.LineIcons) {
    when (this@glyph) {
        GlassTab.CHATS -> MessageCircle
        GlassTab.FEED -> Newspaper
        GlassTab.IDEAS -> Lightbulb
        GlassTab.BOTS -> Bot
        GlassTab.SETTINGS -> Settings
    }
}

/**
 * [fork/mibot-v4] Floating frosted-glass tab bar: a rounded capsule inset
 * from the screen edges that really blurs the page scrolling beneath it
 * (Haze RenderEffect on API 31+; translucent tinted scrim + hairline on 30),
 * a top highlight rim, line icons (duotone + brick-red when selected).
 */
@Composable
private fun GlassTabBar(
    current: GlassTab,
    onSelect: (GlassTab) -> Unit,
    style: GlassStyle,
    haze: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = style == GlassStyle.DUSK
    val shape = RoundedCornerShape(26.dp)
    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.22f else 0.85f),
            GlassStyles.hairline(style),
        ),
    )
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, end = 12.dp, bottom = GlassTabBarMargin),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(GlassTabBarCapsule)
                .clip(shape)
                .hazeEffect(state = haze, style = GlassStyles.bar(style)) {
                    blurEnabled = android.os.Build.VERSION.SDK_INT >= 31
                }
                .border(1.dp, rim, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTab.entries.forEach { tab ->
                val selected = tab == current
                val label = stringResource(tab.labelRes)
                val color by animateColorAsState(if (selected) selectedColor else idleColor, label = "tabColor")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(tab) }
                        .semantics {
                            this.selected = selected
                            contentDescription = label
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 48.dp, height = 28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) selectedColor.copy(alpha = if (dark) 0.20f else 0.12f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        com.openminis.app.mibot.design.Glyph(
                            tab.glyph(),
                            size = 22.dp,
                            tint = color,
                            duotone = selected,
                        )
                    }
                    Text(
                        label,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
