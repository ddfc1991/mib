package com.openminis.app.opencode

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenCode HTTP API 客户端。
 * 认证流程：pair 链接里的 code → GET /auth/connect/{code} 换 token →
 * 之后所有请求用 Basic auth（用户名固定 opencode，密码是 token）。
 */
object OpenCodeApi {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE 长连接不能设读超时
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val sseFactory = EventSources.createFactory(client)

    /** 从用户粘贴的配对链接里拆出服务器地址和 code。兼容本地/隧道链接。 */
    fun parsePairLink(link: String): OcPairLink? {
        val m = Regex("(https?://[^/]+).*?/auth/connect/([A-Za-z0-9_\\-]+)").find(link.trim())
            ?: return null
        return OcPairLink(m.groupValues[1], m.groupValues[2])
    }

    /** 用一次性 code 换取长期 token。 */
    suspend fun pair(baseUrl: String, code: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/auth/connect/$code").get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("配对失败 (${resp.code}): ${body.take(120)}")
            val token = runCatching { JSONObject(body).optString("token") }.getOrDefault("")
            if (token.isBlank()) throw ApiException("配对响应缺少 token")
            token
        }
    }

    private fun authHeader(token: String): String {
        val raw = "opencode:$token"
        return "Basic " + Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun get(url: String, token: String): Response {
        val req = Request.Builder().url(url).header("Authorization", authHeader(token)).get().build()
        return client.newCall(req).execute()
    }

    /** 拉取会话列表。 */
    suspend fun listSessions(baseUrl: String, token: String): List<OcSession> = withContext(Dispatchers.IO) {
        get("$baseUrl/api/session", token).use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("获取会话失败 (${resp.code}): ${body.take(120)}")
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { data.optJSONObject(it) }.map { OcSession.fromJson(it) }
        }
    }

    /** 拉取某个会话的消息。 */
    suspend fun listMessages(baseUrl: String, token: String, sessionId: String): List<OcMessage> =
        withContext(Dispatchers.IO) {
            get("$baseUrl/api/session/$sessionId/message", token).use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException("获取消息失败 (${resp.code}): ${body.take(120)}")
                val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
                (0 until data.length()).mapNotNull { data.optJSONObject(it) }.map { OcMessage.fromJson(it) }
            }
        }

    /** 新建会话，可指定工作目录（留空用服务端默认）。 */
    suspend fun createSession(baseUrl: String, token: String, directory: String? = null): OcSession =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                if (!directory.isNullOrBlank()) put("directory", directory)
            }
            val body = payload.toString().toRequestBody(jsonType)
            val req = Request.Builder()
                .url("$baseUrl/api/session")
                .header("Authorization", authHeader(token))
                .post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException("新建会话失败 (${resp.code}): ${text.take(120)}")
                OcSession.fromJson(JSONObject(text))
            }
        }

    /** 发消息到会话。V2 的 prompt body 是 { text: "..." }。 */
    suspend fun sendPrompt(baseUrl: String, token: String, sessionId: String, text: String) =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("text", text)
            val body = payload.toString().toRequestBody(jsonType)
            val req = Request.Builder()
                .url("$baseUrl/api/session/$sessionId/prompt")
                .header("Authorization", authHeader(token))
                .post(body).build()
            client.newCall(req).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ApiException("发送失败 (${resp.code}): ${respBody.take(120)}")
            }
        }

    /** 中断当前会话执行。 */
    suspend fun interrupt(baseUrl: String, token: String, sessionId: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/api/session/$sessionId/interrupt")
            .header("Authorization", authHeader(token))
            .post("{}".toRequestBody(jsonType)).build()
        client.newCall(req).execute().use { /* 不关心响应体 */ }
    }

    /** 获取服务器信息（测试连接用）。 */
    suspend fun info(baseUrl: String, token: String): String = withContext(Dispatchers.IO) {
        get("$baseUrl/api/info", token).use { resp ->
            if (!resp.isSuccessful) throw ApiException("连接失败 (${resp.code})")
            resp.body?.string().orEmpty()
        }
    }

    /**
     * 订阅 SSE 事件流。onEvent 回传解析后的事件。
     * 返回 EventSource，页面退出时调用 cancel()。
     */
    fun subscribeEvents(
        baseUrl: String,
        token: String,
        onEvent: (OcEvent) -> Unit,
        onFailure: (Throwable) -> Unit = {},
    ): EventSource {
        val req = Request.Builder()
            .url("$baseUrl/api/event")
            .header("Authorization", authHeader(token))
            .get().build()
        val listener = object : EventSourceListener() {
            override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
                val ev = parseEvent(type, data) ?: return
                onEvent(ev)
            }
            override fun onFailure(source: EventSource, t: Throwable?, response: Response?) {
                onFailure(t ?: ApiException("事件流断开"))
            }
        }
        return sseFactory.newEventSource(req, listener)
    }

    private fun parseEvent(type: String?, data: String): OcEvent? {
        if (type.isNullOrBlank()) return null
        val o = runCatching { JSONObject(data) }.getOrNull() ?: return OcEvent(type, null, null, null, null)
        val props = o.optJSONObject("properties") ?: o
        return OcEvent(
            type = type,
            sessionId = props.optString("sessionID").ifBlank { null },
            delta = props.optString("delta").ifBlank { null },
            text = props.optString("text").ifBlank { null },
            status = props.optString("status").ifBlank { null },
        )
    }

    class ApiException(message: String) : Exception(message)
}
