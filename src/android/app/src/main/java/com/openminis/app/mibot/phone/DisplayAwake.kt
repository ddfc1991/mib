/*
 * Adapted from Mika (https://github.com/wangzan101/Mika), app/src/main/java/com/wangzan101/mika/cap/phone/shell/DisplayAwake.kt @ 6661b9e
 * Copyright 2026 wangzan101 — Licensed under the Apache License, Version 2.0.
 * Modifications for Mibot (com.dl1991.mibot), distributed under GPL-3.0-or-later:
 * package rename, comments translated to English.
 */
package com.openminis.app.mibot.phone

import java.lang.reflect.Method

/**
 * Keeps Mibot's agent display awake. A display created with OWN_DISPLAY_GROUP (Android 13+
 * trusted display) is its own display group with its own screen-off timer; injected input is
 * not user activity, so at the screen-off timeout the group sleeps: frames stop compositing,
 * screenshots freeze, taps do nothing. While the display exists, report user activity for it
 * every [PERIOD_MS] (far below the shortest allowed timeout) and stop when it is released.
 * When [poke] cannot report (no per-display API), give up for good; the display still works.
 */
internal class DisplayAwake(
    /** Report one user activity for this display; false when that is impossible. */
    private val poke: (Int) -> Boolean,
    /** Run now and then every [PERIOD_MS]; returns a way to stop it. */
    private val repeat: (Runnable) -> () -> Unit,
) {
    private val running = HashMap<Int, () -> Unit>()
    @Volatile private var unavailable = false

    fun start(displayId: Int) {
        if (unavailable) return
        val cancel = repeat(Runnable { if (!poke(displayId)) giveUp() })
        synchronized(running) { running.put(displayId, cancel) }?.invoke()
        // giveUp may have run before registration finished on the very first poke: stop again.
        if (unavailable) stopAll()
    }

    fun stop(displayId: Int) {
        synchronized(running) { running.remove(displayId) }?.invoke()
    }

    fun stopAll() {
        synchronized(running) { running.values.toList().also { running.clear() } }.forEach { it() }
    }

    fun isRunning(displayId: Int): Boolean = synchronized(running) { displayId in running }

    private fun giveUp() {
        unavailable = true
        stopAll()
    }

    companion object {
        const val PERIOD_MS = 4_000L
        // PowerManager.USER_ACTIVITY_EVENT_TOUCH
        const val EVENT_TOUCH = 2
        // Display.DEFAULT_DISPLAY_GROUP (hidden constant)
        private const val DEFAULT_DISPLAY_GROUP = 0

        /**
         * Only report for a display that really is its own group. Reporting to the default group
         * (the user's main screen) would keep the user's screen on too. [groupId] null = unreadable,
         * then decide by whether it was created with [ownGroupFlag].
         */
        fun needed(flags: Int, ownGroupFlag: Int, groupId: Int?): Boolean =
            flags and ownGroupFlag != 0 && groupId != DEFAULT_DISPLAY_GROUP

        /**
         * IPowerManager's per-display overload `userActivity(int displayId, long time, int event, int flags)`
         * (Android 12+). Null when only the old overload exists: it reports to the default group and
         * would keep the user's main screen on, so it is not used.
         */
        fun userActivityMethod(cls: Class<*>): Method? = cls.methods.firstOrNull {
            it.name == "userActivity" && it.parameterTypes.contentEquals(PER_DISPLAY)
        }

        private val PER_DISPLAY = arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    }
}
