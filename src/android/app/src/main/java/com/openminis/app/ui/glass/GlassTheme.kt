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
    val DuskBackground = Color(0xFF0E121C)
    val DuskSurface = Color(0xFF1A1F2B)
    val DuskOn = Color(0xFFECEFF6)
    val DuskOnMuted = Color(0xFFAAB1C3)
    val DuskAccent = Color(0xFFEE8E70)
    val DuskTint = Color(0xFF141925)

    // B: warm-white minimal
    val WarmBackground = Color(0xFFF2F4F7)
    val WarmSurface = Color(0xFFFFFFFF)
    val WarmOn = Color(0xFF1C212C)
    val WarmOnMuted = Color(0xFF545B6A)
    val WarmAccent = Color(0xFFB9503A)
    val WarmTint = Color(0xFFF7F8FA)

    fun scheme(base: ColorScheme, style: GlassStyle): ColorScheme = when (style) {
        GlassStyle.DUSK -> base.copy(
            primary = DuskAccent, onPrimary = Color(0xFF231A3D),
            primaryContainer = Color(0xFF3A3156), onPrimaryContainer = DuskOn,
            secondaryContainer = Color(0xFF2E2940), onSecondaryContainer = DuskOn,
            background = DuskBackground, onBackground = DuskOn,
            surface = DuskSurface, onSurface = DuskOn,
            surfaceVariant = Color(0xFF2B2639), onSurfaceVariant = DuskOnMuted,
            surfaceContainerLowest = Color(0xFF121019), surfaceContainerLow = Color(0xFF1C1927),
            surfaceContainer = Color(0xFF221E2F), surfaceContainerHigh = Color(0xFF2A2638),
            surfaceContainerHighest = Color(0xFF332E42),
            outline = Color(0xFF6E6884), outlineVariant = Color(0xFF3A3549),
            inverseSurface = DuskOn, inverseOnSurface = DuskBackground,
        )
        GlassStyle.WARM -> base.copy(
            primary = Color(0xFF8A5A33), onPrimary = Color.White,
            primaryContainer = Color(0xFFF1E4D6), onPrimaryContainer = WarmOn,
            secondaryContainer = Color(0xFFF0EAE1), onSecondaryContainer = WarmOn,
            background = WarmBackground, onBackground = WarmOn,
            surface = WarmSurface, onSurface = WarmOn,
            surfaceVariant = Color(0xFFF0EAE1), onSurfaceVariant = WarmOnMuted,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F3ED),
            surfaceContainer = Color(0xFFF3EEE7), surfaceContainerHigh = Color(0xFFEDE7DF),
            surfaceContainerHighest = Color(0xFFE7E0D7),
            outline = Color(0xFFB9AFA3), outlineVariant = Color(0xFFE6DFD5),
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
