package com.openminis.app.mibot.design

// [fork/mibot-v4] Refined line icon set. Glyph geometry is Lucide
// (https://lucide.dev, ISC License, (c) Lucide Contributors; parts derived
// from Feather, MIT) converted to path data by v4gen/gen_icons.py. Drawn at
// a ~1.5px stroke; the selected state adds a soft duotone fill.

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One line icon: path data on a 24x24 grid. */
class LineGlyph(val id: String, val paths: List<String>) {
    val outline: ImageVector by lazy { build(1.6f, fill = false) }
    val bold: ImageVector by lazy { build(1.95f, fill = false) }
    val fill: ImageVector by lazy { build(0f, fill = true) }

    private fun build(stroke: Float, fill: Boolean): ImageVector {
        val b = ImageVector.Builder(
            name = "Line." + id + if (fill) ".fill" else "",
            defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        )
        for (p in paths) {
            val nodes = addPathNodes(p)
            if (fill) {
                b.addPath(pathData = nodes, fill = SolidColor(Color.Black))
            } else {
                b.addPath(
                    pathData = nodes, fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        return b.build()
    }
}

/**
 * Draws a [LineGlyph]. [duotone] = selected state: a soft fill in the same
 * tint under a slightly bolder stroke.
 */
@Composable
fun Glyph(
    glyph: LineGlyph,
    modifier: Modifier = Modifier,
    size: Dp = MibotIcon.Standard,
    tint: Color = LocalContentColor.current,
    duotone: Boolean = false,
    contentDescription: String? = null,
) {
    Box(modifier.size(size)) {
        if (duotone) Icon(glyph.fill, null, Modifier.matchParentSize(), tint = tint.copy(alpha = tint.alpha * 0.22f))
        Icon(if (duotone) glyph.bold else glyph.outline, contentDescription, Modifier.matchParentSize(), tint = tint)
    }
}

object LineIcons {
    val MessageCircle: LineGlyph by lazy { LineGlyph("message-circle", listOf("M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719")) }
    val MessagesSquare: LineGlyph by lazy { LineGlyph("messages-square", listOf("M16 10a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 14.286V4a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z", "M20 9a2 2 0 0 1 2 2v10.286a.71.71 0 0 1-1.212.502l-2.202-2.202A2 2 0 0 0 17.172 19H10a2 2 0 0 1-2-2v-1")) }
    val Newspaper: LineGlyph by lazy { LineGlyph("newspaper", listOf("M15 18h-5", "M18 14h-8", "M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-4 0v-9a2 2 0 0 1 2-2h2", "M11 6h6a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1Z")) }
    val Lightbulb: LineGlyph by lazy { LineGlyph("lightbulb", listOf("M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5", "M9 18h6", "M10 22h4")) }
    val Bot: LineGlyph by lazy { LineGlyph("bot", listOf("M12 8V4H8", "M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2Z", "M2 14h2", "M20 14h2", "M15 13v2", "M9 13v2")) }
    val Settings: LineGlyph by lazy { LineGlyph("settings", listOf("M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z")) }
    val Palette: LineGlyph by lazy { LineGlyph("palette", listOf("M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z", "M13 6.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", "M17 10.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", "M6 12.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z", "M8 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z")) }
    val Image: LineGlyph by lazy { LineGlyph("image", listOf("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z", "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21")) }
    val Sun: LineGlyph by lazy { LineGlyph("sun", listOf("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41")) }
    val Moon: LineGlyph by lazy { LineGlyph("moon", listOf("M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401")) }
    val SunMoon: LineGlyph by lazy { LineGlyph("sun-moon", listOf("M12 2v2", "M14.837 16.385a6 6 0 1 1-7.223-7.222c.624-.147.97.66.715 1.248a4 4 0 0 0 5.26 5.259c.589-.255 1.396.09 1.248.715", "M16 12a4 4 0 0 0-4-4", "m19 5-1.256 1.256", "M20 12h2")) }
    val Zap: LineGlyph by lazy { LineGlyph("zap", listOf("M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z")) }
    val History: LineGlyph by lazy { LineGlyph("history", listOf("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M12 7v5l4 2")) }
    val House: LineGlyph by lazy { LineGlyph("house", listOf("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")) }
    val CornerDownLeft: LineGlyph by lazy { LineGlyph("corner-down-left", listOf("M20 4v7a4 4 0 0 1-4 4H4", "m9 10-5 5 5 5")) }
    val Keyboard: LineGlyph by lazy { LineGlyph("keyboard", listOf("M10 8h.01", "M12 12h.01", "M14 8h.01", "M16 12h.01", "M18 8h.01", "M6 8h.01", "M7 16h10", "M8 12h.01", "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2Z")) }
    val Type: LineGlyph by lazy { LineGlyph("type", listOf("M12 4v16", "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2", "M9 20h6")) }
    val Globe: LineGlyph by lazy { LineGlyph("globe", listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20")) }
    val Network: LineGlyph by lazy { LineGlyph("network", listOf("M17 16h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1Z", "M3 16h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1Z", "M10 2h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1Z", "M5 16v-3a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v3", "M12 12V8")) }
    val Server: LineGlyph by lazy { LineGlyph("server", listOf("M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2Z", "M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2Z", "M6 6L6.01 6", "M6 18L6.01 18")) }
    val Database: LineGlyph by lazy { LineGlyph("database", listOf("M3 5a9 3 0 1 0 18 0a9 3 0 1 0 -18 0Z", "M3 5V19A9 3 0 0 0 21 19V5", "M3 12A9 3 0 0 0 21 12")) }
    val Folder: LineGlyph by lazy { LineGlyph("folder", listOf("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")) }
    val FolderOpen: LineGlyph by lazy { LineGlyph("folder-open", listOf("m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2")) }
    val FolderArchive: LineGlyph by lazy { LineGlyph("folder-archive", listOf("M13 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", "M20.9 19.8A2 2 0 0 0 22 18V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h5.1", "M15 11v-1", "M15 17v-2")) }
    val FolderPlus: LineGlyph by lazy { LineGlyph("folder-plus", listOf("M12 10v6", "M9 13h6", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")) }
    val FileText: LineGlyph by lazy { LineGlyph("file-text", listOf("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z", "M14 2v5a1 1 0 0 0 1 1h5", "M10 9H8", "M16 13H8", "M16 17H8")) }
    val Cloud: LineGlyph by lazy { LineGlyph("cloud", listOf("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z")) }
    val CloudUpload: LineGlyph by lazy { LineGlyph("cloud-upload", listOf("M12 13v8", "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242", "m8 17 4-4 4 4")) }
    val CloudDownload: LineGlyph by lazy { LineGlyph("cloud-download", listOf("M12 13v8l-4-4", "m12 21 4-4", "M4.393 15.269A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.436 8.284")) }
    val Archive: LineGlyph by lazy { LineGlyph("archive", listOf("M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z", "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", "M10 12h4")) }
    val Terminal: LineGlyph by lazy { LineGlyph("terminal", listOf("M12 19h8", "m4 17 6-6-6-6")) }
    val Brain: LineGlyph by lazy { LineGlyph("brain", listOf("M12 18V5", "M15 13a4.17 4.17 0 01-3-4 4.17 4.17 0 01-3 4", "M17.707 6.294A3 3 0 1012 5a3 3 0 10-5.707 1.294", "M17.996 5.125a4 4 0 012.526 5.77", "M18 18a4 4 0 002-7.465", "M19.967 17.482a4 4 0 11-7.967.517 4 4 0 11-7.967-.517", "M6 18a4 4 0 01-2-7.465", "M6.004 5.125a4 4 0 00-2.526 5.77")) }
    val Puzzle: LineGlyph by lazy { LineGlyph("puzzle", listOf("M15.39 4.39a1 1 0 0 0 1.68-.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0-.474 1.68l1.683 1.682a2.414 2.414 0 0 1 0 3.414L19.61 15.39a1 1 0 0 1-1.68-.474 2.5 2.5 0 1 0-3.014 3.015 1 1 0 0 1 .474 1.68l-1.683 1.682a2.414 2.414 0 0 1-3.414 0L8.61 19.61a1 1 0 0 0-1.68.474 2.5 2.5 0 1 1-3.014-3.015 1 1 0 0 0 .474-1.68l-1.683-1.682a2.414 2.414 0 0 1 0-3.414L4.39 8.61a1 1 0 0 1 1.68.474 2.5 2.5 0 1 0 3.014-3.015 1 1 0 0 1-.474-1.68l1.683-1.682a2.414 2.414 0 0 1 3.414 0z")) }
    val Info: LineGlyph by lazy { LineGlyph("info", listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 16v-4", "M12 8h.01")) }
    val CircleAlert: LineGlyph by lazy { LineGlyph("circle-alert", listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 8L12 12", "M12 16L12.01 16")) }
    val TriangleAlert: LineGlyph by lazy { LineGlyph("triangle-alert", listOf("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01")) }
    val CircleCheck: LineGlyph by lazy { LineGlyph("circle-check", listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "m16 9-5.5 5.5L8 12")) }
    val Check: LineGlyph by lazy { LineGlyph("check", listOf("M20 6 9 17l-5-5")) }
    val Layers: LineGlyph by lazy { LineGlyph("layers", listOf("M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z", "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12", "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17")) }
    val Waypoints: LineGlyph by lazy { LineGlyph("waypoints", listOf("m10.586 5.414-5.172 5.172", "m18.586 13.414-5.172 5.172", "M6 12h12", "M10 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", "M10 4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", "M18 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", "M2 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z")) }
    val KeyRound: LineGlyph by lazy { LineGlyph("key-round", listOf("M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z", "M16 7.5a0.5 0.5 0 1 0 1 0a0.5 0.5 0 1 0 -1 0Z")) }
    val Lock: LineGlyph by lazy { LineGlyph("lock", listOf("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z", "M7 11V7a5 5 0 0 1 10 0v4")) }
    val LockOpen: LineGlyph by lazy { LineGlyph("lock-open", listOf("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z", "M7 11V7a5 5 0 0 1 9.9-1")) }
    val Shield: LineGlyph by lazy { LineGlyph("shield", listOf("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z")) }
    val ShieldCheck: LineGlyph by lazy { LineGlyph("shield-check", listOf("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z", "m9 12 2 2 4-4")) }
    val Bug: LineGlyph by lazy { LineGlyph("bug", listOf("M12 20v-9", "M14 7a4 4 0 0 1 4 4v3a6 6 0 0 1-12 0v-3a4 4 0 0 1 4-4z", "M14.12 3.88 16 2", "M21 21a4 4 0 0 0-3.81-4", "M21 5a4 4 0 0 1-3.55 3.97", "M22 13h-4", "M3 21a4 4 0 0 1 3.81-4", "M3 5a4 4 0 0 0 3.55 3.97", "M6 13H2", "m8 2 1.88 1.88", "M9 7.13V6a3 3 0 1 1 6 0v1.13")) }
    val Accessibility: LineGlyph by lazy { LineGlyph("accessibility", listOf("M15 4a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z", "m18 19 1-7-6 1", "m5 8 3-3 5.5 3-2.36 3.5", "M4.24 14.5a5 5 0 0 0 6.88 6", "M13.76 17.5a5 5 0 0 0-6.88-6")) }
    val SlidersHorizontal: LineGlyph by lazy { LineGlyph("sliders-horizontal", listOf("M10 5H3", "M12 19H3", "M14 3v4", "M16 17v4", "M21 12h-9", "M21 19h-5", "M21 5h-7", "M8 10v4", "M8 12H3")) }
    val RefreshCw: LineGlyph by lazy { LineGlyph("refresh-cw", listOf("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5")) }
    val Smartphone: LineGlyph by lazy { LineGlyph("smartphone", listOf("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2Z", "M12 18h.01")) }
    val ChartColumn: LineGlyph by lazy { LineGlyph("chart-column", listOf("M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3")) }
    val Bell: LineGlyph by lazy { LineGlyph("bell", listOf("M10.268 21a2 2 0 0 0 3.464 0", "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326")) }
    val Activity: LineGlyph by lazy { LineGlyph("activity", listOf("M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2")) }
    val Package: LineGlyph by lazy { LineGlyph("package", listOf("M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z", "M12 22V12", "M3.29 7L12 12L20.71 7", "m7.5 4.27 9 5.15")) }
    val AudioLines: LineGlyph by lazy { LineGlyph("audio-lines", listOf("M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3")) }
    val Mic: LineGlyph by lazy { LineGlyph("mic", listOf("M12 19v3", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 2h0a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h0a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3Z")) }
    val Smile: LineGlyph by lazy { LineGlyph("smile", listOf("M15 10V9", "M16.472 15a6 6 0 01-8.943 0", "M9 10V9", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z")) }
    val Mail: LineGlyph by lazy { LineGlyph("mail", listOf("m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7", "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2Z")) }
    val Copy: LineGlyph by lazy { LineGlyph("copy", listOf("M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2Z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")) }
    val Cable: LineGlyph by lazy { LineGlyph("cable", listOf("M17 19a1 1 0 0 1-1-1v-2a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2a1 1 0 0 1-1 1z", "M17 21v-2", "M19 14V6.5a1 1 0 0 0-7 0v11a1 1 0 0 1-7 0V10", "M21 21v-2", "M3 5V3", "M4 10a2 2 0 0 1-2-2V6a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2a2 2 0 0 1-2 2z", "M7 5V3")) }
    val Wrench: LineGlyph by lazy { LineGlyph("wrench", listOf("M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z")) }
    val BatteryCharging: LineGlyph by lazy { LineGlyph("battery-charging", listOf("m11 7-3 5h4l-3 5", "M14.856 6H16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.935", "M22 14v-4", "M5.14 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2.936")) }
    val Users: LineGlyph by lazy { LineGlyph("users", listOf("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M16 3.128a4 4 0 0 1 0 7.744", "M22 21v-2a4 4 0 0 0-3-3.87", "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z")) }
    val User: LineGlyph by lazy { LineGlyph("user", listOf("M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z")) }
    val Wifi: LineGlyph by lazy { LineGlyph("wifi", listOf("M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0", "M8.5 16.429a5 5 0 0 1 7 0")) }
    val Timer: LineGlyph by lazy { LineGlyph("timer", listOf("M10 2L14 2", "M12 14L15 11", "M4 14a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z")) }
    val Clock: LineGlyph by lazy { LineGlyph("clock", listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 6v6l4 2")) }
    val Router: LineGlyph by lazy { LineGlyph("router", listOf("M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2Z", "M6.01 18H6", "M10.01 18H10", "M15 10v4", "M17.84 7.17a4 4 0 0 0-5.66 0", "M20.66 4.34a8 8 0 0 0-11.31 0")) }
    val Search: LineGlyph by lazy { LineGlyph("search", listOf("m21 21-4.34-4.34", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z")) }
    val FlaskConical: LineGlyph by lazy { LineGlyph("flask-conical", listOf("M14 2v6a2 2 0 0 0 .245.96l5.51 10.08A2 2 0 0 1 18 22H6a2 2 0 0 1-1.755-2.96l5.51-10.08A2 2 0 0 0 10 8V2", "M6.453 15h11.094", "M8.5 2h7")) }
    val PictureInPicture2: LineGlyph by lazy { LineGlyph("picture-in-picture-2", listOf("M21 9V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v10c0 1.1.9 2 2 2h4", "M14 13h6a2 2 0 0 1 2 2v3a2 2 0 0 1 -2 2h-6a2 2 0 0 1 -2 -2v-3a2 2 0 0 1 2 -2Z")) }
    val Link: LineGlyph by lazy { LineGlyph("link", listOf("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")) }
    val Code: LineGlyph by lazy { LineGlyph("code", listOf("m16 18 6-6-6-6", "m8 6-6 6 6 6")) }
    val Hand: LineGlyph by lazy { LineGlyph("hand", listOf("M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2", "M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2", "M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8", "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15")) }
    val MessageSquareWarning: LineGlyph by lazy { LineGlyph("message-square-warning", listOf("M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z", "M12 15h.01", "M12 7v4")) }
    val Heart: LineGlyph by lazy { LineGlyph("heart", listOf("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5")) }
    val Pencil: LineGlyph by lazy { LineGlyph("pencil", listOf("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4")) }
    val Download: LineGlyph by lazy { LineGlyph("download", listOf("M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5")) }
    val Trash2: LineGlyph by lazy { LineGlyph("trash-2", listOf("M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")) }
    val LayoutDashboard: LineGlyph by lazy { LineGlyph("layout-dashboard", listOf("M4 3h5a1 1 0 0 1 1 1v7a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-7a1 1 0 0 1 1 -1Z", "M15 3h5a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z", "M15 12h5a1 1 0 0 1 1 1v7a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-7a1 1 0 0 1 1 -1Z", "M4 16h5a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z")) }
    val Ban: LineGlyph by lazy { LineGlyph("ban", listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M4.929 4.929 19.07 19.071")) }
    val Sparkles: LineGlyph by lazy { LineGlyph("sparkles", listOf("M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z", "M20 2v4", "M22 4h-4", "M2 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z")) }
    val Box: LineGlyph by lazy { LineGlyph("box", listOf("M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z", "m3.3 7 8.7 5 8.7-5", "M12 22V12")) }
    val Share2: LineGlyph by lazy { LineGlyph("share-2", listOf("M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M8.59 13.51L15.42 17.49", "M15.41 6.51L8.59 10.49")) }
    val ExternalLink: LineGlyph by lazy { LineGlyph("external-link", listOf("M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")) }
    val LayoutGrid: LineGlyph by lazy { LineGlyph("layout-grid", listOf("M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1Z", "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1Z", "M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1Z", "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1Z")) }
    val Cpu: LineGlyph by lazy { LineGlyph("cpu", listOf("M12 20v2", "M12 2v2", "M17 20v2", "M17 2v2", "M2 12h2", "M2 17h2", "M2 7h2", "M20 12h2", "M20 17h2", "M20 7h2", "M7 20v2", "M7 2v2", "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2Z", "M9 8h6a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1Z")) }
    val Gem: LineGlyph by lazy { LineGlyph("gem", listOf("M10.5 3 8 9l4 13 4-13-2.5-6", "M17 3a2 2 0 0 1 1.6.8l3 4a2 2 0 0 1 .013 2.382l-7.99 10.986a2 2 0 0 1-3.247 0l-7.99-10.986A2 2 0 0 1 2.4 7.8l2.998-3.997A2 2 0 0 1 7 3z", "M2 9h20")) }
    val Route: LineGlyph by lazy { LineGlyph("route", listOf("M3 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15", "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z")) }
    val Stethoscope: LineGlyph by lazy { LineGlyph("stethoscope", listOf("M11 2v2", "M5 2v2", "M5 3H4a2 2 0 0 0-2 2v4a6 6 0 0 0 12 0V5a2 2 0 0 0-2-2h-1", "M8 15a6 6 0 0 0 12 0v-3", "M18 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z")) }
    val CircleStop: LineGlyph by lazy { LineGlyph("circle-stop", listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M10 9h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1Z")) }
    val ArrowUpDown: LineGlyph by lazy { LineGlyph("arrow-up-down", listOf("m21 16-4 4-4-4", "M17 20V4", "m3 8 4-4 4 4", "M7 4v16")) }
    val Volume2: LineGlyph by lazy { LineGlyph("volume-2", listOf("M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z", "M16 9a5 5 0 0 1 0 6", "M19.364 18.364a9 9 0 0 0 0-12.728")) }
    val Send: LineGlyph by lazy { LineGlyph("send", listOf("M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z", "m21.854 2.147-10.94 10.939")) }
    val Video: LineGlyph by lazy { LineGlyph("video", listOf("m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5", "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2Z")) }
    val Printer: LineGlyph by lazy { LineGlyph("printer", listOf("M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2", "M6 9V3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v6", "M7 14h10a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-10a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1Z")) }
    val Pointer: LineGlyph by lazy { LineGlyph("pointer", listOf("M22 14a8 8 0 0 1-8 8", "M18 11v-1a2 2 0 0 0-2-2a2 2 0 0 0-2 2", "M14 10V9a2 2 0 0 0-2-2a2 2 0 0 0-2 2v1", "M10 9.5V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v10", "M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15")) }
    val Eye: LineGlyph by lazy { LineGlyph("eye", listOf("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z")) }
    val EyeOff: LineGlyph by lazy { LineGlyph("eye-off", listOf("M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20")) }
    val Plus: LineGlyph by lazy { LineGlyph("plus", listOf("M5 12h14", "M12 5v14")) }
    val ArrowLeft: LineGlyph by lazy { LineGlyph("arrow-left", listOf("m12 19-7-7 7-7", "M19 12H5")) }
    val ChevronRight: LineGlyph by lazy { LineGlyph("chevron-right", listOf("m9 18 6-6-6-6")) }
    val ChevronDown: LineGlyph by lazy { LineGlyph("chevron-down", listOf("m6 9 6 6 6-6")) }
    val EllipsisVertical: LineGlyph by lazy { LineGlyph("ellipsis-vertical", listOf("M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z", "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z", "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z")) }
    val X: LineGlyph by lazy { LineGlyph("x", listOf("M18 6 6 18", "m6 6 12 12")) }
    val Menu: LineGlyph by lazy { LineGlyph("menu", listOf("M4 5h16", "M4 12h16", "M4 19h16")) }
    val Monitor: LineGlyph by lazy { LineGlyph("monitor", listOf("M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2Z", "M8 21L16 21", "M12 17L12 21")) }
    val Laptop: LineGlyph by lazy { LineGlyph("laptop", listOf("M18 5a2 2 0 0 1 2 2v8.526a2 2 0 0 0 .212.897l1.068 2.127a1 1 0 0 1-.9 1.45H3.62a1 1 0 0 1-.9-1.45l1.068-2.127A2 2 0 0 0 4 15.526V7a2 2 0 0 1 2-2z", "M20.054 15.987H3.946")) }
    val ArrowLeftRight: LineGlyph by lazy { LineGlyph("arrow-left-right", listOf("M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4")) }
    val HardDrive: LineGlyph by lazy { LineGlyph("hard-drive", listOf("M10 16h.01", "M2.212 11.577a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z", "M21.946 12.013H2.054", "M6 16h.01")) }
    val ScanSearch: LineGlyph by lazy { LineGlyph("scan-search", listOf("M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z", "m16 16-1.9-1.9")) }
    val UserRoundCheck: LineGlyph by lazy { LineGlyph("user-round-check", listOf("M2 21a8 8 0 0 1 13.292-6", "M5 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0Z", "m16 19 2 2 4-4")) }
    val LoaderCircle: LineGlyph by lazy { LineGlyph("loader-circle", listOf("M21 12a9 9 0 1 1-6.219-8.56")) }
    val WandSparkles: LineGlyph by lazy { LineGlyph("wand-sparkles", listOf("m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72", "m14 7 3 3", "M5 6v4", "M19 14v4", "M10 2v2", "M7 8H3", "M21 16h-4", "M11 3H9")) }
    val Plug: LineGlyph by lazy { LineGlyph("plug", listOf("M12 22v-5", "M15 8V2", "M17 8a1 1 0 0 1 1 1v4a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1z", "M9 8V2")) }
    val QrCode: LineGlyph by lazy { LineGlyph("qr-code", listOf("M4 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z", "M17 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z", "M4 16h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z", "M21 16h-3a2 2 0 0 0-2 2v3", "M21 21v.01", "M12 7v3a2 2 0 0 1-2 2H7", "M3 12h.01", "M12 3h.01", "M12 16v.01", "M16 12h1", "M21 12v.01", "M12 21v-1")) }
    val RotateCcw: LineGlyph by lazy { LineGlyph("rotate-ccw", listOf("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")) }
    val CirclePlay: LineGlyph by lazy { LineGlyph("circle-play", listOf("M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z")) }
    val Power: LineGlyph by lazy { LineGlyph("power", listOf("M12 2v10", "M18.4 6.6a9 9 0 1 1-12.77.04")) }
    val Gift: LineGlyph by lazy { LineGlyph("gift", listOf("M12 7v14", "M20 11v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8", "M7.5 7a1 1 0 0 1 0-5A4.8 8 0 0 1 12 7a4.8 8 0 0 1 4.5-5 1 1 0 0 1 0 5", "M4 7h16a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1v-2a1 1 0 0 1 1 -1Z")) }
    val Key: LineGlyph by lazy { LineGlyph("key", listOf("m2 21 9.6-9.6", "m7.5 15.5 2.3 2.3a1 1 0 0 1 0 1.4l-2.1 2.1a1 1 0 0 1-1.4 0L4 19", "M10 7.5a5.5 5.5 0 1 0 11 0a5.5 5.5 0 1 0 -11 0Z")) }
    val MonitorSmartphone: LineGlyph by lazy { LineGlyph("monitor-smartphone", listOf("M18 8V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h8", "M10 19v-3.96 3.15", "M7 19h5", "M18 12h2a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-2a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2Z")) }
    val SquareTerminal: LineGlyph by lazy { LineGlyph("square-terminal", listOf("m7 11 2-2-2-2", "M11 13h4", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z")) }
    val Radar: LineGlyph by lazy { LineGlyph("radar", listOf("M19.07 4.93A10 10 0 0 0 6.99 3.34", "M4 6h.01", "M2.29 9.62A10 10 0 1 0 21.31 8.35", "M16.24 7.76A6 6 0 1 0 8.23 16.67", "M12 18h.01", "M17.99 11.66A6 6 0 0 1 15.77 16.67", "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z", "m13.41 10.59 5.66-5.66")) }
    val Fingerprint: LineGlyph by lazy { LineGlyph("fingerprint", listOf("M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4", "M14 13.12c0 2.38 0 6.38-1 8.88", "M17.29 21.02c.12-.6.43-2.3.5-3.02", "M2 12a10 10 0 0 1 18-6", "M2 16h.01", "M21.8 16c.2-2 .131-5.354 0-6", "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2", "M8.65 22c.21-.66.45-1.32.57-2", "M9 6.8a6 6 0 0 1 9 5.2v2")) }
    val CalendarCheck: LineGlyph by lazy { LineGlyph("calendar-check", listOf("M8 2v3", "M16 2v3", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z", "M3 9h18", "m9 15 2 2 4-4")) }
}

object MibotIcons {
    /** Material icon name (e.g. "Palette") -> line glyph, so inherited screens get the same set. */
    private val byMaterialName: Map<String, LineGlyph> by lazy {
        with(LineIcons) {
            mapOf(
        "Palette" to Palette,
        "Wallpaper" to Image,
        "LightMode" to Sun,
        "DarkMode" to Moon,
        "BrightnessAuto" to SunMoon,
        "Bolt" to Zap,
        "FlashOn" to Zap,
        "History" to History,
        "Restore" to RotateCcw,
        "ChatBubble" to MessageCircle,
        "ChatBubbleOutline" to MessageCircle,
        "Chat" to MessageCircle,
        "Forum" to MessagesSquare,
        "Home" to House,
        "KeyboardReturn" to CornerDownLeft,
        "Keyboard" to Keyboard,
        "TextFields" to Type,
        "FormatSize" to Type,
        "Language" to Globe,
        "Lan" to Network,
        "Dns" to Server,
        "Storage" to Database,
        "Folder" to Folder,
        "FolderShared" to FolderOpen,
        "FolderZip" to FolderArchive,
        "CreateNewFolder" to FolderPlus,
        "Description" to FileText,
        "Article" to FileText,
        "InsertDriveFile" to FileText,
        "Cloud" to Cloud,
        "CloudUpload" to CloudUpload,
        "CloudDownload" to CloudDownload,
        "Backup" to Archive,
        "Archive" to Archive,
        "Terminal" to SquareTerminal,
        "Psychology" to Brain,
        "SmartToy" to Bot,
        "Android" to Bot,
        "Extension" to Puzzle,
        "Info" to Info,
        "ErrorOutline" to CircleAlert,
        "Error" to CircleAlert,
        "Warning" to TriangleAlert,
        "CheckCircle" to CircleCheck,
        "Check" to Check,
        "Layers" to Layers,
        "Hub" to Waypoints,
        "Key" to KeyRound,
        "VpnKey" to KeyRound,
        "Lock" to Lock,
        "ScreenLockPortrait" to Lock,
        "LockOpen" to LockOpen,
        "Shield" to Shield,
        "Security" to ShieldCheck,
        "AdminPanelSettings" to ShieldCheck,
        "BugReport" to Bug,
        "Accessibility" to Accessibility,
        "Tune" to SlidersHorizontal,
        "RestartAlt" to RefreshCw,
        "Refresh" to RefreshCw,
        "Sync" to RefreshCw,
        "Autorenew" to RefreshCw,
        "PhonelinkSetup" to MonitorSmartphone,
        "PhoneAndroid" to Smartphone,
        "BarChart" to ChartColumn,
        "Notifications" to Bell,
        "NotificationsActive" to Bell,
        "MonitorHeart" to Activity,
        "Inventory" to Package,
        "GraphicEq" to AudioLines,
        "RecordVoiceOver" to Mic,
        "Mic" to Mic,
        "Face" to Smile,
        "Email" to Mail,
        "ContentCopy" to Copy,
        "Cable" to Cable,
        "Build" to Wrench,
        "Handyman" to Wrench,
        "BatteryFull" to BatteryCharging,
        "BatteryChargingFull" to BatteryCharging,
        "BatteryAlert" to BatteryCharging,
        "AccountTree" to Network,
        "People" to Users,
        "Groups" to Users,
        "Person" to User,
        "Wifi" to Wifi,
        "VpnLock" to Shield,
        "Timer" to Timer,
        "Schedule" to Clock,
        "Router" to Router,
        "Search" to Search,
        "Science" to FlaskConical,
        "PictureInPicture" to PictureInPicture2,
        "Link" to Link,
        "Lightbulb" to Lightbulb,
        "Image" to Image,
        "Javascript" to Code,
        "Code" to Code,
        "FrontHand" to Hand,
        "Feedback" to MessageSquareWarning,
        "FavoriteBorder" to Heart,
        "EditNote" to Pencil,
        "Edit" to Pencil,
        "DynamicFeed" to Newspaper,
        "Download" to Download,
        "FileDownload" to Download,
        "Delete" to Trash2,
        "DeleteOutline" to Trash2,
        "DeleteSweep" to Trash2,
        "Dashboard" to LayoutDashboard,
        "Block" to Ban,
        "AutoAwesome" to Sparkles,
        "ViewInAr" to Box,
        "Settings" to Settings,
        "Share" to Share2,
        "OpenInBrowser" to ExternalLink,
        "Launch" to ExternalLink,
        "Apps" to LayoutGrid,
        "Memory" to Cpu,
        "Diamond" to Gem,
        "AltRoute" to Route,
        "Troubleshoot" to Stethoscope,
        "Stop" to CircleStop,
        "StopCircle" to CircleStop,
        "SwapVert" to ArrowUpDown,
        "VolumeUp" to Volume2,
        "Send" to Send,
        "Videocam" to Video,
        "Print" to Printer,
        "TouchApp" to Pointer,
        "Visibility" to Eye,
        "VisibilityOff" to EyeOff,
        "Add" to Plus,
        "ArrowBack" to ArrowLeft,
        "KeyboardArrowRight" to ChevronRight,
        "ExpandMore" to ChevronDown,
        "MoreVert" to EllipsisVertical,
        "Close" to X,
        "Menu" to Menu,
        "SystemUpdate" to Download,
        "Fingerprint" to Fingerprint,
        "Event" to CalendarCheck,
            )
        }
    }

    fun lineFor(v: ImageVector): LineGlyph? {
        if (v.name.startsWith("Line.")) return null
        return byMaterialName[v.name.substringAfterLast('.')]
    }
}
