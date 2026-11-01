package com.openminis.app.ui.glass

import androidx.compose.ui.graphics.Color

/** Coarse weather buckets; only used to tint the ambient backdrop, never shown. */
enum class WeatherKind { UNKNOWN, CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

enum class DayPhase { DAWN, DAY, DUSK, NIGHT }

/**
 * [fork/glass-ui] Pure palette logic for the ambient backdrop: a time-of-day
 * base palette, nudged toward the current weather. Kept free of Android types
 * (except Color) so it is trivially unit-testable.
 */
object AmbientPalette {

    fun phaseFor(hourOfDay: Int): DayPhase = when (hourOfDay) {
        in 5..7 -> DayPhase.DAWN
        in 8..16 -> DayPhase.DAY
        in 17..19 -> DayPhase.DUSK
        else -> DayPhase.NIGHT
    }

    /** WMO weather interpretation code (Open-Meteo `weather_code`) -> bucket. */
    fun kindForWmo(code: Int): WeatherKind = when (code) {
        0, 1 -> WeatherKind.CLEAR
        2, 3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        in 51..67, in 80..82 -> WeatherKind.RAIN
        in 71..77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.STORM
        else -> WeatherKind.UNKNOWN
    }

    private fun base(phase: DayPhase): List<Color> = when (phase) {
        DayPhase.DAWN -> listOf(Color(0xFFFFB199), Color(0xFFF7C8E0), Color(0xFF9FB8F5), Color(0xFFFFE3B3))
        DayPhase.DAY -> listOf(Color(0xFF7FC8F8), Color(0xFFB4E1FF), Color(0xFFA0E7E5), Color(0xFFE6F0FF))
        DayPhase.DUSK -> listOf(Color(0xFFFF8C69), Color(0xFFB06AB3), Color(0xFF4568DC), Color(0xFFFFC371))
        DayPhase.NIGHT -> listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF3A2D6B), Color(0xFF0F2027))
    }

    /** Colour the weather pulls the palette toward, and how hard. */
    private fun weatherPull(kind: WeatherKind): Pair<Color, Float>? = when (kind) {
        WeatherKind.CLOUDY -> Color(0xFF9AA5B1) to 0.35f
        WeatherKind.FOG -> Color(0xFFC9CED6) to 0.5f
        WeatherKind.RAIN -> Color(0xFF4B6584) to 0.45f
        WeatherKind.SNOW -> Color(0xFFE8F1FA) to 0.5f
        WeatherKind.STORM -> Color(0xFF2C3A47) to 0.6f
        WeatherKind.CLEAR, WeatherKind.UNKNOWN -> null
    }

    fun colors(phase: DayPhase, kind: WeatherKind): List<Color> {
        val pull = weatherPull(kind) ?: return base(phase)
        return base(phase).map { lerp(it, pull.first, pull.second) }
    }

    internal fun lerp(a: Color, b: Color, t: Float): Color = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )

    /**
     * Pull the time/weather palette toward the chosen glass style: A (dusk)
     * keeps it deep and moody, B (warm white) washes it out to a soft cream.
     */
    fun styled(colors: List<Color>, style: GlassStyle): List<Color> = when (style) {
        GlassStyle.DUSK -> colors.mapIndexed { i, c -> lerp(c, if (i % 2 == 0) Color(0xFF2A1F45) else Color(0xFF5A3A5E), 0.45f) }
        GlassStyle.WARM -> colors.map { lerp(it, Color(0xFFF7EFE4), 0.65f) }
    }

    /** Whether text over this palette should be light. */
    fun isDark(phase: DayPhase, kind: WeatherKind): Boolean =
        phase == DayPhase.NIGHT || kind == WeatherKind.STORM ||
            (phase == DayPhase.DUSK && kind == WeatherKind.RAIN)

    // ------------------------------------------------------------ v2 sky

    /**
     * [fork/mibot-v2] Cloud-sky backdrop. Light = soft daytime sky with white
     * clouds and drifting fog; dark = night sky with stars and blue-grey
     * clouds. The variant follows the app theme (so text contrast is always
     * right); the hour only warms the light sky at dawn/dusk, and the weather
     * only tints/thickens it — nothing about the weather is ever displayed.
     */
    fun sky(dark: Boolean, hour: Int, kind: WeatherKind): SkyColors {
        val phase = phaseFor(hour)
        // [fork/mibot-v3] Hazy pastel sky (blue-grey top, warm cream below) with
        // white / warm-white cloud wisps; night = deep blue with blue-grey mist.
        var s = if (dark) SkyColors(
            top = Color(0xFF080E1C), mid = Color(0xFF121B32), bottom = Color(0xFF222942),
            cloud = Color(0xFF3D4A68), fog = Color(0xFF4A5572), stars = 1f, cover = 0.45f, fogAmount = 0.45f,
        ) else SkyColors(
            top = Color(0xFF9CB9D6), mid = Color(0xFFC8D5E2), bottom = Color(0xFFE8E0D6),
            cloud = Color(0xFFF7F9FC), fog = Color(0xFFFFF3E8), stars = 0f, cover = 0.45f, fogAmount = 0.5f,
        )
        if (!dark && (phase == DayPhase.DAWN || phase == DayPhase.DUSK)) {
            val warm = Color(0xFFF2C7B0)
            s = s.copy(mid = lerp(s.mid, warm, 0.35f), bottom = lerp(s.bottom, Color(0xFFF6E3D6), 0.4f))
        }
        return when (kind) {
            WeatherKind.CLOUDY -> s.copy(top = lerp(s.top, s.fog, 0.35f), cover = s.cover + 0.25f, stars = s.stars * 0.4f)
            WeatherKind.FOG -> s.copy(top = lerp(s.top, s.fog, 0.5f), fogAmount = 0.9f, stars = s.stars * 0.2f)
            WeatherKind.RAIN -> s.copy(
                top = lerp(s.top, if (dark) Color(0xFF0B1220) else Color(0xFF8E9BAA), 0.5f),
                mid = lerp(s.mid, if (dark) Color(0xFF162030) else Color(0xFFB5C0CC), 0.4f),
                cloud = lerp(s.cloud, Color(0xFF9AA6B4), if (dark) 0.2f else 0.35f), cover = s.cover + 0.35f, stars = 0f,
            )
            WeatherKind.SNOW -> s.copy(top = lerp(s.top, Color(0xFFDCE6EF), if (dark) 0.1f else 0.45f), cover = s.cover + 0.3f, stars = s.stars * 0.3f)
            WeatherKind.STORM -> s.copy(
                top = lerp(s.top, Color(0xFF2C3A47), if (dark) 0.3f else 0.55f),
                cloud = lerp(s.cloud, Color(0xFF6B7684), 0.5f), cover = s.cover + 0.45f, stars = 0f,
            )
            else -> s
        }.let { it.copy(cover = it.cover.coerceIn(0f, 1f)) }
    }
}

data class SkyColors(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val cloud: Color,
    val fog: Color,
    /** 0..1 star visibility. */
    val stars: Float,
    /** 0..1 cloud cover. */
    val cover: Float,
    /** 0..1 fog strength. */
    val fogAmount: Float,
)
