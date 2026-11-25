package com.mibot.opencode

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Credentials
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [openbots/opencode] 服务器台账。
 * 格式:{"servers":[{"name":"","url":"http://192.168.x.x:4096","password":""}]}
 * 密码随台账保存——截图里的"54adfc19a7e160e4"那类长串就是 serve 的 basic auth。
 */
class OpenCodeStore(context: Context) {

    class Server(val name: String, val url: String, val password: String)

    private val file = File(context.filesDir, "opencode/servers.json")

    /** 台账很小，同步读（UI 线程可接受）。 */
    fun list(): List<Server> {
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("servers") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url", "").trim().trimEnd('/')
            if (url.isEmpty()) return@mapNotNull null
            Server(o.optString("name", "").ifBlank { url }, url, o.optString("password", ""))
        }
    }

    suspend fun upsert(name: String, url: String, password: String) = withContext(Dispatchers.IO) {
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: JSONObject()
        val arr = root.optJSONArray("servers") ?: JSONArray()
        val clean = url.trim().trimEnd('/')
        var replaced = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("url", "").trim().trimEnd('/') == clean) {
                arr.put(i, JSONObject().put("name", name.trim()).put("url", clean).put("password", password))
                replaced = true
                break
            }
        }
        if (!replaced) arr.put(JSONObject().put("name", name.trim()).put("url", clean).put("password", password))
        root.put("servers", arr)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString(1))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    suspend fun remove(url: String) = withContext(Dispatchers.IO) {
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@withContext
        val arr = root.optJSONArray("servers") ?: return@withContext
        val keep = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("url", "").trim().trimEnd('/') != url.trim().trimEnd('/')) keep.put(o)
        }
        root.put("servers", keep)
        file.writeText(root.toString(1))
    }
}
// ---------------------------------------------------------------------------
// 会话/消息模型 + HTTP 客户端
// ---------------------------------------------------------------------------

/** serve 的会话：/session 返回列表。 */
data class OcSession(
    val id: String,
    val title: String,
    val agent: String,
    val model: String,
    val directory: String,
    val cost: Double,
    val tokens: Int,
    val createdAt: Long,
)

/** 一条消息：/session/:id/message。 */
data class OcMessage(val role: String, val text: String, val time: Long)

/** 服务器连接结果（截图里"无法配对"那张卡就是这里的错误）。 */
data class OcCheck(val ok: Boolean, val reason: String, val sessionCount: Int = 0)

/**
 * OkHttp 客户端：app 走真机 Android 网络，能连局域网 serve（沙箱连不了，所以不能用沙箱测）。
 * 失败统一返回 OcCheck(ok=false, reason)，不抛异常——UI 直接显示。
 */
object OpenCodeApi {

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun get(server: OpenCodeStore.Server, path: String): okhttp3.Response {
        val req = okhttp3.Request.Builder().url(server.url + path)
            .addHeader("Accept", "application/json")
            .apply { if (server.password.isNotEmpty()) header("Authorization", Credentials.basic(server.password, "")) }
            .build()
        return client.newCall(req).execute()
    }

    private fun post(server: OpenCodeStore.Server, path: String, body: JSONObject): okhttp3.Response {
        val req = okhttp3.Request.Builder().url(server.url + path)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .apply { if (server.password.isNotEmpty()) header("Authorization", Credentials.basic(server.password, "")) }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return client.newCall(req).execute()
    }

    /** 连接测试：能列会话就算通（对齐截图的"无法配对"提示）。 */
    fun check(server: OpenCodeStore.Server): OcCheck {
        val resp = try { get(server, "/session") } catch (e: Exception) {
            return OcCheck(false, "连不上：${short(e)}")
        }
        return try {
            if (!resp.isSuccessful) return OcCheck(false, "服务器返回 ${resp.code}（${resp.message}）")
            val arr = JSONArray(resp.body?.string().orEmpty())
            OcCheck(true, "已连接", (0 until arr.length()).count { arr.optJSONObject(it) != null })
        } catch (e: Exception) {
            OcCheck(false, "不是 OpenCode 服务：${short(e)}")
        } finally { resp.close() }
    }

    fun sessions(server: OpenCodeStore.Server): List<OcSession> {
        val resp = try { get(server, "/session") } catch (e: Exception) { return emptyList() }
        return try {
            val arr = JSONArray(resp.body?.string().orEmpty())
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parse(it) } }
        } catch (e: Exception) { emptyList() } finally { resp.close() }
    }

    private fun parse(o: JSONObject): OcSession? {
        val id = o.optString("id", "")
        if (id.isEmpty()) return null
        val time = o.optJSONObject("time") ?: return null
        return OcSession(
            id = id,
            title = o.optString("title", "").ifBlank { o.optString("slug", id.take(8)) },
            agent = o.optString("agent", "build"),
            model = o.optJSONObject("model")?.optString("modelID", "").orEmpty(),
            directory = o.optString("directory", "").ifBlank { o.optString("path", "") },
            cost = o.optDouble("cost", 0.0),
            tokens = o.optInt("tokens", 0),
            createdAt = time.optLong("created", 0),
        )
    }

    fun createSession(server: OpenCodeStore.Server, directory: String, agent: String): String? {
        val body = JSONObject().put("agent", agent)
            .apply { if (directory.isNotBlank()) put("directory", directory) }
        val resp = try { post(server, "/session", body) } catch (e: Exception) { return null }
        return try {
            val id = JSONObject(resp.body?.string().orEmpty()).optString("id", "")
            if (!resp.isSuccessful || id.isEmpty()) null else id
        } catch (e: Exception) { null } finally { resp.close() }
    }

    /** 发一条消息；同步等 serve 处理完，返回助手回复文本。 */
    fun sendMessage(server: OpenCodeStore.Server, sessionId: String, text: String, agent: String = "build"): String? {
        val body = JSONObject().put("agent", agent)
            .put("parts", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
        val resp = try { post(server, "/session/$sessionId/message", body) } catch (e: Exception) { return null }
        return try {
            if (!resp.isSuccessful) return null
            readReply(server, sessionId)
        } finally { resp.close() }
    }

    private fun readReply(server: OpenCodeStore.Server, sessionId: String): String? {
        val resp = try { get(server, "/session/$sessionId/message") } catch (e: Exception) { return null }
        return try {
            val arr = JSONArray(resp.body?.string().orEmpty())
            val items = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .map { messagesOf(it) }
                .flatten()
            items.filter { it.role == "assistant" }.lastOrNull()?.text
        } catch (e: Exception) { null } finally { resp.close() }
    }

    private fun messagesOf(m: JSONObject): List<OcMessage> {
        val info = m.optJSONObject("info") ?: return emptyList()
        val arr = m.optJSONArray("parts") ?: return emptyList()
        val sb = StringBuilder()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { p ->
            if (p.optString("type") == "text") sb.append(p.optString("text", ""))
        }
        val t = info.optJSONObject("time")?.optLong("created", 0) ?: 0L
        return listOf(OcMessage(info.optString("role", "user"), sb.toString(), t))
    }

    private fun short(e: Exception): String {
        val m = (e.message ?: e::class.simpleName ?: "连接失败")
        return if (e is java.net.SocketTimeoutException || "timeout" in m.lowercase()) "超时（服务器没响应）" else m
    }
}