package com.openminis.app.mibot.ideas

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * [fork/mibot-ideas] One 点子 note. Persisted in `filesDir/ideas/ideas.json`
 * — NOT Room.
 */
@Serializable
data class Idea(
    val id: String,
    val text: String,
    /** Palette index 0 until [IdeaStore.COLOR_COUNT]. */
    val color: Int = 0,
    val pinned: Boolean = false,
    val updatedAt: Long,
)

@Serializable
data class IdeaFile(val version: Int = 1, val ideas: List<Idea> = emptyList())

/**
 * File-backed notes: pinned first, then most recently edited. Atomic writes
 * (temp + rename), like GroupChatStore. Pure JVM so it is unit-testable;
 * the Android entry is [Ideas].
 */
class IdeaStore(private val dir: File) {
    private val lock = Any()
    private val _ideas = MutableStateFlow<List<Idea>>(emptyList())
    val ideas: StateFlow<List<Idea>> = _ideas.asStateFlow()

    init { refresh() }

    fun refresh() = synchronized(lock) { _ideas.value = ordered(read()) }

    fun get(id: String): Idea? = _ideas.value.firstOrNull { it.id == id }

    /** Null when [text] is blank. */
    fun add(text: String, color: Int = 0, pinned: Boolean = false, now: Long = System.currentTimeMillis()): Idea? {
        val body = text.trim().take(MAX_TEXT)
        if (body.isEmpty()) return null
        val idea = Idea(UUID.randomUUID().toString(), body, clampColor(color), pinned, now)
        synchronized(lock) { write(read() + idea) }
        return idea
    }

    /** Edit text and/or color (bumps updatedAt). A blank text is rejected (returns null). */
    fun update(id: String, text: String? = null, color: Int? = null, now: Long = System.currentTimeMillis()): Idea? = synchronized(lock) {
        val cur = read()
        val old = cur.firstOrNull { it.id == id } ?: return null
        val newText = text?.trim()?.take(MAX_TEXT)
        if (newText != null && newText.isEmpty()) return null
        val next = old.copy(text = newText ?: old.text, color = color?.let(::clampColor) ?: old.color, updatedAt = now)
        write(cur.map { if (it.id == id) next else it })
        next
    }

    /** Pin/unpin; does not change updatedAt. */
    fun setPinned(id: String, pinned: Boolean): Idea? = synchronized(lock) {
        val cur = read()
        val old = cur.firstOrNull { it.id == id } ?: return null
        val next = old.copy(pinned = pinned)
        write(cur.map { if (it.id == id) next else it })
        next
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val cur = read()
        val next = cur.filterNot { it.id == id }
        if (next.size == cur.size) return false
        write(next)
        true
    }

    private fun file() = File(dir, FILE_NAME)

    private fun read(): List<Idea> {
        val f = file()
        if (!f.isFile) return emptyList()
        return runCatching { JSON.decodeFromString(IdeaFile.serializer(), f.readText()).ideas }.getOrDefault(emptyList())
    }

    private fun write(list: List<Idea>) {
        dir.mkdirs()
        val target = file()
        val tmp = File(dir, "$FILE_NAME.tmp")
        tmp.writeText(JSON.encodeToString(IdeaFile.serializer(), IdeaFile(ideas = list)))
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) error("could not write $FILE_NAME")
        }
        _ideas.value = ordered(list)
    }

    companion object {
        const val FILE_NAME = "ideas.json"
        const val COLOR_COUNT = 6
        const val MAX_TEXT = 4000
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun clampColor(c: Int) = if (c in 0 until COLOR_COUNT) c else 0
        fun ordered(l: List<Idea>) = l.sortedWith(compareByDescending<Idea> { it.pinned }.thenByDescending { it.updatedAt })
    }
}
