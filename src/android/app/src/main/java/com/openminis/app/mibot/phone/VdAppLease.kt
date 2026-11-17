package com.openminis.app.mibot.phone

import kotlinx.coroutines.delay

/**
 * [fork/mibot-vd-stability] One app at a time on the agent display.
 *
 * There is a single virtual display, so a second task that launches a DIFFERENT
 * app silently replaces what the first task was looking at — the first task then
 * taps into a screen it never opened. Android also keeps most apps single-instance,
 * so "just open another copy" is not available either.
 *
 * This is a cooperative lease: a launcher waits (up to [timeoutMs]) while another
 * package holds the display, and the hold expires on its own when nobody has
 * touched the display for [IDLE_MS] — so a crashed or abandoned task cannot wedge
 * it forever.
 */
object VdAppLease {
    private const val IDLE_MS = 120_000L
    private const val POLL_MS = 500L

    @Volatile private var current: String? = null
    @Volatile private var lastTouch: Long = 0L

    /** Called after every successful interaction, so an active task keeps the lease. */
    fun touch() {
        lastTouch = System.currentTimeMillis()
    }

    /** The package currently holding the display, or null when free / expired. */
    fun holder(): String? {
        val h = current ?: return null
        if (System.currentTimeMillis() - lastTouch > IDLE_MS) {
            current = null
            return null
        }
        return h
    }

    suspend fun acquire(pkg: String, timeoutMs: Long = 180_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val h = holder()
            if (h == null || h == pkg) {
                current = pkg
                touch()
                return true
            }
            if (System.currentTimeMillis() >= deadline) return false
            delay(POLL_MS)
        }
    }

    fun release(pkg: String) {
        if (current == pkg) current = null
    }
}
