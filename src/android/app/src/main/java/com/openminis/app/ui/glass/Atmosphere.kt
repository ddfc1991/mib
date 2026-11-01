package com.openminis.app.ui.glass

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * [fork/mibot-v5] Atmospheric backdrop — replaces the blobby cloud/fog sky.
 *
 * Design: content readability first. A soft vertical sky gradient whose hue
 * follows the time of day (dawn / day / dusk / night) inside the theme's own
 * tonal range (warm porcelain in light, cool graphite in dark), one faint
 * glow, and a VERY low-contrast volumetric haze. Weather is a restrained
 * overlay: thin sparse rain streaks, tiny soft snow dots, faint twinkling
 * stars at night, and overcast = desaturation. Nothing about the weather is
 * ever displayed as data.
 *
 * Rendering:
 * - API 33+: AGSL RuntimeShader — domain-warped fBm haze drifting slowly.
 * - API 30–32: the same picture pre-rendered ONCE per palette into a small
 *   bitmap (box-blurred) and upscaled bilinearly = a soft, static gradient.
 * - Clock: 30 fps cap (FULL), ~8 fps at 0.35x speed (REDUCED), none (OFF).
 *   It only ticks while the host is RESUMED (hidden / screen off = paused)
 *   and while battery saver is off and system animations are enabled
 *   (rememberAmbientTime / ambientMotionAllowed).
 */

/** User setting 动态背景: 开 / 降低动态 / 关. */
enum class BackdropMotion { FULL, REDUCED, OFF }

data class Atmos(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val haze: Color,
    /** 0..1 haze mix strength (kept ≤ ~0.16 so it stays behind the glass). */
    val hazeAmount: Float,
    val glow: Color,
    val glowX: Float,
    val glowY: Float,
    val glowAmount: Float,
    val stars: Float,
    val rain: Float,
    val snow: Float,
    val dark: Boolean,
)

object AtmospherePalette {
    private fun base(dark: Boolean, phase: DayPhase): Atmos = if (dark) when (phase) {
        DayPhase.DAWN -> Atmos(Color(0xFF0E1014), Color(0xFF15171C), Color(0xFF1E1B1D), Color(0xFF8C96A6), 0.06f,
            Color(0xFF3E2C29), 0.18f, 0.92f, 0.55f, 0f, 0f, 0f, true)
        DayPhase.DAY -> Atmos(Color(0xFF12151A), Color(0xFF161A20), Color(0xFF1B1E23), Color(0xFF8C96A6), 0.06f,
            Color(0xFF2B3542), 0.82f, 0.05f, 0.45f, 0f, 0f, 0f, true)
        DayPhase.DUSK -> Atmos(Color(0xFF0F1116), Color(0xFF16171C), Color(0xFF221C1B), Color(0xFF8C96A6), 0.06f,
            Color(0xFF3D2723), 0.85f, 0.95f, 0.55f, 0.35f, 0f, 0f, true)
        DayPhase.NIGHT -> Atmos(Color(0xFF0A0C10), Color(0xFF0E1014), Color(0xFF14171C), Color(0xFF7F8A9B), 0.055f,
            Color(0xFF1D2634), 0.85f, 0.04f, 0.6f, 1f, 0f, 0f, true)
    } else when (phase) {
        DayPhase.DAWN -> Atmos(Color(0xFFE9E5E3), Color(0xFFF1ECE7), Color(0xFFF6EFE6), Color.White, 0.12f,
            Color(0xFFF2D8C6), 0.12f, 0.9f, 0.45f, 0f, 0f, 0f, false)
        DayPhase.DAY -> Atmos(Color(0xFFE3E8EC), Color(0xFFEEEEEB), Color(0xFFF5F1EA), Color.White, 0.12f,
            Color(0xFFFFFFFF), 0.85f, 0.04f, 0.5f, 0f, 0f, 0f, false)
        DayPhase.DUSK -> Atmos(Color(0xFFE5E1E4), Color(0xFFEFE8E3), Color(0xFFF4E6DA), Color.White, 0.12f,
            Color(0xFFEFCFBA), 0.88f, 0.92f, 0.45f, 0f, 0f, 0f, false)
        DayPhase.NIGHT -> Atmos(Color(0xFFDEE1E6), Color(0xFFE9E9E8), Color(0xFFF1EDE7), Color.White, 0.10f,
            Color(0xFFE8ECF2), 0.85f, 0.05f, 0.35f, 0f, 0f, 0f, false)
    }

    private fun desat(c: Color, k: Float): Color {
        val l = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        return AmbientPalette.lerp(c, Color(l, l, l), k)
    }

    private fun Atmos.desaturated(k: Float): Atmos = copy(
        top = desat(top, k), mid = desat(mid, k), bottom = desat(bottom, k), glow = desat(glow, k),
        glowAmount = glowAmount * (1f - 0.6f * k),
    )

    fun of(dark: Boolean, hour: Int, kind: WeatherKind): Atmos {
        val b = base(dark, AmbientPalette.phaseFor(hour))
        return when (kind) {
            WeatherKind.CLOUDY -> b.desaturated(0.6f).copy(hazeAmount = b.hazeAmount + 0.02f, stars = b.stars * 0.3f)
            WeatherKind.FOG -> b.desaturated(0.4f).copy(hazeAmount = if (dark) 0.10f else 0.16f, stars = b.stars * 0.15f)
            WeatherKind.RAIN -> b.desaturated(0.5f).copy(rain = 1f, stars = 0f)
            WeatherKind.SNOW -> b.desaturated(0.3f).copy(snow = 1f, stars = b.stars * 0.3f)
            WeatherKind.STORM -> b.desaturated(0.7f).copy(rain = 1.4f, stars = 0f,
                top = AmbientPalette.lerp(b.top, if (dark) Color.Black else Color(0xFFCDD1D6), 0.25f))
            else -> b
        }
    }
}

private fun Color.f3() = floatArrayOf(red, green, blue)

private const val ATMOS_AGSL = """
uniform float2 res;
uniform float t;
uniform float3 top;
uniform float3 mid;
uniform float3 bottom;
uniform float3 haze;
uniform float hazeAmt;
uniform float3 glow;
uniform float2 glowPos;
uniform float glowAmt;
uniform float hazeOnly;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}
half4 main(float2 xy) {
    float2 uv = xy / res;
    float aspect = res.x / res.y;
    float y = uv.y;
    float3 col = y < 0.55 ? mix(top, mid, y / 0.55) : mix(mid, bottom, (y - 0.55) / 0.45);
    float2 d = (uv - glowPos) * float2(aspect, 1.0);
    col = mix(col, glow, glowAmt * exp(-dot(d, d) * 2.6));
    // Volumetric haze: domain-warped fBm, drifting very slowly.
    float2 p = float2(uv.x * aspect, uv.y) * 1.5;
    float2 q = float2(fbm(p + float2(t * 0.010, 0.0)), fbm(p + float2(5.2, 1.3) - float2(0.0, t * 0.006)));
    float n = fbm(p + 1.7 * q + float2(t * 0.004, 0.0));
    float h = smoothstep(0.38, 0.82, n) * (0.55 + 0.45 * smoothstep(0.1, 1.0, y));
    float dither = (hash(xy) - 0.5) / 255.0;
    if (hazeOnly > 0.5) {
        float a = clamp(h * hazeAmt * 1.6, 0.0, 1.0);
        return half4(half3(haze * a), half(a));
    }
    col = mix(col, haze, h * hazeAmt) + dither;
    return half4(half3(col), 1.0);
}
"""

/**
 * The Mibot backdrop. [hazeOnly] draws just the transparent haze layer (the
 * optional "haze over your photo" overlay).
 */
@Composable
fun AtmosphereBackdrop(atmos: Atmos, motion: BackdropMotion, modifier: Modifier = Modifier, hazeOnly: Boolean = false) {
    val time = when (motion) {
        BackdropMotion.FULL -> rememberAmbientTime(33L)
        BackdropMotion.REDUCED -> rememberAmbientTime(125L) * 0.35f
        // A fixed, wall-clock-seeded frame: still varies between launches.
        BackdropMotion.OFF -> remember { ((System.currentTimeMillis() / 1000L) % 3600L).toFloat() }
    }
    Box(modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ShaderAtmosphere(atmos, time, hazeOnly)
        } else {
            BakedAtmosphere(atmos, hazeOnly)
        }
        if (!hazeOnly && motion != BackdropMotion.OFF) WeatherOverlay(atmos, time, animate = true)
        else if (!hazeOnly && atmos.stars > 0.01f) WeatherOverlay(atmos.copy(rain = 0f, snow = 0f), time, animate = false)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderAtmosphere(a: Atmos, time: Float, hazeOnly: Boolean) {
    val shader = remember { RuntimeShader(ATMOS_AGSL) }
    Canvas(Modifier.fillMaxSize()) {
        shader.setFloatUniform("res", size.width, size.height)
        shader.setFloatUniform("t", time)
        shader.setFloatUniform("top", a.top.f3())
        shader.setFloatUniform("mid", a.mid.f3())
        shader.setFloatUniform("bottom", a.bottom.f3())
        shader.setFloatUniform("haze", a.haze.f3())
        shader.setFloatUniform("hazeAmt", a.hazeAmount)
        shader.setFloatUniform("glow", a.glow.f3())
        shader.setFloatUniform("glowPos", a.glowX, a.glowY)
        shader.setFloatUniform("glowAmt", a.glowAmount)
        shader.setFloatUniform("hazeOnly", if (hazeOnly) 1f else 0f)
        drawRect(ShaderBrush(shader))
    }
}

// ------------------------------------------------------------- API 30–32

@Composable
private fun BakedAtmosphere(a: Atmos, hazeOnly: Boolean) {
    val bmp by produceState<ImageBitmap?>(null, a, hazeOnly) {
        value = withContext(Dispatchers.Default) { runCatching { AtmosphereBaker.bake(a, hazeOnly) }.getOrNull() }
    }
    Canvas(Modifier.fillMaxSize()) {
        val b = bmp
        if (b == null) {
            if (!hazeOnly) drawRect(Brush.verticalGradient(0f to a.top, 0.55f to a.mid, 1f to a.bottom))
            return@Canvas
        }
        drawImage(
            image = b,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(b.width, b.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Low, // bilinear upscale of a blurred bake = soft
        )
    }
}

/** CPU twin of the shader at low resolution, box-blurred once. Pure, cached per palette. */
internal object AtmosphereBaker {
    private const val W = 72
    private const val H = 128

    private fun hash(ix: Int, iy: Int): Float {
        var h = ix * 374761393 + iy * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
    }

    private fun vnoise(x: Float, y: Float): Float {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        val fx = x - ix; val fy = y - iy
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        val a = hash(ix, iy); val b = hash(ix + 1, iy); val c = hash(ix, iy + 1); val d = hash(ix + 1, iy + 1)
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy
    }

    private fun fbm(x0: Float, y0: Float): Float {
        var x = x0; var y = y0; var v = 0f; var amp = 0.5f
        repeat(4) { v += amp * vnoise(x, y); x = x * 2.03f + 17.1f; y = y * 2.03f + 9.2f; amp *= 0.5f }
        return v
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t)
    }

    fun bake(a: Atmos, hazeOnly: Boolean): ImageBitmap {
        val r = FloatArray(W * H); val g = FloatArray(W * H); val bl = FloatArray(W * H); val al = FloatArray(W * H)
        val aspect = W.toFloat() / H
        for (j in 0 until H) for (i in 0 until W) {
            val u = (i + 0.5f) / W; val v = (j + 0.5f) / H
            val c0: Color; val c1: Color; val k: Float
            if (v < 0.55f) { c0 = a.top; c1 = a.mid; k = v / 0.55f } else { c0 = a.mid; c1 = a.bottom; k = (v - 0.55f) / 0.45f }
            var cr = c0.red + (c1.red - c0.red) * k
            var cg = c0.green + (c1.green - c0.green) * k
            var cb = c0.blue + (c1.blue - c0.blue) * k
            val dx = (u - a.glowX) * aspect; val dy = v - a.glowY
            val gk = a.glowAmount * exp(-(dx * dx + dy * dy) * 2.6f)
            cr += (a.glow.red - cr) * gk; cg += (a.glow.green - cg) * gk; cb += (a.glow.blue - cb) * gk
            val px = u * aspect * 1.5f; val py = v * 1.5f
            val qx = fbm(px, py); val qy = fbm(px + 5.2f, py + 1.3f)
            val n = fbm(px + 1.7f * qx, py + 1.7f * qy)
            val h = smooth(0.38f, 0.82f, n) * (0.55f + 0.45f * smooth(0.1f, 1f, v))
            val idx = j * W + i
            if (hazeOnly) {
                val alpha = (h * a.hazeAmount * 1.6f).coerceIn(0f, 1f)
                r[idx] = a.haze.red; g[idx] = a.haze.green; bl[idx] = a.haze.blue; al[idx] = alpha
            } else {
                val m = h * a.hazeAmount
                r[idx] = cr + (a.haze.red - cr) * m; g[idx] = cg + (a.haze.green - cg) * m; bl[idx] = cb + (a.haze.blue - cb) * m; al[idx] = 1f
            }
        }
        val rr = blur(blur(r)); val gg = blur(blur(g)); val bb = blur(blur(bl)); val aa = blur(blur(al))
        val px = IntArray(W * H) { idx ->
            val alpha = (aa[idx].coerceIn(0f, 1f) * 255).roundToInt()
            val red = (rr[idx].coerceIn(0f, 1f) * 255).roundToInt()
            val green = (gg[idx].coerceIn(0f, 1f) * 255).roundToInt()
            val blue = (bb[idx].coerceIn(0f, 1f) * 255).roundToInt()
            (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
        return Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** 3x3 box blur, clamped edges. */
    private fun blur(src: FloatArray): FloatArray {
        val out = FloatArray(src.size)
        for (j in 0 until H) for (i in 0 until W) {
            var s = 0f; var n = 0
            for (dj in -1..1) for (di in -1..1) {
                val x = (i + di).coerceIn(0, W - 1); val y = (j + dj).coerceIn(0, H - 1)
                s += src[y * W + x]; n++
            }
            out[j * W + i] = s / n
        }
        return out
    }
}

// ---------------------------------------------------------- weather overlay

/** Deterministic particle seeds: x, phase, speed, size (all 0..1). */
private val PARTICLES: List<FloatArray> = run {
    var s = 0x2545F491
    fun rnd(): Float { s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5); return ((s ushr 8) and 0xFFFF) / 65535f }
    List(80) { floatArrayOf(rnd(), rnd(), rnd(), rnd()) }
}

@Composable
private fun WeatherOverlay(a: Atmos, time: Float, animate: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        if (a.stars > 0.01f) drawStars(a, time, animate)
        if (a.rain > 0.01f) drawRain(a, time)
        if (a.snow > 0.01f) drawSnow(a, time)
    }
}

private fun DrawScope.drawStars(a: Atmos, time: Float, animate: Boolean) {
    val n = 60
    for (i in 0 until n) {
        val p = PARTICLES[i]
        val y = p[1] * p[1] * 0.62f // denser toward the top
        val tw = if (animate) 0.6f + 0.4f * sin(time * (0.6f + p[2] * 1.4f) + p[3] * 6.28f) else 0.8f
        val alpha = (a.stars * tw * (0.18f + 0.32f * p[3]) * (1f - y)).coerceIn(0f, 0.5f)
        drawCircle(Color.White.copy(alpha = alpha), radius = (0.5f + p[3] * 0.8f) * density, center = Offset(p[0] * size.width, y * size.height))
    }
}

private fun DrawScope.drawRain(a: Atmos, time: Float) {
    val count = (24 * a.rain).roundToInt().coerceIn(0, 40)
    val c = if (a.dark) Color(0xFFB8C2D0).copy(alpha = 0.10f) else Color(0xFF5C6670).copy(alpha = 0.10f)
    val len = 22f * density
    for (i in 0 until count) {
        val p = PARTICLES[i]
        val speed = 0.35f + p[2] * 0.25f // screen heights per second
        val y = (((p[1] + time * speed) % 1.15f) - 0.08f) * size.height
        val x = p[0] * size.width
        val l = len * (0.7f + p[3] * 0.6f)
        drawLine(c, Offset(x, y), Offset(x - l * 0.12f, y + l), strokeWidth = 0.8f * density, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSnow(a: Atmos, time: Float) {
    val count = (36 * a.snow).roundToInt().coerceIn(0, 60)
    val c = if (a.dark) Color.White.copy(alpha = 0.30f) else Color(0xFFAEB6BF).copy(alpha = 0.40f)
    for (i in 0 until count) {
        val p = PARTICLES[(i + 20) % PARTICLES.size]
        val speed = 0.025f + p[2] * 0.03f
        val y = (((p[1] + time * speed) % 1.1f) - 0.05f) * size.height
        val x = (p[0] + 0.015f * sin(time * 0.5f + p[3] * 6.28f)) * size.width
        drawCircle(c, radius = (0.9f + p[3] * 1.3f) * density, center = Offset(x, y))
    }
}
