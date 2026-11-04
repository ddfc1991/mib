/*
 * Adapted from Mika (https://github.com/wangzan101/Mika), app/src/main/java/com/wangzan101/mika/cap/phone/display/DisplayFrameGeometry.kt @ 6661b9e
 * Copyright 2026 wangzan101 — Licensed under the Apache License, Version 2.0.
 * Modifications for Mibot (com.dl1991.mibot), distributed under GPL-3.0-or-later:
 * package rename; logical↔buffer point mapping (toBuffer/toLogical) and screenshot scale helpers, English comments.
 */
package com.openminis.app.mibot.phone

/**
 * ROTATION_0/90/180/270 are 0/1/2/3. The physical buffer never changes; the logical display swaps
 * width and height at a quarter turn. (Mika: 物理缓冲不变,逻辑屏在四分之一圈时交换宽高。)
 */
internal data class DisplayFrameGeometry(val bufferWidth: Int, val bufferHeight: Int, val rotation: Int) {
    private val sideways get() = rotation == 1 || rotation == 3
    val width: Int get() = if (sideways) bufferHeight else bufferWidth
    val height: Int get() = if (sideways) bufferWidth else bufferHeight

    /** 按短边限制预览:横竖屏保持相近的像素量,避免横屏只剩一条低分辨率画面。 */
    fun previewWidth(shortSide: Int): Int =
        if (width > height) maxOf(1, (width.toLong() * shortSide / height).toInt()) else shortSide

    fun previewSize(maxWidth: Int): Pair<Int, Int> {
        val w = minOf(maxWidth, width)
        return w to maxOf(1, (height.toLong() * w / width).toInt())
    }

    /**
     * Mibot: a point in the physical buffer → the app's logical (upright) coordinates, the same
     * transform [uprightDisplayFrame] draws with. Continuous coordinates (edges map to edges).
     */
    fun toLogical(bx: Int, by: Int): Pair<Int, Int> = when (rotation) {
        1 -> by to bufferWidth - bx
        2 -> bufferWidth - bx to bufferHeight - by
        3 -> bufferHeight - by to bx
        else -> bx to by
    }

    /** Mibot: inverse of [toLogical]. */
    fun toBuffer(lx: Int, ly: Int): Pair<Int, Int> = when (rotation) {
        1 -> bufferWidth - ly to lx
        2 -> bufferWidth - lx to bufferHeight - ly
        3 -> ly to bufferHeight - lx
        else -> lx to ly
    }
}

/**
 * Mibot: screenshots sent to the model are downscaled so the long side is ≤ maxSide. Taps and
 * accessibility bounds use the display's logical pixels, so image points are scaled back here.
 */
internal object ShotScale {
    fun scale(width: Int, height: Int, maxSide: Int): Float =
        if (width <= 0 || height <= 0) 1f else minOf(1f, maxSide.toFloat() / maxOf(width, height))

    fun size(width: Int, height: Int, scale: Float): Pair<Int, Int> =
        maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())

    /** Image point → display point, clamped inside the display. */
    fun toDisplay(ix: Int, iy: Int, scale: Float, width: Int, height: Int): Pair<Int, Int> {
        val s = if (scale <= 0f) 1f else scale
        val x = Math.round(ix / s).coerceIn(0, maxOf(0, width - 1))
        val y = Math.round(iy / s).coerceIn(0, maxOf(0, height - 1))
        return x to y
    }
}

/**
 * Mibot: size of the agent display. Real resolution and dpi (as Mika's TaskRun does), always as a
 * portrait buffer so a landscape-held phone does not create a sideways display; apps that want
 * landscape rotate the display themselves (AgentDisplay follows the rotation). Explicit sizes win.
 */
internal object AgentDisplaySpec {
    fun of(realWidth: Int, realHeight: Int, densityDpi: Int, width: Int = 0, height: Int = 0): Triple<Int, Int, Int> {
        val short = minOf(realWidth, realHeight)
        val long = maxOf(realWidth, realHeight)
        val w = (if (width > 0) width else short).coerceIn(320, 4096) / 2 * 2
        val h = (if (height > 0) height else long).coerceIn(320, 4096) / 2 * 2
        return Triple(w, h, densityDpi.coerceIn(120, 800))
    }
}
