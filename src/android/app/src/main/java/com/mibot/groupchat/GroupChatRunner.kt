package com.mibot.groupchat

import com.mibot.core.model.Author
import com.mibot.core.model.Message
import com.mibot.core.model.ModelRole
import com.mibot.core.model.Part
import com.mibot.core.model.Role
import com.mibot.settings.ProviderStore

/**
 * 群聊编排:把一条群消息分给每位成员,让模型以该成员的身份回一句。
 * 只做一次不含工具的对话(群聊不需要读屏、跑命令),历史全部按用户消息+发言者前缀送出,
 * 这样多人都说过话也不会撞上「assistant 不能连发」的服务商限制。
 */
class GroupChatRunner(private val providers: ProviderStore) {
    /** 主模型是否可用;**null 说明还没配模型**。 */
    fun ready(): Boolean = providers.modelFor(ModelRole.Main) != null

    suspend fun ask(member: GroupMember, chat: GroupChat): Result<String> = runCatching {
        val model = member.configId?.let { providers.modelForConfig(it) }
            ?: chat.configId?.let { providers.modelForConfig(it) }
            ?: providers.modelFor(ModelRole.Main)
            ?: error("还没有可用模型,先到「设置 → 模型」里选一个主模型")
        val others = chat.members.filter { it.name != member.name }.joinToString("、") { it.name }
        val system = buildString {
            append("你在一个多人群聊里只扮演「").append(member.name).append("」这一位成员。")
            if (others.isNotBlank()) append("群里还有:").append(others).append("。")
            append("你只以这个身份说话:不替别人发言,不写旁白、舞台说明或括号里的动作,不在开头加自己的名字。")
            append("聊天记录每条都写成「名字:内容」,照着接话即可。回复短、口语、像真人,一般一两句,不要长篇。")
            if (member.prompt.isNotBlank()) append("\n\n你的人设:\n").append(member.prompt.trim())
        }
        val history = chat.messages.takeLast(HISTORY_LIMIT).map { m ->
            Message(
                role = Role.User,
                parts = listOf(Part.Text("${m.author}:${m.text}")),
                author = Author.User,
            )
        }
        val prompt = Message(
            role = Role.User,
            parts = listOf(Part.Text("(现在轮到你以「${member.name}」的身份说一句)")),
            author = Author.User,
        )
        val reply = model.complete(system, history + prompt, emptyList())
        val text = reply.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }.trim()
        if (text.isEmpty()) error("没有拿到回复")
        text
    }.recoverCatching { e ->
        // 给用户看的错误别带一长串堆栈
        throw IllegalStateException(e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "调用失败")
    }

    private companion object {
        /** 群里最多回看多少条:够接上下文,又不至于把整段历史每次都发出去。 */
        const val HISTORY_LIMIT = 40
    }
}
