package com.openminis.app.mibot.providers

import android.content.Context
import com.openminis.app.data.model.ProviderType
import com.openminis.app.data.model.VoiceProviderTemplate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [fork/mibot-v2] Open-ended provider registry. Every entry is a JSON
 * manifest: built-ins ship in assets/mibot/providers.json, and the user or
 * the agent (`provider_manage` tool) can add more as files in
 * filesDir/mibot/providers/. The picker groups them as
 * 免费可用 / 官方 API / 网页登录接入（非官方） / 自定义.
 *
 * Manifest fields: id, name, group (free|official|web_login|custom),
 * protocol (openai|anthropic|gemini|openrouter|custom), auth
 * (api_key|oauth_pkce|web_login_capture|none), baseUrl, appendV1, models[],
 * keyUrl, docUrl, note_zh/note_en, color, risk (bool),
 * capture {cookie|header|token, adapter} for web_login_capture.
 */
data class ProviderPreset(
    val id: String,
    val name: String,
    val group: String,
    val protocol: String,
    val auth: String,
    val baseUrl: String,
    val appendV1: Boolean,
    val models: List<String>,
    val keyUrl: String?,
    val docUrl: String?,
    val noteZh: String?,
    val noteEn: String?,
    val color: Long,
    val risk: Boolean,
    val builtIn: Boolean,
) {
    /** Protocols the existing provider stack can drive today. */
    val supported: Boolean get() = protocol in setOf("openai", "anthropic", "gemini", "openrouter") && auth != "web_login_capture"

    val providerType: ProviderType?
        get() = when (protocol) {
            "openai" -> ProviderType.openAI
            "anthropic" -> ProviderType.anthropic
            "gemini" -> ProviderType.gemini
            "openrouter" -> ProviderType.openRouter
            else -> null
        }

    /** Reuses the add-provider prefill path (type + base URL + label + v1 policy). */
    fun asTemplate(): VoiceProviderTemplate? {
        val type = providerType ?: return null
        return VoiceProviderTemplate(
            id = "mibot.$id",
            name = name,
            providerType = type,
            baseURL = baseUrl,
            appendV1 = appendV1,
            capability = VoiceProviderTemplate.Capability.BOTH,
            baseURLMarkers = emptyList(),
            mockModels = emptyList(),
            note = null,
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("group", group); put("protocol", protocol); put("auth", auth)
        put("baseUrl", baseUrl); put("appendV1", appendV1)
        put("models", JSONArray(models))
        keyUrl?.let { put("keyUrl", it) }; docUrl?.let { put("docUrl", it) }
        noteZh?.let { put("note_zh", it) }; noteEn?.let { put("note_en", it) }
        put("color", String.format("#%06X", color and 0xFFFFFF))
        put("risk", risk)
    }
}

object ProviderPresets {
    private fun userDir(ctx: Context) = File(ctx.applicationContext.filesDir, "mibot/providers").apply { mkdirs() }

    fun all(ctx: Context): List<ProviderPreset> {
        val out = mutableListOf<ProviderPreset>()
        runCatching {
            val text = ctx.assets.open("mibot/providers.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) parse(arr.getJSONObject(i), true)?.let(out::add)
        }
        userDir(ctx).listFiles { f -> f.name.endsWith(".json") }?.forEach { f ->
            runCatching { parse(JSONObject(f.readText()), false) }.getOrNull()?.let { p ->
                out.removeAll { it.id == p.id }
                out.add(p)
            }
        }
        return out
    }

    fun parse(o: JSONObject, builtIn: Boolean): ProviderPreset? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val models = o.optJSONArray("models")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        val auth = o.optString("auth", "api_key")
        val color = runCatching { java.lang.Long.parseLong(o.optString("color", "#8A8F99").removePrefix("#"), 16) or 0xFF000000L }
            .getOrDefault(0xFF8A8F99L)
        return ProviderPreset(
            id = id,
            name = o.optString("name", id),
            group = o.optString("group", if (auth == "web_login_capture") "web_login" else "custom"),
            protocol = o.optString("protocol", "openai"),
            auth = auth,
            baseUrl = o.optString("baseUrl"),
            appendV1 = o.optBoolean("appendV1", false),
            models = models,
            keyUrl = o.optString("keyUrl").takeIf { it.isNotBlank() },
            docUrl = o.optString("docUrl").takeIf { it.isNotBlank() },
            noteZh = o.optString("note_zh").takeIf { it.isNotBlank() },
            noteEn = o.optString("note_en").takeIf { it.isNotBlank() },
            color = color,
            risk = o.optBoolean("risk", auth == "web_login_capture"),
            builtIn = builtIn,
        )
    }

    /** Store a user/agent manifest. Returns an error or null. */
    fun save(ctx: Context, json: JSONObject): String? {
        val id = json.optString("id")
        if (!Regex("[a-z0-9_.-]{2,40}").matches(id)) return "id must be 2-40 chars [a-z0-9_.-]"
        if (json.optString("baseUrl").isBlank() && json.optString("protocol") != "gemini") return "baseUrl is required"
        if (json.optString("auth") == "web_login_capture") json.put("risk", true)
        File(userDir(ctx), "$id.json").writeText(json.toString(2))
        return null
    }
}
