package com.mibot.groupchat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 群里的一位成员:固定下名字与提示词(人格正文),这样人格被删掉后群里的历史仍然说得出是谁。 */
@Serializable
data class GroupMember(
    val name: String,
    val prompt: String = "",
    /** 来源人格;人格删了不影响群(名字和提示词已抄一份)。 */
    val personaId: String = "",
    /** 这位成员单独用的模型配置;空表示跟随群设置。 */
    val configId: String? = null,
)

/** 群里的一条消息。[fromBot] 表示是某个成员说的,否则是用户说的。 */
@Serializable
data class GroupMessage(
    val at: Long = 0L,
    val author: String,
    val text: String,
    val fromBot: Boolean = false,
)

/** 一个群聊:标题 + 成员 + 消息记录。[configId] 为空表示成员都用主模型。 */
@Serializable
data class GroupChat(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val members: List<GroupMember> = emptyList(),
    val messages: List<GroupMessage> = emptyList(),
    val createdAt: Long = 0L,
    val configId: String? = null,
)

@Serializable
private data class GroupChatFile(val version: Int = 1, val chats: List<GroupChat> = emptyList())

/**
 * 群聊存储:`files/settings/groupchats.json`。原子写、带版本号,读不懂时把原文件另存成 `.bad-<时间>` 并当空列表。
 * 不含密钥,不加密。
 */
class GroupChatStore(private val dir: File) {
    private val file = File(dir, "groupchats.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val _chats = MutableStateFlow(load())
    val chats: StateFlow<List<GroupChat>> = _chats.asStateFlow()

    fun get(id: String?): GroupChat? = id?.let { key -> _chats.value.firstOrNull { it.id == key } }

    @Synchronized
    fun create(title: String, members: List<GroupMember>, configId: String? = null): GroupChat {
        val name = title.trim().ifBlank { members.joinToString("、") { it.name }.ifBlank { "新群聊" } }
        val chat = GroupChat(
            title = name, members = members, configId = configId,
            createdAt = System.currentTimeMillis(),
        )
        save(_chats.value + chat)
        return chat
    }

    @Synchronized
    fun append(id: String, message: GroupMessage) {
        if (_chats.value.none { it.id == id }) return
        save(_chats.value.map { if (it.id == id) it.copy(messages = it.messages + message) else it })
    }

    /** 群用的模型配置;null 表示回到群设置里选的那个,再没有就用主模型。 */
    @Synchronized
    fun setConfig(id: String, configId: String?) {
        save(_chats.value.map { if (it.id == id) it.copy(configId = configId) else it })
    }

    @Synchronized
    fun rename(id: String, title: String) {
        val name = title.trim()
        if (name.isEmpty()) return
        save(_chats.value.map { if (it.id == id) it.copy(title = name) else it })
    }

    @Synchronized
    fun clear(id: String) {
        save(_chats.value.map { if (it.id == id) it.copy(messages = emptyList()) else it })
    }

    @Synchronized
    fun remove(id: String) {
        val next = _chats.value.filterNot { it.id == id }
        if (next.size != _chats.value.size) save(next)
    }

    @Synchronized private fun save(list: List<GroupChat>) {
        dir.mkdirs()
        val text = json.encodeToString(GroupChatFile(chats = list))
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
        _chats.value = list
    }

    private fun load(): List<GroupChat> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<GroupChatFile>(file.readText()).chats }
            .getOrElse {
                runCatching { file.copyTo(File(dir, "${file.name}.bad-${System.currentTimeMillis()}"), true) }
                emptyList()
            }
    }
}
