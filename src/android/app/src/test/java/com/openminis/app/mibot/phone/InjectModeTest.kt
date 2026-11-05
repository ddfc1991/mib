/*
 * Adapted from Mika (https://github.com/wangzan101/Mika), app/src/test/java/com/wangzan101/mika/cap/phone/shell/InjectModeTest.kt @ 6661b9e
 * Copyright 2026 wangzan101 — Licensed under the Apache License, Version 2.0.
 * Modifications for Mibot (com.dl1991.mibot), distributed under GPL-3.0-or-later:
 * package rename, English comments.
 */
package com.openminis.app.mibot.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inject mode: keys wait for the dispatch result and report failure (so callers fall back);
 * touches stay async. Down/up pair: fall back only when DOWN failed; otherwise only retry UP.
 */
class InjectModeTest {
    // InputManager.INJECT_INPUT_EVENT_MODE_*
    private val async = 0
    private val waitForResult = 1
    private val waitForFinish = 2

    @Test fun keysWaitForTheDispatchResult() {
        assertEquals(waitForResult, ShellService.injectMode(key = true, waitForFinish = false))
    }

    @Test fun touchesStayAsync() {
        assertEquals(async, ShellService.injectMode(key = false, waitForFinish = false))
    }

    @Test fun explicitWaitForFinishWins() {
        assertEquals(waitForFinish, ShellService.injectMode(key = true, waitForFinish = true))
        assertEquals(waitForFinish, ShellService.injectMode(key = false, waitForFinish = true))
    }

    @Test fun undeliveredDownFallsBack() {
        var ups = 0
        var fallbacks = 0
        assertTrue(ShellService.sendPair({ false }, { ups++; true }, { fallbacks++; true }))
        assertEquals(1, fallbacks)
        assertEquals(0, ups)
        assertFalse(ShellService.sendPair({ false }, { true }, { false }))
    }

    @Test fun deliveredDownNeverFallsBackAndRetriesUpOnce() {
        var ups = 0
        var fallbacks = 0
        assertFalse(ShellService.sendPair({ true }, { ups++; false }, { fallbacks++; true }))
        assertEquals(2, ups)
        assertEquals(0, fallbacks)
    }
}
