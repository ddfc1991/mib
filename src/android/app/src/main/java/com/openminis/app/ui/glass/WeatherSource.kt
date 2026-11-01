package com.openminis.app.ui.glass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * [fork/glass-ui] Quiet, best-effort weather lookup that only tints the
 * ambient backdrop. Nothing about the weather is ever displayed.
 *
 * Privacy / cost rules:
 * - Never asks for a permission: uses the last known location only when the
 *   user already granted location to the app for other features; otherwise
 *   returns [WeatherKind.UNKNOWN] (time-of-day palette only).
 * - Coordinates are rounded to 0.1 deg (~10 km) before leaving the device.
 * - One request per hour at most (cached in prefs), keyless Open-Meteo API.
 * - Any failure is swallowed; the backdrop simply falls back.
 */
object WeatherSource {
    private const val FILE = "glass_ui_weather"
    private const val TTL_MS = 60L * 60 * 1000

    suspend fun current(context: Context): WeatherKind = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val cachedAt = prefs.getLong("at", 0L)
        val cached = runCatching { WeatherKind.valueOf(prefs.getString("kind", "") ?: "") }.getOrNull()
        if (cached != null && System.currentTimeMillis() - cachedAt < TTL_MS) return@withContext cached

        val loc = lastKnownLocation(context) ?: return@withContext cached ?: WeatherKind.UNKNOWN
        val lat = (loc.first * 10).roundToInt() / 10.0
        val lon = (loc.second * 10).roundToInt() / 10.0
        val kind = runCatching {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val code = JSONObject(body).getJSONObject("current").getInt("weather_code")
                AmbientPalette.kindForWmo(code)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        if (kind != null) {
            prefs.edit().putLong("at", System.currentTimeMillis()).putString("kind", kind.name).apply()
            kind
        } else {
            cached ?: WeatherKind.UNKNOWN
        }
    }

    private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
        val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            listOf(LocationManager.PASSIVE_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) {
            null
        }
    }
}
