package com.openminis.app.ui.glass

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.openminis.app.ui.theme.AppTokens

/**
 * [fork/glass-ui] Colour scheme for glass-hosted screens (tab pages, the chat
 * list, the sidebar). Derived from the app's own scheme with the surface and
 * text roles replaced, so Material 3 components keep working unchanged.
 * Screens outside the glass shell (an open chat, sub-settings pages) keep the
 * user's normal theme.
 */
object GlassPalette {
    // A: dark dusk glass
    // [fork/mibot-v2] "Dusk" now means the night variant of the unified
    // Mibot design system, "Warm" the light cloud-sky variant.
    val DuskBackground = AppTokens.dark.bg
    val DuskSurface = AppTokens.dark.surface
    val DuskOn = AppTokens.dark.ink
    val DuskOnMuted = AppTokens.dark.ink2
    val DuskAccent = AppTokens.dark.accent
    val DuskTint = AppTokens.dark.surface2

    // B: light variant of the same palette
    val WarmBackground = AppTokens.light.bg
    val WarmSurface = AppTokens.light.surface
    val WarmOn = AppTokens.light.ink
    val WarmOnMuted = AppTokens.light.ink2
    val WarmAccent = AppTokens.light.accent
    val WarmTint = AppTokens.light.surface2

    fun scheme(base: ColorScheme, style: GlassStyle): ColorScheme = when (style) {
        GlassStyle.DUSK -> base.copy(
            primary = DuskAccent, onPrimary = AppTokens.dark.onAccent,
            primaryContainer = DuskTint, onPrimaryContainer = DuskOn,
            secondaryContainer = DuskTint, onSecondaryContainer = DuskOn,
            background = DuskBackground, onBackground = DuskOn,
            surface = DuskSurface, onSurface = DuskOn,
            surfaceVariant = DuskTint, onSurfaceVariant = DuskOnMuted,
            surfaceContainerLowest = DuskBackground, surfaceContainerLow = DuskSurface,
            surfaceContainer = DuskTint, surfaceContainerHigh = DuskTint,
            surfaceContainerHighest = DuskTint,
            outline = AppTokens.dark.border, outlineVariant = AppTokens.dark.border,
            inverseSurface = DuskOn, inverseOnSurface = DuskBackground,
        )
        GlassStyle.WARM -> base.copy(
            primary = WarmAccent, onPrimary = AppTokens.light.onAccent,
            primaryContainer = WarmTint, onPrimaryContainer = WarmOn,
            secondaryContainer = WarmTint, onSecondaryContainer = WarmOn,
            background = WarmBackground, onBackground = WarmOn,
            surface = WarmSurface, onSurface = WarmOn,
            surfaceVariant = WarmTint, onSurfaceVariant = WarmOnMuted,
            surfaceContainerLowest = WarmSurface, surfaceContainerLow = WarmTint,
            surfaceContainer = WarmTint, surfaceContainerHigh = WarmTint,
            surfaceContainerHighest = WarmTint,
            outline = AppTokens.light.border, outlineVariant = AppTokens.light.border,
            inverseSurface = WarmOn, inverseOnSurface = WarmBackground,
        )
    }
}

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.DUSK }

@Composable
fun currentGlassSettings(): GlassSettings {
    val context = LocalContext.current
    val s by remember(context) { GlassPrefs.flow(context) }.collectAsState()
    return s
}

/**
 * [fork/mibot-v2] Glass pages now share the app-wide Mibot scheme (one design
 * system); this only publishes which variant is active for the few glass
 * components that pick hairlines/tints by it. With the Mibot theme off it
 * still applies the old glass palette.
 */
@Composable
fun GlassStyleTheme(content: @Composable () -> Unit) {
    if (com.openminis.app.ui.theme.LocalMibotActive.current) {
        val style = if (com.openminis.app.ui.theme.LocalMibotDark.current) GlassStyle.DUSK else GlassStyle.WARM
        CompositionLocalProvider(LocalGlassStyle provides style) { content() }
        return
    }
    val style = currentGlassSettings().style
    val scheme = GlassPalette.scheme(MaterialTheme.colorScheme, style)
    CompositionLocalProvider(LocalGlassStyle provides style) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            content()
        }
    }
}
