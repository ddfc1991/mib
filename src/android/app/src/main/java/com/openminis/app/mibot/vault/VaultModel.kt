package com.openminis.app.mibot.vault

import org.json.JSONArray
import org.json.JSONObject

/** How the AI may use an account. */
enum class VaultPermission { AUTO, ASK, DISABLED }

/** Built-in categories; users can type their own. */
object VaultCategories {
    val defaults = listOf("AI 服务", "邮箱", "云服务器", "社交", "其他")
    val defaultsEn = listOf("AI services", "Email", "Cloud servers", "Social", "Other")
}

/** Last fetched account status (balance / points / quota / expiry). */
data class VaultStatus(
    val balance: String? = null,
    val points: String? = null,
    val credits: String? = null,
    val quota: String? = null,
    val expiry: String? = null,
    val updatedAt: Long = 0L,
    val error: String? = null,
    val lastCheckinAt: Long = 0L,
    /** ok / manual / error / none */
    val lastCheckinState: String? = null,
    val lastCheckinMessage: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        balance?.let { put("balance", it) }
        points?.let { put("points", it) }
        credits?.let { put("credits", it) }
        quota?.let { put("quota", it) }
        expiry?.let { put("expiry", it) }
        put("updatedAt", updatedAt)
        error?.let { put("error", it) }
        put("lastCheckinAt", lastCheckinAt)
        lastCheckinState?.let { put("lastCheckinState", it) }
        lastCheckinMessage?.let { put("lastCheckinMessage", it) }
    }

    /** The non-empty fields, labelled, for list rows and the CLI. */
    fun summary(labels: Map<String, String>): List<Pair<String, String>> = buildList {
        balance?.let { add((labels["balance"] ?: "balance") to it) }
        points?.let { add((labels["points"] ?: "points") to it) }
        credits?.let { add((labels["credits"] ?: "credits") to it) }
        quota?.let { add((labels["quota"] ?: "quota") to it) }
        expiry?.let { add((labels["expiry"] ?: "expiry") to it) }
    }

    companion object {
        fun fromJson(o: JSONObject?): VaultStatus? = o?.let {
            VaultStatus(
                balance = it.optStringOrNull("balance"),
                points = it.optStringOrNull("points"),
                credits = it.optStringOrNull("credits"),
                quota = it.optStringOrNull("quota"),
                expiry = it.optStringOrNull("expiry"),
                updatedAt = it.optLong("updatedAt", 0L),
                error = it.optStringOrNull("error"),
                lastCheckinAt = it.optLong("lastCheckinAt", 0L),
                lastCheckinState = it.optStringOrNull("lastCheckinState"),
                lastCheckinMessage = it.optStringOrNull("lastCheckinMessage"),
            )
        }
    }
}

/**
 * One account. The secret is only ever held encrypted ([secretEnc], Android
 * Keystore AES-GCM); nothing in this class is plaintext-secret.
 */
data class VaultEntry(
    val id: String,
    val site: String,
    val username: String,
    val category: String = "其他",
    val secretEnc: String? = null,
    val notes: String = "",
    val permission: VaultPermission = VaultPermission.ASK,
    /** Site manifest used for status / check-in; null = auto-match by site. */
    val manifestId: String? = null,
    val status: VaultStatus? = null,
    /** [fork/mibot-v4] Catalog service id (assets/mibot/services.json); null = match by site. */
    val serviceId: String? = null,
    /** [fork/mibot-v4] What the secret is: api_key / cookie / oauth / password. */
    val kind: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val hasSecret: Boolean get() = !secretEnc.isNullOrEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("site", site)
        put("username", username)
        put("category", category)
        secretEnc?.let { put("secretEnc", it) }
        put("notes", notes)
        put("permission", permission.name)
        manifestId?.let { put("manifestId", it) }
        status?.let { put("status", it.toJson()) }
        serviceId?.let { put("serviceId", it) }
        kind?.let { put("kind", it) }
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    /** Metadata safe to show to the agent / CLI: never the secret. */
    fun toPublicJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("site", site)
        put("username", username)
        put("category", category)
        put("has_secret", hasSecret)
        put("permission", permission.name.lowercase())
        if (notes.isNotBlank()) put("notes", notes)
        serviceId?.let { put("service", it) }
        kind?.let { put("kind", it) }
        status?.let { put("status", it.toJson()) }
    }

    companion object {
        fun fromJson(o: JSONObject): VaultEntry = VaultEntry(
            id = o.getString("id"),
            site = o.optString("site"),
            username = o.optString("username"),
            category = o.optString("category", "其他").ifBlank { "其他" },
            secretEnc = o.optStringOrNull("secretEnc"),
            notes = o.optString("notes"),
            permission = runCatching { VaultPermission.valueOf(o.optString("permission")) }.getOrDefault(VaultPermission.ASK),
            manifestId = o.optStringOrNull("manifestId"),
            status = VaultStatus.fromJson(o.optJSONObject("status")),
            serviceId = o.optStringOrNull("serviceId"),
            kind = o.optStringOrNull("kind"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }
}

/** One audit-log line: who used which account for what, and the outcome. */
data class VaultAuditEvent(
    val at: Long,
    val entryId: String,
    val label: String,
    /** agent / cli / user / checkin / status */
    val actor: String,
    val action: String,
    /** allowed / denied / disabled / error / ok */
    val result: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("at", at).put("entryId", entryId).put("label", label)
        .put("actor", actor).put("action", action).put("result", result)

    companion object {
        fun fromJson(o: JSONObject) = VaultAuditEvent(
            at = o.optLong("at"),
            entryId = o.optString("entryId"),
            label = o.optString("label"),
            actor = o.optString("actor"),
            action = o.optString("action"),
            result = o.optString("result"),
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
