package com.openminis.app.opencode

import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenCode 手机客户端的数据模型。
 * 用 org.json 手动解析：API 的可选字段多（title/model/outcome 都可能缺），
 * 严格 schema 反序列化容易整条失败，手动解析缺字段只退化成默认值。
 */

data class OcSession(
    val id: String,
    val title: String,
    val agent: String?,
    val modelId: String?,
    val providerId: String?,
    val outcome: String?,
    val updatedAt: Long,
    val directory: String?,
) {
    companion object {
        fun fromJson(o: JSONObject): OcSession = OcSession(
            id = o.optString("id"),
            title = o.optString("title").ifBlank { "新会话" },
            agent = o.optString("agent").ifBlank { null },
            modelId = o.optJSONObject("model")?.optString("id")?.ifBlank { null },
            providerId = o.optJSONObject("model")?.optString("providerID")?.ifBlank { null },
            outcome = o.optString("outcome").ifBlank { null },
            updatedAt = o.optJSONObject("time")?.optLong("updated") ?: 0L,
            directory = o.optJSONObject("location")?.optString("directory")?.ifBlank { null },
        )
    }
}

data class OcMessage(
    val id: String,
    val role: String,
    val parts: List<OcPart>,
    val modelId: String?,
) {
    val isUser: Boolean get() = role == "user"

    companion object {
        fun fromJson(o: JSONObject): OcMessage {
            val arr: JSONArray = o.optJSONArray("content") ?: JSONArray()
            val parts = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { OcPart.fromJson(it) }
            }
            return OcMessage(
                id = o.optString("id"),
                role = o.optString("type"),
                parts = parts,
                modelId = o.optJSONObject("model")?.optString("id")?.ifBlank { null },
            )
        }
    }
}

data class OcPart(
    val type: String,
    val text: String,
    val toolName: String?,
    val toolStatus: String?,
    val toolInput: String?,
) {
    companion object {
        fun fromJson(o: JSONObject): OcPart = OcPart(
            type = o.optString("type"),
            text = o.optString("text"),
            toolName = o.optString("name").ifBlank { null },
            toolStatus = o.optJSONObject("state")?.optString("status")?.ifBlank { null },
            toolInput = o.optJSONObject("state")?.optJSONObject("input")?.optString("command")
                ?.ifBlank { null }
                ?: o.optJSONObject("state")?.optJSONObject("input")?.optString("description")?.ifBlank { null },
        )
    }
}

/** 配对链接解析结果。 */
data class OcPairLink(val baseUrl: String, val code: String)

/** 服务端 SSE 事件（只保留 UI 需要的字段）。 */
data class OcEvent(
    val type: String,
    val sessionId: String?,
    val delta: String?,
    val text: String?,
    val status: String?,
)
