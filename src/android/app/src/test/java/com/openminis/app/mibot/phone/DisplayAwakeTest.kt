/*
 * Adapted from Mika (https://github.com/wangzan101/Mika), app/src/test/java/com/wangzan101/mika/cap/phone/shell/DisplayAwakeTest.kt @ 6661b9e
 * Copyright 2026 wangzan101 — Licensed under the Apache License, Version 2.0.
 * Modifications for Mibot (com.dl1991.mibot), distributed under GPL-3.0-or-later:
 * package rename, English comments.
 */
package com.openminis.app.mibot.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A display in its own group never sleeps: poked on a timer while it exists, stopped on release;
 * give up when the power call is unavailable; only the per-display userActivity overload is used.
 */
class DisplayAwakeTest {
    private val ownGroup = 1 shl 11

    /** Records repeating tasks; the test "ticks" them by hand. */
    private class Timer {
        val tasks = mutableListOf<Runnable>()
        val cancelled = mutableSetOf<Runnable>()
        fun repeat(r: Runnable): () -> Unit { tasks += r; return { cancelled += r } }
        fun tick() = tasks.toList().forEach { if (it !in cancelled) it.run() }
        val live get() = tasks.count { it !in cancelled }
    }

    @Test fun pokesEachDisplayUntilItIsReleased() {
        val timer = Timer()
        val poked = mutableListOf<Int>()
        val awake = DisplayAwake({ poked += it; true }, timer::repeat)
        awake.start(5)
        awake.start(7)
        timer.tick()
        timer.tick()
        assertEquals(listOf(5, 7, 5, 7), poked)
        awake.stop(5)
        poked.clear()
        timer.tick()
        assertEquals(listOf(7), poked)
        awake.stopAll()
        assertEquals(0, timer.live)
    }

    @Test fun startingTheSameDisplayAgainKeepsOnlyOneTimer() {
        val timer = Timer()
        val awake = DisplayAwake({ true }, timer::repeat)
        awake.start(5)
        awake.start(5)
        assertEquals(1, timer.live)
        awake.stop(5)
        assertEquals(0, timer.live)
    }

    @Test fun givesUpEverywhereOnceThePowerCallIsUnavailable() {
        val timer = Timer()
        var calls = 0
        val awake = DisplayAwake({ calls++; false }, timer::repeat)
        awake.start(5)
        awake.start(7)
        timer.tick()
        assertEquals(0, timer.live)
        // displays created afterwards are not registered
        awake.start(9)
        assertEquals(0, timer.live)
        assertEquals(1, calls)
    }

    @Test fun onlyDisplaysInTheirOwnGroupAreKeptAwake() {
        assertTrue(DisplayAwake.needed(ownGroup or 1, ownGroup, groupId = 3))
        // group unreadable: decide by the creation flags
        assertTrue(DisplayAwake.needed(ownGroup, ownGroup, groupId = null))
        // no own group (untrusted fallback) or actually in the default group: would keep the main screen on
        assertFalse(DisplayAwake.needed(1, ownGroup, groupId = null))
        assertFalse(DisplayAwake.needed(ownGroup, ownGroup, groupId = 0))
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    private class OldPower { fun userActivity(time: Long, event: Int, flags: Int) {} }

    @Suppress("unused", "UNUSED_PARAMETER")
    private class NewPower {
        fun userActivity(time: Long, event: Int, flags: Int) {}
        fun userActivity(displayId: Int, time: Long, event: Int, flags: Int) {}
    }

    @Test fun picksOnlyThePerDisplayUserActivity() {
        assertNull(DisplayAwake.userActivityMethod(OldPower::class.java))
        val m = DisplayAwake.userActivityMethod(NewPower::class.java)
        assertNotNull(m)
        m!!
        assertEquals(4, m.parameterCount)
        assertEquals(Int::class.javaPrimitiveType, m.parameterTypes[0])
    }
}
