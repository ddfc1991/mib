package com.openminis.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Accent: iOS blue, desaturated. [T-android-accent-blue-parity]
//
// Was a teal (#2E8B8B / #4DD9D9) that predated iOS settling on blue. The hue
// now comes from iOS Assets.xcassets/AccentColor.colorset (sRGB components
// r0.212 g0.525 b0.933 -> #3686EE light, r0.329 g0.565 b0.894 -> #5490E4 dark),
// but iOS's saturation (84% / 73%) read as glaring on Android's darker
// surfaces, so SATURATION is dialled back ~30% with hue and lightness kept:
//   light  #3686EE  S84% L57%  ->  #528AD2  S59% L57%
//   dark   #5490E4  S73% L61%  ->  #6A94CE  S51% L61%
//
// Lightness is deliberately NOT raised, which is the other way to "lighten".
// It would have softened dark mode further but pushed light-mode contrast on
// white from 3.62 to 2.62 — below WCAG AA's 4.5 for text. Desaturating keeps
// dark mode at 5.93 (passing) and leaves light mode where it was.
//
// Names keep the `Teal` prefix only to avoid churning 90+ call sites; the
// value is the contract, not the name.
private val TealPrimary = AppTokens.light.accent
private val TealOnPrimary = AppTokens.light.onAccent
private val TealPrimaryContainer = Color(0xFFB2DFDB)
private val TealOnPrimaryContainer = Color(0xFF00332F)
private val TealSecondary = AppTokens.light.ink2
private val TealOnSecondary = AppTokens.light.onAccent
private val TealSecondaryContainer = AppTokens.light.surface2
private val TealOnSecondaryContainer = AppTokens.light.ink
private val TealTertiary = AppTokens.light.ink2
private val TealOnTertiary = AppTokens.light.onAccent
private val TealTertiaryContainer = AppTokens.light.surface2
private val TealOnTertiaryContainer = AppTokens.light.ink
private val TealBackground = AppTokens.light.bg
private val TealOnBackground = AppTokens.light.ink
private val TealSurface = AppTokens.light.surface
private val TealOnSurface = AppTokens.light.ink
private val TealSurfaceVariant = AppTokens.light.surface2
private val TealOnSurfaceVariant = AppTokens.light.ink2
private val TealOutline = AppTokens.light.border

private val TealDarkPrimary = AppTokens.dark.accent
private val TealDarkOnPrimary = AppTokens.dark.onAccent
private val TealDarkPrimaryContainer = Color(0xFF1A6B6B)
private val TealDarkOnPrimaryContainer = Color(0xFFB2DFDB)
private val TealDarkSecondary = AppTokens.dark.ink2
private val TealDarkOnSecondary = AppTokens.dark.onAccent
private val TealDarkSecondaryContainer = AppTokens.dark.surface2
private val TealDarkOnSecondaryContainer = AppTokens.dark.ink
private val TealDarkBackground = AppTokens.dark.bg
private val TealDarkOnBackground = AppTokens.dark.ink
private val TealDarkSurface = AppTokens.dark.surface
private val TealDarkOnSurface = AppTokens.dark.ink
private val TealDarkSurfaceVariant = AppTokens.dark.surface2
private val TealDarkOnSurfaceVariant = AppTokens.dark.ink2
private val TealDarkOutline = AppTokens.dark.border

// Neutral grouped-card surfaces (iOS-style system-grouped background).
// Override Material3's tonal `surfaceContainer*` so cards don't pick up the
// teal primary tint.
// Light: page = #F2F2F7 gray, card = white
// Dark:  page = #000, card = #1C1C1E
private val NeutralGroupedBg = AppTokens.light.bg
private val NeutralGroupedCard = AppTokens.light.surface
private val NeutralGroupedCardElevated = AppTokens.light.surface2
private val NeutralOutline = AppTokens.light.border

private val NeutralDarkGroupedBg = AppTokens.dark.bg
private val NeutralDarkGroupedCard = AppTokens.dark.surface
private val NeutralDarkGroupedCardElevated = AppTokens.dark.surface2
private val NeutralDarkOutline = AppTokens.dark.border

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    tertiary = TealTertiary,
    onTertiary = TealOnTertiary,
    tertiaryContainer = TealTertiaryContainer,
    onTertiaryContainer = TealOnTertiaryContainer,
    background = NeutralGroupedBg,
    onBackground = TealOnBackground,
    surface = NeutralGroupedBg,
    onSurface = TealOnSurface,
    surfaceVariant = NeutralGroupedCard,
    onSurfaceVariant = TealOnSurfaceVariant,
    surfaceContainerLowest = NeutralGroupedBg,
    surfaceContainerLow = NeutralGroupedCard,
    surfaceContainer = NeutralGroupedCard,
    surfaceContainerHigh = NeutralGroupedCardElevated,
    surfaceContainerHighest = NeutralGroupedCardElevated,
    outline = NeutralOutline,
    outlineVariant = NeutralOutline,
)

private val DarkColorScheme = darkColorScheme(
    primary = TealDarkPrimary,
    onPrimary = TealDarkOnPrimary,
    primaryContainer = TealDarkPrimaryContainer,
    onPrimaryContainer = TealDarkOnPrimaryContainer,
    secondary = TealDarkSecondary,
    onSecondary = TealDarkOnSecondary,
    secondaryContainer = TealDarkSecondaryContainer,
    onSecondaryContainer = TealDarkOnSecondaryContainer,
    background = NeutralDarkGroupedBg,
    onBackground = TealDarkOnBackground,
    surface = NeutralDarkGroupedBg,
    onSurface = TealDarkOnSurface,
    surfaceVariant = NeutralDarkGroupedCard,
    onSurfaceVariant = TealDarkOnSurfaceVariant,
    surfaceContainerLowest = NeutralDarkGroupedBg,
    surfaceContainerLow = NeutralDarkGroupedCard,
    surfaceContainer = NeutralDarkGroupedCard,
    surfaceContainerHigh = NeutralDarkGroupedCardElevated,
    surfaceContainerHighest = NeutralDarkGroupedCardElevated,
    outline = NeutralDarkOutline,
    outlineVariant = NeutralDarkOutline,
)

// App-wide FAB accent color (warm beige, matching iOS New Chat button).
// Reads from ChatPalette so it follows the in-app theme override (theme_mode pref),
// not android.isSystemInDarkTheme(), which only tracks the system setting.
@Composable
fun minisFabColor(): Color = LocalChatPalette.current.fabAccent

// [destiny/v7] Editorial Minimalism shape scale. The V7 design uses hairline
// dividers and flat sections instead of stacked rounded cards, so the global
// M3 shapes are pulled in — only sheets/dialogs keep a visible corner.
private val MinisShapes = Shapes(
    extraSmall = RoundedCornerShape(AppTokens.radiusXs),
    small = RoundedCornerShape(AppTokens.radiusSm),
    medium = RoundedCornerShape(AppTokens.radiusMd),
    large = RoundedCornerShape(AppTokens.radiusLg),
    extraLarge = RoundedCornerShape(AppTokens.radiusLg),
)

/**
 * [destiny/omega-skin] Material scheme derived from the user's skin.
 *
 * [fork/opencode-ui] A skin now overrides ONLY the accent. Background/surface/
 * text come from [AppTokens.of] using the effective `skin.isDark` (which
 * MinisTheme sets from the theme-mode preference). Reading them from the skin
 * object left HERMES's dark ink/surface in the light scheme, so MibotCard —
 * which reads AppTokens — painted a white card under white `onSurface` text.
 */
fun skinColorScheme(skin: Skin): androidx.compose.material3.ColorScheme {
    val t = AppTokens.of(skin.isDark)
    return (if (skin.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = skin.accent,
        onPrimary = skin.onAccent,
        primaryContainer = skin.accent.copy(alpha = 0.18f),
        onPrimaryContainer = t.ink,
        secondary = t.ink2,
        onSecondary = t.bg,
        tertiary = skin.accent,
        onTertiary = skin.onAccent,
        background = t.bg,
        onBackground = t.ink,
        surface = t.surface,
        onSurface = t.ink,
        surfaceVariant = t.surface2,
        onSurfaceVariant = t.ink2,
        outline = t.border.copy(alpha = 0.6f),
        outlineVariant = t.border,
        inverseSurface = t.ink,
        inverseOnSurface = t.bg,
    )
}

/** [destiny/omega-skin] Chat palette derived from the user's skin. */
fun skinChatPalette(skin: Skin): ChatPalette {
    val base = if (skin.isDark) DarkChatPalette else LightChatPalette
    val t = AppTokens.of(skin.isDark)
    return base.copy(
        isDark = skin.isDark,
        background = t.bg,
        secondaryBg = t.surface,
        inputBg = t.surface,
        inputIconBg = t.surface,
        primaryText = t.ink,
        secondaryText = t.ink2,
        tertiaryText = t.ink3,
        userBubble = skin.accent,
        toolBg = t.surface,
        toolCapsuleBg = t.surface,
        sendButton = skin.accent,
        link = skin.accent,
        thinking = skin.accent,
        sheetHeaderBg = t.surface,
        fabAccent = skin.accent,
    )
}

@Composable
fun MinisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    // [fork/mibot-v2] Mibot design system: translucent surfaces over the
    // shared sky backdrop, brick-red accent. False = the stock palette.
    mibot: Boolean = false,
    // [destiny/omega-skin] The selected skin drives EVERY page (Hermes frame,
    // Mibot component pages, chat) so the whole app reads as one product.
    skin: Skin = SkinStore.current.value,
    content: @Composable () -> Unit,
) {
    // 明暗由主题模式唯一决定；皮肤只贡献强调色。
    // 旧实现里皮肤自带 isDark，会出现「主题模式=浅色但皮肤=深色」时
    // MaterialTheme 与页面底色互相打架的情况。
    val effectiveSkin = skin.copy(isDark = darkTheme)
    val colorScheme = when {
        mibot -> skinColorScheme(effectiveSkin)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val typography = if (mibot) mibotTypography(fontScale) else scaledTypography(fontScale)
    val chatPalette = when {
        mibot -> skinChatPalette(effectiveSkin)
        darkTheme -> DarkChatPalette
        else -> LightChatPalette
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MinisShapes,
        typography = typography,
    ) {
        // [fork/mibot-v4] Compact everywhere: the system font scale is still
        // honoured, but capped at 1.15x on Mibot screens so very large system
        // settings don't blow every page up to 18-20sp body text.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val capped = if (mibot && density.fontScale > MIBOT_MAX_FONT_SCALE)
            androidx.compose.ui.unit.Density(density.density, MIBOT_MAX_FONT_SCALE) else density
        CompositionLocalProvider(
            LocalChatPalette provides chatPalette,
            LocalMibotActive provides mibot,
            LocalMibotDark provides darkTheme,
            androidx.compose.ui.platform.LocalDensity provides capped,
            content = content,
        )
    }
}

/** [fork/mibot-v4] Upper bound for the system font scale on Mibot screens. */
const val MIBOT_MAX_FONT_SCALE = 1.15f

/** [fork/mibot-v2] True while the Mibot design system (shared backdrop) is active. */
val LocalMibotActive = androidx.compose.runtime.staticCompositionLocalOf { false }

/** [fork/mibot-v2] Whether the app is currently rendering its dark variant. */
val LocalMibotDark = androidx.compose.runtime.staticCompositionLocalOf { false }

// [destiny/v7] Native port of the V7 design source (mibot-v7.html).
// Editorial Minimalism: near-black #0A0A0B / ivory #F2F0EB / one copper
// accent #C8926A, hairline dividers, flat sections, no frosted cards.
// Light is warm porcelain #FAF9F5 with the copper pressed darker so text
// still clears WCAG AA. Semantic colours stay muted — a status is a dot plus
// a sentence, never a coloured badge.
object MibotPalette {
    val AccentLight = AppTokens.light.accent
    val AccentDark = AppTokens.dark.accent
    val InkLight = AppTokens.light.ink
    val InkMutedLight = AppTokens.light.ink2
    val InkDark = AppTokens.dark.ink
    val InkMutedDark = AppTokens.dark.ink2
    val Porcelain = AppTokens.light.bg
    val Graphite = AppTokens.dark.bg
    val HairlineDark = AppTokens.dark.border
    val HairlineLight = AppTokens.light.border
    val SuccessLight = AppTokens.light.success
    val WarnLight = AppTokens.light.warn
    val ErrorLight = AppTokens.light.danger
    val SuccessDark = AppTokens.dark.success
    val WarnDark = AppTokens.dark.warn
    val ErrorDark = AppTokens.dark.danger
}

val MibotLightColorScheme = lightColorScheme(
    primary = MibotPalette.AccentLight,
    onPrimary = AppTokens.light.onAccent,
    primaryContainer = AppTokens.light.surface2,
    onPrimaryContainer = MibotPalette.InkLight,
    secondary = MibotPalette.InkMutedLight,
    onSecondary = AppTokens.light.onAccent,
    secondaryContainer = AppTokens.light.surface2,
    onSecondaryContainer = MibotPalette.InkLight,
    tertiary = MibotPalette.InkMutedLight,
    onTertiary = AppTokens.light.onAccent,
    tertiaryContainer = AppTokens.light.surface2,
    onTertiaryContainer = MibotPalette.InkLight,
    background = MibotPalette.Porcelain,
    onBackground = MibotPalette.InkLight,
    surface = AppTokens.light.surface,
    onSurface = MibotPalette.InkLight,
    surfaceVariant = AppTokens.light.surface2,
    onSurfaceVariant = MibotPalette.InkMutedLight,
    surfaceContainerLowest = MibotPalette.Porcelain,
    surfaceContainerLow = AppTokens.light.surface,
    surfaceContainer = AppTokens.light.surface2,
    surfaceContainerHigh = AppTokens.light.surface2,
    surfaceContainerHighest = AppTokens.light.surface2,
    outline = MibotPalette.HairlineLight,
    outlineVariant = MibotPalette.HairlineLight,
    inverseSurface = MibotPalette.InkLight,
    inverseOnSurface = MibotPalette.Porcelain,
    error = MibotPalette.ErrorLight,
)

val MibotDarkColorScheme = darkColorScheme(
    primary = MibotPalette.AccentDark,
    onPrimary = AppTokens.dark.onAccent,
    primaryContainer = AppTokens.dark.surface2,
    onPrimaryContainer = MibotPalette.InkDark,
    secondary = MibotPalette.InkMutedDark,
    onSecondary = AppTokens.dark.onAccent,
    secondaryContainer = AppTokens.dark.surface2,
    onSecondaryContainer = MibotPalette.InkDark,
    tertiary = MibotPalette.InkMutedDark,
    onTertiary = AppTokens.dark.onAccent,
    tertiaryContainer = AppTokens.dark.surface2,
    onTertiaryContainer = MibotPalette.InkDark,
    background = MibotPalette.Graphite,
    onBackground = MibotPalette.InkDark,
    surface = AppTokens.dark.surface,
    onSurface = MibotPalette.InkDark,
    surfaceVariant = AppTokens.dark.surface2,
    onSurfaceVariant = MibotPalette.InkMutedDark,
    surfaceContainerLowest = MibotPalette.Graphite,
    surfaceContainerLow = AppTokens.dark.surface,
    surfaceContainer = AppTokens.dark.surface2,
    surfaceContainerHigh = AppTokens.dark.surface2,
    surfaceContainerHighest = AppTokens.dark.surface2,
    outline = MibotPalette.HairlineDark,
    outlineVariant = MibotPalette.HairlineDark,
    inverseSurface = MibotPalette.InkDark,
    inverseOnSurface = MibotPalette.Graphite,
    error = MibotPalette.ErrorDark,
)

// 统一别名：聊天页不再自带一套 iOS 蓝，跟随全局 token。
val HermesDarkColorScheme = MibotDarkColorScheme

/**
 * [T-multi-select-share] Re-themes a subtree light or dark regardless of the app's
 * own appearance, keeping the app's typography (font scale) and shapes. The chat
 * export uses it so a dark app can share a light page and the other way round:
 * every chat row reads its colours from MaterialTheme / LocalChatPalette.
 */
@Composable
fun MinisAppearance(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(
            LocalChatPalette provides if (darkTheme) DarkChatPalette else LightChatPalette,
            content = content,
        )
    }
}

private fun TextStyle.scale(factor: Float): TextStyle =
    if (factor == 1f) this else copy(fontSize = fontSize * factor)

/**
 * [T-fa-locale] Each paragraph takes its direction from its own text, but
 * stays left-aligned.
 *
 * The app keeps a left-to-right layout in every language (LocaleWrap.forceLtr),
 * and under an LTR layout Compose otherwise lays every paragraph out LTR too.
 * Persian text that mixes in Latin words ("کلیدهای API و ورود با OAuth") then
 * comes out in left-to-right word order, which a Persian reader reads
 * scrambled. Content direction fixes the word order; Left (not Start, which
 * would follow an RTL paragraph to the right edge) keeps every label where it
 * sits today. For left-to-right text both are what it already had.
 */
private fun TextStyle.bidi(): TextStyle =
    copy(textDirection = TextDirection.Content, textAlign = TextAlign.Left)

/**
 * [destiny/v7] Native port of the V7 type scale. Hierarchy comes from a wide
 * weight spread (29/700, 21/650, 16/600, 14/400, 10/650) rather than from
 * eleven nearly identical font sizes. The user's font-size setting still
 * scales it and MIBOT_MAX_FONT_SCALE still caps it.
 */
private fun mibotTypography(factor: Float): Typography {
    val base = Typography()
    fun TextStyle.m(size: Float, line: Float, weight: androidx.compose.ui.text.font.FontWeight) = copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp,
    ).scale(factor).bidi()
    val n = androidx.compose.ui.text.font.FontWeight.Normal
    val md = androidx.compose.ui.text.font.FontWeight.Medium
    val sb = androidx.compose.ui.text.font.FontWeight(650)
    val b = androidx.compose.ui.text.font.FontWeight.Bold
    return Typography(
        displayLarge = base.displayLarge.m(29f, 36f, b),
        displayMedium = base.displayMedium.m(26f, 33f, b),
        displaySmall = base.displaySmall.m(23f, 30f, sb),
        headlineLarge = base.headlineLarge.m(21f, 28f, sb),
        headlineMedium = base.headlineMedium.m(19f, 26f, sb),
        headlineSmall = base.headlineSmall.m(17f, 24f, sb),
        titleLarge = base.titleLarge.m(16f, 22f, md),
        titleMedium = base.titleMedium.m(14.5f, 20f, md),
        titleSmall = base.titleSmall.m(13.5f, 19f, md),
        bodyLarge = base.bodyLarge.m(14f, 20f, n),
        bodyMedium = base.bodyMedium.m(14f, 20f, n),
        bodySmall = base.bodySmall.m(12.5f, 17f, n),
        labelLarge = base.labelLarge.m(13f, 18f, md),
        labelMedium = base.labelMedium.m(11.5f, 16f, md),
        labelSmall = base.labelSmall.m(10f, 14f, sb),
    )
}

private fun scaledTypography(factor: Float): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.scale(factor).bidi(),
        displayMedium = base.displayMedium.scale(factor).bidi(),
        displaySmall = base.displaySmall.scale(factor).bidi(),
        headlineLarge = base.headlineLarge.scale(factor).bidi(),
        headlineMedium = base.headlineMedium.scale(factor).bidi(),
        headlineSmall = base.headlineSmall.scale(factor).bidi(),
        titleLarge = base.titleLarge.scale(factor).bidi(),
        titleMedium = base.titleMedium.scale(factor).bidi(),
        titleSmall = base.titleSmall.scale(factor).bidi(),
        bodyLarge = base.bodyLarge.scale(factor).bidi(),
        bodyMedium = base.bodyMedium.scale(factor).bidi(),
        bodySmall = base.bodySmall.scale(factor).bidi(),
        labelLarge = base.labelLarge.scale(factor).bidi(),
        labelMedium = base.labelMedium.scale(factor).bidi(),
        labelSmall = base.labelSmall.scale(factor).bidi(),
    )
}
