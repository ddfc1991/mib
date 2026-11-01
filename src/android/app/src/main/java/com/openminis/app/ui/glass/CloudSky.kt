package com.openminis.app.ui.glass

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
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
 * [fork/mibot-v3] Realistic drifting cloud sky.
 *
 * Three soft cloud layers (far / mid / near) are baked ONCE, off the main
 * thread, into small alpha textures: horizontally-tileable value-noise fBm with
 * domain warping (wispy, not blobby), a gentle threshold (no hard edges), a
 * vertical band per layer, then a light blur. At draw time each layer is just
 * a bilinear-upscaled bitmap (which makes it naturally soft) tinted and slid
 * sideways at its own, very slow speed — parallax at a cost of three
 * drawImage calls per frame, on every API level (no per-pixel shader work).
 *
 * The weather nudges cover (layer opacity), haze and drift speed; night swaps
 * in a deep blue gradient with twinkling stars behind blue-grey clouds. The
 * clock is shared with the old fog (pauses when not RESUMED, in battery saver
 * and when animations are disabled).
 */

private const val TEX_W = 256
private const val TEX_H = 320

private class LayerSpec(val seed: Int, val cells: Int, val lo: Float, val hi: Float, val bandCenter: Float, val bandWidth: Float, val aspect: Float)

private val SPECS = listOf(
    LayerSpec(41, 3, 0.40f, 0.80f, 0.28f, 0.28f, 1.25f),  // far, high in the sky
    LayerSpec(7, 4, 0.42f, 0.82f, 0.52f, 0.30f, 1.50f),   // middle
    LayerSpec(23, 5, 0.45f, 0.85f, 0.80f, 0.28f, 1.75f),  // near, low haze
)

/** Baked once per process; ~100 ms of work on a background thread. */
internal object CloudTextures {
    @Volatile private var cached: List<ImageBitmap>? = null

    suspend fun get(): List<ImageBitmap> = cached ?: withContext(Dispatchers.Default) {
        cached ?: SPECS.map { bake(it) }.also { cached = it }
    }

    private fun hash(ix: Int, iy: Int, seed: Int): Float {
        var h = ix * 374761393 + iy * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7fffffff) / 2147483647f
    }

    /** Value noise, periodic in x with period [px] cells (so the layer tiles horizontally). */
    private fun vnoise(x: Float, y: Float, px: Int, seed: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        val xa = Math.floorMod(x0, px); val xb = Math.floorMod(x0 + 1, px)
        val a = hash(xa, y0, seed); val b = hash(xb, y0, seed)
        val c = hash(xa, y0 + 1, seed); val d = hash(xb, y0 + 1, seed)
        val top = a + (b - a) * ux
        val bot = c + (d - c) * ux
        return top + (bot - top) * uy
    }

    private fun fbm(x0: Float, y0: Float, period: Int, seed: Int, octaves: Int): Float {
        var x = x0; var y = y0; var p = period
        var v = 0f; var amp = 0.5f; var norm = 0f
        for (o in 0 until octaves) {
            v += amp * vnoise(x, y, p, seed + o * 131)
            norm += amp
            x *= 2; y *= 2; p *= 2; amp *= 0.5f
        }
        return v / norm
    }

    private fun bake(s: LayerSpec): ImageBitmap {
        val a = FloatArray(TEX_W * TEX_H)
        for (j in 0 until TEX_H) {
            val yy = j / TEX_H.toFloat()
            val band = exp(-((yy - s.bandCenter) / s.bandWidth).let { it * it })
            val v = yy * s.cells * s.aspect * 2f
            for (i in 0 until TEX_W) {
                val u = i / TEX_W.toFloat() * s.cells
                val wx = fbm(u + 3.1f, v + 1.7f, s.cells, s.seed + 7, 3)
                val wy = fbm(u + 8.3f, v + 2.8f, s.cells, s.seed + 11, 3)
                val n = fbm(u + (wx - 0.5f) * 1.4f, v + (wy - 0.5f) * 1.1f, s.cells, s.seed, 5)
                var t = ((n - s.lo) / (s.hi - s.lo)).coerceIn(0f, 1f)
                t = t * t * (3 - 2 * t)
                a[j * TEX_W + i] = t * band
            }
        }
        val blurred = blur(blur(a))
        val px = IntArray(TEX_W * TEX_H) { ((blurred[it] * 255f).roundToInt().coerceIn(0, 255) shl 24) or 0xFFFFFF }
        return Bitmap.createBitmap(px, TEX_W, TEX_H, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** 3×3 box blur, wrapping horizontally (keeps the texture tileable). */
    private fun blur(src: FloatArray): FloatArray {
        val out = FloatArray(src.size)
        for (j in 0 until TEX_H) for (i in 0 until TEX_W) {
            var sum = 0f; var n = 0
            for (dj in -1..1) {
                val y = j + dj
                if (y < 0 || y >= TEX_H) continue
                for (di in -1..1) {
                    sum += src[y * TEX_W + Math.floorMod(i + di, TEX_W)]; n++
                }
            }
            out[j * TEX_W + i] = sum / n
        }
        return out
    }
}

private val STARS: List<FloatArray> by lazy {
    val r = java.util.Random(1234)
    List(150) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.62f, 0.5f + r.nextFloat() * 1.5f, r.nextFloat() * 6.28f) }
}

/**
 * The Mibot sky. [cloudsOnly] draws just the drifting cloud layers (for the
 * optional "clouds over your photo" overlay).
 */
@Composable
fun CloudSky(sky: SkyColors, dark: Boolean, modifier: Modifier = Modifier, cloudsOnly: Boolean = false) {
    val time = rememberAmbientTime()
    val layers by produceState<List<ImageBitmap>?>(null) { value = CloudTextures.get() }
    val stormy = sky.cover > 0.75f
    val speed = if (stormy) 1.6f else 1f
    val alphaScale = (0.55f + 0.6f * sky.cover).coerceIn(0.4f, 1.15f)
    val shade = remember(sky) { AmbientPalette.lerp(sky.cloud, if (dark) sky.top else sky.mid, if (dark) 0.25f else 0.45f) }
    Canvas(modifier.fillMaxSize()) {
        if (!cloudsOnly) {
            drawRect(Brush.verticalGradient(0f to sky.top, 0.55f to sky.mid, 1f to sky.bottom))
            if (!dark) {
                // Warm, hazy sun glow low on the left (reference: peach light through mist).
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFFF4D6BC).copy(alpha = 0.55f * (1f - sky.cover * 0.5f)), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.74f), radius = size.maxDimension * 0.55f,
                    ),
                )
            } else {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF8FA3C8).copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.12f), radius = size.maxDimension * 0.35f,
                    ),
                )
            }
            if (sky.stars > 0.01f) {
                for (s in STARS) {
                    val tw = 0.55f + 0.45f * sin(time * s[2] * 0.7f + s[3])
                    drawCircle(
                        Color.White.copy(alpha = (tw * sky.stars * (1f - s[1]) * 0.9f).coerceIn(0f, 1f)),
                        radius = 0.9f + s[2] * 0.55f,
                        center = Offset(s[0] * size.width, s[1] * size.height),
                    )
                }
            }
        }
        val l = layers ?: return@Canvas
        val k = if (cloudsOnly) 0.6f else 1f
        // far → near: slower → faster (parallax), drifting left to right.
        drawCloudLayer(l[0], sky.cloud, (0.85f * alphaScale * k).coerceAtMost(1f), 0.004f * speed, time, 1.7f)
        drawCloudLayer(l[1], if (dark) sky.fog else sky.fog, (0.75f * alphaScale * k).coerceAtMost(1f), 0.008f * speed, time, 1.4f)
        drawCloudLayer(l[2], shade, (0.60f * alphaScale * k).coerceAtMost(1f), 0.014f * speed, time, 1.2f)
        if (!cloudsOnly && sky.fogAmount > 0.6f) {
            // Fog / mist weather: an even veil that thickens toward the bottom.
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, sky.fog.copy(alpha = 0.45f * (sky.fogAmount - 0.5f)))))
        }
    }
}

/** One cloud layer: the texture spans the full height and [widthScale]× the width, tiled sideways. */
private fun DrawScope.drawCloudLayer(tex: ImageBitmap, tint: Color, alpha: Float, speedWidthsPerS: Float, time: Float, widthScale: Float) {
    val tileW = (size.width * widthScale).roundToInt().coerceAtLeast(1)
    val h = size.height.roundToInt().coerceAtLeast(1)
    val shift = (((speedWidthsPerS * size.width * time) % tileW) + tileW) % tileW
    val filter = ColorFilter.tint(tint, BlendMode.SrcIn)
    var x = shift - tileW
    while (x < size.width) {
        drawImage(
            image = tex,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(tex.width, tex.height),
            dstOffset = IntOffset(x.roundToInt(), 0),
            dstSize = IntSize(tileW, h),
            alpha = alpha,
            colorFilter = filter,
            filterQuality = FilterQuality.Low, // bilinear upscale = soft edges
        )
        x += tileW
    }
}
