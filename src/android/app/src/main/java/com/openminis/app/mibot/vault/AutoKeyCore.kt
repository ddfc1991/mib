package com.openminis.app.mibot.vault

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * [fork/mibot-autokey-v2] The pure (no Android) half of the AutoKey engine,
 * so recipe parsing, key matching, candidate ranking and the create-flow
 * state machine are covered by JVM unit tests (AutoKeyCoreTest).
 *
 * Nothing here solves captchas or reads SMS: it only decides which ordinary
 * page action comes next, how long a step may take before a human is asked,
 * and whether a string really is a fresh API key.
 */
object RecipeMerge {
    /** Service facts a recipe is merged with (from services.json). */
    data class Svc(val id: String, val domains: List<String>, val keyRegex: String?, val webStart: String)

    /**
     * Merges [recipe] over [defaults]. Lists in a recipe are tried first,
     * then the defaults. Hosts = service domains + the recipe's own `hosts`
     * (a console that moved to a new domain, e.g. platform.claude.com).
     * `keysMatch` is the recipe's own, else derived from the start URL.
     */
    fun merge(defaults: JSONObject, recipe: JSONObject, svc: Svc): JSONObject {
        val merged = JSONObject(defaults.toString())
        val rk = recipe.keys()
        while (rk.hasNext()) {
            val k = rk.next()
            val v = recipe.get(k)
            val d = merged.optJSONArray(k)
            if (v is JSONArray && d != null && k != "hosts") {
                val out = JSONArray()
                for (i in 0 until v.length()) out.put(v.get(i))
                for (i in 0 until d.length()) if (!contains(out, d.get(i))) out.put(d.get(i))
                merged.put(k, out)
            } else merged.put(k, v)
        }
        val start = recipe.optString("start").ifBlank { svc.webStart }
        merged.put("start", start)
        merged.put("keyRegex", recipe.optString("keyRegex").ifBlank { svc.keyRegex ?: NEVER })
        val hosts = LinkedHashSet<String>()
        svc.domains.forEach { hosts.add(it.lowercase()) }
        recipe.optJSONArray("hosts")?.let { a -> for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let { hosts.add(it.lowercase()) } }
        merged.put("hosts", JSONArray(hosts.toList()))
        if (recipe.optString("keysMatch").isBlank()) merged.put("keysMatch", deriveMatch(start))
        return merged
    }

    const val NEVER = "(?!x)x"

    private fun contains(a: JSONArray, v: Any): Boolean { for (i in 0 until a.length()) if (a.get(i) == v) return true; return false }

    /** Last path segment (or first query name) of [url] as a loose regex; ".*" when there is none. */
    fun deriveMatch(url: String): String {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return ".*"
        val seg = (u.rawPath ?: "").split('/').lastOrNull { it.isNotBlank() }
            ?: u.rawQuery?.split('&')?.firstOrNull()?.substringBefore('=')?.takeIf { it.isNotBlank() }
            ?: return ".*"
        val sb = StringBuilder()
        for (ch in seg) when {
            ch == '-' || ch == '_' -> sb.append(".?")
            ch.isLetterOrDigit() -> sb.append(ch)
            else -> sb.append('\\').append(ch)
        }
        return sb.toString()
    }

    /** True when [host] is one of [hosts] or a subdomain of one (a host entry may carry a path; only its host part counts). */
    fun ownHost(host: String, hosts: List<String>): Boolean {
        val h = host.lowercase().removePrefix("www.")
        return hosts.any { e ->
            val hh = e.lowercase().substringBefore('/').removePrefix("www.")
            hh.isNotBlank() && (h == hh || h.endsWith(".$hh"))
        }
    }

    fun hosts(merged: JSONObject): List<String> = merged.optJSONArray("hosts")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

    /** Problems of a recipes document on its own (regexes, required fields). */
    fun problems(recipesJson: String): List<String> {
        val out = mutableListOf<String>()
        val root = runCatching { JSONObject(recipesJson) }.getOrElse { return listOf("invalid JSON") }
        if (root.optInt("version") < 1) out += "version < 1"
        val recipes = root.optJSONObject("recipes") ?: return out + "no recipes"
        val it = recipes.keys()
        while (it.hasNext()) {
            val id = it.next()
            val r = recipes.getJSONObject(id)
            if (r.optInt("v") < 1) out += "$id: v < 1"
            val start = r.optString("start")
            if (start.isNotBlank() && !start.startsWith("https://")) out += "$id: start not https"
            r.optString("keyRegex").takeIf { it.isNotBlank() }?.let { rx -> if (runCatching { Regex(rx) }.isFailure) out += "$id: keyRegex invalid" }
            r.optString("keysMatch").takeIf { it.isNotBlank() }?.let { rx -> if (runCatching { Regex(rx) }.isFailure) out += "$id: keysMatch invalid" }
            val h = r.optString("handoff")
            if (h.isNotBlank() && h != "customtab") out += "$id: unknown handoff $h"
            when (val fl = r.optString("flow")) {
                "", "web" -> {}
                "oauth_pkce" -> {
                    for (k in listOf("authUrl", "exchangeUrl")) if (!r.optString(k).startsWith("https://")) out += "$id: oauth_pkce needs https $k"
                    val cbs = r.optJSONArray("callbacks")
                    if (cbs == null || cbs.length() == 0) out += "$id: oauth_pkce needs callbacks"
                    else for (i in 0 until cbs.length()) if (cbs.optString(i) !in Pkce.CALLBACK_KINDS) out += "$id: unknown callback ${cbs.optString(i)}"
                    r.optString("deepLink").takeIf { it.isNotBlank() }?.let { dl -> if (!Regex("^[a-z][a-z0-9+.-]*://[a-z0-9.-]+(/.*)?$").matches(dl) || dl.startsWith("http")) out += "$id: bad deepLink $dl" }
                }
                else -> out += "$id: unknown flow $fl"
            }
            r.optJSONArray("precheck")?.let { a ->
                for (i in 0 until a.length()) {
                    val pc = a.optJSONObject(i)
                    if (pc == null || pc.optString("rx").isBlank()) { out += "$id: precheck[$i] needs rx"; continue }
                    if (runCatching { Regex(pc.optString("rx")) }.isFailure) out += "$id: precheck[$i] rx invalid"
                    val v = pc.optString("view", "assist")
                    if (v != "assist" && v != "verify") out += "$id: precheck[$i] unknown view $v"
                }
            }
            r.optJSONArray("hosts")?.let { a -> for (i in 0 until a.length()) { val hh = a.optString(i); if (hh.isBlank() || hh.contains("://") || hh != hh.lowercase()) out += "$id: bad host '$hh'" } }
        }
        return out
    }
}

/** Decides whether a captured string is a real, unmasked key for one recipe. */
class KeyMatcher(keyRegex: String) {
    // A key must not touch another key character OR a mask character on either
    // side: "sk-abc…(20+ chars)****wxyz" is a masked key, not a key prefix.
    private val rx: Regex? = runCatching { Regex("(?<![A-Za-z0-9_\\-*\u2022\u2026\u25cf\u00b7]|\\.\\.\\.)(?:$keyRegex)(?![A-Za-z0-9_\\-*\u2022\u2026\u25cf\u00b7]|\\.\\.\\.)") }.getOrNull()

    /** Every unmasked key in [text], in order. */
    fun findAll(text: String?): List<String> {
        val r = rx ?: return emptyList()
        if (text.isNullOrEmpty() || text.length > 3_000_000) return emptyList()
        return r.findAll(text).map { it.value }.filter { !masked(it) && it.length >= MIN_LEN }.distinct().toList()
    }

    /** [s] (trimmed) is exactly one key. */
    fun matches(s: String?): Boolean {
        val t = s?.trim() ?: return false
        return findAll(t).singleOrNull() == t
    }

    companion object {
        const val MIN_LEN = 16
        fun masked(s: String): Boolean = s.contains('*') || s.contains('•') || s.contains('…') || s.contains("...") || s.contains('●') || s.contains('·')
        /** A pasted value that does not match the recipe but still looks like a token (probe decides). */
        fun looksLikeToken(s: String?): Boolean {
            val t = s?.trim() ?: return false
            return t.length in 16..400 && t.none { it.isWhitespace() } && !masked(t) && t.any { it.isDigit() } && t.any { it.isLetter() }
        }
    }
}

/**
 * Candidate keys seen during one run, from every channel: the in-page
 * capture layer (network / dialog / page / clipboard hooks, any frame), the
 * native clipboard listener, and the user's paste. A key first seen after
 * the run clicked "create" wins; a key that was already on the page is kept
 * only as a fallback. Clipboard and paste are user- or site-driven copies
 * and win at once.
 */
class Candidates(private val matcher: KeyMatcher, private val sources: Set<String>? = null) {
    data class C(val key: String, val via: String, val firstSeen: Long)

    private val list = mutableListOf<C>()

    @Synchronized fun offer(text: String?, via: String, now: Long): List<String> {
        if (sources != null && via !in sources && via !in DIRECT) return emptyList()
        val added = mutableListOf<String>()
        for (k in matcher.findAll(text)) {
            if (list.any { it.key == k }) continue
            list.add(C(k, via, now)); added.add(k)
        }
        return added
    }

    /** A pasted / clipboard value that does not match the regex but looks like a token. */
    @Synchronized fun offerLoose(text: String, via: String, now: Long): Boolean {
        val t = text.trim()
        if (!KeyMatcher.looksLikeToken(t) || list.any { it.key == t }) return false
        list.add(C(t, via, now)); return true
    }

    @Synchronized fun all(): List<C> = list.toList()

    /**
     * The key to finish with, or null. [createdAt] = when the run first
     * clicked create (null = never). [allowExisting] = the create flow gave
     * up, so a key that was already on the page is acceptable.
     */
    @Synchronized fun pick(createdAt: Long?, allowExisting: Boolean): C? {
        list.filter { it.via in DIRECT }.maxByOrNull { it.firstSeen }?.let { return it }
        if (createdAt != null) {
            val fresh = list.filter { it.firstSeen >= createdAt }
            if (fresh.isNotEmpty()) return fresh.minByOrNull { rank(it.via) * 10_000_000_000L - it.firstSeen }
        }
        if (allowExisting) return list.maxByOrNull { it.firstSeen }
        return null
    }

    companion object {
        /** Channels that are an explicit copy by the site or the user. */
        val DIRECT = setOf("clipboard", "clipboard-native", "paste")
        private fun rank(via: String) = when (via) { "network" -> 0; "dialog" -> 1; "page" -> 2; else -> 3 }
    }
}

/** What the page-side `detect` saw (a subset of its JSON, parsed for the engine). */
data class PageObs(
    val state: String,
    val onKeys: Boolean = false,
    val rendered: Boolean = true,
    val dialogOpen: Boolean = false,
    val nameEmpty: Boolean = false,
    val createFound: Boolean = false,
    val confirmFound: Boolean = false,
    val formOnPage: Boolean = false,
    val path: String = "",
    val precheck: Int = -1,
) {
    companion object {
        fun parse(o: JSONObject): PageObs = PageObs(
            state = o.optString("state", "unknown"),
            onKeys = o.optBoolean("onKeys"),
            rendered = o.optBoolean("rendered", true),
            dialogOpen = o.optBoolean("dialogOpen"),
            nameEmpty = o.optBoolean("nameEmpty"),
            createFound = o.optBoolean("createFound"),
            confirmFound = o.optBoolean("confirmFound"),
            formOnPage = o.optBoolean("formOnPage"),
            path = o.optString("path"),
            precheck = o.optInt("precheck", -1),
        )
    }
}

/**
 * Step budgets, tracked by STEP (not by url/state signature, so a console
 * that keeps rewriting its URL cannot reset them). When a step runs out the
 * engine opens the visible sheet on that step instead of spinning.
 */
enum class AkStep(val zh: String, val en: String, val budgetMs: Long, val checklist: Int) {
    OPEN("打开页面", "Open page", 20_000, 0),
    CHALLENGE("页面安全检查", "Security check", 20_000, 0),
    LOGIN("登录", "Sign in", 30_000, 1),
    NAV("打开 Key 页", "Open key page", 20_000, 2),
    CREATE("新建 Key", "Create key", 15_000, 3),
    FILL("填写名称", "Name the key", 8_000, 3),
    CONFIRM("确认创建", "Confirm", 15_000, 3),
    READ("读取 Key", "Read key", 20_000, 4),
    ;

    companion object {
        val CHECKLIST = listOf("打开页面", "登录", "打开 Key 页", "新建 Key", "读取 Key")
        fun budgets(recipe: JSONObject?): Map<AkStep, Long> {
            val o = recipe?.optJSONObject("budgets")
            return values().associateWith { s -> o?.optLong(s.name.lowercase(), 0L)?.takeIf { it > 0 } ?: s.budgetMs }
        }
    }
}

class StepClock(private val budgets: Map<AkStep, Long>) {
    var step: AkStep = AkStep.OPEN; private set
    var since: Long = 0; private set
    /** Steps whose budget already opened the sheet (once per entry). */
    private var flagged = false

    fun enter(s: AkStep, now: Long) { if (s != step || since == 0L) { step = s; since = now; flagged = false } }
    fun elapsed(now: Long) = now - since
    fun expired(now: Long) = since > 0 && now - since > (budgets[step] ?: step.budgetMs)
    /** True exactly once per step entry when its budget runs out. */
    fun expiredOnce(now: Long): Boolean { if (!flagged && expired(now)) { flagged = true; return true }; return false }
}

/**
 * The observation-driven create flow: act, then wait (within the step
 * budget) for something observable before the next act. Never clicks
 * create again after confirm (no second key), never re-clicks within the
 * debounce, retries each act up to [MAX_TRIES].
 */
class CreateFlow(budgets: Map<AkStep, Long> = AkStep.budgets(null)) {
    enum class Act { CLICK_CREATE, FILL_NAME, CONFIRM, REVEAL, WAIT, DONE, NEED_USER }
    data class Decision(val act: Act, val reason: String = "")

    val clock = StepClock(budgets)
    var createdAt: Long? = null; private set
    var confirmedAt: Long? = null; private set
    var gaveUp = false; private set
    private val tries = HashMap<AkStep, Int>()
    private var lastActAt = 0L
    private var lastPath: String? = null
    private var noCreateSeen = 0

    fun tries(s: AkStep) = tries[s] ?: 0

    fun begin(now: Long) { if (clock.step.ordinal < AkStep.CREATE.ordinal) clock.enter(AkStep.CREATE, now) }

    fun next(o: PageObs, hasFreshKey: Boolean, now: Long): Decision {
        if (hasFreshKey) return Decision(Act.DONE)
        if (gaveUp) return Decision(Act.WAIT)
        begin(now)
        // SPA route change while creating: start over at create, but never after confirm.
        if (lastPath != null && o.path != lastPath && confirmedAt == null && clock.step in setOf(AkStep.FILL, AkStep.CONFIRM) && !o.dialogOpen && !o.formOnPage) {
            clock.enter(AkStep.CREATE, now); lastActAt = 0
        }
        lastPath = o.path
        val sinceAct = now - lastActAt
        when (clock.step) {
            AkStep.CREATE -> {
                if (createdAt != null && (o.dialogOpen || o.formOnPage)) { clock.enter(AkStep.FILL, now); return next(o, false, now) }
                // Already on a full-page "new key" form (Hugging Face /settings/tokens/new).
                if (createdAt == null && o.formOnPage && o.nameEmpty && !o.dialogOpen) { createdAt = now; clock.enter(AkStep.FILL, now); return next(o, false, now) }
                if (sinceAct < WAIT_AFTER_ACT) return Decision(Act.WAIT)
                if (!o.createFound) {
                    noCreateSeen++
                    if (clock.expired(now)) return giveUp("没找到「创建 Key」按钮（页面可能改版）")
                    return Decision(Act.WAIT)
                }
                if (tries(AkStep.CREATE) >= MAX_TRIES) return if (clock.expired(now)) giveUp("点了「创建」但没有出现新建窗口") else Decision(Act.WAIT)
                bump(AkStep.CREATE, now); if (createdAt == null) createdAt = now
                return Decision(Act.CLICK_CREATE)
            }
            AkStep.FILL -> {
                if (o.nameEmpty && tries(AkStep.FILL) < 2) { bump(AkStep.FILL, now); return Decision(Act.FILL_NAME) }
                clock.enter(AkStep.CONFIRM, now); lastActAt = now - WAIT_AFTER_ACT + 400
                return Decision(Act.WAIT)
            }
            AkStep.CONFIRM -> {
                if (confirmedAt != null && !o.dialogOpen && !o.formOnPage) { clock.enter(AkStep.READ, now); return Decision(Act.WAIT) }
                if (sinceAct < WAIT_AFTER_ACT) return Decision(Act.WAIT)
                if (confirmedAt != null && sinceAct > 3_000 && (o.dialogOpen || o.formOnPage) && !o.confirmFound) { clock.enter(AkStep.READ, now); return Decision(Act.WAIT) }
                if (!o.confirmFound) return if (clock.expired(now)) giveUp("没找到「确认创建」按钮") else Decision(Act.WAIT)
                if (tries(AkStep.CONFIRM) >= MAX_TRIES) { clock.enter(AkStep.READ, now); return Decision(Act.WAIT) }
                bump(AkStep.CONFIRM, now); if (confirmedAt == null) confirmedAt = now
                return Decision(Act.CONFIRM)
            }
            AkStep.READ -> {
                if (sinceAct >= REVEAL_EVERY && tries(AkStep.READ) < MAX_TRIES) { bump(AkStep.READ, now); return Decision(Act.REVEAL) }
                if (clock.expired(now)) return giveUp("已尝试新建 Key，但没在页面上读到它")
                return Decision(Act.WAIT)
            }
            else -> return Decision(Act.WAIT)
        }
    }

    private fun bump(s: AkStep, now: Long) { tries[s] = tries(s) + 1; lastActAt = now }
    private fun giveUp(why: String): Decision { gaveUp = true; return Decision(Act.NEED_USER, why) }

    companion object {
        const val MAX_TRIES = 3
        const val WAIT_AFTER_ACT = 1_600L
        const val REVEAL_EVERY = 3_000L
    }
}

/**
 * Null `evaluateJavascript` results (destroyed WebView, hung renderer,
 * a script that threw). Every one still counts against the run timeout.
 */
class NullEvals(private val showAt: Int = 5, private val failAt: Int = 40) {
    var streak = 0; private set
    enum class Verdict { OK, KEEP_WAITING, SHOW_STUCK, FAIL }
    fun onResult(ok: Boolean): Verdict {
        if (ok) { streak = 0; return Verdict.OK }
        streak++
        return when {
            streak >= failAt -> Verdict.FAIL
            streak == showAt -> Verdict.SHOW_STUCK
            else -> Verdict.KEEP_WAITING
        }
    }
}

/** Maps a page state to the step it belongs to (for budgets and the checklist). */
object StateSteps {
    fun stepFor(state: String, onKeys: Boolean, rendered: Boolean): AkStep? = when (state) {
        "loading", "unknown" -> AkStep.OPEN
        "challenge" -> AkStep.CHALLENGE
        "login", "verify", "qr", "email_code", "sms_code" -> AkStep.LOGIN
        "in" -> if (!rendered) AkStep.OPEN else if (onKeys) null else AkStep.NAV
        "elsewhere", "google" -> AkStep.LOGIN
        else -> AkStep.OPEN
    }

    /** States in which the engine must never reload the page. */
    fun noReload(state: String) = state == "challenge" || state == "verify" || state == "qr" || state == "loading"
}

/** `intent://` link → (package-less) target + fallback, parsed without Android for tests. */
object IntentLinks {
    /** browser_fallback_url of an intent:// URL (only http/https), or null. */
    fun fallback(url: String): String? {
        if (!url.startsWith("intent:", ignoreCase = true)) return null
        val frag = url.substringAfter("#Intent;", "")
        for (part in frag.split(';')) {
            if (part.startsWith("S.browser_fallback_url=")) {
                val v = runCatching { java.net.URLDecoder.decode(part.substringAfter('='), "UTF-8") }.getOrNull() ?: return null
                return v.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            }
        }
        return null
    }

    fun isGoogleLogin(host: String?): Boolean = host != null && (host == "accounts.google.com" || host.endsWith(".accounts.google.com"))
}

/**
 * [fork/mibot-autokey-v2] OAuth PKCE (recipe `flow: "oauth_pkce"`, OpenRouter):
 * verifier / S256 challenge, the authorization URL and the callback parser,
 * pure so they are unit tested. OpenRouter docs (openrouter.ai/docs/guides/
 * overview/auth/oauth, fetched 2026-11-04): GET /auth?callback_url=…
 * &code_challenge=…&code_challenge_method=S256[&state=…&key_label=…], then
 * POST /api/v1/auth/keys {code, code_verifier, code_challenge_method} → {key}.
 */
object Pkce {
    private fun b64url(b: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    /** RFC 7636 verifier from [random] bytes (32 bytes → 43 chars; 96 → 128). */
    fun verifier(random: ByteArray): String {
        require(random.size in 32..96) { "32..96 random bytes" }
        return b64url(random)
    }

    fun challengeS256(verifier: String): String =
        b64url(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun authUrl(base: String, callback: String, challenge: String, state: String?, keyLabel: String?): String = buildString {
        append(base).append(if ('?' in base) '&' else '?')
        append("callback_url=").append(enc(callback))
        append("&code_challenge=").append(enc(challenge)).append("&code_challenge_method=S256")
        if (!state.isNullOrBlank()) append("&state=").append(enc(state))
        if (!keyLabel.isNullOrBlank()) append("&key_label=").append(enc(keyLabel))
    }

    data class Callback(val code: String?, val state: String?, val error: String?)

    /**
     * Parses [url] when it is the callback [expected] (same scheme, host and
     * path; query ignored), else null. A callback without code and without
     * error is still returned (code = null) so the caller can report it.
     */
    fun parseCallback(url: String, expected: String): Callback? {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val e = runCatching { URI(expected.trim()) }.getOrNull() ?: return null
        if (!u.scheme.equals(e.scheme, ignoreCase = true)) return null
        if (!(u.host ?: u.authority ?: "").equals(e.host ?: e.authority ?: "", ignoreCase = true)) return null
        if ((u.path ?: "").trimEnd('/') != (e.path ?: "").trimEnd('/')) return null
        val q = HashMap<String, String>()
        (u.rawQuery ?: "").split('&').filter { it.isNotBlank() }.forEach { kv ->
            val k = java.net.URLDecoder.decode(kv.substringBefore('='), "UTF-8")
            val v = java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8")
            q.putIfAbsent(k, v)
        }
        return Callback(q["code"]?.takeIf { it.isNotBlank() }, q["state"]?.takeIf { it.isNotBlank() },
            (q["error_description"] ?: q["error"])?.takeIf { it.isNotBlank() })
    }

    fun exchangeBody(code: String, verifier: String): JSONObject =
        JSONObject().put("code", code).put("code_verifier", verifier).put("code_challenge_method", "S256")

    /** The verifier for a callback: by state; with no state, only when exactly one attempt is open. */
    fun verifierFor(cb: Callback, attempts: Map<String, String>): String? =
        if (cb.state != null) attempts[cb.state] else attempts.values.singleOrNull()

    /** Callback kinds a recipe may list, in the order they are tried. */
    val CALLBACK_KINDS = setOf("deeplink", "loopback")
}
