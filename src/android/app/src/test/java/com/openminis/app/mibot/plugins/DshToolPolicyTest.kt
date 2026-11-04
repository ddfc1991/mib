package com.openminis.app.mibot.plugins

import com.openminis.app.mibot.plugins.DshToolPolicy.Decision as D
import com.openminis.app.mibot.plugins.DshToolPolicy.Setting as S
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork/mibot-dsh-tools] D3: per-tool policy, run memory, argument preview, output scrub + clip. */
class DshToolPolicyTest {
    private val P = DshToolPolicy

    // ------------------------------------------------------------ settings + decisions

    @Test fun `defaults by risk - read runs, write and external ask`() {
        assertEquals(S.ALLOW, P.settingOf(emptyMap(), "t", DshToolRisk.READ))
        assertEquals(S.ASK, P.settingOf(emptyMap(), "t", DshToolRisk.WRITE))
        assertEquals(S.ASK, P.settingOf(emptyMap(), "t", DshToolRisk.EXTERNAL))
        assertEquals(D.RUN, P.decide(P.settingOf(emptyMap(), "t", DshToolRisk.READ), DshToolRisk.READ, false))
        assertEquals(D.ASK, P.decide(P.settingOf(emptyMap(), "t", DshToolRisk.WRITE), DshToolRisk.WRITE, false))
        assertEquals(D.ASK, P.decide(P.settingOf(emptyMap(), "t", DshToolRisk.EXTERNAL), DshToolRisk.EXTERNAL, false))
    }

    @Test fun `stored setting wins, junk falls back to default`() {
        assertEquals(S.DENY, P.settingOf(mapOf("t" to "deny"), "t", DshToolRisk.READ))
        assertEquals(S.ALLOW, P.settingOf(mapOf("t" to "ALLOW"), "t", DshToolRisk.WRITE))
        assertEquals(S.ASK, P.settingOf(mapOf("t" to "maybe"), "t", DshToolRisk.WRITE))
        assertEquals(S.ALLOW, P.settingOf(mapOf("other" to "deny"), "t", DshToolRisk.READ))
        assertNull(S.parse("nope"))
        assertEquals(S.ALWAYS, S.parse(" always "))
    }

    @Test fun `write set to allow runs, external set to allow still asks, always runs`() {
        assertEquals(D.RUN, P.decide(S.ALLOW, DshToolRisk.WRITE, false))
        assertEquals(D.ASK, P.decide(S.ALLOW, DshToolRisk.EXTERNAL, false))
        assertEquals(D.RUN, P.decide(S.ALWAYS, DshToolRisk.EXTERNAL, false))
        assertEquals(D.ASK, P.decide(S.ASK, DshToolRisk.READ, false))
    }

    @Test fun `deny beats everything, declined-in-run beats asking and allowing`() {
        for (r in DshToolRisk.values()) {
            assertEquals(D.DENY, P.decide(S.DENY, r, false))
            assertEquals(D.DENY, P.decide(S.DENY, r, true))
            assertEquals(D.DECLINED, P.decide(S.ASK, r, true))
            assertEquals(D.DECLINED, P.decide(S.ALLOW, r, true))
            assertEquals(D.DECLINED, P.decide(S.ALWAYS, r, true))
        }
    }

    @Test fun `always-allow from the dialog stores always only for external`() {
        assertEquals(S.ALWAYS, P.afterAlways(DshToolRisk.EXTERNAL))
        assertEquals(S.ALLOW, P.afterAlways(DshToolRisk.WRITE))
        assertEquals(S.ALLOW, P.switchOf(S.ALWAYS))
        assertEquals(S.DENY, P.switchOf(S.DENY))
    }

    @Test fun `withSetting drops entries equal to the default`() {
        var m = P.withSetting(emptyMap(), "w", DshToolRisk.WRITE, S.ALLOW)
        assertEquals(mapOf("w" to "allow"), m)
        m = P.withSetting(m, "r", DshToolRisk.READ, S.ALLOW)          // default for read: nothing stored
        assertEquals(mapOf("w" to "allow"), m)
        m = P.withSetting(m, "r", DshToolRisk.READ, S.DENY)
        assertEquals("deny", m["r"])
        m = P.withSetting(m, "w", DshToolRisk.WRITE, S.ASK)           // back to default: removed
        assertEquals(mapOf("r" to "deny"), m)
        m = P.withSetting(m, "e", DshToolRisk.EXTERNAL, S.ALWAYS)
        assertEquals("always", m["e"])
    }

    @Test fun `policy key is the host tool, or the command key`() {
        val reg = DshToolRegistry.build(listOf(DshPluginTools("o/p", tools = listOf(
            DshToolSpec("t1", "d", """{"type":"object","properties":{}}""", DshToolRisk.READ)),
            commands = listOf(DshCommandSpec("hello")))))
        assertEquals(setOf("t1", DshToolPolicy.COMMAND_KEY), reg.map { P.keyOf(it) }.toSet())
    }

    // ------------------------------------------------------------ run memory

    @Test fun `denial is remembered per run only, bounded, and can be forgotten`() {
        val m = DshToolPolicy.RunMemory(maxRuns = 2)
        m.decline("run1", "plugin__a__x")
        assertTrue(m.declined("run1", "plugin__a__x"))
        assertFalse(m.declined("run2", "plugin__a__x"))
        assertFalse(m.declined("run1", "plugin__a__y"))
        m.decline("run2", "plugin__a__x"); m.decline("run3", "plugin__a__x")
        assertFalse(m.declined("run1", "plugin__a__x"))              // evicted (oldest)
        assertTrue(m.declined("run3", "plugin__a__x"))
        m.forget("plugin__a__x")
        assertFalse(m.declined("run3", "plugin__a__x"))
    }

    // ------------------------------------------------------------ preview

    @Test fun `preview drops tool_title, masks secret keys, flattens, clips to 200`() {
        val a = JSONObject().put("tool_title", "do it").put("query", "line1\nline2\tx")
            .put("password", "hunter22").put("nested", JSONObject().put("api_key", "sk-123456").put("ok", 1))
            .put("acct", "{{vault:abc}}")
        val p = P.preview(a)
        assertFalse(p.contains("tool_title"))
        assertFalse(p.contains("hunter22")); assertFalse(p.contains("sk-123456"))
        assertTrue(p.contains(DshToolPolicy.MASK))
        assertFalse(p.contains("\n")); assertFalse(p.contains("\t"))
        assertTrue(p.contains("{{vault:abc}}"))
        assertTrue(P.preview(JSONObject().put("城市", "北京")).contains("城市: \"北京\""))
        val long = P.preview(JSONObject().put("q", "x".repeat(1000)))
        assertEquals(200, long.length); assertTrue(long.endsWith("…"))
    }

    // ------------------------------------------------------------ output

    @Test fun `scrub removes secrets and their json-escaped forms, longest first, ignores short ones`() {
        val secret = "p\"ass/wo\\rd!"
        val escaped = JSONObject.quote(secret).let { it.substring(1, it.length - 1) }
        val out = "raw=$secret json={\"pw\":\"$escaped\"} short=abc tok=abcdef12 sub=abcdef"
        val s = P.scrub(out, listOf(secret, "abc", "abcdef12", "abcdef"))
        assertFalse(s.contains(secret)); assertFalse(s.contains(escaped))
        assertTrue(s.contains("short=abc"))          // < 4 chars never scrubbed
        assertTrue(s.contains("tok=${DshToolPolicy.MASK}"))
        assertTrue(s.contains("sub=${DshToolPolicy.MASK}"))
        assertEquals("nothing here", P.scrub("nothing here", emptyList()))
    }

    @Test fun `clip keeps output at most 64 KB without splitting a code point`() {
        assertEquals("small", P.clip("small"))
        val big = "汉".repeat(40_000)                    // 120 000 bytes
        val c = P.clip(big)
        assertTrue(c.toByteArray(Charsets.UTF_8).size <= DshToolPolicy.OUTPUT_CAP)
        assertTrue(c.contains("[output truncated: 120000 bytes, limit 65536]"))
        assertFalse(c.contains("\uFFFD"))
        val exact = "a".repeat(DshToolPolicy.OUTPUT_CAP)
        assertEquals(exact, P.clip(exact))
    }

    @Test fun `finish scrubs before clipping`() {
        val secret = "SECRETVALUE"
        val out = P.finish("x".repeat(70_000) + secret, listOf(secret), cap = 70_100)
        assertFalse(out.contains(secret))
        assertTrue(out.endsWith(DshToolPolicy.MASK))
    }
}
