package com.openminis.app.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * [fork/glass-ui] Ambient backdrop rendered as slowly drifting fog.
 *
 * - API 33+: an AGSL RuntimeShader, three fBm value-noise layers drifting
 *   sideways at different speeds (parallax) with a ~45 s "breathing" swell,
 *   tinted by the time-of-day / weather palette.
 * - Below 33: two tileable fog textures generated once at low resolution
 *   (bilinear upscaling makes them naturally soft, i.e. "pre-blurred") and
 *   translated sideways at different speeds.
 *
 * Cost control: frames are driven by a ~25 fps clock that only ticks while the
 * host is RESUMED (so backgrounded / screen off = stopped) and while motion is
 * allowed (battery saver off and system animations not disabled). When paused
 * the last frame stays on screen.
 */

private const val FRAME_MS = 40L          // ~25 fps
private const val BREATH_PERIOD_S = 45f   // fog density swell

/** False when battery saver is on or the user disabled animations (reduce motion). */
fun ambientMotionAllowed(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (pm?.isPowerSaveMode == true) return false
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale != 0f
}

/** Seconds of fog time; advances only while visible and motion is allowed. */
@Composable
private fun rememberFogTime(): Float = rememberAmbientTime(FRAME_MS)

/**
 * [fork/mibot-v3] Shared ambient clock. The cloud sky drifts so slowly that
 * ~15 fps is visually identical to 25 and costs less.
 */
@Composable
internal fun rememberAmbientTime(frameMs: Long = 66L): Float {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    // Seeded from the wall clock so a static frame still varies between visits.
    var t by remember { mutableFloatStateOf(((System.currentTimeMillis() / 1000L) % 3600L).toFloat()) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = System.nanoTime()
            var sinceCheck = 0L
            var allowed = ambientMotionAllowed(context)
            while (true) {
                delay(if (allowed) frameMs else 1_000L)
                val now = System.nanoTime()
                val dt = (now - last) / 1e9f
                last = now
                sinceCheck += (dt * 1000).toLong()
                if (sinceCheck >= 1_000L) {
                    allowed = ambientMotionAllowed(context)
                    sinceCheck = 0L
                }
                if (allowed) t = (t + dt.coerceAtMost(0.25f)) % 3600f
            }
        }
    }
    return t
}

@Composable
fun AmbientFog(colors: List<Color>, modifier: Modifier = Modifier) {
    val time = rememberFogTime()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderFog(colors, time, modifier)
    } else {
        BitmapFog(colors, time, modifier)
    }
}

// ---------------------------------------------------------------- API 33+

private const val FOG_AGSL = """
uniform float2 res;
uniform float t;
uniform float breath;
uniform float4 base0;
uniform float4 base1;
uniform float4 fog1;
uniform float4 fog2;

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
    float a = 0.55;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p);
        p = p * 2.07 + float2(13.1, 7.7);
        a *= 0.5;
    }
    return v;
}
half4 main(float2 xy) {
    float2 uv = xy / res.y;
    float y = clamp(xy.y / res.y, 0.0, 1.0);
    float3 col = mix(base0.rgb, base1.rgb, y);
    // Three layers, slow sideways drift at different speeds (parallax).
    float f1 = fbm(uv * 1.3 + float2(t * 0.012, t * 0.002));
    float f2 = fbm(uv * 2.1 + float2(-t * 0.020, 0.0) + 5.3);
    float f3 = fbm(uv * 3.4 + float2(t * 0.034, -t * 0.004) + 11.7);
    float m1 = smoothstep(0.30, 0.80, f1) * (0.80 + 0.20 * breath);
    float m2 = smoothstep(0.38, 0.88, f2) * (0.85 + 0.15 * (1.0 - breath));
    float m3 = smoothstep(0.45, 0.95, f3);
    col = mix(col, fog1.rgb, m1 * 0.60);
    col = mix(col, fog2.rgb, m2 * 0.45);
    col = mix(col, float3(1.0), m3 * 0.08);
    return half4(half3(col), 1.0);
}
"""

private fun Color.f4() = floatArrayOf(red, green, blue, 1f)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderFog(colors: List<Color>, time: Float, modifier: Modifier) {
    val shader = remember { RuntimeShader(FOG_AGSL) }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("res", size.width, size.height)
        shader.setFloatUniform("t", time)
        shader.setFloatUniform("breath", breath(time))
        shader.setFloatUniform("base0", colors[0].f4())
        shader.setFloatUniform("base1", colors[3].f4())
        shader.setFloatUniform("fog1", colors[1].f4())
        shader.setFloatUniform("fog2", colors[2].f4())
        // Fresh brush per frame so the updated uniforms are always picked up.
        drawRect(ShaderBrush(shader))
    }
}

private fun breath(t: Float): Float = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / BREATH_PERIOD_S)

// ---------------------------------------------------------------- < API 33

@Composable
private fun BitmapFog(colors: List<Color>, time: Float, modifier: Modifier) {
    val far = remember { FogTexture.generate(seed = 7, size = 96, cells = 3) }
    val near = remember { FogTexture.generate(seed = 23, size = 96, cells = 4) }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(colors[0], colors[3])))
        val b = breath(time)
        drawFogLayer(far, colors[1], alpha = 0.55f + 0.15f * b, speedPxPerS = size.width * 0.012f, time = time, scale = 1.6f)
        drawFogLayer(near, colors[2], alpha = 0.45f + 0.10f * (1f - b), speedPxPerS = -size.width * 0.022f, time = time, scale = 1.2f)
    }
}

private fun DrawScope.drawFogLayer(
    tex: ImageBitmap,
    tint: Color,
    alpha: Float,
    speedPxPerS: Float,
    time: Float,
    scale: Float,
) {
    // One tile covers the screen height * scale; tiles repeat horizontally.
    val tile = (size.maxDimension * scale).roundToInt().coerceAtLeast(1)
    val shift = ((speedPxPerS * time) % tile + tile) % tile
    val filter = ColorFilter.tint(tint, BlendMode.SrcIn)
    var x = -shift
    val top = ((size.height - tile) / 2f).roundToInt()
    while (x < size.width) {
        drawImage(
            image = tex,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(tex.width, tex.height),
            dstOffset = IntOffset(x.roundToInt(), top),
            dstSize = IntSize(tile, tile),
            alpha = alpha,
            colorFilter = filter,
            filterQuality = FilterQuality.Low, // bilinear: soft, cheap
        )
        x += tile
    }
}

/** Tileable value-noise fBm baked into a small alpha mask (white, alpha = fog). */
internal object FogTexture {
    fun generate(seed: Int, size: Int, cells: Int): ImageBitmap {
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            var v = 0f
            var amp = 0.55f
            var c = cells
            for (o in 0 until 3) {
                v += amp * periodicNoise(x * c / size.toFloat(), y * c / size.toFloat(), c, seed + o * 31)
                amp *= 0.5f
                c *= 2
            }
            val a = smooth(0.30f, 0.80f, v)
            px[y * size + x] = ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun hash(ix: Int, iy: Int, seed: Int): Float {
        var h = ix * 374761393 + iy * 668265263 + seed * 2147483647
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7fffffff) / 2147483647f
    }

    private fun periodicNoise(x: Float, y: Float, period: Int, seed: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        fun h(ix: Int, iy: Int) = hash(Math.floorMod(ix, period), Math.floorMod(iy, period), seed)
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        val a = h(x0, y0); val b = h(x0 + 1, y0); val c = h(x0, y0 + 1); val d = h(x0 + 1, y0 + 1)
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}

// =================================================================== v2 sky
// [fork/mibot-v2] Cloud-sky / night-sky backdrop: 3-stop sky gradient, slow
// drifting cloud bank, two fog layers with parallax, and (night) twinkling
// stars hidden behind clouds. Same clock / pause rules as the fog above.

private const val SKY_AGSL = """
uniform float2 res;
uniform float t;
uniform float breath;
uniform float4 top;
uniform float4 mid;
uniform float4 bot;
uniform float4 cloud;
uniform float4 fog;
uniform float stars;
uniform float cover;
uniform float fogAmt;

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
    float a = 0.55;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + float2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}
half4 main(float2 xy) {
    float2 uv = xy / res.y;
    float y = clamp(xy.y / res.y, 0.0, 1.0);
    float3 col = y < 0.55 ? mix(top.rgb, mid.rgb, y / 0.55) : mix(mid.rgb, bot.rgb, (y - 0.55) / 0.45);

    // Cloud bank: stretched fBm drifting slowly to the right.
    float c = fbm(float2(uv.x * 1.1 + t * 0.006, uv.y * 2.2 - t * 0.0015));
    float lo = 0.62 - cover * 0.28;
    float cm = smoothstep(lo, lo + 0.22, c) * (1.0 - 0.45 * smoothstep(0.55, 1.0, y));
    float shade = smoothstep(lo, lo + 0.5, fbm(float2(uv.x * 2.3 + t * 0.006 + 3.0, uv.y * 4.0)));

    // Stars (night): sparse grid points, twinkling, behind clouds.
    if (stars > 0.01) {
        float2 cell = floor(xy / 22.0);
        float h = hash(cell);
        if (h > 0.965) {
            float2 center = (cell + float2(hash(cell + 3.1), hash(cell + 7.7))) * 22.0;
            float d = length(xy - center);
            float tw = 0.6 + 0.4 * sin(t * (0.6 + h * 1.4) + h * 40.0);
            float s = smoothstep(1.6, 0.0, d) * tw * stars * (1.0 - smoothstep(0.45, 0.9, y));
            col += float3(s) * (1.0 - cm);
        }
    }

    col = mix(col, mix(cloud.rgb, cloud.rgb * 0.86, shade), cm * 0.88);

    // Two fog layers, opposite drift (parallax), breathing slowly.
    float f1 = fbm(uv * 1.5 + float2(-t * 0.010, t * 0.0015) + 5.3);
    float f2 = fbm(uv * 2.6 + float2(t * 0.018, 0.0) + 11.7);
    float fm = smoothstep(0.38, 0.88, f1) * (0.35 + 0.65 * y) * (0.8 + 0.2 * breath);
    float fm2 = smoothstep(0.45, 0.95, f2) * (0.25 + 0.75 * y);
    col = mix(col, fog.rgb, fm * fogAmt * 0.65);
    col = mix(col, fog.rgb, fm2 * fogAmt * 0.35);
    return half4(half3(col), 1.0);
}
"""

@Composable
fun SkyBackdrop(sky: SkyColors, modifier: Modifier = Modifier) {
    val time = rememberFogTime()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderSky(sky, time, modifier)
    } else {
        BitmapSky(sky, time, modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderSky(sky: SkyColors, time: Float, modifier: Modifier) {
    val shader = remember { RuntimeShader(SKY_AGSL) }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("res", size.width, size.height)
        shader.setFloatUniform("t", time)
        shader.setFloatUniform("breath", breath(time))
        shader.setFloatUniform("top", sky.top.f4())
        shader.setFloatUniform("mid", sky.mid.f4())
        shader.setFloatUniform("bot", sky.bottom.f4())
        shader.setFloatUniform("cloud", sky.cloud.f4())
        shader.setFloatUniform("fog", sky.fog.f4())
        shader.setFloatUniform("stars", sky.stars)
        shader.setFloatUniform("cover", sky.cover)
        shader.setFloatUniform("fogAmt", sky.fogAmount)
        drawRect(ShaderBrush(shader))
    }
}

/** < API 33: gradient + baked cloud/fog textures + precomputed stars. */
@Composable
private fun BitmapSky(sky: SkyColors, time: Float, modifier: Modifier) {
    val clouds = remember { FogTexture.generate(seed = 41, size = 128, cells = 3) }
    val far = remember { FogTexture.generate(seed = 7, size = 96, cells = 3) }
    val near = remember { FogTexture.generate(seed = 23, size = 96, cells = 4) }
    val starField = remember {
        val r = java.util.Random(1234)
        List(140) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.6f, 0.6f + r.nextFloat() * 1.4f, r.nextFloat() * 6.28f) }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(0f to sky.top, 0.55f to sky.mid, 1f to sky.bottom))
        if (sky.stars > 0.01f) {
            for (s in starField) {
                val tw = 0.6f + 0.4f * sin(time * s[2] + s[3])
                drawCircle(
                    Color.White.copy(alpha = (tw * sky.stars * (1f - s[1])).coerceIn(0f, 1f)),
                    radius = 1.1f + s[2] * 0.5f,
                    center = Offset(s[0] * size.width, s[1] * size.height),
                )
            }
        }
        val b = breath(time)
        drawFogLayer(clouds, sky.cloud, alpha = 0.55f + 0.35f * sky.cover, speedPxPerS = size.width * 0.006f, time = time, scale = 1.4f)
        drawFogLayer(far, sky.fog, alpha = (0.35f + 0.15f * b) * sky.fogAmount, speedPxPerS = -size.width * 0.010f, time = time, scale = 1.6f)
        drawFogLayer(near, sky.fog, alpha = (0.30f + 0.10f * (1f - b)) * sky.fogAmount, speedPxPerS = size.width * 0.018f, time = time, scale = 1.2f)
    }
}
