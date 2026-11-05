/*
 * Adapted from Mika (https://github.com/wangzan101/Mika), app/src/test/java/com/wangzan101/mika/cap/phone/shell/DisplayFlagsTest.kt @ 6661b9e
 * Copyright 2026 wangzan101 — Licensed under the Apache License, Version 2.0.
 * Modifications for Mibot (com.dl1991.mibot), distributed under GPL-3.0-or-later:
 * package rename, Android 12 (sdk 31) cases, English comments.
 */
package com.openminis.app.mibot.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent display flags: with the trusted-display permission they are unchanged; without it
 * (shell lacks ADD_TRUSTED_DISPLAY) only the trusted-only ones are dropped. Only SecurityException retries.
 */
class DisplayFlagsTest {
    // DisplayManager.VIRTUAL_DISPLAY_FLAG_*
    private val public = 1 shl 0
    private val ownContentOnly = 1 shl 3
    private val supportsTouch = 1 shl 6
    private val rotatesWithContent = 1 shl 7
    private val destroyContentOnRemoval = 1 shl 8
    private val trusted = 1 shl 10
    private val ownDisplayGroup = 1 shl 11
    private val alwaysUnlocked = 1 shl 12
    private val touchFeedbackDisabled = 1 shl 13
    private val ownFocus = 1 shl 14
    private val stealTopFocusDisabled = 1 shl 16

    private val base = public or ownContentOnly or supportsTouch or rotatesWithContent or destroyContentOnRemoval

    @Test fun trustedFlagsOnAndroid14AreUnchanged() {
        assertEquals(
            base or touchFeedbackDisabled or trusted or ownDisplayGroup or alwaysUnlocked or ownFocus or stealTopFocusDisabled,
            ShellService.displayFlags(sdk = 34, trusted = true),
        )
    }

    @Test fun untrustedFlagsOnAndroid14DropOnlyTheTrustedOnes() {
        val flags = ShellService.displayFlags(sdk = 34, trusted = false)
        assertEquals(base or touchFeedbackDisabled, flags)
        // must be dropped
        for (bit in listOf(trusted, ownDisplayGroup, alwaysUnlocked, ownFocus, stealTopFocusDisabled)) {
            assertEquals("flag $bit must be dropped", 0, flags and bit)
        }
        // independent of trust: kept
        for (bit in listOf(public, ownContentOnly, supportsTouch, rotatesWithContent, destroyContentOnRemoval, touchFeedbackDisabled)) {
            assertEquals("flag $bit must stay", bit, flags and bit)
        }
    }

    @Test fun untrustedFlagsOnAndroid13KeepOnlyTouchFeedbackFromTheVersionSpecificOnes() {
        assertEquals(
            base or touchFeedbackDisabled or trusted or ownDisplayGroup or alwaysUnlocked,
            ShellService.displayFlags(sdk = 33, trusted = true),
        )
        assertEquals(base or touchFeedbackDisabled, ShellService.displayFlags(sdk = 33, trusted = false))
    }

    @Test fun android12KeepsTheBaseFlagsEitherWay() {
        assertEquals(base, ShellService.displayFlags(sdk = 31, trusted = true))
        assertEquals(base, ShellService.displayFlags(sdk = 31, trusted = false))
    }

    @Test fun olderSystemsKeepTheBaseFlagsEitherWay() {
        assertEquals(base, ShellService.displayFlags(sdk = 32, trusted = true))
        assertEquals(base, ShellService.displayFlags(sdk = 32, trusted = false))
    }

    @Test fun onlySecurityExceptionGetsTheFallback() {
        assertTrue(ShellService.retryWithoutTrust(SecurityException("no ADD_TRUSTED_DISPLAY")))
        assertFalse(ShellService.retryWithoutTrust(IllegalStateException("create failed")))
        assertFalse(ShellService.retryWithoutTrust(RuntimeException("other")))
        assertFalse(ShellService.retryWithoutTrust(Throwable("other")))
    }
}
