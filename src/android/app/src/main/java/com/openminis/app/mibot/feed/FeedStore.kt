package com.openminis.app.mibot.feed

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * [fork/mibot-feed] One 动态 entry: something that happened in the app
 * (a scheduled task finished, a key was saved, a plugin started, a group
 * chat turn ended). Persisted in `filesDir/feed/feed.json` — NOT Room.
 */
@Serializable
data class FeedEntry(
    val id: String,
    /** One of [FeedKind]; unknown kinds from a newer build still render. */
    val kind: String,
    val title: String,
    val body: String = "",
    val ts: Long,
    val liked: Boolean = false,
    /** Where it came from: `session:<id>`, `group:<id>`, `task:<id>`, `plugin:<name>`, `vault:<site>`; null = none. */
    val sourceRef: String? = null,
)

object FeedKind {
    const val TASK_RESULT = "task_result"
    const val KEY_ACQUIRED = "key_acquired"
    const val PLUGIN_EVENT = "plugin_event"
    const val FEIYU_DIARY = "feiyu_diary"
    const val GROUP_SUMMARY = "group_summary"
    val ALL = setOf(TASK_RESULT, KEY_ACQUIRED, PLUGIN_EVENT, FEIYU_DIARY, GROUP_SUMMARY)
}

@Serializable
data class FeedFile(val version: Int = 1, val entries: List<FeedEntry> = emptyList())

/**
 * File-backed feed, newest first, capped at [maxEntries] (oldest unliked
 * entries are dropped first). Written atomically (temp + rename), like
 * GroupChatStore. Pure JVM so it is unit-testable; Android entry is [Feed].
 */
class FeedStore(private val dir: File, private val maxEntries: Int = DEFAULT_MAX) {
    private val lock = Any()
    private val _entries = MutableStateFlow<List<FeedEntry>>(emptyList())

    /** Newest first. */
    val entries: StateFlow<List<FeedEntry>> = _entries.asStateFlow()

    init { refresh() }

    fun refresh() = synchronized(lock) { _entries.value = sorted(read()) }

    fun add(
        kind: String,
        title: String,
        body: String = "",
        sourceRef: String? = null,
        now: Long = System.currentTimeMillis(),
    ): FeedEntry? {
        val t = title.trim().take(MAX_TITLE)
        if (t.isEmpty()) return null
        val e = FeedEntry(
            id = UUID.randomUUID().toString(),
            kind = kind.trim().ifEmpty { FeedKind.PLUGIN_EVENT },
            title = t,
            body = body.trim().take(MAX_BODY),
            ts = now,
            sourceRef = sourceRef?.take(200),
        )
        synchronized(lock) { write(cap(read() + e)) }
        return e
    }

    fun setLiked(id: String, liked: Boolean): Boolean = mutate(id) { it.copy(liked = liked) }

    fun toggleLike(id: String): Boolean = mutate(id) { it.copy(liked = !it.liked) }

    fun delete(id: String): Boolean = synchronized(lock) {
        val cur = read()
        val next = cur.filterNot { it.id == id }
        if (next.size == cur.size) return false
        write(next)
        true
    }

    fun clear() = synchronized(lock) { write(emptyList()) }

    private fun mutate(id: String, f: (FeedEntry) -> FeedEntry): Boolean = synchronized(lock) {
        val cur = read()
        if (cur.none { it.id == id }) return false
        write(cur.map { if (it.id == id) f(it) else it })
        true
    }

    /** Over the cap: drop the oldest unliked first, then the oldest liked. */
    private fun cap(list: List<FeedEntry>): List<FeedEntry> {
        if (list.size <= maxEntries) return list
        var excess = list.size - maxEntries
        val drop = HashSet<String>()
        for (e in list.sortedBy { it.ts }) {
            if (excess == 0) break
            if (!e.liked) { drop += e.id; excess-- }
        }
        for (e in list.sortedBy { it.ts }) {
            if (excess == 0) break
            if (e.id !in drop) { drop += e.id; excess-- }
        }
        return list.filterNot { it.id in drop }
    }

    private fun file() = File(dir, FILE_NAME)

    private fun read(): List<FeedEntry> {
        val f = file()
        if (!f.isFile) return emptyList()
        return runCatching { JSON.decodeFromString(FeedFile.serializer(), f.readText()).entries }.getOrDefault(emptyList())
    }

    private fun write(list: List<FeedEntry>) {
        dir.mkdirs()
        val target = file()
        val tmp = File(dir, "$FILE_NAME.tmp")
        tmp.writeText(JSON.encodeToString(FeedFile.serializer(), FeedFile(entries = sorted(list))))
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) error("could not write $FILE_NAME")
        }
        _entries.value = sorted(list)
    }

    companion object {
        const val FILE_NAME = "feed.json"
        const val DEFAULT_MAX = 300
        const val MAX_TITLE = 120
        const val MAX_BODY = 4000
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private fun sorted(l: List<FeedEntry>) = l.sortedByDescending { it.ts }

        /** "刚刚 / 5 分钟前 / 3 小时前 / 昨天 / MM-dd" style label. */
        fun relativeTime(ts: Long, now: Long, zh: Boolean): String {
            val d = (now - ts).coerceAtLeast(0)
            val min = d / 60_000
            return when {
                min < 1 -> if (zh) "刚刚" else "just now"
                min < 60 -> if (zh) "$min 分钟前" else "${min}m ago"
                min < 24 * 60 -> if (zh) "${min / 60} 小时前" else "${min / 60}h ago"
                min < 48 * 60 -> if (zh) "昨天" else "yesterday"
                else -> java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
            }
        }
    }
}
