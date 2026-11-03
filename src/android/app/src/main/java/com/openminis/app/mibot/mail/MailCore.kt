package com.openminis.app.mibot.mail

import android.content.Context
import android.util.Base64
import com.openminis.app.mibot.vault.VaultCrypto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * [fork/mibot-v5b2] 临时邮箱 / AI 邮箱 core — the ONE implementation behind the
 * `mibot-mail` sandbox CLI (MailCli), the agent tool, the auto-login recipes
 * and the 邮箱台账 screens.
 */
enum class ProbeStatus(val zh: String) {
    OK("正常"), TOKEN_EXPIRED("token过期"), PLATFORM_DOWN("平台不可用"), DELETED("邮箱被删"), RATE_LIMITED("被限流"), NEEDS_CONFIG("需配置"), ERROR("出错")
}

data class ProbeResult(val at: Long, val status: ProbeStatus, val ms: Long, val detail: String) {
    fun toJson() = JSONObject().put("at", at).put("status", status.name).put("ms", ms).put("detail", detail)
    companion object {
        fun from(o: JSONObject?) = o?.let { ProbeResult(it.optLong("at"), runCatching { ProbeStatus.valueOf(it.optString("status")) }.getOrDefault(ProbeStatus.ERROR), it.optLong("ms"), it.optString("detail")) }
    }
}

data class SendRecord(
    val id: String, val at: Long, val to: String, val subject: String, val text: String,
    /** queued / needs_confirm / sent / undone / failed / blocked */
    val state: String, val detail: String = "", val kind: String = "send",
) {
    fun toJson() = JSONObject().put("id", id).put("at", at).put("to", to).put("subject", subject).put("text", text)
        .put("state", state).put("detail", detail).put("kind", kind)
    companion object {
        fun from(o: JSONObject) = SendRecord(o.optString("id"), o.optLong("at"), o.optString("to"), o.optString("subject"), o.optString("text"),
            o.optString("state"), o.optString("detail"), o.optString("kind", "send"))
    }
}

/** Autonomy for outgoing mail. */
enum class MailAutonomy(val zh: String) { AUTO("全自动"), SERVICE("仅自动处理验证与服务邮件，发给真人前确认"), CONFIRM("每封都确认") }

data class Mailbox(
    val id: String,
    val provider: String,
    val address: String,
    val passEnc: String? = null,
    val tokenEnc: String? = null,
    /** Other per-mailbox template vars (accountId…), encrypted JSON. */
    val varsEnc: String? = null,
    val tokenExpiry: Long = 0,
    val lifetimeEnd: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = 0,
    val probe: ProbeResult? = null,
    val messageCount: Int = 0,
    val linked: List<String> = emptyList(),
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val lastCode: String? = null,
    val lastCodeAt: Long = 0,
    val autonomy: MailAutonomy = MailAutonomy.SERVICE,
    val outbox: List<SendRecord> = emptyList(),
) {
    val local get() = address.substringBefore('@')
    val domain get() = address.substringAfter('@', "")

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("provider", provider); put("address", address)
        passEnc?.let { put("passEnc", it) }; tokenEnc?.let { put("tokenEnc", it) }; varsEnc?.let { put("varsEnc", it) }
        put("tokenExpiry", tokenExpiry); put("lifetimeEnd", lifetimeEnd); put("createdAt", createdAt); put("lastLogin", lastLogin)
        probe?.let { put("probe", it.toJson()) }
        put("messageCount", messageCount); put("linked", JSONArray(linked)); put("notes", notes); put("tags", JSONArray(tags))
        lastCode?.let { put("lastCode", it) }; put("lastCodeAt", lastCodeAt); put("autonomy", autonomy.name)
        put("outbox", JSONArray().also { a -> outbox.takeLast(200).forEach { a.put(it.toJson()) } })
    }

    /** Safe for CLI / agent: secrets masked. */
    fun toPublicJson(ctx: Context): JSONObject {
        val p = MailProviders.get(ctx, provider)
        return JSONObject().apply {
            put("id", id); put("address", address); put("platform", provider); put("platform_name", p?.name ?: provider)
            put("can_send", p?.canSend == true); put("receive_only", p?.canSend != true)
            put("password", if (passEnc != null) "••••••" else JSONObject.NULL)
            put("token", if (tokenEnc != null) "••••" + (MailCore.reveal(tokenEnc)?.takeLast(4) ?: "") else JSONObject.NULL)
            if (tokenExpiry > 0) put("token_expiry", tokenExpiry)
            if (lifetimeEnd > 0) put("expires", lifetimeEnd)
            put("created", createdAt); if (lastLogin > 0) put("last_login", lastLogin)
            probe?.let { put("probe", JSONObject().put("status", it.status.zh).put("ms", it.ms).put("at", it.at).put("detail", it.detail)) }
            put("messages", messageCount); put("linked", JSONArray(linked)); put("notes", notes); put("tags", JSONArray(tags))
            lastCode?.let { put("last_code", it) }
            put("autonomy", autonomy.name.lowercase())
            if (p?.publicInbox == true) put("warning", "公开收件箱：任何知道地址的人都能读")
        }
    }

    companion object {
        fun from(o: JSONObject): Mailbox {
            fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            return Mailbox(
                id = o.getString("id"), provider = o.optString("provider"), address = o.optString("address"),
                passEnc = o.optString("passEnc").ifBlank { null }, tokenEnc = o.optString("tokenEnc").ifBlank { null },
                varsEnc = o.optString("varsEnc").ifBlank { null }, tokenExpiry = o.optLong("tokenExpiry"), lifetimeEnd = o.optLong("lifetimeEnd"),
                createdAt = o.optLong("createdAt"), lastLogin = o.optLong("lastLogin"), probe = ProbeResult.from(o.optJSONObject("probe")),
                messageCount = o.optInt("messageCount"), linked = strs("linked"), notes = o.optString("notes"), tags = strs("tags"),
                lastCode = o.optString("lastCode").ifBlank { null }, lastCodeAt = o.optLong("lastCodeAt"),
                autonomy = runCatching { MailAutonomy.valueOf(o.optString("autonomy")) }.getOrDefault(MailAutonomy.SERVICE),
                outbox = o.optJSONArray("outbox")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(SendRecord::from) } } ?: emptyList(),
            )
        }
    }
}

data class MailMessage(val id: String, val from: String, val subject: String, val date: String, val preview: String, val text: String?, val html: String?) {
    fun toJson() = JSONObject().put("id", id).put("from", from).put("subject", subject).put("date", date).put("preview", preview)
}

data class FoundCode(val code: String?, val link: String?, val messageId: String, val from: String, val subject: String) {
    fun toJson() = JSONObject().put("code", code ?: JSONObject.NULL).put("link", link ?: JSONObject.NULL).put("message_id", messageId).put("from", from).put("subject", subject)
}

data class WatchRule(val id: String, val mailbox: String, val fromContains: String, val subjectContains: String, val action: String, val note: String) {
    fun toJson() = JSONObject().put("id", id).put("mailbox", mailbox).put("from", fromContains).put("subject", subjectContains).put("action", action).put("note", note)
    companion object { fun from(o: JSONObject) = WatchRule(o.optString("id"), o.optString("mailbox"), o.optString("from"), o.optString("subject"), o.optString("action", "notify"), o.optString("note")) }
}

class MailException(msg: String) : Exception(msg)

object MailCore {
    private val lock = Any()
    private val _boxes = MutableStateFlow<List<Mailbox>>(emptyList())
    private val _rules = MutableStateFlow<List<WatchRule>>(emptyList())
    @Volatile private var loaded = false
    private val rnd = SecureRandom()

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "mibot-mail").apply { mkdirs() }
    private fun file(ctx: Context) = File(dir(ctx), "ledger.json")

    fun boxes(ctx: Context): StateFlow<List<Mailbox>> { ensure(ctx); return _boxes.asStateFlow() }
    fun rules(ctx: Context): List<WatchRule> { ensure(ctx); return _rules.value }

    private fun ensure(ctx: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            runCatching {
                val o = JSONObject(file(ctx).readText())
                _boxes.value = o.optJSONArray("mailboxes")?.let { a -> (0 until a.length()).mapNotNull { runCatching { Mailbox.from(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList()
                _rules.value = o.optJSONArray("rules")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(WatchRule::from) } } ?: emptyList()
            }
            loaded = true
        }
    }

    private fun persist(ctx: Context) {
        val o = JSONObject().put("version", 1)
            .put("mailboxes", JSONArray().also { a -> _boxes.value.forEach { a.put(it.toJson()) } })
            .put("rules", JSONArray().also { a -> _rules.value.forEach { a.put(it.toJson()) } })
        runCatching { file(ctx).writeText(o.toString()) }
    }

    fun get(ctx: Context, idOrAddr: String): Mailbox? {
        ensure(ctx)
        val k = idOrAddr.trim().lowercase()
        return _boxes.value.find { it.id == k || it.address.lowercase() == k }
    }

    fun update(ctx: Context, id: String, f: (Mailbox) -> Mailbox) {
        ensure(ctx)
        synchronized(lock) { _boxes.value = _boxes.value.map { if (it.id == id) f(it) else it }; persist(ctx) }
    }

    private fun put(ctx: Context, m: Mailbox) {
        ensure(ctx)
        synchronized(lock) { _boxes.value = _boxes.value.filterNot { it.id == m.id } + m; persist(ctx) }
    }

    fun reveal(enc: String?): String? = enc?.let { runCatching { VaultCrypto.decrypt(it) }.getOrNull() }
    private fun enc(s: String?): String? = s?.takeIf { it.isNotEmpty() }?.let { VaultCrypto.encrypt(it) }

    private fun randLocal(): String {
        val a = "abcdefghijkmnpqrstuvwxyz23456789"
        return "mb" + (1..9).map { a[rnd.nextInt(a.length)] }.joinToString("")
    }

    private fun randPass(): String {
        val a = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        return (1..16).map { a[rnd.nextInt(a.length)] }.joinToString("") + "!9"
    }

    private fun varsOf(ctx: Context, m: Mailbox, p: MailProvider): MutableMap<String, String> {
        val v = mutableMapOf("address" to m.address, "local" to m.local, "domain" to m.domain)
        reveal(m.passEnc)?.let { v["pass"] = it }
        reveal(m.tokenEnc)?.let { v["token"] = it }
        reveal(m.varsEnc)?.let { s -> runCatching { val o = JSONObject(s); o.keys().forEach { k -> v[k] = o.optString(k) } } }
        v.putAll(MailProviders.configVars(ctx, p))
        return v
    }

    private fun classify(r: MailHttp.Resp): ProbeStatus = when {
        r.code == -1 -> ProbeStatus.PLATFORM_DOWN
        r.code == 401 || r.code == 403 -> ProbeStatus.TOKEN_EXPIRED
        r.code == 404 || r.code == 410 -> ProbeStatus.DELETED
        r.code == 429 -> ProbeStatus.RATE_LIMITED
        r.code >= 500 -> ProbeStatus.PLATFORM_DOWN
        r.ok -> ProbeStatus.OK
        else -> ProbeStatus.ERROR
    }

    // ------------------------------------------------------------------ create

    /**
     * Creates a mailbox on [providerId], or on the first usable platform with
     * fallback across platforms. [needSend] limits to platforms that can send.
     */
    suspend fun create(ctx: Context, providerId: String? = null, linkService: String? = null, needSend: Boolean = false, exclude: Set<String> = emptySet()): Mailbox {
        ensure(ctx)
        val all = MailProviders.all(ctx)
        val candidates = if (providerId != null) listOfNotNull(all.find { it.id == providerId } ?: throw MailException("unknown platform $providerId"))
        else all.filter { MailProviders.usable(ctx, it) && (!needSend || it.canSend) && it.id !in exclude && (it.kind == "temp" || needSend || it.kind == "demo") }
            .sortedBy { if (it.publicInbox) 1 else 0 }
        if (candidates.isEmpty()) throw MailException(if (needSend) "没有可发信的邮箱平台：请在「邮箱台账 › 平台」配置 AgentMail Key 或自建 Worker" else "没有可用的临时邮箱平台")
        val errors = mutableListOf<String>()
        for (p in candidates) {
            val miss = MailProviders.missing(ctx, p)
            if (miss.isNotEmpty()) { errors += "${p.id}: 需配置 ${miss.joinToString()}"; continue }
            if (!MailProviders.enabled(ctx, p) && providerId == null) continue
            val r = runCatching { createOn(ctx, p, linkService) }
            r.getOrNull()?.let { return it }
            errors += "${p.id}: ${r.exceptionOrNull()?.message}"
        }
        throw MailException("建箱失败：" + errors.joinToString("；"))
    }

    private suspend fun createOn(ctx: Context, p: MailProvider, linkService: String?): Mailbox {
        if (p.kind == "demo") return MailDemo.create(ctx, linkService).also { put(ctx, it) }
        val vars = mutableMapOf("rand" to randLocal(), "pass" to randPass())
        vars.putAll(MailProviders.configVars(ctx, p))
        var expiresAt = 0L
        for (st in p.steps("create")) {
            if (st.has("url")) {
                val r = MailHttp.call(st, vars)
                if (!r.ok) throw MailException("HTTP ${if (r.code < 0) (r.error ?: "network") else r.code.toString()} ${r.body.take(120)}")
                val root = r.json()
                st.optJSONObject("save")?.let { s -> s.keys().forEach { k -> MailHttp.pick(root, s.optString(k))?.let { vars[k] = it } } }
            } else {
                st.keys().forEach { k -> vars[k] = MailHttp.sub(st.optString(k), vars) }
            }
            if ("address" in vars && "local" !in vars) vars["local"] = vars["address"]!!.substringBefore('@')
        }
        val address = vars["address"]?.takeIf { '@' in it } ?: throw MailException("平台没有返回邮箱地址")
        vars["expiresAt"]?.let { s -> runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()?.let { expiresAt = it } }
        val now = System.currentTimeMillis()
        val extra = JSONObject().also { o -> vars.filterKeys { it !in setOf("rand", "pass", "token", "address", "local", "domain") && it !in p.needs }.forEach { (k, v) -> o.put(k, v) } }
        val lifetime = p.json.optDouble("lifetimeHours", 0.0).takeIf { it > 0 }?.let { now + (it * 3_600_000).toLong() } ?: expiresAt
        val ttl = p.json.optDouble("tokenTtlHours", 0.0).takeIf { it > 0 }?.let { now + (it * 3_600_000).toLong() } ?: 0L
        val usesPass = p.json.optJSONArray("create")?.toString()?.contains("{{pass}}") == true
        val m = Mailbox(
            id = "mb" + Integer.toHexString(rnd.nextInt()).takeLast(6), provider = p.id, address = address.lowercase(),
            passEnc = if (usesPass) enc(vars["pass"]) else null, tokenEnc = enc(vars["token"]), varsEnc = enc(extra.toString().takeIf { extra.length() > 0 }),
            tokenExpiry = ttl, lifetimeEnd = lifetime, createdAt = now, lastLogin = if (vars["token"] != null) now else 0,
            linked = listOfNotNull(linkService), autonomy = defaultAutonomy(ctx),
        )
        put(ctx, m)
        return m
    }

    fun defaultAutonomy(ctx: Context): MailAutonomy =
        if (com.openminis.app.mibot.settings.Automation.isMax(ctx)) MailAutonomy.AUTO else MailAutonomy.SERVICE

    // ------------------------------------------------------------------ login

    suspend fun login(ctx: Context, m: Mailbox): Boolean {
        val p = MailProviders.get(ctx, m.provider) ?: return false
        val steps = p.steps("login")
        if (steps.isEmpty()) return m.tokenEnc != null || p.kind == "demo"
        val vars = varsOf(ctx, m, p)
        for (st in steps) {
            val r = MailHttp.call(st, vars)
            if (!r.ok) return false
            val root = r.json()
            st.optJSONObject("save")?.let { s -> s.keys().forEach { k -> MailHttp.pick(root, s.optString(k))?.let { vars[k] = it } } }
        }
        val now = System.currentTimeMillis()
        val ttl = p.json.optDouble("tokenTtlHours", 0.0).takeIf { it > 0 }?.let { now + (it * 3_600_000).toLong() } ?: 0L
        update(ctx, m.id) { it.copy(tokenEnc = enc(vars["token"]) ?: it.tokenEnc, lastLogin = now, tokenExpiry = ttl) }
        return true
    }

    // ------------------------------------------------------------------ inbox

    suspend fun inbox(ctx: Context, m0: Mailbox, retryLogin: Boolean = true): List<MailMessage> {
        if (m0.provider == MailDemo.ID) return MailDemo.inbox(m0).also { l -> update(ctx, m0.id) { it.copy(messageCount = l.size) } }
        val p = MailProviders.get(ctx, m0.provider) ?: throw MailException("platform ${m0.provider} missing")
        var m = m0
        if (m.tokenExpiry in 1 until System.currentTimeMillis() && p.steps("login").isNotEmpty()) { login(ctx, m); m = get(ctx, m.id) ?: m }
        val spec = p.step("list") ?: throw MailException("platform has no list")
        val r = MailHttp.call(spec, varsOf(ctx, m, p))
        if ((r.code == 401 || r.code == 403) && retryLogin && p.steps("login").isNotEmpty() && login(ctx, m)) return inbox(ctx, get(ctx, m.id) ?: m, false)
        if (!r.ok) throw MailException("${classify(r).zh}（HTTP ${if (r.code < 0) r.error else r.code}）")
        val root = r.json()
        if (MailHttp.pick(root, spec.optString("expiredFlag")) == "true") throw MailException(ProbeStatus.DELETED.zh + "（平台已回收）")
        val items = MailHttp.pickArray(root, spec.optString("items", "$")) ?: JSONArray()
        val map = spec.optJSONObject("map") ?: JSONObject()
        val out = (0 until items.length()).mapNotNull { i ->
            val it = items.opt(i) as? JSONObject ?: return@mapNotNull null
            fun f(k: String) = MailHttp.pick(it, map.optString(k)).orEmpty()
            val raw = f("raw").ifBlank { null }
            MailMessage(f("id").ifBlank { i.toString() }, f("from"), f("subject").ifBlank { raw?.let { r2 -> Regex("(?im)^Subject:\\s*(.*)$").find(r2)?.groupValues?.get(1) }.orEmpty() },
                f("date"), f("preview"), f("text").ifBlank { raw }, f("html").ifBlank { null })
        }
        update(ctx, m.id) { it.copy(messageCount = out.size) }
        return out
    }

    suspend fun read(ctx: Context, m: Mailbox, msg: MailMessage): String {
        if (!msg.text.isNullOrBlank()) return msg.text
        if (!msg.html.isNullOrBlank()) return htmlToText(msg.html)
        if (m.provider == MailDemo.ID) return msg.preview
        val p = MailProviders.get(ctx, m.provider) ?: return msg.preview
        val spec = p.step("read") ?: return msg.preview.ifBlank { msg.subject }
        val vars = varsOf(ctx, m, p); vars["id"] = msg.id
        val r = MailHttp.call(spec, vars)
        if (!r.ok) return msg.preview
        if (spec.optBoolean("raw")) return r.body
        val root = r.json()
        MailHttp.pick(root, spec.optString("text"))?.let { return it }
        MailHttp.pick(root, spec.optString("html"))?.let { return htmlToText(it) }
        return msg.preview
    }

    fun htmlToText(h: String): String = h
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>"), "\n")
        .replace(Regex("(?is)<a\\s[^>]*href=\"([^\"]+)\"[^>]*>"), " $1 ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'").replace("&quot;", "\"")
        .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n\\s*\\n+"), "\n").trim()

    // ------------------------------------------------------------------ codes

    private val codeNear = Regex("(?i)(验证码|校验码|动态码|确认码|激活码|verification code|security code|one[- ]time|otp|code|驗證碼|코드|認証コード)[^0-9A-Za-z]{0,40}([0-9]{4,8})")
    private val codeBefore = Regex("(?i)\\b([0-9]{4,8})\\b[^0-9]{0,30}(是你的|为您的|is your|是您的).{0,12}(验证码|code)")
    private val sixDigits = Regex("(?<![0-9#])([0-9]{6})(?![0-9])")
    private val linkRx = Regex("https?://[^\\s\"'<>()]+", RegexOption.IGNORE_CASE)

    /** Extracts a 4–8 digit code or a verify/confirm link from mail text. */
    fun extract(text: String): Pair<String?, String?> {
        val t = text.take(60_000)
        val code = codeNear.find(t)?.groupValues?.get(2) ?: codeBefore.find(t)?.groupValues?.get(1) ?: sixDigits.find(t)?.groupValues?.get(1)
        val link = linkRx.findAll(t).map { it.value.trimEnd('.', ',', ';') }.firstOrNull { l ->
            Regex("(?i)verif|confirm|activat|magic|token=|/auth/|login|signin|validate|code=").containsMatchIn(l) && !Regex("(?i)unsubscribe|privacy|\\.png|\\.jpg").containsMatchIn(l)
        }
        return code to link
    }

    /** Polls the inbox until a code/link arrives (newer than [since]) from [fromDomain]. */
    suspend fun waitCode(ctx: Context, m: Mailbox, fromDomain: String? = null, timeoutSec: Int = 120, since: Set<String> = emptySet(), onTick: (Int) -> Unit = {}): FoundCode? {
        val seen = since.toMutableSet()
        val deadline = System.currentTimeMillis() + timeoutSec * 1000L
        var round = 0
        while (System.currentTimeMillis() < deadline) {
            onTick(round++)
            val msgs = runCatching { inbox(ctx, get(ctx, m.id) ?: m) }.getOrDefault(emptyList())
            for (msg in msgs) {
                if (msg.id in seen) continue
                seen += msg.id
                if (fromDomain != null && !msg.from.contains(fromDomain, ignoreCase = true) && !msg.subject.contains(fromDomain.substringBefore('.'), ignoreCase = true)) continue
                val body = read(ctx, m, msg)
                val (code, link) = extract(msg.subject + "\n" + body)
                if (code != null || link != null) {
                    update(ctx, m.id) { it.copy(lastCode = code ?: link, lastCodeAt = System.currentTimeMillis()) }
                    return FoundCode(code, link, msg.id, msg.from, msg.subject)
                }
            }
            delay(4000)
        }
        return null
    }

    suspend fun search(ctx: Context, q: String, box: Mailbox? = null): List<Pair<Mailbox, MailMessage>> {
        val boxes = box?.let { listOf(it) } ?: boxes(ctx).value
        val needle = q.lowercase()
        return boxes.flatMap { b -> runCatching { inbox(ctx, b) }.getOrDefault(emptyList()).filter { (it.from + " " + it.subject + " " + it.preview).lowercase().contains(needle) }.map { b to it } }
    }

    // ------------------------------------------------------------------ delete / probe

    suspend fun delete(ctx: Context, m: Mailbox): String {
        val p = MailProviders.get(ctx, m.provider)
        var remote = "仅从台账移除（平台不支持删除）"
        val spec = p?.step("delete")
        if (spec != null && !spec.optBoolean("perMessage")) {
            val r = MailHttp.call(spec, varsOf(ctx, m, p))
            remote = if (r.ok) "平台已删除" else "平台删除失败（HTTP ${r.code}），已从台账移除"
        }
        synchronized(lock) { _boxes.value = _boxes.value.filterNot { it.id == m.id }; persist(ctx) }
        return remote
    }

    suspend fun probeProvider(ctx: Context, p: MailProvider): ProbeResult {
        val now = System.currentTimeMillis()
        if (!MailProviders.enabled(ctx, p) && p.disabledReason != null) return ProbeResult(now, ProbeStatus.PLATFORM_DOWN, 0, p.disabledReason)
        val miss = MailProviders.missing(ctx, p)
        if (miss.isNotEmpty()) return ProbeResult(now, ProbeStatus.NEEDS_CONFIG, 0, "需配置 " + miss.joinToString())
        if (p.kind == "demo") return ProbeResult(now, ProbeStatus.OK, 1, "演示 · 本机回环")
        val spec = p.step("probe") ?: p.step("list") ?: return ProbeResult(now, ProbeStatus.ERROR, 0, "模板没有 probe")
        val r = MailHttp.call(spec, MailProviders.configVars(ctx, p) + mapOf("token" to "probe", "address" to "probe@example.com", "local" to "probe"))
        // [fork/mibot-v5b2] Platform level: there is no mailbox to have been deleted, so a 404/410
        // means the platform's endpoint is gone → 平台不可用 (not 邮箱被删).
        val st = when {
            r.ok || r.code == 400 -> ProbeStatus.OK
            r.code == 404 || r.code == 410 -> ProbeStatus.PLATFORM_DOWN
            else -> classify(r)
        }
        return ProbeResult(now, st, r.ms, if (r.code < 0) (r.error ?: "network") else if (st == ProbeStatus.PLATFORM_DOWN && r.code in 404..410) "接口不存在 · HTTP ${r.code}" else "HTTP ${r.code}")
    }

    /** Full mailbox probe: platform reachable, login works, token valid, inbox readable. */
    suspend fun probe(ctx: Context, m: Mailbox): ProbeResult {
        val p = MailProviders.get(ctx, m.provider)
        val t0 = System.currentTimeMillis()
        val res = when {
            p == null -> ProbeResult(t0, ProbeStatus.ERROR, 0, "平台模板缺失")
            m.lifetimeEnd in 1 until t0 -> ProbeResult(t0, ProbeStatus.DELETED, 0, "已过平台保留期")
            else -> {
                val pr = probeProvider(ctx, p)
                if (pr.status != ProbeStatus.OK) pr
                else {
                    val r = runCatching { inbox(ctx, m) }
                    val ms = System.currentTimeMillis() - t0
                    r.fold(
                        { ProbeResult(t0, ProbeStatus.OK, ms, "收件箱可读 · ${it.size} 封") },
                        { e ->
                            val msg = e.message.orEmpty()
                            val st = ProbeStatus.values().firstOrNull { msg.startsWith(it.zh) } ?: ProbeStatus.ERROR
                            ProbeResult(t0, st, ms, msg)
                        },
                    )
                }
            }
        }
        update(ctx, m.id) { it.copy(probe = res) }
        return res
    }

    /** 保活: refresh tokens / re-login, touch inboxes, report expiring + failing. */
    data class KeepAliveReport(val ok: Int, val failed: List<String>, val expiring: List<String>)

    suspend fun keepAlive(ctx: Context): KeepAliveReport {
        val now = System.currentTimeMillis()
        var ok = 0; val failed = mutableListOf<String>(); val expiring = mutableListOf<String>()
        for (m in boxes(ctx).value) {
            if (m.tokenExpiry in 1 until now + 3_600_000) login(ctx, m)
            val r = probe(ctx, get(ctx, m.id) ?: m)
            if (r.status == ProbeStatus.OK) ok++ else failed += "${m.address}：${r.status.zh}"
            if (m.lifetimeEnd > now && m.lifetimeEnd - now < 6 * 3_600_000L) expiring += m.address
            runWatch(ctx, get(ctx, m.id) ?: m)
        }
        return KeepAliveReport(ok, failed, expiring)
    }

    // ------------------------------------------------------------------ watch rules

    fun addRule(ctx: Context, r: WatchRule) { ensure(ctx); synchronized(lock) { _rules.value = _rules.value + r; persist(ctx) } }
    fun removeRule(ctx: Context, id: String) { ensure(ctx); synchronized(lock) { _rules.value = _rules.value.filterNot { it.id == id }; persist(ctx) } }

    private val seenForRules = HashSet<String>()

    suspend fun runWatch(ctx: Context, m: Mailbox): List<String> {
        val rules = rules(ctx).filter { it.mailbox == m.id || it.mailbox == m.address || it.mailbox == "*" }
        if (rules.isEmpty()) return emptyList()
        val hits = mutableListOf<String>()
        for (msg in runCatching { inbox(ctx, m) }.getOrDefault(emptyList())) {
            val key = m.id + "/" + msg.id
            if (!seenForRules.add(key)) continue
            for (r in rules) {
                if (r.fromContains.isNotBlank() && !msg.from.contains(r.fromContains, true)) continue
                if (r.subjectContains.isNotBlank() && !msg.subject.contains(r.subjectContains, true)) continue
                hits += "${r.id}: ${msg.subject}"
                MailNotify.post(ctx, "邮件规则命中 · ${m.address}", "${msg.from}：${msg.subject}" + if (r.note.isNotBlank()) "\n${r.note}" else "")
            }
        }
        return hits
    }

    // ------------------------------------------------------------------ export / import

    fun export(ctx: Context, passphrase: String): String {
        val arr = JSONArray()
        for (m in boxes(ctx).value) {
            val o = m.toJson()
            o.remove("passEnc"); o.remove("tokenEnc"); o.remove("varsEnc")
            reveal(m.passEnc)?.let { o.put("password", it) }
            reveal(m.tokenEnc)?.let { o.put("token", it) }
            reveal(m.varsEnc)?.let { o.put("vars", it) }
            arr.put(o)
        }
        val plain = JSONObject().put("mibot_mail_ledger", 1).put("mailboxes", arr).toString().toByteArray()
        val salt = ByteArray(16).also(rnd::nextBytes); val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, pbk(passphrase, salt), GCMParameterSpec(128, iv)) }
        val ct = c.doFinal(plain)
        fun b(x: ByteArray) = Base64.encodeToString(x, Base64.NO_WRAP)
        return JSONObject().put("format", "mibot-mail-ledger/1").put("kdf", "PBKDF2WithHmacSHA256/120000").put("salt", b(salt)).put("iv", b(iv)).put("data", b(ct)).toString(2)
    }

    fun import(ctx: Context, json: String, passphrase: String): Int {
        val o = JSONObject(json)
        if (o.optString("format") != "mibot-mail-ledger/1") throw MailException("不是 Mibot 邮箱台账导出文件")
        fun d(k: String) = Base64.decode(o.getString(k), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, pbk(passphrase, d("salt")), GCMParameterSpec(128, d("iv"))) }
        val plain = runCatching { String(c.doFinal(d("data"))) }.getOrElse { throw MailException("口令不对或文件损坏") }
        val arr = JSONObject(plain).optJSONArray("mailboxes") ?: JSONArray()
        var n = 0
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            val m = Mailbox.from(j).copy(passEnc = enc(j.optString("password").ifBlank { null }), tokenEnc = enc(j.optString("token").ifBlank { null }), varsEnc = enc(j.optString("vars").ifBlank { null }))
            if (get(ctx, m.address) == null) { put(ctx, m); n++ }
        }
        return n
    }

    private fun pbk(pass: String, salt: ByteArray) =
        SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass.toCharArray(), salt, 120_000, 256)).encoded, "AES")

    fun link(ctx: Context, m: Mailbox, service: String) = update(ctx, m.id) { if (service in it.linked) it else it.copy(linked = it.linked + service) }
}
