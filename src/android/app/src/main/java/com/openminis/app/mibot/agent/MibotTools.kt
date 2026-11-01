package com.openminis.app.mibot.agent

import android.content.Context
import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import com.openminis.app.data.model.ProviderCredential
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.model.ProviderType
import com.openminis.app.data.repository.ProviderRepository
import com.openminis.app.mibot.design.tc
import com.openminis.app.mibot.providers.ProviderPresets
import com.openminis.app.mibot.vault.VaultConfirmBus
import com.openminis.app.mibot.vault.VaultSites
import com.openminis.app.mibot.vault.VaultStore
import com.openminis.app.tools.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * [fork/mibot-v2] Agent tools owned by Mibot:
 *  - account_vault: list accounts (metadata only), refresh status, check in,
 *    list/add site manifests. Secrets are used via {{vault:<id>}} placeholders.
 *  - provider_manage: list registry presets, add / update / test a provider
 *    from a manifest. Adding or changing always asks the user first; keys come
 *    from the vault by id, never through chat.
 */
object MibotTools {
    const val VAULT_TOOL = "account_vault"
    const val PROVIDER_TOOL = "provider_manage"

    fun vaultDefinition() = AgentToolDefinition(
        name = VAULT_TOOL,
        description = "The user's Account Vault (账号台账). action=list returns accounts as metadata (id, site, username, category, permission, status) — never secrets. " +
            "To USE an account, put {{vault:<id>}} (secret) or {{vault:<id>.username}} inside a shell_execute command or browser_use text; the app substitutes it after the user's permission check and scrubs it from output. " +
            "Never ask the user to paste passwords into chat. action=status refreshes balance/points/quota; action=checkin runs daily check-ins (captcha sites come back as manual — tell the user, never try to solve captchas). " +
            "action=sites lists site manifests; action=site_add stores a new manifest (JSON with id, match, status/checkin http specs using {{secret}}). The sandbox also has a `vault` CLI (vault --help).",
        parameters = mapOf(
            "tool_title" to AgentToolParam("string", "A concise 5-10 word summary shown to the user. Use the user's language."),
            "action" to AgentToolParam("string", "list | status | checkin | sites | site_add", enumValues = listOf("list", "status", "checkin", "sites", "site_add")),
            "id" to AgentToolParam("string", "Account id for status/checkin; omit (or 'all') for every account."),
            "category" to AgentToolParam("string", "Optional category filter for list/status/checkin."),
            "manifest" to AgentToolParam("string", "action=site_add: the manifest JSON as a string."),
        ),
        required = listOf("tool_title", "action"),
        propertyOrdering = listOf("tool_title", "action", "id", "category", "manifest"),
    )

    fun providerDefinition() = AgentToolDefinition(
        name = PROVIDER_TOOL,
        description = "Manage model providers for the user. action=presets lists built-in/registered provider manifests. action=test checks an endpoint (GET {base_url}/models) using a key from the vault (key_vault_id) — never ask for keys in chat; ask the user to save the key in 账号台账 first. " +
            "action=add creates a provider (protocol openai|anthropic|gemini, base_url, name, optional models, key_vault_id) after the user confirms in a dialog, and saves a manifest so it appears in the provider picker. action=update changes label/base_url/enabled of an existing provider instance (instance_id), also confirmed. " +
            "For consumer apps without an official API, a manifest may use auth=web_login_capture; such entries are unofficial (ToS / ban risk) and must say so; never implement captcha or anti-bot bypass.",
        parameters = mapOf(
            "tool_title" to AgentToolParam("string", "A concise 5-10 word summary shown to the user. Use the user's language."),
            "action" to AgentToolParam("string", "presets | test | add | update", enumValues = listOf("presets", "test", "add", "update")),
            "name" to AgentToolParam("string", "Display name for add."),
            "id" to AgentToolParam("string", "Manifest id for add (a-z0-9_.-)."),
            "protocol" to AgentToolParam("string", "openai | anthropic | gemini", enumValues = listOf("openai", "anthropic", "gemini")),
            "base_url" to AgentToolParam("string", "API base URL, e.g. https://api.example.com/v1"),
            "models" to AgentToolParam("string", "Optional comma-separated model ids."),
            "key_vault_id" to AgentToolParam("string", "Vault account id holding the API key/token."),
            "key_url" to AgentToolParam("string", "Optional console URL where the user gets a key."),
            "notes" to AgentToolParam("string", "Optional short note (free limits etc.)."),
            "auth" to AgentToolParam("string", "api_key | none | web_login_capture", enumValues = listOf("api_key", "none", "web_login_capture")),
            "instance_id" to AgentToolParam("string", "action=update/test: existing provider instance id."),
            "enabled" to AgentToolParam("boolean", "action=update: enable or disable."),
        ),
        required = listOf("tool_title", "action"),
        propertyOrdering = listOf("tool_title", "action", "name", "id", "protocol", "base_url", "models", "key_vault_id", "key_url", "notes", "auth", "instance_id", "enabled"),
    )

    // ------------------------------------------------------------------ vault

    suspend fun executeVault(ctx: Context, argsJson: String): ToolExecutionResult {
        val a = runCatching { JSONObject(argsJson) }.getOrElse { return ToolExecutionResult("invalid arguments", false) }
        VaultStore.ensureLoaded(ctx)
        val all = VaultStore.entries(ctx).value
        val cat = a.optString("category").takeIf { it.isNotBlank() }
        val id = a.optString("id").takeIf { it.isNotBlank() && it != "all" }
        val selected = all.filter { (id == null || it.id == id) && (cat == null || it.category == cat) }
        return when (a.optString("action")) {
            "list" -> {
                val arr = JSONArray().apply { selected.forEach { put(it.toPublicJson()) } }
                ToolExecutionResult(JSONObject().put("accounts", arr)
                    .put("usage", "Use {{vault:<id>}} / {{vault:<id>.username}} in shell_execute or browser_use. Secrets are never returned.").toString(2), true)
            }
            "status" -> {
                if (selected.isEmpty()) return ToolExecutionResult("no matching accounts", false)
                val arr = JSONArray()
                for (e in selected) {
                    val r = VaultSites.refreshStatus(ctx, e, "agent")
                    arr.put(JSONObject().put("id", e.id).put("site", e.site).put("result", r)
                        .put("status", VaultStore.get(ctx, e.id)?.status?.toJson() ?: JSONObject()))
                }
                ToolExecutionResult(arr.toString(2), true)
            }
            "checkin" -> {
                if (selected.isEmpty()) return ToolExecutionResult("no matching accounts", false)
                val arr = JSONArray()
                for (e in selected) {
                    val r = VaultSites.checkin(ctx, e, "agent")
                    arr.put(JSONObject().put("id", e.id).put("site", e.site).put("state", r.state).put("message", r.message)
                        .apply { r.url?.let { put("open_url", it) } })
                }
                ToolExecutionResult(arr.toString(2), true)
            }
            "sites" -> {
                val arr = JSONArray()
                VaultSites.all(ctx).forEach { arr.put(it.json) }
                ToolExecutionResult(arr.toString(2), true)
            }
            "site_add" -> {
                val m = a.optString("manifest")
                val ok = VaultConfirmBus.ask(
                    tc(ctx, "添加站点清单？", "Add site manifest?"),
                    tc(ctx, "AI 想添加一个账号状态/签到清单：\n\n", "The AI wants to add a status/check-in manifest:\n\n") + m.take(600),
                )
                if (!ok) return ToolExecutionResult("The user declined.", false)
                val err = VaultSites.add(ctx, m)
                VaultStore.log(ctx, null, "agent", "site_add", if (err == null) "ok" else "error")
                if (err != null) ToolExecutionResult(err, false) else ToolExecutionResult("saved", true)
            }
            else -> ToolExecutionResult("unknown action", false)
        }
    }

    // --------------------------------------------------------------- provider

    private suspend fun keyFromVault(ctx: Context, vaultId: String?, purpose: String): Pair<String?, String?> {
        if (vaultId.isNullOrBlank()) return null to null
        val sub = com.openminis.app.mibot.vault.VaultBroker.substitute(ctx, "{{vault:$vaultId}}", "agent", purpose, jsonEscape = false)
        return if (sub.error != null) null to sub.error else sub.text to null
    }

    suspend fun probe(baseUrl: String, key: String?, gemini: Boolean, instanceId: String? = null): String {
        // [fork/mibot-v4] Routed through Settings › Network like real traffic.
        val url = if (gemini) "https://generativelanguage.googleapis.com/v1beta/models" + (key?.let { "?key=$it" } ?: "")
        else baseUrl.trimEnd('/') + "/models"
        val headers = if (!gemini && key != null) mapOf("Authorization" to "Bearer $key") else emptyMap()
        val o = com.openminis.app.mibot.gateway.GatewayNet.request(instanceId, url, headers = headers)
        if (o.code == null) return o.detail
        val count = runCatching { JSONObject(o.body).optJSONArray(if (gemini) "models" else "data")?.length() }.getOrNull()
        return "HTTP ${o.code}" + (count?.let { ", $it models" } ?: "") + if (o.code !in 200..299) ": " + o.body.take(200) else ""
    }

    suspend fun executeProvider(ctx: Context, repo: ProviderRepository, argsJson: String): ToolExecutionResult {
        val a = runCatching { JSONObject(argsJson) }.getOrElse { return ToolExecutionResult("invalid arguments", false) }
        return when (a.optString("action")) {
            "presets" -> {
                val arr = JSONArray()
                ProviderPresets.all(ctx).forEach { arr.put(it.toJson()) }
                ToolExecutionResult(arr.toString(2), true)
            }
            "test" -> {
                val inst = a.optString("instance_id").takeIf { it.isNotBlank() }?.let { repo.instance(it) }
                val base = inst?.effectiveBaseURL ?: a.optString("base_url")
                val gemini = (inst?.providerType == ProviderType.gemini) || a.optString("protocol") == "gemini"
                if (base.isNullOrBlank() && !gemini) return ToolExecutionResult("base_url or instance_id required", false)
                val (key, err) = if (inst != null) (repo.usableApiKey(inst) to null) else keyFromVault(ctx, a.optString("key_vault_id"), "provider test")
                if (err != null) return ToolExecutionResult(err, false)
                val r = runCatching { probe(base ?: "", key, gemini, inst?.id) }.getOrElse { "error: ${it.message}" }
                inst?.let { com.openminis.app.mibot.gateway.GatewayHealth.recordCheck(it.id, com.openminis.app.mibot.vault.VaultBroker.scrub(r, listOfNotNull(key))) }
                ToolExecutionResult(com.openminis.app.mibot.vault.VaultBroker.scrub(r, listOfNotNull(key)), r.startsWith("HTTP 2"))
            }
            "add" -> {
                val name = a.optString("name").ifBlank { return ToolExecutionResult("name required", false) }
                val protocol = a.optString("protocol", "openai")
                val base = a.optString("base_url")
                val auth = a.optString("auth", "api_key")
                val manifestId = a.optString("id").ifBlank { name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifBlank { "custom-" + UUID.randomUUID().toString().take(6) } }
                val type = when (protocol) { "anthropic" -> ProviderType.anthropic; "gemini" -> ProviderType.gemini; else -> ProviderType.openAI }
                val risk = auth == "web_login_capture"
                val ok = VaultConfirmBus.ask(
                    tc(ctx, "添加服务商？", "Add provider?"),
                    tc(ctx, "名称：$name\n协议：$protocol\n地址：$base\n密钥：", "Name: $name\nProtocol: $protocol\nBase URL: $base\nKey: ") +
                        (a.optString("key_vault_id").ifBlank { tc(ctx, "无", "none") }) +
                        if (risk) tc(ctx, "\n\n⚠ 非官方接入，风险自负（可能违反服务条款或封号）。", "\n\n⚠ Unofficial — at your own risk (ToS / ban).") else "",
                    danger = risk,
                )
                if (!ok) return ToolExecutionResult("The user declined.", false)
                val manifest = JSONObject().put("id", manifestId).put("name", name).put("group", if (risk) "web_login" else "custom")
                    .put("protocol", protocol).put("auth", auth).put("baseUrl", base).put("appendV1", false)
                    .put("models", JSONArray(a.optString("models").split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                    .put("keyUrl", a.optString("key_url")).put("note_zh", a.optString("notes")).put("note_en", a.optString("notes"))
                ProviderPresets.save(ctx, manifest)?.let { return ToolExecutionResult(it, false) }
                if (risk) return ToolExecutionResult("Manifest saved as an unofficial web-login entry. Web-login capture providers are not runnable in this build yet (documented as coming soon).", true)
                val (key, err) = keyFromVault(ctx, a.optString("key_vault_id"), "provider add: $name")
                if (err != null) return ToolExecutionResult(err, false)
                val instance = ProviderInstance(
                    id = UUID.randomUUID().toString(),
                    label = name,
                    providerType = type,
                    credentialType = ProviderCredential.apiKey,
                    customBaseURL = base.ifBlank { null },
                    appendV1Suffix = false,
                )
                repo.addInstance(instance)
                if (!key.isNullOrBlank()) repo.saveApiKey(instance.id, key)
                repo.triggerAsyncModelReconcile(instance.id)
                ToolExecutionResult(JSONObject().put("instance_id", instance.id).put("manifest_id", manifestId).put("status", "added").toString(), true)
            }
            "update" -> {
                val inst = repo.instance(a.optString("instance_id")) ?: return ToolExecutionResult("unknown instance_id", false)
                val ok = VaultConfirmBus.ask(
                    tc(ctx, "修改服务商？", "Change provider?"),
                    "${inst.label}\n" + argsJson.take(400),
                )
                if (!ok) return ToolExecutionResult("The user declined.", false)
                val updated = inst.copy().apply {
                    a.optString("name").takeIf { it.isNotBlank() }?.let { label = it }
                    a.optString("base_url").takeIf { it.isNotBlank() }?.let { customBaseURL = it }
                    if (a.has("enabled")) isEnabled = a.optBoolean("enabled")
                }
                repo.updateInstance(updated)
                ToolExecutionResult("updated", true)
            }
            else -> ToolExecutionResult("unknown action", false)
        }
    }
}
