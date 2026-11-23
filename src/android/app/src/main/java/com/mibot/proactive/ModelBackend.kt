package com.mibot.proactive

import com.mibot.MikaApp
import com.mibot.core.model.Capabilities
import com.mibot.core.model.ChatModel
import com.mibot.core.model.Message
import com.mibot.core.model.ModelReply
import com.mibot.core.model.ModelRole
import com.mibot.core.model.Part
import com.mibot.core.model.Role
import com.mibot.core.model.StopReason
import com.mibot.core.model.ToolSpec
import com.mibot.core.model.Usage
import com.mibot.core.proactive.ProactiveCall
import com.mibot.core.proactive.TargetedModel
import com.mibot.core.proactive.TriageTarget
import com.openminis.app.MinisApp
import com.openminis.app.data.model.LLMMessage
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.model.ModelEntry
import com.openminis.app.provider.LLMProvider
import com.openminis.app.provider.ProviderFactory

/**
 * 5 模块(calls/proactive/wechat/sms/pocket)用的模型后端:把 mib-src 的
 * ProviderRepository/ProviderFactory 接进 [MikaApp.providers]。宿主
 * (MinisApp)在子系统初始化完成后注入;注入前 [MikaApp.ModelProviders]
 * 保持占位(modelFor 返回 null,模块降级)。
 */
interface ModelBackend {
    /** 5 模块按角色取模型;取不到(未配模型)为 null。 */
    fun chatModel(role: ModelRole): ChatModel?

    /** 主动能力:此刻选定的判定目标(同意页对着它展示);没配为 null。 */
    fun triageTarget(): TriageTarget?

    /** 主动能力:对着目标建好的一次模型(解密 Key 在这里做,失败为 null)。 */
    fun triageModel(call: ProactiveCall): TargetedModel?
}

/**
 * [LLMProvider] → [ChatModel] 的桥:主动判定/归纳(core proactive)全是
 * 纯文本调用(tools 恒 emptyList()),这里按纯文本转,不支持工具。
 */
class LlmChatModel(
    private val provider: LLMProvider,
    override val name: String,
    private val maxOutputTokens: Int = 2048,
) : ChatModel {
    override val capabilities: Capabilities =
        Capabilities(toolCalls = false, vision = false, contextTokens = 0, reasoning = false)

    override suspend fun complete(
        system: String,
        messages: List<Message>,
        tools: List<ToolSpec>,
    ): ModelReply {
        if (tools.isNotEmpty()) {
            // core proactive 不带工具;带工具说明被误用,直接拒绝而不是静默丢
            throw UnsupportedOperationException("LlmChatModel 不支持工具调用")
        }
        val llmMessages = messages.map { m ->
            LLMMessage(
                role = if (m.role == Role.Assistant) LLMMessage.Role.ASSISTANT else LLMMessage.Role.USER,
                content = m.parts.filterIsInstance<Part.Text>().joinToString("") { it.text },
            )
        }
        val resp = provider.sendMessage(
            messages = llmMessages,
            systemPrompt = system.ifBlank { null },
            maxTokens = maxOutputTokens,
        )
        return ModelReply(
            parts = if (resp.text.isEmpty()) emptyList() else listOf(Part.Text(resp.text)),
            stopReason = when (resp.stopReason) {
                "tool_calls" -> StopReason.ToolUse
                "length", "max_tokens" -> StopReason.MaxTokens
                else -> StopReason.EndTurn
            },
            usage = resp.usage?.let { Usage(it.inputTokens, it.outputTokens) },
        )
    }
}

/**
 * 从 [MinisApp] 的 ProviderRepository 现读配置、现建模型(照 GroupChats/
 * VisionGroupResolver 的做法:每次调用都按当下的配置建,配置变了自然生效)。
 * 选第一个有凭据的实例的第一个非隐藏条目作 5 模块的默认模型。
 */
class MinisModelBackend(private val app: MinisApp) : ModelBackend {

    private fun pick(): Pair<ProviderInstance, ModelEntry>? {
        val repo = app.providerRepositoryOrNull ?: return null
        val config = repo.config.value
        for (instance in config.instances) {
            if (!repo.hasAnyCredential(instance)) continue
            val entry = config.modelEntries.firstOrNull {
                it.providerInstanceId == instance.id && !it.isHidden
            } ?: continue
            return instance to entry
        }
        return null
    }

    private fun build(): TargetedModel? {
        val repo = app.providerRepositoryOrNull ?: return null
        val (instance, entry) = pick() ?: return null
        val apiKey = repo.usableApiKey(instance) ?: return null
        return runCatching {
            val provider = ProviderFactory.create(
                instance, apiKey, entry.baseModel, app,
                sessionId = "proactive", overrides = entry.overrides,
            )
            TargetedModel(
                TriageTarget(
                    providerId = instance.id,
                    providerName = instance.label.ifEmpty { entry.baseModel.provider },
                    model = entry.baseModel.id,
                ),
                LlmChatModel(provider, entry.baseModel.id),
            )
        }.getOrNull()
    }

    override fun triageTarget(): TriageTarget? = pick()?.let { (instance, entry) ->
        TriageTarget(
            providerId = instance.id,
            providerName = instance.label.ifEmpty { entry.baseModel.provider },
            model = entry.baseModel.id,
        )
    }

    override fun triageModel(call: ProactiveCall): TargetedModel? = build()

    override fun chatModel(role: ModelRole): ChatModel? = build()?.model
}
