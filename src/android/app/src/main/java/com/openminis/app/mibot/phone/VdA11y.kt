/*
 * Adapted from Mika (https://github.com/wangzan101/Mika) @ 6661b9e:
 *   app/src/main/java/com/wangzan101/mika/cap/phone/a11y/ScreenAccessibility.kt (windowsOn, gesture, taps, dispatch)
 *   app/src/main/java/com/wangzan101/mika/cap/phone/a11y/AndroidScreen.kt (touch/click order, focusedEditable,
 *   setText, inputText paste, ancestorOrSelf, descendant)
 * Copyright 2026 wangzan101 — Licensed under the Apache License, Version 2.0.
 * Modifications for Mibot (com.dl1991.mibot), distributed under GPL-3.0-or-later:
 * package rename; works on MinisAccessibilityService.getInstance() instead of its own service (that service
 * is not modified); explicit fallback plans (VdInputOrder) shared by VdController; SET_TEXT read-back helper;
 * back only through the global action when the agent display is the top-focused one; English comments.
 */
package com.openminis.app.mibot.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.openminis.app.accessibility.MinisAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Fallback order for every input action on the agent display, Android-free so it is unit-tested.
 * Accessibility comes first (works on ROMs that block shell input injection, e.g. HyperOS without
 * "USB 调试（安全设置）"); shell injection from the helper is the fallback.
 */
object VdInputOrder {
    const val A11Y_GESTURE = "a11y"
    const val NODE_CLICK = "a11y-click"
    const val SHELL = "shell"
    const val SET_TEXT = "set-text"
    const val PASTE = "paste"
    const val KEYS = "keys"
    const val GLOBAL_BACK = "a11y-back"
    const val IME_ENTER = "a11y-enter"

    /** tap: a11y gesture with setDisplayId → ACTION_CLICK on the node under the point → shell tap. */
    fun tap(a11y: Boolean, helper: Boolean): List<String> =
        listOfNotNull(A11Y_GESTURE.takeIf { a11y }, NODE_CLICK.takeIf { a11y }, SHELL.takeIf { helper })

    /** swipe: a11y gesture on the display → shell stroke. */
    fun swipe(a11y: Boolean, helper: Boolean): List<String> =
        listOfNotNull(A11Y_GESTURE.takeIf { a11y }, SHELL.takeIf { helper })

    /**
     * type: ACTION_SET_TEXT with read-back (needs a focused editable node in the tree) → paste
     * (helper clipboard, then ACTION_PASTE on the node or Ctrl+V) → key injection (ASCII only).
     */
    fun type(a11y: Boolean, hasField: Boolean, helper: Boolean, ascii: Boolean): List<String> =
        listOfNotNull(
            SET_TEXT.takeIf { a11y && hasField },
            PASTE.takeIf { helper },
            KEYS.takeIf { helper && ascii },
        )

    /**
     * BACK: the global action only reaches the top-focused display, so it is used only when that is
     * the agent display (never sends BACK to the user's screen); otherwise the helper's key.
     */
    fun back(a11y: Boolean, displayTopFocused: Boolean, helper: Boolean): List<String> =
        listOfNotNull(GLOBAL_BACK.takeIf { a11y && displayTopFocused }, SHELL.takeIf { helper })

    /** ENTER: ACTION_IME_ENTER on the display's focused field (API 30+) → helper key. */
    fun enter(a11y: Boolean, hasField: Boolean, helper: Boolean): List<String> =
        listOfNotNull(IME_ENTER.takeIf { a11y && hasField }, SHELL.takeIf { helper })

    /** Other keys: only the helper can deliver them to a specific display. */
    fun key(helper: Boolean): List<String> = listOfNotNull(SHELL.takeIf { helper })

    /** One way of doing an action; [run] returns true when it worked. */
    class Step(val name: String, val run: suspend () -> Boolean)

    class Outcome(val path: String?, val tried: List<String>) {
        val ok get() = path != null
        /** "a11y" or "a11y-click after a11y" etc., for result strings and logs. */
        fun describe(): String = when {
            path == null && tried.isEmpty() -> "no input path available"
            path == null -> "tried ${tried.joinToString(" → ")}"
            tried.size > 1 -> "$path, after ${tried.dropLast(1).joinToString(" → ")} failed"
            else -> path
        }
    }

    /**
     * Runs the steps of [order] in turn until one returns true. A step that throws counts as failed;
     * cancellation propagates. Steps missing from [steps] are skipped.
     */
    suspend fun firstOk(order: List<String>, steps: List<Step>): Outcome {
        val tried = ArrayList<String>()
        for (name in order) {
            val step = steps.firstOrNull { it.name == name }?.run ?: continue
            tried += name
            val ok = try { step() } catch (e: CancellationException) { throw e } catch (_: Throwable) { false }
            if (ok) return Outcome(name, tried)
        }
        return Outcome(null, tried)
    }

    /**
     * Does a field read back as [expected]? Exact or whitespace-insensitive; a password field only
     * shows bullets, so the length has to match.
     */
    fun readBackMatches(expected: String, actual: String?, password: Boolean): Boolean {
        if (actual == null) return false
        if (actual == expected) return true
        if (password) return actual.length == expected.length
        return actual.filterNot { it.isWhitespace() } == expected.filterNot { it.isWhitespace() }
    }
}

/** Accessibility actions scoped to one display (the agent display, or display 0 in main-screen mode). */
object VdA11y {
    private const val MAX_ANCESTORS = 12
    private const val GESTURE_TIMEOUT_MS = 5_000L
    private const val TAP_MS = 60L
    private const val READBACK_MS = 600L
    private const val POLL_MS = 60L

    fun service(): MinisAccessibilityService? = MinisAccessibilityService.getInstance()

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Tops up the runtime service flags in case the system kept an old service config: not-important
     * views (many apps mark result text that way), interactive windows (needed for windowsOnAllDisplays);
     * touch exploration is dropped (the capability is not declared, so the flag only confuses).
     */
    fun ensureFlags(svc: AccessibilityService? = service()) {
        val info = svc?.serviceInfo ?: return
        val want = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        @Suppress("DEPRECATION")
        val drop = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        val flags = (info.flags or want) and drop.inv()
        if (flags != info.flags) runCatching {
            info.flags = flags
            svc.serviceInfo = info
        }
    }

    /** Windows on [displayId], top first; empty when the display has none (or the service is off). */
    fun windowsOn(displayId: Int): List<AccessibilityWindowInfo> {
        val svc = service() ?: return emptyList()
        val list = if (supported) svc.windowsOnAllDisplays.get(displayId).orEmpty()
        else if (displayId == 0) svc.windows.orEmpty() else emptyList()
        return list.sortedByDescending { it.layer }
    }

    fun roots(displayId: Int): List<AccessibilityNodeInfo> = windowsOn(displayId).mapNotNull { it.root }

    /** Is [displayId] the top-focused display (where global actions such as BACK land)? */
    fun isTopFocused(displayId: Int): Boolean {
        val svc = service() ?: return false
        if (!supported) return displayId == 0
        val active = svc.rootInActiveWindow ?: return false
        val wid = active.windowId
        return windowsOn(displayId).any { it.id == wid }
    }

    // ------------------------------------------------------------ gestures

    /** One-finger gesture along [path] on [displayId]; true when the system completed it. */
    suspend fun gesture(displayId: Int, path: Path, durationMs: Long): Boolean =
        dispatch(displayId, listOf(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))))

    suspend fun gesture(displayId: Int, paths: List<Path>, durationMs: Long): Boolean =
        dispatch(displayId, paths.map { GestureDescription.StrokeDescription(it, 0, durationMs.coerceAtLeast(1)) })

    /** [count] taps on one point in one gesture: each held [holdMs], [gapMs] apart. */
    suspend fun taps(displayId: Int, x: Float, y: Float, count: Int = 1, holdMs: Long = TAP_MS, gapMs: Long = 80): Boolean {
        val at = Path().apply { moveTo(x, y) }
        return dispatch(displayId, (0 until count).map { i ->
            GestureDescription.StrokeDescription(at, i * (holdMs + gapMs), holdMs.coerceAtLeast(1))
        })
    }

    suspend fun line(displayId: Int, points: List<Pair<Int, Int>>, durationMs: Long): Boolean {
        if (points.isEmpty()) return false
        val p = Path().apply {
            moveTo(points[0].first.toFloat(), points[0].second.toFloat())
            points.drop(1).forEach { lineTo(it.first.toFloat(), it.second.toFloat()) }
        }
        return gesture(displayId, p, durationMs)
    }

    private suspend fun dispatch(displayId: Int, strokes: List<GestureDescription.StrokeDescription>): Boolean {
        val svc = service() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        // Before API 30 a gesture can only target display 0.
        if (!supported && displayId != 0) return false
        val builder = GestureDescription.Builder()
        if (supported) builder.setDisplayId(displayId)
        strokes.forEach(builder::addStroke)
        val gesture = runCatching { builder.build() }.getOrNull() ?: return false
        val total = strokes.maxOf { it.startTime + it.duration }
        return withTimeoutOrNull(GESTURE_TIMEOUT_MS + total) {
            suspendCancellableCoroutine { cont ->
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
                }
                val sent = runCatching { svc.dispatchGesture(gesture, callback, null) }.getOrDefault(false)
                if (!sent && cont.isActive) cont.resume(false)
            }
        } ?: false
    }

    // ------------------------------------------------------------ nodes

    /** Smallest visible node under (x, y) on [displayId], searching the top window first. */
    fun nodeAt(displayId: Int, x: Int, y: Int): AccessibilityNodeInfo? {
        val r = Rect()
        for (root in roots(displayId)) {
            var best: AccessibilityNodeInfo? = null
            var bestArea = Long.MAX_VALUE
            fun walk(n: AccessibilityNodeInfo, depth: Int) {
                if (depth > 60) return
                n.getBoundsInScreen(r)
                if (!r.contains(x, y)) return
                val area = r.width().toLong() * r.height()
                if (n.isVisibleToUser && area <= bestArea) { best = n; bestArea = area }
                for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
            }
            walk(root, 0)
            if (best != null) return best
        }
        return null
    }

    /** ACTION_CLICK on the clickable, enabled ancestor-or-self of the node under (x, y). */
    fun clickAt(displayId: Int, x: Int, y: Int): Boolean {
        val node = nodeAt(displayId, x, y) ?: return false
        return click(node)
    }

    fun click(node: AccessibilityNodeInfo): Boolean {
        val target = ancestorOrSelf(node) { it.isClickable && it.isEnabled } ?: return false
        return runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
    }

    /** The editable field holding input focus on [displayId]; inside a focused container, its focused editable child. */
    fun focusedEditable(displayId: Int): AccessibilityNodeInfo? {
        for (root in roots(displayId)) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue
            if (focused.isEditable) return focused
            descendant(focused) { it.isFocused && it.isEditable }?.let { return it }
        }
        return null
    }

    fun inWebView(node: AccessibilityNodeInfo): Boolean =
        ancestorOrSelf(node) { it.className?.toString() == "android.webkit.WebView" } != null

    /** ACTION_SET_TEXT, then read the field back (polling briefly) and compare. */
    suspend fun setTextVerified(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        if (!runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)) return false
        return awaitText(node, text)
    }

    /** Polls the node's text for up to [READBACK_MS]; true when it reads back as [text]. */
    suspend fun awaitText(node: AccessibilityNodeInfo, text: String): Boolean {
        val deadline = SystemClock.uptimeMillis() + READBACK_MS
        while (true) {
            if (!node.refresh()) return false
            if (VdInputOrder.readBackMatches(text, node.text?.toString(), node.isPassword)) return true
            if (SystemClock.uptimeMillis() >= deadline) return false
            delay(POLL_MS)
        }
    }

    /** Selects the field's current content (so a paste replaces it, like SET_TEXT does). */
    fun selectAll(node: AccessibilityNodeInfo) {
        node.refresh()
        val len = node.text?.length ?: 0
        if (len == 0) return
        val range = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
        }
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, range) }
    }

    fun paste(node: AccessibilityNodeInfo): Boolean =
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrDefault(false)

    /** ACTION_IME_ENTER on the field (API 30+). */
    fun imeEnter(node: AccessibilityNodeInfo): Boolean {
        if (!supported) return false
        return runCatching { node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) }.getOrDefault(false)
    }

    fun globalBack(): Boolean =
        service()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true

    private inline fun ancestorOrSelf(node: AccessibilityNodeInfo, test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth <= MAX_ANCESTORS) {
            if (test(n)) return n
            n = n.parent
            depth++
        }
        return null
    }

    private fun descendant(node: AccessibilityNodeInfo, test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(node) }
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 500) {
            val n = queue.removeFirst()
            if (test(n)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return null
    }
}
