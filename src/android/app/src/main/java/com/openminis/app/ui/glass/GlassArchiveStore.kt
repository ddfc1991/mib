package com.openminis.app.ui.glass

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [fork/opencode-ui] Sidebar archive state.
 *
 * Archived chats/groups stay on disk and are still reachable from 「归档」 in
 * the drawer; only the mixed Recent list hides them. Keys are namespaced by
 * row type because a chat and a group chat can theoretically share an id.
 *
 * Kept in its own SharedPreferences file (mirrored into a StateFlow, same as
 * [GlassPrefs]) so this fork-level affordance does not require a Room schema
 * migration or change the meaning of `sessions.folder_id` for the rest of the
 * app. Deleting a row always drops its archive key; nothing can be archived
 * and then silently lost in a hidden list.
 */
object GlassArchiveStore {
    private const val FILE = "glass_archive"
    private const val KEY_IDS = "ids"

    private val state = MutableStateFlow<Set<String>>(emptySet())
    @Volatile private var loaded = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun flow(context: Context): StateFlow<Set<String>> {
        if (!loaded) synchronized(this) {
            if (!loaded) {
                state.value = prefs(context).getStringSet(KEY_IDS, emptySet())?.toSet().orEmpty()
                loaded = true
            }
        }
        return state.asStateFlow()
    }

    /** Add or remove one row; returns the new full set. */
    fun toggle(context: Context, key: String): Set<String> {
        flow(context)
        val next = if (key in state.value) state.value - key else state.value + key
        persist(context, next)
        return next
    }

    /** Used by the delete funnel so an archived id never lingers. */
    fun remove(context: Context, key: String): Set<String> {
        flow(context)
        if (key !in state.value) return state.value
        val next = state.value - key
        persist(context, next)
        return next
    }

    private fun persist(context: Context, next: Set<String>) {
        state.value = next
        prefs(context).edit().putStringSet(KEY_IDS, HashSet(next)).apply()
    }
}
