package com.openminis.app.mibot.ideas

import com.openminis.app.scheduled.ScheduledRepeatMode
import com.openminis.app.scheduled.ScheduledTask
import java.util.Calendar

/**
 * [fork/mibot-ideas] 「变成任务」: an idea becomes a one-shot scheduled task
 * (NEW_SESSION, CALENDAR trigger, repeat ONCE on the picked day) created
 * through the existing ScheduledTaskManager.create().
 */
object IdeaTasks {
    fun label(text: String): String =
        text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(40).orEmpty().ifEmpty { "点子" }

    /** Local wall-clock ms for [hour]:[minute] on the day starting at [dayStartMs]. */
    fun fireAt(dayStartMs: Long, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        timeInMillis = dayStartMs
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Local start-of-day for [ms]. */
    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Default pick: the next whole hour at least 30 minutes away. */
    fun defaultFire(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now + 30 * 60_000L
        if (get(Calendar.MINUTE) != 0 || get(Calendar.SECOND) != 0 || get(Calendar.MILLISECOND) != 0) add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun oneShot(text: String, dayStartMs: Long, hour: Int, minute: Int, label: String = label(text)): ScheduledTask =
        ScheduledTask(
            label = label.trim().ifEmpty { label(text) }.take(60),
            timeOfDayHour = hour.coerceIn(0, 23),
            timeOfDayMinute = minute.coerceIn(0, 59),
            repeatMode = ScheduledRepeatMode.ONCE,
            prompt = text.trim(),
            startDateMs = startOfDay(dayStartMs),
        )
}
