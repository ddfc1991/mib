package com.openminis.app.mibot.mail

import android.content.Context
import com.openminis.app.mibot.vault.VaultSites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * [fork/mibot-v5b2] Mail platforms as DATA (assets/mibot/mail_providers.json,
 * plus user templates in filesDir/mibot-mail/providers/<id>.json added with
 * `mibot-mail providers add`). One interface for every platform: a template
 * names HTTP steps for create / login / list / read / send / reply / forward /
 * delete / probe, with {{var}} substitution and JSONPath-lite extraction
 * (VaultSites.path). New platforms need no release.
 *
 * Temp-mail platforms are receive-only (kind=temp) and are labelled so;
 * sending needs kind=agent (AgentMail) or selfhost (Cloudflare temp-email
 * worker with send enabled). No SMS 接码 platforms, no captcha solving, no
 * proof-of-work or anti-abuse bypass: platforms that require those are listed
 * disabled with the reason.
 */
data class MailProvider(
    val id: String,
    val name: String,
    val site: String,
    /** temp | agent | selfhost | custom | demo */
    val kind: String,
    val canSend: Boolean,
    val enabledByDefault: Boolean,
    val disabledReason: String?,
    val needs: List<String>,
    val verified: String?,
    val publicInbox: Boolean,
    val json: JSONObject,
    val builtIn: Boolean,
) {
    val receiveOnly: Boolean get() = !canSend
    fun step(name: String): JSONObject? = json.optJSONObject(name)
    fun steps(name: String): List<JSONObject> = json.optJSONArray(name)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
}

object MailProviders {
    private fun userDir(ctx: Context) = File(ctx.applicationContext.filesDir, "mibot-mail/providers").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("mibot_mail_providers", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<MailProvider> {
        val out = mutableListOf<MailProvider>()
        runCatching {
            val root = JSONObject(ctx.assets.open("mibot/mail_providers.json").bufferedReader().use { it.readText() })
            val arr = root.optJSONArray("providers") ?: JSONArray()
            for (i in 0 until arr.length()) parse(arr.getJSONObject(i), true)?.let(out::add)
        }
        userDir(ctx).listFiles { f -> f.name.endsWith(".json") }?.forEach { f ->
            runCatching { parse(JSONObject(f.readText()), false) }.getOrNull()?.let { p -> out.removeAll { it.id == p.id }; out.add(p) }
        }
        if (MailDemo.enabled) out.add(MailDemo.provider)
        return out
    }

    fun get(ctx: Context, id: String): MailProvider? = all(ctx).find { it.id == id }

    private fun parse(o: JSONObject, builtIn: Boolean): MailProvider? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        val kind = o.optString("kind", "temp")
        return MailProvider(
            id = id, name = o.optString("name", id), site = o.optString("site"), kind = kind,
            canSend = o.opt("send") is JSONObject || kind == "demo",
            enabledByDefault = o.optBoolean("enabled", true), disabledReason = o.optString("disabledReason").ifBlank { null },
            needs = strs("needs"), verified = o.optString("verified").ifBlank { null },
            publicInbox = o.optBoolean("publicInbox", false), json = o, builtIn = builtIn,
        )
    }

    /** Validates and stores a user template. Returns an error or null. */
    fun add(ctx: Context, json: String): String? {
        val o = runCatching { JSONObject(json) }.getOrElse { return "invalid JSON: ${it.message}" }
        val id = o.optString("id")
        if (!Regex("[a-z0-9_.-]{2,40}").matches(id)) return "template needs an id of 2-40 chars [a-z0-9_.-]"
        if (o.optJSONArray("create") == null || o.optJSONObject("list") == null) return "template needs create[] and list{}"
        File(userDir(ctx), "$id.json").writeText(o.toString(2))
        return null
    }

    fun enabled(ctx: Context, p: MailProvider): Boolean =
        prefs(ctx).getString("enabled.${p.id}", null)?.toBoolean() ?: p.enabledByDefault

    fun setEnabled(ctx: Context, id: String, on: Boolean) = prefs(ctx).edit().putString("enabled.$id", on.toString()).apply()

    /** Per-platform settings (apiKey / base / adminToken / domain), Keystore-encrypted. */
    fun config(ctx: Context, id: String, key: String): String? =
        prefs(ctx).getString("cfg.$id.$key", null)?.let { runCatching { com.openminis.app.mibot.vault.VaultCrypto.decrypt(it) }.getOrNull() }

    fun setConfig(ctx: Context, id: String, key: String, value: String?) {
        val e = prefs(ctx).edit()
        if (value.isNullOrBlank()) e.remove("cfg.$id.$key") else e.putString("cfg.$id.$key", com.openminis.app.mibot.vault.VaultCrypto.encrypt(value.trim()))
        e.apply()
    }

    fun missing(ctx: Context, p: MailProvider): List<String> = p.needs.filter { config(ctx, p.id, it).isNullOrBlank() }

    fun usable(ctx: Context, p: MailProvider): Boolean = enabled(ctx, p) && missing(ctx, p).isEmpty() && p.json.optJSONArray("create") != null

    fun configVars(ctx: Context, p: MailProvider): Map<String, String> =
        p.needs.mapNotNull { k -> config(ctx, p.id, k)?.let { v -> k to v.trimEnd('/') } }.toMap()
}

/** HTTP executor for template steps. */
object MailHttp {
    data class Resp(val code: Int, val body: String, val ms: Long, val error: String? = null) {
        val ok get() = code in 200..299
        fun json(): Any? = runCatching { val s = body.trim(); if (s.startsWith("[")) JSONArray(s) else JSONObject(s) }.getOrNull()
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    }

    fun sub(s: String, vars: Map<String, String>, jsonEscape: Boolean = false): String =
        Regex("""\{\{([A-Za-z0-9_]+)\}\}""").replace(s) { m ->
            val v = vars[m.groupValues[1]] ?: ""
            if (jsonEscape) JSONObject.quote(v).let { it.substring(1, it.length - 1) } else v
        }

    private fun subJson(o: Any?, vars: Map<String, String>): Any? = when (o) {
        is JSONObject -> JSONObject().also { r -> o.keys().forEach { k -> r.put(k, subJson(o.get(k), vars)) } }
        is JSONArray -> JSONArray().also { r -> for (i in 0 until o.length()) r.put(subJson(o.get(i), vars)) }
        is String -> sub(o, vars)
        else -> o
    }

    suspend fun call(spec: JSONObject, vars: Map<String, String>): Resp = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()
        try {
            var url = sub(spec.getString("url"), vars)
            spec.optJSONObject("query")?.let { q ->
                val qs = q.keys().asSequence().joinToString("&") { k -> k + "=" + URLEncoder.encode(sub(q.optString(k), vars), "UTF-8") }
                url += (if ('?' in url) "&" else "?") + qs
            }
            val rb = Request.Builder().url(url).header("Accept", "application/json, text/plain, */*").header("User-Agent", "Mibot-Mail/1.0")
            spec.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> rb.header(k, sub(h.optString(k), vars)) } }
            val method = spec.optString("method", "GET").uppercase()
            val bodyJson = spec.opt("json")?.let { subJson(it, vars) }?.toString()
            val body = bodyJson?.toRequestBody("application/json".toMediaType())
            when (method) {
                "GET" -> rb.get()
                "DELETE" -> if (body != null) rb.delete(body) else rb.delete()
                else -> rb.method(method, body ?: "{}".toRequestBody("application/json".toMediaType()))
            }
            client.newCall(rb.build()).execute().use { r ->
                Resp(r.code, r.body?.string().orEmpty().take(400_000), (System.nanoTime() - t0) / 1_000_000)
            }
        } catch (e: Exception) {
            Resp(-1, "", (System.nanoTime() - t0) / 1_000_000, e.message ?: e.javaClass.simpleName)
        }
    }

    fun pick(root: Any?, path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path == "$") return root?.toString()
        return VaultSites.path(root, path)?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
    }

    fun pickArray(root: Any?, path: String?): JSONArray? {
        if (path.isNullOrBlank()) return null
        if (path == "$") return root as? JSONArray
        return VaultSites.path(root, path) as? JSONArray
    }
}
