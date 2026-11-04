package com.openminis.app.mibot.groups

import android.content.Context
import com.openminis.app.MinisApp
import com.openminis.app.data.model.LLMMessage
import com.openminis.app.data.model.ModelEntry
import com.openminis.app.data.model.SubAgentDefinition
import com.openminis.app.data.model.ThinkingLevel
import com.openminis.app.data.repository.ProviderRepository
import com.openminis.app.mibot.design.tc
import com.openminis.app.provider.LLMProvider
import com.openminis.app.provider.ProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * [fork/mibot-groups] Real model calls for a group chat, through the same
 * provider path every non-UI caller uses (ProviderRepository → ProviderFactory
 * → LLMProvider.sendMessage). Read-only over the provider config and the Bot
 * roster: nothing here writes chat storage, prefs or bot definitions.
 */
class GroupLlmBackend(
    private val context: Context,
    private val repo: ProviderRepository,
    /** Stable conversation id for providers that want one (OpenCode Go). */
    private val conversationId: String,
    private val defs: Map<String, SubAgentDefinition>,
    private val userLabel: String,
) : GroupBackend {

    override suspend fun rank(members: List<GroupBot>, transcript: List<GroupMessage>, limit: Int): RankResult? {
        val entry = entriesFor(null).firstOrNull() ?: return null
        val provider = provider(entry) ?: return null
        val resp = withTimeout(RANK_TIMEOUT_MS) {
            provider.sendMessage(
                messages = listOf(LLMMessage(LLMMessage.Role.USER, GroupPrompt.rankUser(members, transcript, userLabel))),
                systemPrompt = GroupPrompt.rankSystem(limit),
                maxTokens = 120,
                thinkingLevel = ThinkingLevel.OFF,
            )
        }
        val ids = SpeakerSelection.parseRanking(resp.text, members) ?: return null
        return RankResult(ids, resp.usage?.let { it.inputTokens + it.outputTokens } ?: 0)
    }

    override suspend fun reply(bot: GroupBot, members: List<GroupBot>, transcript: List<GroupMessage>): BotReply {
        val def = defs[bot.id]
        val entries = entriesFor(def?.modelGroupId)
        if (entries.isEmpty()) error(tc(context, "没有可用的模型，请先在设置里配置模型", "No usable model — set one up in Settings"))
        val nameOf = { m: GroupMessage -> m.botId?.let { id -> members.firstOrNull { it.id == id }?.name } ?: m.botName ?: "Bot" }
        val turns = GroupPrompt.turns(bot, transcript, userLabel, nameOf)
        val messages = turns.map { LLMMessage(if (it.assistant) LLMMessage.Role.ASSISTANT else LLMMessage.Role.USER, it.text) }
        val system = GroupPrompt.systemPrompt(bot, members, def?.instructions.orEmpty())
        val level = def?.thinkingLevelOverride ?: ThinkingLevel.OFF
        var last: Exception? = null
        // The group's members in order, like a chat's fallback walk; two tries keeps a turn bounded.
        for (entry in entries.take(2)) {
            val provider = provider(entry) ?: continue
            try {
                val resp = withTimeout(REPLY_TIMEOUT_MS) {
                    provider.sendMessage(
                        messages = messages,
                        systemPrompt = system,
                        maxTokens = if (level == ThinkingLevel.OFF) 1200 else 4096,
                        thinkingLevel = level,
                    )
                }
                val text = resp.text.trim()
                if (text.isEmpty()) error(tc(context, "模型返回了空回复", "The model returned an empty reply"))
                return BotReply(text, resp.usage?.let { it.inputTokens + it.outputTokens } ?: 0)
            } catch (e: CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) last = Exception(tc(context, "回复超时", "Reply timed out")) else throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException(tc(context, "无法连接模型", "Could not reach a model"))
    }

    /** The bot's pinned group, else the sub-agent default, else the primary default. */
    private fun entriesFor(groupId: String?): List<ModelEntry> {
        val g = groupId?.let(repo::group)
            ?: repo.defaultSubGroupId?.let(repo::group)
            ?: repo.defaultPrimaryGroupId?.let(repo::group)
        return g?.let(repo::availableMemberEntries).orEmpty()
    }

    private suspend fun provider(entry: ModelEntry): LLMProvider? {
        val instance = repo.instance(entry.providerInstanceId) ?: return null
        val key = repo.requestCredential(instance) ?: return null
        return try {
            ProviderFactory.create(instance, key, entry.model, context, sessionId = conversationId, overrides = entry.overrides)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val RANK_TIMEOUT_MS = 20_000L
        const val REPLY_TIMEOUT_MS = 120_000L
    }
}

/**
 * Process-wide entry: the store at `filesDir/groups/`, the Bot roster (read
 * only), and the running turns. Turns run in an app scope so leaving the
 * screen does not kill them; [stop] cancels.
 */
object GroupChats {
    @Volatile private var store: GroupChatStore? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val _running = MutableStateFlow<Set<String>>(emptySet())
    val running: StateFlow<Set<String>> = _running.asStateFlow()
    private val _notes = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Last turn's stop note per group (budget / round cap / stopped). */
    val notes: StateFlow<Map<String, String>> = _notes.asStateFlow()

    fun store(context: Context): GroupChatStore =
        store ?: synchronized(this) {
            store ?: GroupChatStore(File(context.applicationContext.filesDir, "groups")).also { store = it }
        }

    /** Saved Bots, or empty before the app's repositories are up. */
    fun roster(context: Context): List<SubAgentDefinition> {
        val app = context.applicationContext as? MinisApp ?: return emptyList()
        return app.providerRepositoryOrNull?.subAgents.orEmpty()
    }

    fun asGroupBot(context: Context, def: SubAgentDefinition): GroupBot {
        val label = def.displayName(context)
        return GroupBot(
            id = def.id,
            name = label,
            description = def.displayDescription(context),
            aliases = if (label != def.name) listOf(def.name) else emptyList(),
        )
    }

    fun send(context: Context, groupId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty() || jobs[groupId]?.isActive == true) return
        val app = context.applicationContext
        val st = store(app)
        st.append(groupId, listOf(GroupMessage(role = GroupMessage.ROLE_USER, text = body, ts = System.currentTimeMillis()))) ?: return
        _notes.update { it - groupId }
        _running.update { it + groupId }
        val job = scope.launch {
            try {
                val file = st.load(groupId) ?: return@launch
                val repo = (app as? MinisApp)?.providerRepositoryOrNull
                    ?: error(tc(app, "应用尚未就绪", "App is not ready yet"))
                val defs = repo.subAgents.associateBy { it.id }
                val members = file.group.memberBotIds.mapNotNull { defs[it] }.map { asGroupBot(app, it) }
                if (members.isEmpty()) {
                    _notes.update { it + (groupId to tc(app, "群里的 Bot 都已被删除", "All bots in this group were deleted")) }
                    return@launch
                }
                val backend = GroupLlmBackend(app, repo, groupId, defs, tc(app, "用户", "User"))
                val result = GroupTurnRunner(backend).run(file.group, members, file.messages) { msg -> st.append(groupId, listOf(msg)) }
                val note = when (result.reason) {
                    StopReason.BUDGET -> tc(app, "已达本轮 token 预算，已停止", "Turn token budget reached — stopped")
                    StopReason.MAX_ROUNDS -> tc(app, "已达本轮发言上限（${file.group.maxRounds} 条）", "Reply limit for this turn reached (${file.group.maxRounds})")
                    StopReason.DONE -> null
                }
                if (note != null) _notes.update { it + (groupId to note) }
            } catch (e: CancellationException) {
                _notes.update { it + (groupId to tc(app, "已停止", "Stopped")) }
                throw e
            } catch (e: Exception) {
                _notes.update { it + (groupId to (e.message ?: e.javaClass.simpleName)) }
            } finally {
                jobs.remove(groupId)
                _running.update { it - groupId }
            }
        }
        jobs[groupId] = job
    }

    fun stop(groupId: String) {
        jobs[groupId]?.cancel()
    }

    fun delete(context: Context, groupId: String) {
        stop(groupId)
        store(context).delete(groupId)
        _notes.update { it - groupId }
    }
}
