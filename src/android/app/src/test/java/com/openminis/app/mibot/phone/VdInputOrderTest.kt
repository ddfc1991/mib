/*
 * Mibot (com.dl1991.mibot), GPL-3.0-or-later. Tests the input fallback order ported from Mika's
 * AndroidScreen (https://github.com/wangzan101/Mika @ 6661b9e, Apache-2.0): accessibility first,
 * shell injection as the fallback.
 */
package com.openminis.app.mibot.phone

import com.openminis.app.mibot.phone.VdInputOrder.A11Y_GESTURE
import com.openminis.app.mibot.phone.VdInputOrder.GLOBAL_BACK
import com.openminis.app.mibot.phone.VdInputOrder.IME_ENTER
import com.openminis.app.mibot.phone.VdInputOrder.KEYS
import com.openminis.app.mibot.phone.VdInputOrder.NODE_CLICK
import com.openminis.app.mibot.phone.VdInputOrder.PASTE
import com.openminis.app.mibot.phone.VdInputOrder.SET_TEXT
import com.openminis.app.mibot.phone.VdInputOrder.SHELL
import com.openminis.app.mibot.phone.VdInputOrder.Step
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VdInputOrderTest {

    @Test fun tapGestureThenNodeClickThenShell() {
        assertEquals(listOf(A11Y_GESTURE, NODE_CLICK, SHELL), VdInputOrder.tap(a11y = true, helper = true))
        assertEquals(listOf(SHELL), VdInputOrder.tap(a11y = false, helper = true))
        assertEquals(listOf(A11Y_GESTURE, NODE_CLICK), VdInputOrder.tap(a11y = true, helper = false))
        assertTrue(VdInputOrder.tap(a11y = false, helper = false).isEmpty())
    }

    @Test fun swipeGestureThenShell() {
        assertEquals(listOf(A11Y_GESTURE, SHELL), VdInputOrder.swipe(a11y = true, helper = true))
        assertEquals(listOf(SHELL), VdInputOrder.swipe(a11y = false, helper = true))
    }

    @Test fun typeSetTextThenPasteThenKeys() {
        assertEquals(listOf(SET_TEXT, PASTE, KEYS), VdInputOrder.type(a11y = true, hasField = true, helper = true, ascii = true))
        // No field in the tree: nothing to SET_TEXT on.
        assertEquals(listOf(PASTE, KEYS), VdInputOrder.type(a11y = true, hasField = false, helper = true, ascii = true))
        // Key injection only types ASCII.
        assertEquals(listOf(SET_TEXT, PASTE), VdInputOrder.type(a11y = true, hasField = true, helper = true, ascii = false))
        // Main-screen accessibility mode: no helper, so SET_TEXT only.
        assertEquals(listOf(SET_TEXT), VdInputOrder.type(a11y = true, hasField = true, helper = false, ascii = true))
    }

    @Test fun backUsesTheGlobalActionOnlyWhenTheDisplayIsTopFocused() {
        assertEquals(listOf(GLOBAL_BACK, SHELL), VdInputOrder.back(a11y = true, displayTopFocused = true, helper = true))
        // The user's screen has top focus: a global BACK would hit it, so only the helper's key.
        assertEquals(listOf(SHELL), VdInputOrder.back(a11y = true, displayTopFocused = false, helper = true))
        assertTrue(VdInputOrder.back(a11y = true, displayTopFocused = false, helper = false).isEmpty())
    }

    @Test fun enterImeActionThenShell() {
        assertEquals(listOf(IME_ENTER, SHELL), VdInputOrder.enter(a11y = true, hasField = true, helper = true))
        assertEquals(listOf(SHELL), VdInputOrder.enter(a11y = true, hasField = false, helper = true))
        assertEquals(listOf(SHELL), VdInputOrder.key(helper = true))
    }

    @Test fun firstOkStopsAtTheFirstSuccessAndReportsWhatWasTried() = runBlocking {
        val calls = mutableListOf<String>()
        val r = VdInputOrder.firstOk(
            listOf(A11Y_GESTURE, NODE_CLICK, SHELL),
            listOf(
                Step(A11Y_GESTURE) { calls += A11Y_GESTURE; false },
                Step(NODE_CLICK) { calls += NODE_CLICK; true },
                Step(SHELL) { calls += SHELL; true },
            ),
        )
        assertEquals(NODE_CLICK, r.path)
        assertEquals(listOf(A11Y_GESTURE, NODE_CLICK), calls)
        assertEquals("a11y-click, after a11y failed", r.describe())
    }

    @Test fun firstOkTreatsAThrowingStepAsFailedAndSkipsMissingSteps() = runBlocking {
        val r = VdInputOrder.firstOk(
            listOf(A11Y_GESTURE, NODE_CLICK, SHELL),
            listOf(
                Step(A11Y_GESTURE) { throw IllegalStateException("binder died") },
                Step(SHELL) { true },
            ),
        )
        assertEquals(SHELL, r.path)
        assertEquals(listOf(A11Y_GESTURE, SHELL), r.tried)
    }

    @Test fun firstOkReportsFailureWhenNothingWorks() = runBlocking {
        val r = VdInputOrder.firstOk(listOf(SET_TEXT, PASTE), listOf(Step(SET_TEXT) { false }, Step(PASTE) { false }))
        assertFalse(r.ok)
        assertNull(r.path)
        assertEquals("tried set-text → paste", r.describe())
        assertEquals("no input path available", VdInputOrder.firstOk(emptyList(), emptyList()).describe())
    }

    @Test(expected = CancellationException::class)
    fun firstOkLetsCancellationThrough() {
        runBlocking { VdInputOrder.firstOk(listOf(SHELL), listOf(Step(SHELL) { throw CancellationException("stop") })) }
    }

    @Test fun readBack() {
        assertTrue(VdInputOrder.readBackMatches("hello world", "hello world", password = false))
        assertTrue(VdInputOrder.readBackMatches("hello world", "hello  world", password = false))
        assertFalse(VdInputOrder.readBackMatches("hello", "Search", password = false))
        assertFalse(VdInputOrder.readBackMatches("hello", null, password = false))
        assertTrue(VdInputOrder.readBackMatches("secret", "••••••", password = true))
        assertFalse(VdInputOrder.readBackMatches("secret", "•••", password = true))
    }
}
