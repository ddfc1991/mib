/*
 * Mibot (com.dl1991.mibot), GPL-3.0-or-later. Tests the rotation geometry adapted from Mika
 * (https://github.com/wangzan101/Mika @ 6661b9e, DisplayFrameGeometry.kt, Apache-2.0) and Mibot's
 * coordinate mapping for landscape agent displays and downscaled screenshots.
 */
package com.openminis.app.mibot.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayGeometryTest {
    // A 1080x2400 portrait buffer, as created by vd_create on a typical phone.
    private val bw = 1080
    private val bh = 2400

    @Test fun logicalSizeSwapsAtAQuarterTurn() {
        for (r in 0..3) {
            val g = DisplayFrameGeometry(bw, bh, r)
            if (r % 2 == 0) { assertEquals(bw, g.width); assertEquals(bh, g.height) }
            else { assertEquals(bh, g.width); assertEquals(bw, g.height) }
        }
    }

    @Test fun cornersMapToCornersForEveryRotation() {
        // The logical top-left corner sits at a different buffer corner per rotation.
        assertEquals(0 to 0, DisplayFrameGeometry(bw, bh, 0).toBuffer(0, 0))
        assertEquals(bw to 0, DisplayFrameGeometry(bw, bh, 1).toBuffer(0, 0))
        assertEquals(bw to bh, DisplayFrameGeometry(bw, bh, 2).toBuffer(0, 0))
        assertEquals(0 to bh, DisplayFrameGeometry(bw, bh, 3).toBuffer(0, 0))
    }

    @Test fun landscapeMapping() {
        val g = DisplayFrameGeometry(bw, bh, 1)
        // Logical landscape 2400x1080: its bottom-right is the buffer's bottom-left.
        assertEquals(0 to bh, g.toBuffer(g.width, g.height))
        // Centre stays the centre.
        assertEquals(bw / 2 to bh / 2, g.toBuffer(g.width / 2, g.height / 2))
        assertEquals(g.width / 2 to g.height / 2, g.toLogical(bw / 2, bh / 2))
    }

    @Test fun toLogicalAndToBufferAreInverse() {
        val points = listOf(0 to 0, 17 to 993, 1079 to 2399, 540 to 1200, 1000 to 5)
        for (r in 0..3) {
            val g = DisplayFrameGeometry(bw, bh, r)
            for ((x, y) in points) {
                val (lx, ly) = g.toLogical(x, y)
                assert(lx in 0..g.width && ly in 0..g.height) { "rotation $r: ($x,$y) → ($lx,$ly) outside ${g.width}x${g.height}" }
                assertEquals("rotation $r", x to y, g.toBuffer(lx, ly))
            }
        }
    }

    @Test fun previewKeepsTheShortSide() {
        assertEquals(360, DisplayFrameGeometry(bw, bh, 0).previewWidth(360))
        // Landscape: width grows so the short side stays 360.
        assertEquals(800, DisplayFrameGeometry(bw, bh, 1).previewWidth(360))
        assertEquals(800 to 360, DisplayFrameGeometry(bw, bh, 1).previewSize(800))
    }

    @Test fun screenshotScaleMapsBackToDisplayPixels() {
        val k = ShotScale.scale(bw, bh, 1280)
        assertEquals(1280f / 2400f, k, 1e-6f)
        val (iw, ih) = ShotScale.size(bw, bh, k)
        assertEquals(576, iw); assertEquals(1280, ih)
        assertEquals(540 to 1200, ShotScale.toDisplay(288, 640, k, bw, bh))
        // Clamped inside the display.
        assertEquals(bw - 1 to bh - 1, ShotScale.toDisplay(iw, ih, k, bw, bh))
        // Small displays are not upscaled.
        assertEquals(1f, ShotScale.scale(720, 1280, 1280), 0f)
    }

    @Test fun landscapeScreenshotScale() {
        // Landscape logical 2400x1080: the long side is the width now.
        val g = DisplayFrameGeometry(bw, bh, 1)
        val k = ShotScale.scale(g.width, g.height, 1280)
        assertEquals(1280 to 576, ShotScale.size(g.width, g.height, k))
        assertEquals(1200 to 540, ShotScale.toDisplay(640, 288, k, g.width, g.height))
    }

    @Test fun agentDisplayIsRealSizePortrait() {
        assertEquals(Triple(1080, 2400, 440), AgentDisplaySpec.of(1080, 2400, 440))
        // Phone held in landscape: still a portrait buffer.
        assertEquals(Triple(1080, 2400, 440), AgentDisplaySpec.of(2400, 1080, 440))
        // Explicit sizes win, rounded to even.
        assertEquals(Triple(720, 1280, 320), AgentDisplaySpec.of(1080, 2400, 320, 721, 1280))
    }
}
