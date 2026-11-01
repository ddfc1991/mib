package com.openminis.app.ui.glass

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
private fun rememberWeather(): WeatherKind {
    val context = LocalContext.current
    val weather by produceState(WeatherKind.UNKNOWN) {
        while (true) {
            value = WeatherSource.current(context)
            delay(30L * 60 * 1000)
        }
    }
    return weather
}

@Composable
private fun rememberPhotoBitmap(path: String?): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    return bmp
}

/**
 * [fork/glass-ui] Full-bleed backdrop: the user's photo, the ambient gradient,
 * or the plain theme background. Glass surfaces blur it (and whatever is
 * drawn above it) through Haze; see GlassStyles.
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    settings: GlassSettings = LocalContext.current.let { ctx ->
        remember(ctx) { GlassPrefs.flow(ctx) }.collectAsState().value
    },
) {
    when (settings.mode) {
        BackdropMode.PHOTO -> {
            val bmp = rememberPhotoBitmap(settings.photoPath)
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier.fillMaxSize(),
                )
            } else {
                AmbientGradient(modifier, settings.style)
            }
        }
        BackdropMode.AMBIENT -> AmbientGradient(modifier, settings.style)
        BackdropMode.PLAIN -> {
            val dark = com.openminis.app.ui.theme.LocalMibotDark.current
            val c = if (settings.solidColor != 0) androidx.compose.ui.graphics.Color(settings.solidColor)
            else if (dark) androidx.compose.ui.graphics.Color(0xFF0E121C) else androidx.compose.ui.graphics.Color(0xFFEEF1F5)
            Box(modifier.fillMaxSize().background(c))
        }
    }
}

@Composable
private fun AmbientGradient(modifier: Modifier, style: GlassStyle) {
    val weather = rememberWeather()
    // Re-read the hour once a minute; the palette only changes on the hour.
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }
    if (com.openminis.app.ui.theme.LocalMibotActive.current) {
        val dark = com.openminis.app.ui.theme.LocalMibotDark.current
        val sky = remember(hour, weather, dark) { AmbientPalette.sky(dark, hour, weather) }
        CloudSky(sky, dark, modifier)
        return
    }
    val colors = remember(hour, weather, style) {
        AmbientPalette.styled(AmbientPalette.colors(AmbientPalette.phaseFor(hour), weather), style)
    }
    AmbientFog(colors, modifier)
}

/**
 * [fork/mibot-v2] The one backdrop behind every screen (drawn under the
 * NavHost). Photo mode gets a theme-coloured veil so text stays readable.
 */
@Composable
fun MibotBackdrop(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by remember(context) { GlassPrefs.flow(context) }.collectAsState()
    val dark = com.openminis.app.ui.theme.LocalMibotDark.current
    Box(modifier.fillMaxSize()) {
        GlassBackdrop(settings = settings)
        if (settings.mode == BackdropMode.PHOTO && settings.photoPath != null) {
            val veil = if (dark) androidx.compose.ui.graphics.Color.Black.copy(alpha = settings.dim.coerceIn(0.15f, 0.9f))
            else androidx.compose.ui.graphics.Color.White.copy(alpha = (settings.dim * 0.85f).coerceIn(0.15f, 0.85f))
            Box(Modifier.fillMaxSize().background(veil))
            // [fork/mibot-v3] Optional soft clouds drifting over the user's photo.
            if (settings.fogOverPhoto) {
                val sky = remember(dark) { AmbientPalette.sky(dark, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), WeatherKind.UNKNOWN) }
                CloudSky(sky, dark, cloudsOnly = true)
            }
        }
    }
}
