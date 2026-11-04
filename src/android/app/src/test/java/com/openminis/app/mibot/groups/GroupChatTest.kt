package com.openminis.app.mibot.groups

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** [fork/mibot-groups] Speaker selection, round cap, budget guard, store round-trip. */
class GroupChatTest {
    private val planner = GroupBot("p", "Planner", "Plans trips")
    private val critic = GroupBot("c", "Critic", "Finds problems")
    private val writer = GroupBot("w", "Writer", "Writes copy", aliases = listOf("写手"))
    private val writer2 = GroupBot("w2", "Writer2", "Second writer")
    private val members = listOf(planner, critic, writer)

    private fun group(maxRounds: Int = 3, budget: Int = 100_000) =
        GroupChat("g", "t", members.map { it.id }, 0, 0, maxRounds = maxRounds, tokenBudget = budget)

    private fun user(text: String) = GroupMessage(role = GroupMessage.ROLE_USER, text = text, ts = 1)
    private fun bot(b: GroupBot, text: String) = GroupMessage(role = GroupMessage.ROLE_BOT, botId = b.id, botName = b.name, text = text, ts = 1)

    /** Scripted backend: records who was asked, replies from [script] or "ok". */
    private class Fake(
        val ranking: List<String>? = null,
        val rankThrows: Boolean = false,
        val script: Map<String, String> = emptyMap(),
        val tokensPerReply: Int = 10,
        val failFor: Set<String> = emptySet(),
    ) : GroupBackend {
        val asked = mutableListOf<String>()
        var rankCalls = 0
        override suspend fun rank(members: List<GroupBot>, transcript: List<GroupMessage>, limit: Int): RankResult? {
            rankCalls++
            if (rankThrows) error("rank failed")
            return ranking?.let { RankResult(it, 5) }
        }
        override suspend fun reply(bot: GroupBot, members: List<GroupBot>, transcript: List<GroupMessage>): BotReply {
            asked += bot.id
            if (bot.id in failFor) error("boom")
            return BotReply(script[bot.id] ?: "ok from ${bot.name}", tokensPerReply)
        }
    }

    // ---- mentions

    @Test fun mentionsInOrderDeduped() {
        assertEquals(listOf("c", "p"), SpeakerSelection.mentions("@Critic look, then @planner and @Critic again", members))
    }

    @Test fun mentionPrefersLongestName() {
        val ms = listOf(writer, writer2)
        assertEquals(listOf("w2"), SpeakerSelection.mentions("hi @Writer2!", ms))
        assertEquals(listOf("w"), SpeakerSelection.mentions("hi @Writer, ok", ms))
    }

    @Test fun mentionNeedsBoundaryAndSupportsAliasAndFullWidthAt() {
        assertTrue(SpeakerSelection.mentions("@Plannerx", members).isEmpty())
        assertEquals(listOf("w"), SpeakerSelection.mentions("＠写手 帮我写", members))
        assertTrue(SpeakerSelection.mentions("email a@b.com", members).isEmpty())
    }

    // ---- round robin / ranking parse

    @Test fun roundRobinStartsAfterLastSpeaker() {
        assertEquals(listOf("p", "c", "w"), SpeakerSelection.roundRobin(members, null, 3))
        assertEquals(listOf("w", "p"), SpeakerSelection.roundRobin(members, "c", 2))
        assertEquals(listOf("p", "c", "w"), SpeakerSelection.roundRobin(members, "w", 9))
        assertTrue(SpeakerSelection.roundRobin(emptyList(), null, 3).isEmpty())
    }

    @Test fun parseRankingJsonAndFallbacks() {
        assertEquals(listOf("w", "p"), SpeakerSelection.parseRanking("```json\n[\"Writer\", \"planner\", \"Nobody\"]\n```", members))
        assertEquals(listOf("c", "w"), SpeakerSelection.parseRanking("Critic, 写手", members))
        assertNull(SpeakerSelection.parseRanking("I think nobody", members))
    }

    // ---- runner

    @Test fun explicitMentionSpeaksOnlyThoseBots() = runTest {
        val fake = Fake(ranking = listOf("p", "c", "w"))
        val r = GroupTurnRunner(fake).run(group(), members, listOf(user("@Writer draft it")))
        assertEquals(listOf("w"), fake.asked)
        assertEquals(0, fake.rankCalls)
        assertEquals(StopReason.DONE, r.reason)
    }

    @Test fun botMentionHandsTheFloor() = runTest {
        val fake = Fake(script = mapOf("w" to "Draft done. @Critic thoughts?"))
        val r = GroupTurnRunner(fake).run(group(), members, listOf(user("@Writer draft it")))
        assertEquals(listOf("w", "c"), fake.asked)
        assertEquals(2, r.replies.size)
    }

    @Test fun rankingOrderUsedWhenNoMention() = runTest {
        val fake = Fake(ranking = listOf("w", "p"))
        val r = GroupTurnRunner(fake).run(group(), members, listOf(user("write a plan")))
        assertEquals(listOf("w", "p"), fake.asked)
        assertTrue(r.usedRanking)
        assertEquals(10 + 10 + 5, r.tokensUsed)
    }

    @Test fun rankingFailureFallsBackToRoundRobin() = runTest {
        val history = listOf(user("hi"), bot(planner, "hello"), user("next"))
        val fake = Fake(rankThrows = true)
        val r = GroupTurnRunner(fake).run(group(maxRounds = 2), members, history)
        assertEquals(listOf("c", "w"), fake.asked)
        assertFalse(r.usedRanking)
    }

    @Test fun maxRoundsCapsReplies() = runTest {
        // Bots keep pinging each other; cap must hold.
        val fake = Fake(script = mapOf("p" to "@Critic", "c" to "@Planner"))
        val r = GroupTurnRunner(fake).run(group(maxRounds = 3), members, listOf(user("@Planner go")))
        assertEquals(listOf("p", "c", "p"), fake.asked)
        assertEquals(StopReason.MAX_ROUNDS, r.reason)
    }

    @Test fun budgetStopsBeforeOverspending() = runTest {
        // reserve 800 + overhead 200 + prompt per call; budget allows exactly one call.
        val fake = Fake(ranking = listOf("p", "c", "w"), tokensPerReply = 500)
        val r = GroupTurnRunner(fake).run(group(budget = 1_200), members, listOf(user("hi")))
        assertEquals(listOf("p"), fake.asked)
        assertEquals(StopReason.BUDGET, r.reason)
    }

    @Test fun budgetTooSmallForAnythingSkipsCalls() = runTest {
        val fake = Fake(ranking = listOf("p"))
        val r = GroupTurnRunner(fake).run(group(budget = 50), members, listOf(user("hi")))
        assertTrue(fake.asked.isEmpty())
        assertEquals(0, fake.rankCalls)
        assertEquals(StopReason.BUDGET, r.reason)
    }

    @Test fun failingBotBecomesErrorAndOthersContinue() = runTest {
        val fake = Fake(ranking = listOf("p", "c"), failFor = setOf("p"))
        val r = GroupTurnRunner(fake).run(group(), members, listOf(user("hi")))
        assertEquals(listOf("p", "c"), fake.asked)
        assertTrue(r.replies[0].error)
        assertFalse(r.replies[1].error)
    }

    @Test fun cancellationKeepsDeliveredReplies() = runTest {
        val gate = CompletableDeferred<Unit>()
        val delivered = mutableListOf<GroupMessage>()
        val backend = object : GroupBackend {
            override suspend fun rank(members: List<GroupBot>, transcript: List<GroupMessage>, limit: Int) = RankResult(listOf("p", "c"), 0)
            override suspend fun reply(bot: GroupBot, members: List<GroupBot>, transcript: List<GroupMessage>): BotReply {
                if (bot.id == "c") gate.await()
                return BotReply("hi", 1)
            }
        }
        val job = async { GroupTurnRunner(backend).run(group(), members, listOf(user("hi"))) { delivered += it } }
        testScheduler.advanceUntilIdle()
        job.cancel()
        try { job.await(); fail("expected cancellation") } catch (_: CancellationException) {}
        assertEquals(listOf("p"), delivered.map { it.botId })
    }

    // ---- prompt shaping

    @Test fun turnsAlternateFromBotSeat() {
        val t = GroupPrompt.turns(critic, listOf(user("hi"), bot(planner, "plan"), bot(critic, "meh"), bot(critic, "also")), "User", { it.botName ?: "?" })
        assertEquals(listOf(false, true, false), t.map { it.assistant })
        assertEquals("[User]: hi\n\n[Planner]: plan", t[0].text)
        assertEquals("meh\n\nalso", t[1].text)
    }

    // ---- store

    @Test fun storeRoundTrip() {
        val dir = Files.createTempDirectory("groups").toFile()
        try {
            val s = GroupChatStore(dir)
            val g = s.create("评审组", listOf("p", "c", "p"), now = 100)
            assertEquals(listOf("p", "c"), g.memberBotIds)
            assertEquals(3, g.maxRounds)
            s.append(g.id, listOf(user("你好"), bot(planner, "hi")), now = 200)
            val reopened = GroupChatStore(dir)
            val f = reopened.load(g.id)
            assertNotNull(f)
            assertEquals(g.copy(updatedAt = 200), f!!.group)
            assertEquals(listOf("你好", "hi"), f.messages.map { it.text })
            assertEquals(listOf(g.id), reopened.groups.value.map { it.id })
            assertTrue(reopened.delete(g.id))
            assertTrue(reopened.groups.value.isEmpty())
            assertNull(reopened.load(g.id))
        } finally { dir.deleteRecursively() }
    }

    @Test fun storeRejectsBadIdsAndSmallGroups() {
        val dir = Files.createTempDirectory("groups").toFile()
        try {
            val s = GroupChatStore(dir)
            assertNull(s.load("../etc/passwd"))
            try { s.create("x", listOf("p")); fail() } catch (_: IllegalArgumentException) {}
            File(dir, "junk.json").writeText("not json")
            s.refresh()
            assertTrue(s.groups.value.isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
