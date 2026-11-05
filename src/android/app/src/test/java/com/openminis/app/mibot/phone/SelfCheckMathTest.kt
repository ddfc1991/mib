package com.openminis.app.mibot.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frame blankness and read-back matching used by the virtual screen self-check. */
class SelfCheckMathTest {
    private val black = { _: Int, _: Int -> 0xFF000000.toInt() }
    private val white = { _: Int, _: Int -> 0xFFFFFFFF.toInt() }
    private val half = { _: Int, y: Int -> if (y < 500) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }

    @Test fun blackFrameIsBlack() {
        assertTrue(SelfCheckMath.isBlack(720, 1000, black))
        assertFalse(SelfCheckMath.isBlack(720, 1000, white))
    }

    @Test fun flatFrameHasNoSpreadAndContentHasSome() {
        val flat = SelfCheckMath.lumaGrid(720, 1000, white)
        assertEquals(0, flat.max() - flat.min())
        val split = SelfCheckMath.lumaGrid(720, 1000, half)
        assertTrue(split.max() - split.min() >= SelfCheckMath.BLANK_SPREAD)
    }

    @Test fun readBackIgnoresCaseAndSpacing() {
        assertTrue(SelfCheckMath.contains("Search  MIBOT42\u00A0", "mibot42"))
        assertFalse(SelfCheckMath.contains("Search settings", "mibot42"))
    }
}
