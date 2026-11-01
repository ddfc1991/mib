package com.openminis.app.mibot.vault

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * [fork/mibot-v2] Data-driven site manifests for the vault's per-account
 * "status" (balance / points / quota / expiry) and "checkin" actions.
 *
 * Built-ins ship in assets/mibot/vault_sites.json; the user or the agent can
 * add more as JSON files in filesDir/vault/sites/ (CLI: `vault site add`).
 *
 * Manifest shape:
 * {
 *   "id": "deepseek", "name": "DeepSeek", "match": ["deepseek.com"],
 *   "category": "AI 服务",
 *   "status":  { "type": "http", "method": "GET", "url": "...",
 *                "headers": {"Authorization": "Bearer {{secret}}"},
 *                "fields": {"balance": "$.balance_infos[0].total_balance"} },
 *   "checkin": { "type": "http" | "webview", "url": "...", "method": "POST",
 *                "headers": {...}, "body": "...", "success_path": "$.success",
 *                "message_path": "$.message", "points_path": "$.data.points" }
 * }
 * `{{secret}}` / `{{username}}` are substituted inside the app; the agent and
 * the CLI never see the values. "webview" check-ins (or any site that answers
 * with a captcha / login wall) are marked 需要手动 and open the page for the
 * user. There is deliberately no captcha solving.
 */
object VaultSites {
    data class Manifest(val id: String, val name: String, val match: List<String>, val category: String?, val json: JSONObject, val builtIn: Boolean) {
        val hasStatus get() = json.optJSONObject("status") != null
        val hasCheckin get() = json.optJSONObject("checkin") != null
        val checkinUrl: String? get() = json.optJSONObject("checkin")?.optStringOrNull("url")
    }

    private fun userDir(ctx: Context) = File(ctx.applicationContext.filesDir, "vault/sites").apply { mkdirs() }

    fun all(ctx: Context): List<Manifest> {
        val out = mutableListOf<Manifest>()
        runCatching {
            val text = ctx.assets.open("mibot/vault_sites.json").bufferedReader().use { it.readText() }
            JSONArray(text).objects().forEach { parse(it, true)?.let(out::add) }
        }
        userDir(ctx).listFiles { f -> f.name.endsWith(".json") }?.forEach { f ->
            runCatching { parse(JSONObject(f.readText()), false) }.getOrNull()?.let { m ->
                out.removeAll { it.id == m.id }
                out.add(m)
            }
        }
        return out
    }

    private fun parse(o: JSONObject, builtIn: Boolean): Manifest? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val match = o.optJSONArray("match")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        return Manifest(id, o.optString("name", id), match, o.optStringOrNull("category"), o, builtIn)
    }

    /** Validate and store a user manifest. Returns an error string or null. */
    fun add(ctx: Context, json: String): String? {
        val o = runCatching { JSONObject(json) }.getOrElse { return "invalid JSON: ${it.message}" }
        val id = o.optString("id")
        if (!Regex("[a-z0-9_.-]{2,40}").matches(id)) return "manifest needs an id of 2-40 chars [a-z0-9_.-]"
        if (o.optJSONObject("status") == null && o.optJSONObject("checkin") == null) return "manifest needs a status or checkin action"
        File(userDir(ctx), "$id.json").writeText(o.toString(2))
        return null
    }

    fun forEntry(ctx: Context, e: VaultEntry): Manifest? {
        val list = all(ctx)
        e.manifestId?.let { id -> list.find { it.id == id }?.let { return it } }
        val site = e.site.lowercase()
        return list.find { m -> m.match.any { it.isNotBlank() && site.contains(it.lowercase()) } }
    }

    // ------------------------------------------------------------- running

    data class HttpOut(val code: Int, val body: String)

    private suspend fun http(spec: JSONObject, vars: Map<String, String>): HttpOut = withContext(Dispatchers.IO) {
        fun sub(s: String) = vars.entries.fold(s) { acc, (k, v) -> acc.replace("{{$k}}", v) }
        val url = URL(sub(spec.getString("url")))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = spec.optString("method", "GET").uppercase()
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            spec.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> setRequestProperty(k, sub(h.optString(k))) } }
        }
        try {
            val body = spec.optStringOrNull("body")
            if (body != null && conn.requestMethod != "GET") {
                conn.doOutput = true
                if (conn.getRequestProperty("Content-Type") == null) conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(sub(body).toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            HttpOut(code, stream?.bufferedReader()?.use { it.readText() }?.take(64_000) ?: "")
        } finally {
            conn.disconnect()
        }
    }

    /** Minimal JSONPath: `$.a.b[0].c`. */
    fun path(json: Any?, p: String): Any? {
        var cur: Any? = json
        val tokens = Regex("""[^.\[\]]+|\[\d+]""").findAll(p.removePrefix("$")).map { it.value }.toList()
        for (tok in tokens) {
            cur = when {
                tok.startsWith("[") -> (cur as? JSONArray)?.opt(tok.trim('[', ']').toInt())
                else -> (cur as? JSONObject)?.opt(tok)
            }
            if (cur == null || cur == JSONObject.NULL) return null
        }
        return cur
    }

    private fun parseBody(body: String): Any? = runCatching {
        val s = body.trim()
        if (s.startsWith("[")) JSONArray(s) else JSONObject(s)
    }.getOrNull()

    private fun looksLikeCaptcha(body: String): Boolean {
        val b = body.lowercase()
        return listOf("captcha", "geetest", "hcaptcha", "recaptcha", "turnstile", "验证码", "人机验证").any { b.contains(it) }
    }

    private fun vars(ctx: Context, e: VaultEntry): Map<String, String>? {
        val secret = VaultStore.revealSecret(ctx, e) ?: return null
        return mapOf("secret" to secret, "username" to e.username)
    }

    /** Refresh one account's status. Returns a short human result. */
    suspend fun refreshStatus(ctx: Context, e: VaultEntry, actor: String): String {
        val m = forEntry(ctx, e)
        val spec = m?.json?.optJSONObject("status")
        if (spec == null) {
            VaultStore.updateStatus(ctx, e.id) { it.copy(error = "no status manifest", updatedAt = System.currentTimeMillis()) }
            return "no status manifest for ${e.site}"
        }
        if (e.permission == VaultPermission.DISABLED && actor != "user") {
            VaultStore.log(ctx, e, actor, "status", "disabled")
            return "account disabled for automatic use"
        }
        val v = vars(ctx, e) ?: return "no secret stored"
        val out = runCatching { http(spec, v) }.getOrElse {
            VaultStore.updateStatus(ctx, e.id) { s -> s.copy(error = it.message ?: "network error", updatedAt = System.currentTimeMillis()) }
            VaultStore.log(ctx, e, actor, "status", "error")
            return "error: ${it.message}"
        }
        if (out.code !in 200..299) {
            val err = "HTTP ${out.code}"
            VaultStore.updateStatus(ctx, e.id) { it.copy(error = err, updatedAt = System.currentTimeMillis()) }
            VaultStore.log(ctx, e, actor, "status", "error")
            return err
        }
        val root = parseBody(out.body)
        val fields = spec.optJSONObject("fields") ?: JSONObject()
        fun f(name: String): String? = fields.optStringOrNull(name)?.let { p -> path(root, p)?.toString() }
            ?.let { value -> (fields.optJSONObject("format")?.optStringOrNull(name))?.replace("{}", value) ?: value }
        val prefix = spec.optJSONObject("prefix")
        fun pf(name: String, v: String?) = v?.let { (prefix?.optString(name) ?: "") + it }
        VaultStore.updateStatus(ctx, e.id) {
            it.copy(
                balance = pf("balance", f("balance")) ?: it.balance,
                points = pf("points", f("points")) ?: it.points,
                credits = pf("credits", f("credits")) ?: it.credits,
                quota = pf("quota", f("quota")) ?: it.quota,
                expiry = pf("expiry", f("expiry")) ?: it.expiry,
                updatedAt = System.currentTimeMillis(),
                error = null,
            )
        }
        VaultStore.log(ctx, e, actor, "status", "ok")
        return "ok"
    }

    data class CheckinResult(val state: String, val message: String, val url: String? = null)

    /** Run one account's check-in. state: ok / manual / error / none. */
    suspend fun checkin(ctx: Context, e: VaultEntry, actor: String): CheckinResult {
        val m = forEntry(ctx, e)
        val spec = m?.json?.optJSONObject("checkin") ?: return CheckinResult("none", "no checkin manifest")
        if (e.permission == VaultPermission.DISABLED && actor != "user") {
            VaultStore.log(ctx, e, actor, "checkin", "disabled")
            return CheckinResult("error", "account disabled for automatic use")
        }
        val result = when (spec.optString("type", "http")) {
            "webview" -> CheckinResult(
                "manual",
                "需要手动 / manual check-in",
                spec.optStringOrNull("url") ?: "https://${e.site.removePrefix("https://").removePrefix("http://")}",
            )
            else -> {
                val v = vars(ctx, e) ?: return CheckinResult("error", "no secret stored")
                val out = runCatching { http(spec, v) }.getOrElse { return CheckinResult("error", it.message ?: "network error").also { r -> record(ctx, e, actor, r) } }
                when {
                    looksLikeCaptcha(out.body) -> CheckinResult("manual", "需要手动 (captcha)", spec.optStringOrNull("page") ?: spec.optStringOrNull("url"))
                    out.code !in 200..299 -> CheckinResult("error", "HTTP ${out.code}")
                    else -> {
                        val root = parseBody(out.body)
                        val okPath = spec.optStringOrNull("success_path")
                        val ok = okPath == null || path(root, okPath).let { it == true || it?.toString() == "true" || it?.toString() == "0" }
                        val msg = spec.optStringOrNull("message_path")?.let { path(root, it)?.toString() } ?: if (ok) "ok" else "failed"
                        val pts = spec.optStringOrNull("points_path")?.let { path(root, it)?.toString() }
                        CheckinResult(if (ok) "ok" else "error", if (pts != null) "$msg (+$pts)" else msg)
                    }
                }
            }
        }
        record(ctx, e, actor, result)
        return result
    }

    private fun record(ctx: Context, e: VaultEntry, actor: String, r: CheckinResult) {
        VaultStore.updateStatus(ctx, e.id) {
            it.copy(lastCheckinAt = System.currentTimeMillis(), lastCheckinState = r.state, lastCheckinMessage = r.message)
        }
        VaultStore.log(ctx, e, actor, "checkin", if (r.state == "ok") "ok" else r.state)
    }
}
