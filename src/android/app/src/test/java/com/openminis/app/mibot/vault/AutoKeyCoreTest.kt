package com.openminis.app.mibot.vault

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * [fork/mibot-autokey-v2] JVM tests for the pure half of AutoKey
 * (AutoKeyCore.kt): recipe merge / hosts, key matching (masked keys are
 * never accepted), current key formats (MiniMax sk-api- / sk-cp-, Ark ark-),
 * candidate priority, the observation-driven create flow (bounded retries,
 * never a second create after confirm), step budgets and the null-eval bound.
 */
class AutoKeyCoreTest {

    private fun arr(vararg s: String) = JSONArray(s.toList())
    private fun list(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.get(it) }

    // ---------------------------------------------------------------- RecipeMerge

    private val defaults = JSONObject()
        .put("createText", arr("Create API key", "新建"))
        .put("confirmText", arr("Create", "确定"))
        .put("keyName", "Mibot")
        .put("hosts", arr("default.example"))

    private val claudeSvc = RecipeMerge.Svc(
        id = "claude",
        domains = listOf("anthropic.com", "Console.Anthropic.com", "claude.ai"),
        keyRegex = "sk-ant-[A-Za-z0-9_\\-]{40,}",
        webStart = "https://console.anthropic.com/settings/keys",
    )

    @Test
    fun `merge puts recipe list entries first, then defaults, without duplicates`() {
        val r = JSONObject().put("createText", arr("Create Key", "新建")).put("keyName", "Mibot-claude")
        val m = RecipeMerge.merge(defaults, r, claudeSvc)
        assertEquals(listOf("Create Key", "新建", "Create API key"), list(m.getJSONArray("createText")))
        assertEquals(listOf("Create", "确定"), list(m.getJSONArray("confirmText")))
        assertEquals("Mibot-claude", m.getString("keyName"))
        // defaults are not mutated
        assertEquals(listOf("Create API key", "新建"), list(defaults.getJSONArray("createText")))
    }

    @Test
    fun `hosts are service domains plus recipe hosts, lowercased and unique`() {
        val r = JSONObject().put("hosts", arr("platform.claude.com", "Claude.com", "claude.ai", ""))
        val m = RecipeMerge.merge(defaults, r, claudeSvc)
        assertEquals(
            listOf("anthropic.com", "console.anthropic.com", "claude.ai", "platform.claude.com", "claude.com"),
            RecipeMerge.hosts(m),
        )
        // defaults' own "hosts" never leak in (hosts are not list-merged)
        assertFalse(RecipeMerge.hosts(m).contains("default.example"))
    }

    @Test
    fun `start, keyRegex and keysMatch fall back to service and derived values`() {
        val m = RecipeMerge.merge(defaults, JSONObject(), claudeSvc)
        assertEquals("https://console.anthropic.com/settings/keys", m.getString("start"))
        assertEquals(claudeSvc.keyRegex, m.getString("keyRegex"))
        assertEquals("keys", m.getString("keysMatch"))

        val noRx = RecipeMerge.merge(defaults, JSONObject(), claudeSvc.copy(keyRegex = null))
        assertEquals(RecipeMerge.NEVER, noRx.getString("keyRegex"))
        assertFalse(Regex(RecipeMerge.NEVER).containsMatchIn("anything at all sk-ant-xxxx"))

        val own = RecipeMerge.merge(defaults, JSONObject().put("keysMatch", "console/access.*api-keys").put("start", "https://platform.minimaxi.com/console/access?tab=api-keys"), claudeSvc)
        assertEquals("console/access.*api-keys", own.getString("keysMatch"))
        assertEquals("https://platform.minimaxi.com/console/access?tab=api-keys", own.getString("start"))
    }

    @Test
    fun `deriveMatch uses the last path segment or the first query name`() {
        assertEquals("api.?keys", RecipeMerge.deriveMatch("https://platform.openai.com/api-keys"))
        assertEquals("api.?keys", RecipeMerge.deriveMatch("https://platform.deepseek.com/api_keys"))
        assertEquals("apiKey", RecipeMerge.deriveMatch("https://bailian.console.aliyun.com/?apiKey=1"))
        assertEquals(".*", RecipeMerge.deriveMatch("https://console.x.ai/"))
        assertEquals(".*", RecipeMerge.deriveMatch("not a url at all %%"))
        val m = Regex(RecipeMerge.deriveMatch("https://platform.openai.com/api-keys"), RegexOption.IGNORE_CASE)
        assertTrue(m.containsMatchIn("https://platform.openai.com/api-keys?tab=x"))
        assertTrue(m.containsMatchIn("https://x/API_Keys"))
    }

    @Test
    fun `ownHost matches the host and its subdomains only`() {
        val hosts = listOf("anthropic.com", "claude.com", "siliconflow.cn/account")
        assertTrue(RecipeMerge.ownHost("anthropic.com", hosts))
        assertTrue(RecipeMerge.ownHost("www.anthropic.com", hosts))
        assertTrue(RecipeMerge.ownHost("platform.claude.com", hosts))
        assertTrue(RecipeMerge.ownHost("PLATFORM.Claude.COM", hosts))
        assertTrue(RecipeMerge.ownHost("account.siliconflow.cn", hosts))
        assertFalse(RecipeMerge.ownHost("evilclaude.com", hosts))
        assertFalse(RecipeMerge.ownHost("claude.com.evil.io", hosts))
        assertFalse(RecipeMerge.ownHost("accounts.google.com", hosts))
        assertFalse(RecipeMerge.ownHost("anything.com", listOf("", "/path")))
    }

    @Test
    fun `problems flags bad recipes`() {
        assertEquals(listOf("invalid JSON"), RecipeMerge.problems("{nope"))
        val bad = JSONObject().put("version", 1).put(
            "recipes", JSONObject()
                .put("a", JSONObject().put("v", 0).put("start", "http://x.com"))
                .put("b", JSONObject().put("v", 1).put("keyRegex", "sk-[").put("keysMatch", "(").put("handoff", "popup"))
                .put("ok", JSONObject().put("v", 2).put("start", "https://ok.com/keys").put("keyRegex", "ok_[a-z]{20}").put("handoff", "customtab")),
        )
        val p = RecipeMerge.problems(bad.toString())
        assertTrue(p.toString(), "a: v < 1" in p)
        assertTrue(p.toString(), "a: start not https" in p)
        assertTrue(p.toString(), "b: keyRegex invalid" in p)
        assertTrue(p.toString(), "b: keysMatch invalid" in p)
        assertTrue(p.toString(), "b: unknown handoff popup" in p)
        assertFalse(p.toString(), p.any { it.startsWith("ok:") })
        assertTrue("no recipes" in RecipeMerge.problems(JSONObject().put("version", 1).toString()))
    }

    @Test
    fun `bundled key_recipes json has no problems`() {
        val f = listOf(
            File("src/main/assets/mibot/key_recipes.json"),
            File("app/src/main/assets/mibot/key_recipes.json"),
            File("src/android/app/src/main/assets/mibot/key_recipes.json"),
        ).firstOrNull { it.isFile }
        assumeTrue("key_recipes.json not reachable from ${File(".").absolutePath}", f != null)
        assertEquals(emptyList<String>(), RecipeMerge.problems(f!!.readText()))
    }

    // ---------------------------------------------------------------- KeyMatcher

    private val openai = KeyMatcher("sk-(proj-)?[A-Za-z0-9_\\-]{32,}")
    private val k1 = "sk-proj-" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7"

    @Test
    fun `finds a plain key in text, json and next to punctuation`() {
        assertEquals(listOf(k1), openai.findAll("your key: $k1 (keep it safe)"))
        assertEquals(listOf(k1), openai.findAll("{\"secret\":\"$k1\",\"id\":1}"))
        assertEquals(listOf(k1), openai.findAll("<code>$k1</code>"))
        assertEquals(listOf(k1), openai.findAll("$k1. Copy it now"))
        assertEquals(listOf(k1), openai.findAll("$k1 $k1"))
        assertTrue(openai.matches("  $k1\n"))
        assertFalse(openai.matches("key $k1"))
        assertTrue(openai.findAll(null).isEmpty())
        assertTrue(openai.findAll("").isEmpty())
    }

    @Test
    fun `masked keys are rejected, including a long prefix before the mask`() {
        val head = "sk-proj-A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5"
        for (m in listOf("$head****wxyz", "$head••••wxyz", "$head…wxyz", "$head...wxyz", "$head●●●●", "$head·····",
            "sk-proj-****" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7", "••••$head", "...$head")) {
            assertEquals(m, emptyList<String>(), openai.findAll("Secret: $m"))
            assertFalse(m, openai.matches(m))
        }
        assertTrue(KeyMatcher.masked("sk-ab**cd"))
        assertTrue(KeyMatcher.masked("sk-ab...cd"))
        assertFalse(KeyMatcher.masked(k1))
    }

    @Test
    fun `a key that only appears inside a longer token is not taken`() {
        assertTrue(openai.findAll("xsk-proj-A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7").isEmpty())
        val ds = KeyMatcher("sk-[0-9a-f]{32}")
        assertTrue(ds.findAll("sk-" + "0123456789abcdef0123456789abcdef" + "0").isEmpty())
        assertEquals(1, ds.findAll("sk-" + "0123456789abcdef0123456789abcdef").size)
    }

    @Test
    fun `invalid regex or too short matches give nothing`() {
        assertTrue(KeyMatcher("sk-[").findAll("sk-anything-here-1234567890").isEmpty())
        assertTrue(KeyMatcher("ab[0-9]{3}").findAll("ab123").isEmpty()) // < MIN_LEN
        assertTrue(KeyMatcher(RecipeMerge.NEVER).findAll("sk-" + "a".repeat(40)).isEmpty())
    }

    @Test
    fun `minimax sk-api and sk-cp keys`() {
        val mm = KeyMatcher("sk-(?:api|cp)-[A-Za-z0-9_\\-]{40,}|eyJ[A-Za-z0-9_\\-]{60,}\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+")
        val api = "sk-api-" + "Ab12_Cd34-Ef56Gh78Ij90Kl12Mn34Op56Qr78St90Uv12"
        val cp = "sk-cp-" + "ZZ12yy34XX56ww78VV90uu12TT34ss56RR78qq90PP"
        val jwt = "eyJ" + "a".repeat(64) + ".eyJzdWIiOiIxIn0.sig_ABC-123"
        assertEquals(listOf(api), mm.findAll("新的密钥 $api 请妥善保存"))
        assertEquals(listOf(cp), mm.findAll("{\"api_key\":\"$cp\"}"))
        assertEquals(listOf(jwt), mm.findAll("legacy: $jwt"))
        assertTrue(mm.findAll("sk-api-Ab12Cd34****Uv12").isEmpty())
        assertTrue(mm.findAll("sk-xx-" + "a".repeat(44)).isEmpty())
    }

    @Test
    fun `ark keys in the new ark- format and the old uuid format`() {
        val ark = KeyMatcher("ark-[A-Za-z0-9\\-]{30,}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val newKey = "ark-" + "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d-12345"
        val uuid = "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"
        assertEquals(listOf(newKey), ark.findAll("API Key: $newKey"))
        assertEquals(listOf(uuid), ark.findAll("API Key: $uuid"))
        assertTrue(ark.findAll("ark-1a2b****5c6d").isEmpty())
        assertTrue(ark.findAll("1a2b3c4d-****-****-****-1e2f3a4b5c6d").isEmpty())
    }

    @Test
    fun `looksLikeToken accepts a plausible pasted token only`() {
        assertTrue(KeyMatcher.looksLikeToken("  abc123DEF456ghi789  "))
        assertFalse(KeyMatcher.looksLikeToken("short1"))
        assertFalse(KeyMatcher.looksLikeToken("has space 1234567890abc"))
        assertFalse(KeyMatcher.looksLikeToken("abcdefghijklmnopqrstu"))
        assertFalse(KeyMatcher.looksLikeToken("12345678901234567890"))
        assertFalse(KeyMatcher.looksLikeToken("abc123****def456ghi"))
        assertFalse(KeyMatcher.looksLikeToken(null))
    }

    // ---------------------------------------------------------------- Candidates

    private val gsk = KeyMatcher("gsk_[A-Za-z0-9]{40,}")
    private fun g(n: Int) = "gsk_" + n.toString().padStart(4, '0') + "A".repeat(40)

    @Test
    fun `an existing key on the page is only a fallback`() {
        val c = Candidates(gsk)
        assertEquals(listOf(g(1)), c.offer("list: ${g(1)}", "page", 1_000))
        assertNull(c.pick(createdAt = null, allowExisting = false))
        assertNull(c.pick(createdAt = 2_000, allowExisting = false))
        assertEquals(g(1), c.pick(createdAt = 2_000, allowExisting = true)!!.key)
    }

    @Test
    fun `a fresh key after create wins, network over dialog over page`() {
        val c = Candidates(gsk)
        c.offer(g(1), "page", 1_000)
        c.offer(g(2), "page", 3_000)
        c.offer(g(3), "dialog", 3_500)
        assertEquals(g(3), c.pick(2_000, false)!!.key)
        c.offer(g(4), "network", 3_100)
        assertEquals(g(4), c.pick(2_000, false)!!.key)
        // the same channel: the later one
        c.offer(g(5), "network", 3_900)
        assertEquals(g(5), c.pick(2_000, false)!!.key)
    }

    @Test
    fun `clipboard and paste win at once, latest first`() {
        val c = Candidates(gsk)
        c.offer(g(1), "network", 3_000)
        c.offer(g(2), "clipboard-native", 2_500)
        assertEquals(g(2), c.pick(2_000, false)!!.key)
        c.offer(g(3), "paste", 4_000)
        assertEquals(g(3), c.pick(null, false)!!.key)
        assertEquals("paste", c.pick(null, false)!!.via)
    }

    @Test
    fun `duplicates and masked keys are not added`() {
        val c = Candidates(gsk)
        assertEquals(1, c.offer("${g(1)} ${g(1)}", "page", 1).size)
        assertTrue(c.offer(g(1), "network", 2).isEmpty())
        assertTrue(c.offer("gsk_0001AAAA****AAAA", "dialog", 3).isEmpty())
        assertEquals(1, c.all().size)
        assertEquals("page", c.all()[0].via)
    }

    @Test
    fun `sources filter channels but never direct copies`() {
        val c = Candidates(gsk, sources = setOf("dialog"))
        assertTrue(c.offer(g(1), "page", 1).isEmpty())
        assertTrue(c.offer(g(2), "network", 1).isEmpty())
        assertEquals(1, c.offer(g(3), "dialog", 1).size)
        assertEquals(1, c.offer(g(4), "clipboard", 1).size)
    }

    @Test
    fun `loose paste accepts a token the regex does not know`() {
        val c = Candidates(gsk)
        assertTrue(c.offerLoose(" newFormat_abc123XYZ789 ", "paste", 5))
        assertFalse(c.offerLoose("newFormat_abc123XYZ789", "paste", 6))
        assertFalse(c.offerLoose("not a token", "paste", 7))
        assertEquals("newFormat_abc123XYZ789", c.pick(null, false)!!.key)
    }

    // ---------------------------------------------------------------- StepClock / budgets

    @Test
    fun `step budgets come from the recipe, else the defaults`() {
        val b = AkStep.budgets(JSONObject().put("budgets", JSONObject().put("create", 30_000).put("login", 0)))
        assertEquals(30_000L, b[AkStep.CREATE])
        assertEquals(AkStep.LOGIN.budgetMs, b[AkStep.LOGIN])
        assertEquals(AkStep.READ.budgetMs, AkStep.budgets(null)[AkStep.READ])
        assertEquals(AkStep.values().size, b.size)
    }

    @Test
    fun `step clock times out per step and flags once per entry`() {
        val t = 1_000_000L
        val c = StepClock(AkStep.budgets(null))
        assertFalse(c.expired(t + 999_999)) // never entered
        c.enter(AkStep.NAV, t)
        assertFalse(c.expired(t + 20_000))
        assertTrue(c.expired(t + 20_001))
        assertTrue(c.expiredOnce(t + 20_001))
        assertFalse(c.expiredOnce(t + 25_000))
        // Re-entering the same step does not reset it (URL churn cannot reset a budget).
        c.enter(AkStep.NAV, t + 21_000)
        assertEquals(t, c.since)
        assertTrue(c.expired(t + 21_000))
        // A new step starts a new budget and a new flag.
        c.enter(AkStep.CREATE, t + 30_000)
        assertEquals(AkStep.CREATE, c.step)
        assertEquals(5_000L, c.elapsed(t + 35_000))
        assertFalse(c.expired(t + 45_000))
        assertTrue(c.expiredOnce(t + 45_001))
        assertFalse(c.expiredOnce(t + 50_000))
    }

    // ---------------------------------------------------------------- CreateFlow

    private val T = 1_000_000L
    private fun keys(path: String = "/api-keys", dialog: Boolean = false, nameEmpty: Boolean = false, create: Boolean = true,
                     confirm: Boolean = false, form: Boolean = false) =
        PageObs(state = "in", onKeys = true, dialogOpen = dialog, nameEmpty = nameEmpty, createFound = create, confirmFound = confirm,
            formOnPage = form, path = path)

    @Test
    fun `dialog flow - create, fill, confirm, read, done`() {
        val f = CreateFlow()
        assertEquals(CreateFlow.Act.CLICK_CREATE, f.next(keys(), false, T).act)
        assertEquals(T, f.createdAt)
        assertEquals(CreateFlow.Act.FILL_NAME, f.next(keys(dialog = true, nameEmpty = true, confirm = true), false, T + 1_000).act)
        assertEquals(AkStep.FILL, f.clock.step)
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(dialog = true, confirm = true), false, T + 2_000).act)
        assertEquals(AkStep.CONFIRM, f.clock.step)
        assertEquals(CreateFlow.Act.CONFIRM, f.next(keys(dialog = true, confirm = true), false, T + 3_300).act)
        assertNotNull(f.confirmedAt)
        // Key dialog stays open without a confirm button: move on to READ, then reveal.
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(dialog = true), false, T + 4_000).act)
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(dialog = true), false, T + 7_000).act)
        assertEquals(AkStep.READ, f.clock.step)
        assertEquals(CreateFlow.Act.REVEAL, f.next(keys(dialog = true), false, T + 10_500).act)
        assertEquals(CreateFlow.Act.DONE, f.next(keys(dialog = true), true, T + 11_000).act)
    }

    @Test
    fun `never clicks create again after confirm`() {
        val f = CreateFlow()
        var creates = 0
        fun step(o: PageObs, t: Long): CreateFlow.Act = f.next(o, false, t).act.also { if (it == CreateFlow.Act.CLICK_CREATE) creates++ }
        step(keys(), T)
        step(keys(dialog = true, nameEmpty = true, confirm = true), T + 1_000)
        step(keys(dialog = true, confirm = true), T + 2_000)
        assertEquals(CreateFlow.Act.CONFIRM, step(keys(dialog = true, confirm = true), T + 3_300))
        // Dialog closes, key lands in the list (or not), create button visible again, even on a new route.
        var t = T + 4_000
        val acts = mutableListOf<CreateFlow.Act>()
        while (t < T + 120_000) { acts += step(keys(path = if (t % 2 == 0L) "/api-keys" else "/api-keys#x"), t); t += 1_000 }
        assertEquals(1, creates)
        assertFalse(acts.contains(CreateFlow.Act.CONFIRM))
        assertTrue(acts.contains(CreateFlow.Act.REVEAL))
        assertTrue("READ budget ends in a hand-off", acts.contains(CreateFlow.Act.NEED_USER))
        assertEquals(1, acts.count { it == CreateFlow.Act.NEED_USER })
        assertTrue(f.gaveUp)
        assertTrue(f.tries(AkStep.READ) <= CreateFlow.MAX_TRIES)
    }

    @Test
    fun `create retries are bounded, then the user is asked`() {
        val f = CreateFlow()
        val acts = mutableListOf<CreateFlow.Act>()
        var t = T
        while (t <= T + 40_000) { acts += f.next(keys(), false, t).act; t += 500 }
        assertEquals(CreateFlow.MAX_TRIES, acts.count { it == CreateFlow.Act.CLICK_CREATE })
        assertEquals(1, acts.count { it == CreateFlow.Act.NEED_USER })
        // clicks are spaced by at least WAIT_AFTER_ACT
        val idx = acts.withIndex().filter { it.value == CreateFlow.Act.CLICK_CREATE }.map { it.index }
        idx.zipWithNext().forEach { (a, b) -> assertTrue((b - a) * 500L >= CreateFlow.WAIT_AFTER_ACT) }
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(), false, t + 1_000).act)
    }

    @Test
    fun `no create button - waits for the budget, then hands off once`() {
        val f = CreateFlow()
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(create = false), false, T).act)
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(create = false), false, T + 14_000).act)
        val d = f.next(keys(create = false), false, T + 15_001)
        assertEquals(CreateFlow.Act.NEED_USER, d.act)
        assertTrue(d.reason.isNotBlank())
        assertNull(f.createdAt)
        // a key that shows up after the hand-off still finishes the flow
        assertEquals(CreateFlow.Act.DONE, f.next(keys(create = false), true, T + 20_000).act)
    }

    @Test
    fun `slow dialog - create is retried, name filled when it finally opens`() {
        val f = CreateFlow()
        assertEquals(CreateFlow.Act.CLICK_CREATE, f.next(keys(), false, T).act)
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(), false, T + 1_000).act)
        assertEquals(CreateFlow.Act.CLICK_CREATE, f.next(keys(), false, T + 2_000).act)
        assertEquals(T, f.createdAt) // createdAt is the FIRST click
        assertEquals(CreateFlow.Act.FILL_NAME, f.next(keys(dialog = true, nameEmpty = true, confirm = true), false, T + 5_000).act)
        // name did not stick (controlled input): one more fill, then go on
        assertEquals(CreateFlow.Act.FILL_NAME, f.next(keys(dialog = true, nameEmpty = true, confirm = true), false, T + 6_000).act)
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(dialog = true, nameEmpty = true, confirm = true), false, T + 7_000).act)
        assertEquals(AkStep.CONFIRM, f.clock.step)
    }

    @Test
    fun `confirm retries are bounded and never exceed MAX_TRIES`() {
        val f = CreateFlow()
        f.next(keys(), false, T)
        f.next(keys(dialog = true, confirm = true), false, T + 1_000) // FILL -> CONFIRM
        var t = T + 1_100
        var confirms = 0
        while (t < T + 60_000) { if (f.next(keys(dialog = true, confirm = true), false, t).act == CreateFlow.Act.CONFIRM) confirms++; t += 500 }
        assertEquals(CreateFlow.MAX_TRIES, confirms)
        assertEquals(1, f.tries(AkStep.CREATE))
    }

    @Test
    fun `route change before confirm starts over at create`() {
        val f = CreateFlow()
        f.next(keys(), false, T)
        assertEquals(CreateFlow.Act.FILL_NAME, f.next(keys(dialog = true, nameEmpty = true), false, T + 1_000).act)
        // SPA navigated away and the dialog is gone
        assertEquals(CreateFlow.Act.CLICK_CREATE, f.next(keys(path = "/other"), false, T + 2_000).act)
        assertEquals(AkStep.CREATE, f.clock.step)
        assertEquals(2, f.tries(AkStep.CREATE))
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(path = "/other"), false, T + 2_100).act)
    }

    @Test
    fun `full page form (Hugging Face tokens new) is filled without a create click`() {
        val f = CreateFlow()
        val form = keys(path = "/settings/tokens/new", form = true, nameEmpty = true, confirm = true)
        assertEquals(CreateFlow.Act.FILL_NAME, f.next(form, false, T).act)
        assertEquals(T, f.createdAt)
        assertEquals(0, f.tries(AkStep.CREATE))
        f.next(form.copy(nameEmpty = false), false, T + 1_000)
        assertEquals(CreateFlow.Act.CONFIRM, f.next(form.copy(nameEmpty = false), false, T + 3_000).act)
        // form submitted -> page with the token modal
        assertEquals(CreateFlow.Act.WAIT, f.next(keys(path = "/settings/tokens", dialog = true), false, T + 4_000).act)
    }

    @Test
    fun `PageObs parses detect json with safe defaults`() {
        val o = PageObs.parse(JSONObject("""{"state":"in","onKeys":true,"dialogOpen":true,"nameEmpty":true,"createFound":true,"confirmFound":false,"formOnPage":false,"path":"/keys","precheck":2}"""))
        assertEquals(PageObs("in", onKeys = true, rendered = true, dialogOpen = true, nameEmpty = true, createFound = true, path = "/keys", precheck = 2), o)
        val e = PageObs.parse(JSONObject())
        assertEquals("unknown", e.state)
        assertTrue(e.rendered)
        assertEquals(-1, e.precheck)
    }

    // ---------------------------------------------------------------- NullEvals / states / intents

    @Test
    fun `null evals show the stuck sheet once and fail at the bound`() {
        val n = NullEvals(showAt = 5, failAt = 40)
        val v = (1..4).map { n.onResult(false) }
        assertTrue(v.all { it == NullEvals.Verdict.KEEP_WAITING })
        assertEquals(NullEvals.Verdict.SHOW_STUCK, n.onResult(false))
        repeat(34) { assertEquals(NullEvals.Verdict.KEEP_WAITING, n.onResult(false)) }
        assertEquals(39, n.streak)
        assertEquals(NullEvals.Verdict.FAIL, n.onResult(false))
        assertEquals(NullEvals.Verdict.FAIL, n.onResult(false))
        assertEquals(NullEvals.Verdict.OK, n.onResult(true))
        assertEquals(0, n.streak)
        assertEquals(NullEvals.Verdict.KEEP_WAITING, n.onResult(false))
    }

    @Test
    fun `null evals at the default bound stay within the 15 minute run`() {
        val n = NullEvals()
        var i = 0
        while (n.onResult(false) != NullEvals.Verdict.FAIL) i++
        // one eval is at most 6 s (timeout) + 1 s poll delay
        assertTrue((i + 1) * 7_000L < 15 * 60_000L)
    }

    @Test
    fun `states map to steps and challenges are never reloaded`() {
        assertEquals(AkStep.OPEN, StateSteps.stepFor("loading", false, false))
        assertEquals(AkStep.CHALLENGE, StateSteps.stepFor("challenge", false, true))
        assertEquals(AkStep.LOGIN, StateSteps.stepFor("qr", false, true))
        assertEquals(AkStep.LOGIN, StateSteps.stepFor("google", false, true))
        assertEquals(AkStep.OPEN, StateSteps.stepFor("in", false, false))
        assertEquals(AkStep.NAV, StateSteps.stepFor("in", false, true))
        assertNull(StateSteps.stepFor("in", true, true))
        assertTrue(StateSteps.noReload("challenge"))
        assertTrue(StateSteps.noReload("verify"))
        assertTrue(StateSteps.noReload("qr"))
        assertFalse(StateSteps.noReload("in"))
    }

    @Test
    fun `intent links keep only an http fallback`() {
        val u = "intent://login?x=1#Intent;scheme=weixin;package=com.tencent.mm;S.browser_fallback_url=https%3A%2F%2Fopen.weixin.qq.com%2Fqr%3Fa%3D1;end"
        assertEquals("https://open.weixin.qq.com/qr?a=1", IntentLinks.fallback(u))
        assertNull(IntentLinks.fallback("intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"))
        assertNull(IntentLinks.fallback("intent://x#Intent;scheme=alipays;end"))
        assertNull(IntentLinks.fallback("https://example.com"))
        assertTrue(IntentLinks.isGoogleLogin("accounts.google.com"))
        assertFalse(IntentLinks.isGoogleLogin("accounts.google.com.evil.io"))
        assertFalse(IntentLinks.isGoogleLogin(null))
    }

    // ---------------------------------------------------------------- recipes v2 (bundled data)

    private fun asset(name: String): File? = listOf(
        File("src/main/assets/mibot/$name"),
        File("app/src/main/assets/mibot/$name"),
        File("src/android/app/src/main/assets/mibot/$name"),
    ).firstOrNull { it.isFile }

    private fun bundled(): JSONObject? = asset("key_recipes.json")?.let { JSONObject(it.readText()) }

    @Test
    fun `bundled recipes are v2 for all 17 services and every start URL belongs to its service`() {
        val rf = asset("key_recipes.json"); val sf = asset("services.json")
        assumeTrue("assets not reachable", rf != null && sf != null)
        val root = JSONObject(rf!!.readText())
        assertTrue(root.optInt("version") >= 2)
        val ids = root.getJSONObject("recipes").keys().asSequence().toSet()
        for (id in listOf("openai", "claude", "gemini", "deepseek", "kimi", "qwen", "siliconflow", "zhipu", "doubao", "hunyuan",
            "minimax", "groq", "mistral", "perplexity", "xai", "huggingface", "elevenlabs")) {
            assertTrue(id, id in ids)
            assertTrue(id, root.getJSONObject("recipes").getJSONObject(id).optInt("v") >= 2)
        }
        assertEquals(emptyList<String>(), ConnectTargets.problems(sf!!.readText(), rf.readText()))
    }

    @Test
    fun `verify selectors no longer treat QR or one-time-code inputs as captcha`() {
        val d = bundled()?.getJSONObject("defaults"); assumeTrue(d != null)
        val v = list(d!!.getJSONArray("verifySelectors")).map { it.toString() }
        assertFalse(v.toString(), v.any { it.contains("one-time-code") || it.contains("qr", ignoreCase = true) })
        val t = list(d.getJSONArray("verifyText")).map { it.toString() }
        assertFalse("扫码登录" in t); assertFalse("安全验证" in t)
    }

    @Test
    fun `v2 recipe hosts, key pages and key formats`() {
        val r = bundled()?.getJSONObject("recipes"); assumeTrue(r != null)
        fun merged(id: String, domains: List<String>) = RecipeMerge.merge(JSONObject(), r!!.getJSONObject(id), RecipeMerge.Svc(id, domains, null, "https://example.com/"))
        val claude = merged("claude", listOf("anthropic.com", "console.anthropic.com", "claude.ai"))
        assertTrue(RecipeMerge.ownHost("platform.claude.com", RecipeMerge.hosts(claude)))
        assertTrue(Regex(claude.getString("keysMatch"), RegexOption.IGNORE_CASE).containsMatchIn("https://platform.claude.com/settings/keys"))
        val mm = merged("minimax", listOf("minimaxi.com"))
        assertTrue(Regex(mm.getString("keysMatch"), RegexOption.IGNORE_CASE).containsMatchIn("https://platform.minimaxi.com/console/access?tab=api-keys"))
        val mmK = KeyMatcher(mm.getString("keyRegex"))
        assertTrue(mmK.matches("sk-api-" + "Ab3_dE5-fG7hI9jK1lM3nO5pQ7"))
        assertTrue(mmK.matches("sk-cp-" + "Ab3dE5fG7hI9jK1lM3nO5pQ7rS"))
        assertFalse(mmK.matches("eyJhbGciOiJIUzI1NiJ9" + "a".repeat(60) + ".b.c"))
        val ark = KeyMatcher(merged("doubao", listOf("volcengine.com")).getString("keyRegex"))
        assertTrue(ark.matches("ark-" + "0f1e2d3c4b5a69788796a5b4c3d2e1f0"))
        assertFalse(ark.matches("ark****d5b88"))
        assertTrue(ark.findAll("request 0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0").isEmpty())
        val xai = merged("xai", listOf("x.ai"))
        assertTrue(xai.getString("start").endsWith("/team/default/api-keys"))
        assertFalse(xai.getString("keysMatch") == ".*")
        assertEquals("customtab", r!!.getJSONObject("gemini").optString("handoff"))
        for (id in r.keys()) assertEquals(id, emptyList<String>(), RecipeMerge.problems(JSONObject().put("version", 2).put("recipes", JSONObject().put(id, r.getJSONObject(id))).toString()))
    }

    @Test
    fun `problems flags bad prechecks and hosts`() {
        val bad = JSONObject().put("version", 2).put("recipes", JSONObject().put("x", JSONObject().put("v", 2)
            .put("precheck", JSONArray().put(JSONObject().put("rx", "(")).put(JSONObject().put("rx", "ok").put("view", "popup")).put(JSONObject()))
            .put("hosts", arr("https://x.com", "Upper.com", "ok.com"))))
        val p = RecipeMerge.problems(bad.toString())
        assertTrue(p.toString(), "x: precheck[0] rx invalid" in p)
        assertTrue(p.toString(), "x: precheck[1] unknown view popup" in p)
        assertTrue(p.toString(), "x: precheck[2] needs rx" in p)
        assertTrue(p.toString(), "x: bad host 'https://x.com'" in p)
        assertTrue(p.toString(), "x: bad host 'Upper.com'" in p)
        assertFalse(p.toString(), p.any { it.contains("ok.com") })
    }
}
