package com.mibot.chat

import android.content.Context
import com.mibot.core.agent.Turn
import java.util.UUID

/** 当前对话(proactive 模块用):占位,完整版见 Miga chat/ConversationStore.kt。 */
sealed interface CurrentChat {
    data object Fresh : CurrentChat
    data class Open(val id: String) : CurrentChat
}

/**
 * 会话存储占位(proactive 模块用):内存实现,提醒对话不持久化(进程重启丢失)。
 * 后续接 mib-src 的会话体系(openminis.app),届时替换 [shared] 返回真实现。
 */
class ConversationStore {
    private val turns = mutableMapOf<String, MutableList<Turn>>()
    private val titles = mutableMapOf<String, String>()
    @Volatile private var current: CurrentChat? = null

    fun newConversation(): String = UUID.randomUUID().toString()

    fun turns(conversationId: String): List<Turn> = turns[conversationId]?.toList() ?: emptyList()

    fun append(conversationId: String, turn: Turn, files: List<SharedFile> = emptyList()) {
        turns.getOrPut(conversationId) { mutableListOf() }.add(turn)
    }

    fun setTitle(conversationId: String, title: String): Boolean {
        titles[conversationId] = title
        return true
    }

    fun setCurrent(chat: CurrentChat?) {
        current = chat
    }

    companion object {
        /** 占位工厂:每个调用方拿独立内存实例;后续接 MikaApp.conversations。 */
        fun shared(context: Context): ConversationStore = ConversationStore()
    }
}
