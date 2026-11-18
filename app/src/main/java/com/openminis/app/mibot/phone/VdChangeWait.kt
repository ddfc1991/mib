package com.openminis.app.mibot.phone

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * [fork/mibot-vd-stability] Wait until the agent display stops changing before
 * the next action.
 *
 * The old flow acted the instant the previous tap returned, so on a slow screen
 * (list animations, network spinners, a page still painting) the next read
 * captured the PREVIOUS screen — the model then reasoned about stale content and
 * tapped the wrong thing. This samples the accessibility tree until two
 * consecutive samples agree for [quietMs].
 *
 * Falls back to a fixed short delay when no accessibility service is attached,
 * because without the tree there is nothing to compare.
 */
object VdChangeWait {
    private const val POLL_MS = 120L

    private fun append(sb: StringBuilder, node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > 40) return
        val r = Rect()
        runCatching { node.getBoundsInScreen(r) }
        sb.append(node.className).append('|')
            .append(node.text).append('|')
            .append(node.contentDescription).append('|')
            .append(node.isClickable).append('|')
            .append(r.left).append(',').append(r.top).append(',')
            .append(r.right).append(',').append(r.bottom).append('\n')
        for (i in 0 until node.childCount) append(sb, node.getChild(i), depth + 1)
    }

    /** Stable hash of what is currently on [displayId]. */
    fun signature(displayId: Int): Int {
        val sb = StringBuilder()
        for (root in VdA11y.roots(displayId)) append(sb, root, 0)
        return sb.toString().hashCode()
    }

    /**
     * @return true when the screen went quiet, false when it kept moving until
     *         [timeoutMs] (the caller should still proceed — just warn).
     */
    suspend fun awaitStable(displayId: Int, quietMs: Long = 350, timeoutMs: Long = 5000): Boolean {
        if (VdA11y.service() == null) {
            delay(260)
            return false
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = signature(displayId)
        var stableSince = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_MS)
            val sig = signature(displayId)
            if (sig != last) {
                last = sig
                stableSince = System.currentTimeMillis()
                continue
            }
            if (System.currentTimeMillis() - stableSince >= quietMs) return true
        }
        return false
    }
}
