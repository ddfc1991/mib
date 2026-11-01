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
private val TealPrimary = Color(0xFF528AD2)
private val TealOnPrimary = Color(0xFFFFFFFF)
private val TealPrimaryContainer = Color(0xFFB2DFDB)
private val TealOnPrimaryContainer = Color(0xFF00332F)
private val TealSecondary = Color(0xFF4A6360)
private val TealOnSecondary = Color(0xFFFFFFFF)
private val TealSecondaryContainer = Color(0xFFCCE8E4)
private val TealOnSecondaryContainer = Color(0xFF05201D)
private val TealTertiary = Color(0xFF46617A)
private val TealOnTertiary = Color(0xFFFFFFFF)
private val TealTertiaryContainer = Color(0xFFCDE5FF)
private val TealOnTertiaryContainer = Color(0xFF001D32)
private val TealBackground = Color(0xFFF5FAFA)
private val TealOnBackground = Color(0xFF171D1C)
private val TealSurface = Color(0xFFF5FAFA)
private val TealOnSurface = Color(0xFF171D1C)
private val TealSurfaceVariant = Color(0xFFDAE5E2)
private val TealOnSurfaceVariant = Color(0xFF3F4947)
private val TealOutline = Color(0xFF6F7977)

private val TealDarkPrimary = Color(0xFF6A94CE)
private val TealDarkOnPrimary = Color(0xFF003737)
private val TealDarkPrimaryContainer = Color(0xFF1A6B6B)
private val TealDarkOnPrimaryContainer = Color(0xFFB2DFDB)
private val TealDarkSecondary = Color(0xFFB1CCC8)
private val TealDarkOnSecondary = Color(0xFF1C3532)
private val TealDarkSecondaryContainer = Color(0xFF334B48)
private val TealDarkOnSecondaryContainer = Color(0xFFCCE8E4)
private val TealDarkBackground = Color(0xFF0E1514)
private val TealDarkOnBackground = Color(0xFFDEE4E2)
private val TealDarkSurface = Color(0xFF0E1514)
private val TealDarkOnSurface = Color(0xFFDEE4E2)
private val TealDarkSurfaceVariant = Color(0xFF3F4947)
private val TealDarkOnSurfaceVariant = Color(0xFFBEC9C6)
private val TealDarkOutline = Color(0xFF899390)

// Neutral grouped-card surfaces (iOS-style system-grouped background).
// Override Material3's tonal `surfaceContainer*` so cards don't pick up the
// teal primary tint.
// Light: page = #F2F2F7 gray, card = white
// Dark:  page = #000, card = #1C1C1E
private val NeutralGroupedBg = Color(0xFFF2F2F7)
private val NeutralGroupedCard = Color(0xFFFFFFFF)
private val NeutralGroupedCardElevated = Color(0xFFF7F7FA)
private val NeutralOutline = Color(0xFFD1D1D6)

private val NeutralDarkGroupedBg = Color(0xFF000000)
private val NeutralDarkGroupedCard = Color(0xFF1C1C1E)
private val NeutralDarkGroupedCardElevated = Color(0xFF2C2C2E)
private val NeutralDarkOutline = Color(0xFF38383A)

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

// App-wide shape system — larger corners for a modern, friendly feel
// DropdownMenu uses extraSmall, Dialog uses extraLarge, BottomSheet uses extraLarge
private val MinisShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),   // DropdownMenu, Tooltip, OutlinedTextField default
    small = RoundedCornerShape(12.dp),        // Chip, TextField
    medium = RoundedCornerShape(20.dp),       // Card, Snackbar
    large = RoundedCornerShape(24.dp),        // NavigationDrawer
    extraLarge = RoundedCornerShape(28.dp),   // Dialog, BottomSheet
)

@Composable
fun MinisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    // [fork/mibot-v2] Mibot design system: translucent surfaces over the
    // shared sky backdrop, brick-red accent. False = the stock palette.
    mibot: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        mibot && darkTheme -> MibotDarkColorScheme
        mibot -> MibotLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val typography = if (mibot) mibotTypography(fontScale) else scaledTypography(fontScale)
    val chatPalette = when {
        mibot && darkTheme -> MibotDarkChatPalette
        mibot -> MibotLightChatPalette
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

// [fork/mibot-v5] Mibot palettes — refined, restrained system.
// Dark = neutral cool graphite (#0E1014 base, layered glass at 6–10 % white);
// light = warm porcelain. ONE accent (deep cinnabar), used sparingly; every
// other role is neutral. Contrast (WCAG AA, checked on the frosted card fill):
//   light accent #AE4530 on card 5.5, white on accent 5.7, on tonal 4.8
//   dark  accent #E07E66 on glass 5.5, #1A0F0B on accent 6.6
//   onSurfaceVariant: light #5C606A 6.1, dark #9EA3AD 6.2
// `background` stays transparent so every stock Scaffold shows the shared
// backdrop; cards/sheets are frosted and dialogs stay opaque.
object MibotPalette {
    val AccentLight = Color(0xFFAE4530)
    val AccentDark = Color(0xFFE07E66)
    val InkLight = Color(0xFF1B1D22)
    val InkMutedLight = Color(0xFF5C606A)
    val InkDark = Color(0xFFE8EAEE)
    val InkMutedDark = Color(0xFF9EA3AD)
    val Porcelain = Color(0xFFF3F0EA)
    val Graphite = Color(0xFF0E1014)
    // Muted semantic colours (text-safe on the card fill in each theme).
    val SuccessLight = Color(0xFF456E53)
    val WarnLight = Color(0xFF87591A)
    val ErrorLight = Color(0xFFA8404C)
    val SuccessDark = Color(0xFF8FBE9D)
    val WarnDark = Color(0xFFDDB067)
    val ErrorDark = Color(0xFFEC939C)
}

val MibotLightColorScheme = lightColorScheme(
    primary = MibotPalette.AccentLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF1E3DD),
    onPrimaryContainer = Color(0xFF3E160D),
    secondary = Color(0xFF5E6470),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E6E1),
    onSecondaryContainer = MibotPalette.InkLight,
    tertiary = Color(0xFF4F6577),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2E7EC),
    onTertiaryContainer = Color(0xFF17232D),
    background = Color.Transparent,
    onBackground = MibotPalette.InkLight,
    surface = Color(0x8CFFFFFF),
    onSurface = MibotPalette.InkLight,
    surfaceVariant = Color(0xD9FFFFFF),
    onSurfaceVariant = MibotPalette.InkMutedLight,
    surfaceContainerLowest = Color(0x99FFFFFF),
    surfaceContainerLow = Color(0xE0FCFBF9),
    surfaceContainer = Color(0xEDFAF9F6),
    surfaceContainerHigh = Color(0xF5F7F5F1),
    surfaceContainerHighest = Color(0xFFF1EEE9),
    outline = Color(0xFF9EA1A8),
    outlineVariant = Color(0x2E1B1D22),
    inverseSurface = Color(0xFF26282D),
    inverseOnSurface = Color(0xFFF1EFEB),
    error = MibotPalette.ErrorLight,
)

val MibotDarkColorScheme = darkColorScheme(
    primary = MibotPalette.AccentDark,
    onPrimary = Color(0xFF1A0F0B),
    primaryContainer = Color(0xFF3A2620),
    onPrimaryContainer = Color(0xFFF6DDD5),
    secondary = Color(0xFFB4B9C2),
    onSecondary = Color(0xFF1A1C21),
    secondaryContainer = Color(0xFF262930),
    onSecondaryContainer = MibotPalette.InkDark,
    tertiary = Color(0xFFA9BCCB),
    onTertiary = Color(0xFF15202A),
    tertiaryContainer = Color(0xFF243039),
    onTertiaryContainer = Color(0xFFDCE5EC),
    background = Color.Transparent,
    onBackground = MibotPalette.InkDark,
    surface = Color(0x8C0E1014),
    onSurface = MibotPalette.InkDark,
    surfaceVariant = Color(0xCC1A1C21),
    onSurfaceVariant = MibotPalette.InkMutedDark,
    surfaceContainerLowest = Color(0x990B0C10),
    surfaceContainerLow = Color(0xE0181A1F),
    surfaceContainer = Color(0xEB1B1D22),
    surfaceContainerHigh = Color(0xF2212328),
    surfaceContainerHighest = Color(0xFF282A30),
    outline = Color(0xFF7E838D),
    outlineVariant = Color(0x29FFFFFF),
    inverseSurface = MibotPalette.InkDark,
    inverseOnSurface = Color(0xFF1A1C21),
    error = MibotPalette.ErrorDark,
)

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
 * [fork/mibot-v3] Compact Mibot type scale (see MibotType): body 14–14.5sp,
 * secondary 12–12.5sp, titles 15–18sp, headlines 20–24sp. One family (system
 * sans → Roboto / Noto Sans CJK SC), weights 400/500/600. The user's font-size
 * setting still scales it.
 */
private fun mibotTypography(factor: Float): Typography {
    val base = Typography()
    fun TextStyle.m(size: Float, line: Float, weight: androidx.compose.ui.text.font.FontWeight) = copy(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
        fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp,
    ).scale(factor).bidi()
    val n = androidx.compose.ui.text.font.FontWeight.Normal
    val md = androidx.compose.ui.text.font.FontWeight.Medium
    val sb = androidx.compose.ui.text.font.FontWeight.SemiBold
    return Typography(
        displayLarge = base.displayLarge.m(34f, 40f, n),
        displayMedium = base.displayMedium.m(30f, 36f, n),
        displaySmall = base.displaySmall.m(26f, 32f, n),
        headlineLarge = base.headlineLarge.m(24f, 30f, sb),
        headlineMedium = base.headlineMedium.m(22f, 28f, sb),
        headlineSmall = base.headlineSmall.m(20f, 26f, sb),
        titleLarge = base.titleLarge.m(17f, 23f, sb),
        titleMedium = base.titleMedium.m(14.5f, 20f, md),
        titleSmall = base.titleSmall.m(13.5f, 19f, md),
        bodyLarge = base.bodyLarge.m(14f, 20f, n),
        bodyMedium = base.bodyMedium.m(14f, 20f, n),
        bodySmall = base.bodySmall.m(12f, 17f, n),
        labelLarge = base.labelLarge.m(13.5f, 18f, md),
        labelMedium = base.labelMedium.m(12f, 16f, md),
        labelSmall = base.labelSmall.m(11f, 15f, md),
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
