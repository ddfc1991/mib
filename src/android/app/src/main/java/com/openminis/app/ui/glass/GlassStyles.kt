package com.openminis.app.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle

/**
 * [fork/glass-ui] Haze styles for the glass surfaces. Haze blurs with
 * RenderEffect on API 31+; below that `blurEnabled` defaults to false and it
 * paints [HazeStyle.fallbackTint] instead, so the fallback tints are nearly
 * opaque to keep text legible over an un-blurred backdrop.
 */
object GlassStyles {
    // [fork/frost] Both styles now come from the shared frost tokens: the bar
    // was a 0.40/0.46 scrim (text on a busy page), now dark 0.62 / light 0.72
    // over the blur with a 0.88 no-blur fallback; the sheet is a little denser.

    /** Docked / floating bottom bar. */
    @Composable
    @ReadOnlyComposable
    fun bar(style: GlassStyle): HazeStyle =
        FrostTokens.style(style == GlassStyle.DUSK, FrostLevel.Bar, LocalFrost.current.photo)

    /** Sidebar sheet: denser so long lists stay readable. */
    @Composable
    @ReadOnlyComposable
    fun sheet(style: GlassStyle): HazeStyle =
        FrostTokens.style(style == GlassStyle.DUSK, FrostLevel.Sheet, LocalFrost.current.photo)

    /** Hairline colour for borders/dividers on glass. */
    fun hairline(style: GlassStyle): Color = FrostTokens.hairline(style == GlassStyle.DUSK)
}
