package com.openminis.app.mibot.groups

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * [fork/mibot-groups] A bot group chat. Persisted as one JSON file per group
 * under `filesDir/groups/` (see [GroupChatStore]) — deliberately NOT Room, so
 * the chat database schema is untouched.
 */
@Serializable
data class GroupChat(
    val id: String,
    val title: String,
    /** Saved Bot (SubAgentDefinition) ids, in the order the user picked them. */
    val memberBotIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    /** Max bot replies per user turn. */
    val maxRounds: Int = DEFAULT_MAX_ROUNDS,
    /** Token ceiling for one user turn (all bot calls + the speaker pick). */
    val tokenBudget: Int = DEFAULT_TOKEN_BUDGET,
) {
    companion object {
        const val DEFAULT_MAX_ROUNDS = 3
        const val MAX_ROUNDS_LIMIT = 8
        const val DEFAULT_TOKEN_BUDGET = 24_000
    }
}

@Serializable
data class GroupMessage(
    val id: String = UUID.randomUUID().toString(),
    /** [ROLE_USER] or [ROLE_BOT]. */
    val role: String,
    /** Set for bot messages. */
    val botId: String? = null,
    /** Bot name at send time, so a deleted bot still renders. */
    val botName: String? = null,
    val text: String,
    val ts: Long,
    /** True for a bot turn that failed (text holds the error). Never shown to bots. */
    val error: Boolean = false,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_BOT = "bot"
    }
}

@Serializable
data class GroupChatFile(
    val version: Int = 1,
    val group: GroupChat,
    val messages: List<GroupMessage> = emptyList(),
)

/**
 * File-backed store. One `<id>.json` per group, written atomically
 * (temp file + rename). Pure JVM so it is unit-testable; the Android entry is
 * [GroupChats.store].
 */
class GroupChatStore(private val dir: File) {
    private val lock = Any()
    private val _groups = MutableStateFlow<List<GroupChat>>(emptyList())

    /** All groups, newest activity first. Drives the tab list and the drawer. */
    val groups: StateFlow<List<GroupChat>> = _groups.asStateFlow()

    init { refresh() }

    fun refresh() = synchronized(lock) {
        _groups.value = (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { readFile(it)?.group }
            .sortedByDescending { it.updatedAt }
    }

    fun load(id: String): GroupChatFile? = synchronized(lock) { fileFor(id)?.let(::readFile) }

    fun create(title: String, memberBotIds: List<String>, now: Long = System.currentTimeMillis()): GroupChat {
        require(memberBotIds.distinct().size >= 2) { "a group needs at least 2 bots" }
        val g = GroupChat(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "群聊" }.take(60),
            memberBotIds = memberBotIds.distinct(),
            createdAt = now,
            updatedAt = now,
        )
        synchronized(lock) { write(GroupChatFile(group = g)) }
        return g
    }

    fun append(id: String, messages: List<GroupMessage>, now: Long = System.currentTimeMillis()): GroupChatFile? = synchronized(lock) {
        val cur = load(id) ?: return null
        val next = cur.copy(group = cur.group.copy(updatedAt = now), messages = cur.messages + messages)
        write(next)
        next
    }

    fun updateGroup(group: GroupChat): GroupChatFile? = synchronized(lock) {
        val cur = load(group.id) ?: return null
        val next = cur.copy(group = group.copy(id = cur.group.id, createdAt = cur.group.createdAt))
        write(next)
        next
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        val ok = fileFor(id)?.delete() == true
        refresh()
        ok
    }

    private fun write(f: GroupChatFile) {
        val target = fileFor(f.group.id) ?: error("bad group id")
        dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(JSON.encodeToString(GroupChatFile.serializer(), f))
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) error("could not write ${target.name}")
        }
        refresh()
    }

    private fun readFile(file: File): GroupChatFile? =
        runCatching { JSON.decodeFromString(GroupChatFile.serializer(), file.readText()) }.getOrNull()

    /** Ids are UUIDs we mint; anything else (path tricks) is rejected. */
    private fun fileFor(id: String): File? =
        if (ID_RE.matches(id)) File(dir, "$id.json") else null

    companion object {
        private val ID_RE = Regex("[A-Za-z0-9-]{1,64}")
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
