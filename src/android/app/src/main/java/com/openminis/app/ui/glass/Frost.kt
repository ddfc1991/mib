package com.openminis.app.ui.glass

import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openminis.app.ui.theme.LocalMibotDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * [fork/frost] ONE shared frosted-glass ("毛玻璃朦胧") surface for the whole app.
 *
 * Replaces the old low-alpha fills (0.40–0.72 flat over the moving sky) that let
 * text sit on a busy background. Every Mibot surface now draws:
 *
 *  - API 31+: the shared backdrop (the sky / photo under the NavHost, registered
 *    as a Haze source by [FrostHost] + [frostSource]) blurred with RenderEffect,
 *    a light grain, then a tinted scrim (light ~0.72, dark ~0.62);
 *  - API < 31, inside a dialog / sheet window, or with no backdrop host: a
 *    higher-opacity flat tint (~0.88) so text stays readable without blur;
 *  - a 1dp hairline rim (top highlight → hairline) and an optional soft shadow.
 *
 * The scrim is denser over a user photo ([FrostEnv.photo]) because a
 * photo can hold high-contrast detail the procedural sky never has. Tints are
 * chosen so body text (ink) and muted text keep ≥ 4.5:1 in both themes.
 */
enum class FrostLevel {
    /** Cards, list rows, inputs, chips: the default. */
    Card,
    /** Floating bars (tab bar, top-bar buttons). Same scrim, stronger blur. */
    Bar,
    /** Sheets / drawers / overlays holding long text: a little denser. */
    Sheet,
}

/** What the frosted surfaces blur, published by [FrostHost]. */
@Immutable
class FrostEnv(
    val haze: HazeState?,
    /** The window the backdrop source lives in; other windows can't sample it. */
    val hostView: View?,
    /** The backdrop is the user's photo: denser scrim (see [FrostTokens.scrimAlpha]). */
    val photo: Boolean,
)

val LocalFrost = staticCompositionLocalOf { FrostEnv(null, null, false) }

object FrostTokens {
    private val DarkBase = Color(0xFF16181D)
    private val LightBase = Color(0xFFFBFAF8)

    fun base(dark: Boolean): Color = if (dark) DarkBase else LightBase

    /**
     * Scrim alpha over the blurred backdrop.
     *
     * [fork/glass-depth] Airier than the old 0.62/0.72 wash: the tint now only
     * has to keep text legible, while the sheen + rim (see [sheen], [rim]) do
     * the "this is a sheet of glass" reading. Sheets stay denser because they
     * hold long-form text; a user photo keeps the strongest scrim because its
     * detail is arbitrary (pure white / pure black can sit under the panel).
     */
    fun scrimAlpha(dark: Boolean, level: FrostLevel, photo: Boolean = false): Float {
        var a = when (level) {
            FrostLevel.Card, FrostLevel.Bar -> if (dark) 0.46f else 0.55f
            FrostLevel.Sheet -> if (dark) 0.60f else 0.66f
        }
        if (photo) a = maxOf(a, if (dark) 0.76f else 0.80f)
        return a
    }

    /** Flat tint when there is no blur (API < 31, other windows, no host). */
    fun fallbackAlpha(level: FrostLevel, photo: Boolean = false): Float = when {
        photo -> 0.94f
        level == FrostLevel.Sheet -> 0.93f
        else -> 0.86f
    }

    /** [fork/glass-depth] A wider kernel is what makes the backdrop read as "frosted", not "tinted". */
    fun blurRadius(level: FrostLevel): Dp = when (level) {
        FrostLevel.Card -> 30.dp
        FrostLevel.Bar -> 40.dp
        FrostLevel.Sheet -> 48.dp
    }

    /** The no-blur fill; also what [com.openminis.app.mibot.design.MibotTokens.card] returns. */
    fun flat(dark: Boolean, level: FrostLevel = FrostLevel.Card, photo: Boolean = false): Color =
        base(dark).copy(alpha = fallbackAlpha(level, photo))

    fun hairline(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.10f) else Color(0xFF1B1D22).copy(alpha = 0.10f)

    /**
     * Rim: a lit top-left edge falling to the hairline at the bottom-right.
     * A single alpha for the whole ring reads as a plain outline; the gradient
     * is what sells a bevelled pane under a light source.
     */
    fun rim(dark: Boolean): Brush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = if (dark) 0.30f else 0.92f),
            0.45f to Color.White.copy(alpha = if (dark) 0.10f else 0.28f),
            1f to hairline(dark),
        ),
    )

    /**
     * [fork/glass-depth] Specular sheen: a soft white wash from the top-left
     * corner, gone by the middle. Drawn behind content so it never tints text.
     * Dark glass gets a faint version; light glass needs more to separate a
     * near-white pane from a near-white page.
     */
    fun sheen(dark: Boolean): Brush = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.10f else 0.55f),
            Color.White.copy(alpha = if (dark) 0.025f else 0.16f),
            Color.Transparent,
        ),
    )

    /** Inner bottom shade: the pane has thickness, so its lower edge sits in shadow. */
    fun innerShade(dark: Boolean): Brush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.62f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (dark) 0.12f else 0.05f),
        ),
    )

    fun style(
        dark: Boolean,
        level: FrostLevel,
        photo: Boolean = false,
        accent: Color = Color.Transparent,
    ): HazeStyle {
        val tints = ArrayList<HazeTint>(2)
        tints += HazeTint(base(dark).copy(alpha = scrimAlpha(dark, level, photo)))
        // [fork/glass-depth] 2–3% of the active skin accent keeps the glass
        // tied to the theme instead of reading as neutral grey, without
        // turning the card into a coloured panel.
        if (accent.alpha > 0f) {
            tints += HazeTint(accent.copy(alpha = if (dark) 0.045f else 0.028f))
        }
        return HazeStyle(
            backgroundColor = base(dark),
            tints = tints,
            blurRadius = blurRadius(level),
            noiseFactor = if (dark) 0.045f else 0.055f,
            fallbackTint = HazeTint(flat(dark, level, photo)),
        )
    }
}

/** Dark when the Mibot night variant is active (else: the current scheme is dark). */
@Composable
@ReadOnlyComposable
fun frostIsDark(): Boolean =
    if (com.openminis.app.ui.theme.LocalMibotActive.current) LocalMibotDark.current
    else androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * Root host: creates the backdrop Haze state and publishes it. Put it once
 * around the activity content; [frostSource] goes on the backdrop itself.
 */
@Composable
fun FrostHost(enabled: Boolean = true, content: @Composable () -> Unit) {
    val haze = rememberHazeState()
    val view = LocalView.current
    val context = LocalContext.current
    val settings = remember(context) { GlassPrefs.flow(context) }.collectAsState().value
    val photo = settings.mode == BackdropMode.PHOTO && settings.photoPath != null
    val env = remember(haze, view, photo, enabled) { FrostEnv(if (enabled) haze else null, view, photo) }
    CompositionLocalProvider(LocalFrost provides env) { content() }
}

/** Registers this layer (the shared backdrop) as what frosted surfaces blur. */
@Composable
fun Modifier.frostSource(): Modifier {
    val haze = LocalFrost.current.haze ?: return this
    return this.hazeSource(haze)
}

/**
 * The frosted surface. Clips to [shape], blurs + tints (or flat-tints on the
 * fallback path), draws the hairline rim and, with [elevation] > 0, a soft
 * shadow. [haze] overrides the shared backdrop (e.g. the tab bar blurs the
 * page scrolling beneath it). [tint] lays an extra wash on top (selected chips).
 */
@Composable
fun Modifier.frosted(
    shape: Shape = RectangleShape,
    level: FrostLevel = FrostLevel.Card,
    border: Boolean = true,
    elevation: Dp = 0.dp,
    haze: HazeState? = null,
    tint: Color = Color.Transparent,
): Modifier {
    val dark = frostIsDark()
    val env = LocalFrost.current
    val view = LocalView.current
    val state = haze ?: env.haze?.takeIf { env.hostView == null || env.hostView === view }
    val blur = state != null && Build.VERSION.SDK_INT >= 31
    var m = this
    if (elevation > 0.dp) {
        // A wider, softer shadow than the old 2dp card lift. Dark glass needs
        // more black to detach from the page; light glass reads better with a
        // grey-blue shadow than with pure black.
        val c = if (dark) Color.Black.copy(alpha = 0.38f) else Color(0xFF2A2F3A).copy(alpha = 0.14f)
        m = m.shadow(elevation, shape, clip = false, ambientColor = c, spotColor = c)
    }
    m = m.clip(shape)
    m = if (blur) {
        m.hazeEffect(
            state = state,
            style = FrostTokens.style(
                dark = dark,
                level = level,
                photo = env.photo,
                accent = com.openminis.app.ui.theme.AppTokens.currentAccent,
            ),
        ) { blurEnabled = true }
    } else {
        m.background(FrostTokens.flat(dark, level, env.photo))
    }
    if (tint.alpha > 0f) m = m.background(tint)
    // [fork/glass-depth] Sheen + lower-edge shade sit above the tint but behind
    // the composable's own content, so text is never washed out.
    m = m.drawBehind {
        drawRect(FrostTokens.sheen(dark))
        drawRect(FrostTokens.innerShade(dark))
    }
    if (border) m = m.border(0.8.dp, FrostTokens.rim(dark), shape)
    return m
}

/** Box wrapper around [frosted] for call sites that want a component. */
@Composable
fun FrostedSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    level: FrostLevel = FrostLevel.Card,
    border: Boolean = true,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.frosted(shape, level, border, elevation), content = content)
}
