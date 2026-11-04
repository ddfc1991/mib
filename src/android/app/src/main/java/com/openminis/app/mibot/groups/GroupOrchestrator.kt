package com.openminis.app.mibot.groups

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

/**
 * [fork/mibot-groups] Group chat orchestration. Pure Kotlin (no Android, no
 * provider types) so speaker selection, the round cap and the budget guard
 * are covered by JVM unit tests; [GroupLlmBackend] plugs the real model calls in.
 */

/** A member as the orchestrator sees it. [name] is what `@` matches and what other bots read. */
data class GroupBot(
    val id: String,
    val name: String,
    val description: String = "",
    /** Extra names `@` may use (e.g. the localized label of the built-in bot). */
    val aliases: List<String> = emptyList(),
)

data class BotReply(val text: String, val tokensUsed: Int)
data class RankResult(val botIds: List<String>, val tokensUsed: Int)

interface GroupBackend {
    /**
     * One short call ranking members by relevance to the latest user message.
     * Null (or a throw) = no opinion; the runner falls back to round-robin.
     */
    suspend fun rank(members: List<GroupBot>, transcript: List<GroupMessage>, limit: Int): RankResult?

    /** One bot's reply to the transcript so far. Throws on failure. */
    suspend fun reply(bot: GroupBot, members: List<GroupBot>, transcript: List<GroupMessage>): BotReply
}

enum class StopReason {
    /** Everyone who should speak spoke. */
    DONE,
    /** [GroupChat.maxRounds] replies reached with speakers still pending. */
    MAX_ROUNDS,
    /** The next call would exceed [GroupChat.tokenBudget]. */
    BUDGET,
}

data class TurnResult(val replies: List<GroupMessage>, val reason: StopReason, val tokensUsed: Int, val usedRanking: Boolean)

object SpeakerSelection {
    private val AT = charArrayOf('@', '＠')

    /**
     * Bot ids `@`-mentioned in [text], in order of first appearance, de-duplicated.
     * At each `@` the longest matching name (or alias) wins, so `@Writer2`
     * does not resolve to `Writer`. Matching is case-insensitive; a name must
     * end at a non-identifier char (or end of text).
     */
    fun mentions(text: String, members: List<GroupBot>): List<String> {
        val out = LinkedHashSet<String>()
        var i = text.indexOfAny(AT)
        while (i >= 0) {
            var best: GroupBot? = null
            var bestLen = 0
            for (m in members) {
                for (n in listOf(m.name) + m.aliases) {
                    if (n.isBlank() || n.length <= bestLen) continue
                    if (!text.regionMatches(i + 1, n, 0, n.length, ignoreCase = true)) continue
                    val end = i + 1 + n.length
                    val boundary = end >= text.length || !isNameChar(text[end]) || !isNameChar(n.last())
                    if (boundary) { best = m; bestLen = n.length }
                }
            }
            best?.let { out.add(it.id) }
            i = text.indexOfAny(AT, i + 1)
        }
        return out.toList()
    }

    private fun isNameChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '-'

    /**
     * Up to [count] distinct members, cycling from the one after [lastSpeakerId]
     * (or the first member when nobody has spoken).
     */
    fun roundRobin(members: List<GroupBot>, lastSpeakerId: String?, count: Int): List<String> {
        if (members.isEmpty() || count <= 0) return emptyList()
        val start = (members.indexOfFirst { it.id == lastSpeakerId } + 1).mod(members.size)
        return (0 until minOf(count, members.size)).map { members[(start + it) % members.size].id }
    }

    /**
     * Parse the ranking call's answer: a JSON array of names (preferred), or a
     * comma / newline separated list. Unknown names are dropped; null when
     * nothing usable remains.
     */
    fun parseRanking(raw: String, members: List<GroupBot>): List<String>? {
        val body = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = body.indexOf('[').takeIf { it >= 0 }?.let { s ->
            val e = body.lastIndexOf(']')
            if (e > s) runCatching { Json.parseToJsonElement(body.substring(s, e + 1)).jsonArray }.getOrNull() else null
        }
        val names: List<String> = arr?.mapNotNull { (it as? JsonPrimitive)?.content } ?: body.split(',', '\n', '、', '，')
        val ids = LinkedHashSet<String>()
        for (n in names) {
            val key = n.trim().trim('"', '\'', '@', '＠', '-', '*', ' ').trim()
            if (key.isEmpty()) continue
            members.firstOrNull { m -> m.name.equals(key, true) || m.aliases.any { it.equals(key, true) } || m.id == key }
                ?.let { ids.add(it.id) }
        }
        return ids.toList().ifEmpty { null }
    }
}

/** Rough token estimate: CJK ~1 token/char, everything else ~4 chars/token. */
object TokenEstimate {
    fun of(text: String): Int {
        var cjk = 0
        var other = 0
        for (c in text) if (c.code in 0x2E80..0x9FFF || c.code in 0xAC00..0xD7AF || c.code in 0xF900..0xFAFF || c.code in 0xFF00..0xFFEF) cjk++ else other++
        return cjk + (other + 3) / 4
    }

    fun of(messages: List<GroupMessage>): Int = messages.sumOf { of(it.text) + 4 }
}

/**
 * Runs one user turn: decides who speaks and in which order, calls the bots,
 * enforces [GroupChat.maxRounds] and [GroupChat.tokenBudget]. Cancellation
 * (the stop button) propagates as [CancellationException]; replies already
 * delivered through `onReply` stay delivered.
 */
class GroupTurnRunner(
    private val backend: GroupBackend,
    /** Tokens held back for each reply's output when checking the budget. */
    private val replyReserve: Int = REPLY_RESERVE,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        group: GroupChat,
        members: List<GroupBot>,
        transcript: List<GroupMessage>,
        onReply: suspend (GroupMessage) -> Unit = {},
    ): TurnResult {
        val maxRounds = group.maxRounds.coerceIn(1, GroupChat.MAX_ROUNDS_LIMIT)
        val budget = group.tokenBudget.coerceAtLeast(1)
        val byId = members.associateBy { it.id }
        val history = transcript.toMutableList()
        val replies = mutableListOf<GroupMessage>()
        var used = 0
        val userText = transcript.lastOrNull { it.role == GroupMessage.ROLE_USER }?.text.orEmpty()

        val queue = ArrayDeque(SpeakerSelection.mentions(userText, members))
        val explicit = queue.isNotEmpty()
        var usedRanking = false
        // Planned order for an un-addressed turn: relevance ranking, else round-robin.
        val planned = ArrayDeque<String>()
        if (!explicit) {
            val lastBot = transcript.lastOrNull { it.role == GroupMessage.ROLE_BOT && !it.error }?.botId
            val pickCost = TokenEstimate.of(transcript.takeLast(RANK_CONTEXT)) + RANK_RESERVE
            val ranked = if (members.size > 1 && pickCost <= budget) {
                currentCoroutineContext().ensureActive()
                val r = try { backend.rank(members, transcript, maxRounds) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                if (r != null) used += r.tokensUsed.coerceAtLeast(0)
                r?.botIds?.filter { it in byId }?.distinct()?.takeIf { it.isNotEmpty() }
            } else null
            usedRanking = ranked != null
            planned.addAll((ranked ?: SpeakerSelection.roundRobin(members, lastBot, maxRounds)).take(maxRounds))
        }

        var lastSpeaker: String? = null
        var reason = StopReason.DONE
        while (true) {
            val next = when {
                queue.isNotEmpty() -> queue.removeFirst()
                planned.isNotEmpty() -> planned.removeFirst()
                else -> null
            } ?: break
            if (replies.size >= maxRounds) { reason = StopReason.MAX_ROUNDS; break }
            if (next == lastSpeaker) continue
            val bot = byId[next] ?: continue
            val prompt = TokenEstimate.of(history) + TokenEstimate.of(bot.description) + SYSTEM_OVERHEAD
            if (used + prompt + replyReserve > budget) { reason = StopReason.BUDGET; break }
            currentCoroutineContext().ensureActive()
            val msg = try {
                val r = backend.reply(bot, members, history)
                used += r.tokensUsed.takeIf { it > 0 } ?: (prompt + TokenEstimate.of(r.text))
                GroupMessage(role = GroupMessage.ROLE_BOT, botId = bot.id, botName = bot.name, text = r.text.trim(), ts = now())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                used += prompt
                GroupMessage(role = GroupMessage.ROLE_BOT, botId = bot.id, botName = bot.name, text = e.message ?: e.javaClass.simpleName, ts = now(), error = true)
            }
            history.add(msg)
            replies.add(msg)
            lastSpeaker = bot.id
            onReply(msg)
            if (!msg.error) {
                // A bot may hand the floor to another member by @-mentioning it.
                for (id in SpeakerSelection.mentions(msg.text, members)) {
                    if (id != bot.id && id !in queue) queue.addLast(id)
                }
            }
        }
        return TurnResult(replies, reason, used, usedRanking)
    }

    companion object {
        const val REPLY_RESERVE = 800
        const val RANK_RESERVE = 300
        const val RANK_CONTEXT = 8
        const val SYSTEM_OVERHEAD = 200
    }
}

/** Builds what each model call sees. Pure; [GroupLlmBackend] maps it to provider messages. */
object GroupPrompt {
    data class Turn(val assistant: Boolean, val text: String)

    const val MAX_HISTORY_CHARS = 24_000

    fun systemPrompt(bot: GroupBot, members: List<GroupBot>, instructions: String): String = buildString {
        if (instructions.isNotBlank()) append(instructions.trim()).append("\n\n")
        append("## Group chat\n")
        append("You are \"").append(bot.name).append("\" in a group chat with the user and these bots: ")
        append(members.filter { it.id != bot.id }.joinToString(", ") { "\"${it.name}\"" }).append(".\n")
        append("Messages from others are prefixed with [Name]. Reply only as yourself, without a name prefix. ")
        append("Keep it short and add something new; do not repeat what others said. ")
        append("To hand the floor to another bot, mention it as @Name. Reply in the user's language.")
    }

    /**
     * The transcript from [bot]'s seat: its own messages are assistant turns,
     * everyone else's are user turns prefixed with the speaker. Adjacent same-role
     * turns are merged so providers that require alternation accept it; the
     * oldest messages are dropped past [maxChars]. Failed turns are hidden.
     */
    fun turns(bot: GroupBot, transcript: List<GroupMessage>, userLabel: String, nameOf: (GroupMessage) -> String, maxChars: Int = MAX_HISTORY_CHARS): List<Turn> {
        val visible = transcript.filter { !it.error }
        val kept = ArrayDeque<GroupMessage>()
        var chars = 0
        for (m in visible.asReversed()) {
            if (chars + m.text.length > maxChars && kept.isNotEmpty()) break
            kept.addFirst(m)
            chars += m.text.length
        }
        val out = mutableListOf<Turn>()
        for (m in kept) {
            val own = m.role == GroupMessage.ROLE_BOT && m.botId == bot.id
            val text = if (own) m.text else "[${if (m.role == GroupMessage.ROLE_USER) userLabel else nameOf(m)}]: ${m.text}"
            val last = out.lastOrNull()
            if (last != null && last.assistant == own) out[out.size - 1] = Turn(own, last.text + "\n\n" + text) else out.add(Turn(own, text))
        }
        if (out.firstOrNull()?.assistant == true) out.add(0, Turn(false, "[$userLabel]: (group chat started)"))
        if (out.isEmpty() || out.last().assistant) out.add(Turn(false, "(Continue as ${bot.name}.)"))
        return out
    }

    fun rankSystem(limit: Int): String =
        "You route messages in a group chat of AI bots. Pick the bots best suited to answer the latest user message, " +
            "most relevant first, at most $limit. Answer with only a JSON array of bot names, e.g. [\"A\",\"B\"]."

    fun rankUser(members: List<GroupBot>, transcript: List<GroupMessage>, userLabel: String): String = buildString {
        append("Bots:\n")
        members.forEach { append("- ").append(it.name).append(": ").append(it.description.take(200)).append('\n') }
        append("\nRecent messages:\n")
        transcript.filter { !it.error }.takeLast(GroupTurnRunner.RANK_CONTEXT).forEach { m ->
            val who = if (m.role == GroupMessage.ROLE_USER) userLabel else (m.botName ?: "bot")
            append('[').append(who).append("]: ").append(m.text.take(600)).append('\n')
        }
    }
}
