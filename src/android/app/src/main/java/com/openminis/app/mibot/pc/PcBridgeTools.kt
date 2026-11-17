package com.openminis.app.mibot.pc

import android.content.Context
import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import com.openminis.app.tools.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * [fork/mib-pc-bridge] One tool that drives the user's own Windows computer.
 *
 * It talks to PCBridge — a tiny HTTP service running on the PC itself
 * (see `pcbridge/` in the Alma workspace, default port 17890) — over the
 * Tailscale link, so the phone agent can run shell commands, read/write
 * files, take a screenshot or type on that machine.
 *
 * Configuration (url + token) lives in a private SharedPreferences file and is
 * written ONCE by `action=setup`. The token never appears in the transcript:
 * `setup` scrubs it from its own result.
 */
object PcBridgeTools {
    const val TOOL = "pc_bridge"
    val NAMES = setOf(TOOL)

    private const val PREFS = "pc_bridge"
    private const val KEY_URL = "url"
    private const val KEY_TOKEN = "token"
    private const val DEFAULT_URL = "http://100.74.1.11:17890"
    private const val MAX_TEXT = 12000
    private const val TAG = "[fork/mib-pc-bridge]"

    private val title = "tool_title" to AgentToolParam(
        "string",
        "A concise 5-10 word summary shown to the user. Use the user's language.",
    )

    fun definition(): AgentToolDefinition = AgentToolDefinition(
        name = TOOL,
        description = "Control the user's own Windows desktop (this is NOT the phone): run shell commands, list/read/write files, " +
            "take a screenshot, or type keys into the focused window. Talks to PCBridge over Tailscale, so the PC must be awake " +
            "and online — if it fails with 'connect', say so instead of retrying forever.\n\n" +
            "action=info first to check the link and learn the host. action=exec runs a command: default shell is bash " +
            "(Git Bash, POSIX syntax); use shell=cmd or shell=powershell for Windows-native syntax. " +
            "Paths accept C:/Users/... or ~. Output is capped, so prefer targeted commands (Select-String / findstr) over dumping whole files.\n\n" +
            "action=screenshot returns the PC screen as an image — use it to see what is actually on that machine before acting. " +
            "action=keys sends PowerShell SendKeys text to the focused window (e.g. 'hello{ENTER}'), for rare cases where a command is not enough.\n\n" +
            "If no token is configured yet the tool replies with setup instructions: ask the user for the bridge token shown on the PC " +
            "(pcbridge/config.json), then call action=setup once. Never echo that token back in chat.",
        parameters = mapOf(
            title,
            "action" to AgentToolParam(
                "string",
                "info | exec | ls | read | write | keys | screenshot | setup",
                enumValues = listOf("info", "exec", "ls", "read", "write", "keys", "screenshot", "setup"),
            ),
            "command" to AgentToolParam("string", "action=exec: the command line to run."),
            "shell" to AgentToolParam(
                "string",
                "action=exec: bash (default, Git Bash/POSIX) | cmd | powershell.",
                enumValues = listOf("bash", "cmd", "powershell"),
            ),
            "cwd" to AgentToolParam("string", "action=exec: working directory, e.g. C:/Users/Admin/Documents."),
            "path" to AgentToolParam("string", "action=ls/read/write: file or directory path on the PC."),
            "content" to AgentToolParam("string", "action=write: the text to write."),
            "mode" to AgentToolParam(
                "string",
                "action=write: overwrite (default) | append.",
                enumValues = listOf("overwrite", "append"),
            ),
            "text" to AgentToolParam("string", "action=keys: SendKeys text, e.g. 'notepad{ENTER}' or '^c'."),
            "timeout_ms" to AgentToolParam("integer", "action=exec: command timeout in ms (default 30000, max 300000)."),
            "url" to AgentToolParam("string", "action=setup: bridge base URL, e.g. http://100.74.1.11:17890."),
            "token" to AgentToolParam("string", "action=setup: the bridge token from pcbridge/config.json on the PC."),
        ),
        required = listOf("tool_title", "action"),
        propertyOrdering = listOf(
            "tool_title", "action", "command", "shell", "cwd", "path", "content",
            "mode", "text", "timeout_ms", "url", "token",
        ),
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun baseUrl(ctx: Context): String =
        (prefs(ctx).getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL).trimEnd('/')

    private fun token(ctx: Context): String = prefs(ctx).getString(KEY_TOKEN, "") ?: ""

    private class Resp(val code: Int, val body: ByteArray) {
        val text: String get() = String(body, Charsets.UTF_8)
        val ok: Boolean get() = code in 200..299
        fun json(): JSONObject? = runCatching { JSONObject(text) }.getOrNull()
    }

    private fun enc(v: String): String =
        URLEncoder.encode(v, "UTF-8").replace("+", "%20")

    private fun request(
        method: String,
        urlStr: String,
        token: String,
        body: String?,
        contentType: String?,
        timeoutMs: Int,
    ): Resp {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection)
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = timeoutMs
            if (token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token)
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType ?: "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            Resp(code, bytes)
        } finally {
            conn.disconnect()
        }
    }

    private fun clip(s: String, max: Int = MAX_TEXT): String =
        if (s.length <= max) s else s.substring(0, max) + "\n… (truncated, " + (s.length - max) + " more chars)"

    private fun requireToken(ctx: Context): String? {
        val t = token(ctx)
        return if (t.isEmpty()) null else t
    }

    private val NO_TOKEN = "PC bridge is not configured yet. Ask the user for the token from pcbridge/config.json on the PC " +
        "(the 'token' field), then call pc_bridge with action=setup url=<bridge url> token=<that token>. " +
        "Never print the token back to the user."

    suspend fun execute(ctx: Context, argsJson: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        val a = runCatching { JSONObject(argsJson) }.getOrElse { JSONObject() }
        val toolTitle = a.optString("tool_title", "PC bridge")
        val action = a.optString("action", "info").ifBlank { "info" }
        try {
            when (action) {
                "setup" -> doSetup(ctx, a, toolTitle)
                "info" -> doInfo(ctx, toolTitle)
                "exec" -> doExec(ctx, a, toolTitle)
                "ls" -> doLs(ctx, a, toolTitle)
                "read" -> doRead(ctx, a, toolTitle)
                "write" -> doWrite(ctx, a, toolTitle)
                "keys" -> doKeys(ctx, a, toolTitle)
                "screenshot" -> doScreenshot(ctx, toolTitle)
                else -> ToolExecutionResult("unknown action: " + action, false, toolTitle = toolTitle)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ToolExecutionResult("PC did not answer in time (timeout). It may be busy — try a shorter command or raise timeout_ms.", false, toolTitle = toolTitle)
        } catch (e: java.net.ConnectException) {
            ToolExecutionResult("cannot reach the PC bridge at " + baseUrl(ctx) + " — the computer is off, asleep, or off Tailscale.", false, toolTitle = toolTitle)
        } catch (e: Exception) {
            ToolExecutionResult("pc_bridge failed: " + (e.message ?: e.javaClass.simpleName), false, toolTitle = toolTitle)
        }
    }

    private fun doSetup(ctx: Context, a: JSONObject, toolTitle: String): ToolExecutionResult {
        val url = a.optString("url", "").trim()
        val tok = a.optString("token", "").trim()
        if (url.isNotEmpty() || tok.isNotEmpty()) {
            val e = prefs(ctx).edit()
            if (url.isNotEmpty()) e.putString(KEY_URL, url.trimEnd('/'))
            if (tok.isNotEmpty()) e.putString(KEY_TOKEN, tok)
            e.apply()
        }
        val r = request("GET", baseUrl(ctx) + "/info", token(ctx), null, null, 15000)
        return if (r.ok) {
            val j = r.json()
            ToolExecutionResult(
                "bridge configured and reachable: " + baseUrl(ctx) +
                    (if (j != null) " (host " + j.optString("hostname") + ", user " + j.optString("user") + ")" else "") +
                    ". The token is stored on this device only.",
                true, toolTitle = toolTitle,
            )
        } else if (r.code == 401) {
            ToolExecutionResult("bridge answered 401 — the token is wrong. Ask the user to re-check pcbridge/config.json.", false, toolTitle = toolTitle)
        } else if (r.code == 403) {
            ToolExecutionResult("bridge refused this phone's IP (403): the phone must be on the same Tailscale network as the PC.", false, toolTitle = toolTitle)
        } else {
            ToolExecutionResult("bridge reachable at " + baseUrl(ctx) + " but /info returned " + r.code + ": " + clip(r.text, 500), false, toolTitle = toolTitle)
        }
    }

    private fun doInfo(ctx: Context, toolTitle: String): ToolExecutionResult {
        val t = requireToken(ctx) ?: return ToolExecutionResult(NO_TOKEN, false, toolTitle = toolTitle)
        val r = request("GET", baseUrl(ctx) + "/info", t, null, null, 15000)
        if (!r.ok) return ToolExecutionResult("GET /info -> " + r.code + ": " + clip(r.text, 500), false, toolTitle = toolTitle)
        val j = r.json() ?: return ToolExecutionResult(clip(r.text), true, toolTitle = toolTitle)
        val sb = StringBuilder()
        sb.append("host ").append(j.optString("hostname")).append(" (user ").append(j.optString("user")).append(")\n")
        sb.append("os ").append(j.optString("platform")).append(" ").append(j.optString("arch")).append("\n")
        sb.append("cpu ").append(j.optString("cpu")).append(" x").append(j.optInt("cores")).append("\n")
        sb.append("mem ").append(j.optDouble("freeMemGB")).append("G free of ").append(j.optDouble("totalMemGB")).append("G\n")
        sb.append("home ").append(j.optString("home")).append("\n")
        sb.append("cwd ").append(j.optString("cwd")).append("\n")
        sb.append("tailscale ").append(j.optJSONArray("tailscaleIp")?.toString() ?: "-")
        return ToolExecutionResult(sb.toString(), true, toolTitle = toolTitle)
    }

    private fun doExec(ctx: Context, a: JSONObject, toolTitle: String): ToolExecutionResult {
        val t = requireToken(ctx) ?: return ToolExecutionResult(NO_TOKEN, false, toolTitle = toolTitle)
        val cmd = a.optString("command", a.optString("cmd", "")).trim()
        if (cmd.isEmpty()) return ToolExecutionResult("action=exec needs `command`.", false, toolTitle = toolTitle)
        val payload = JSONObject()
        payload.put("cmd", cmd)
        payload.put("shell", a.optString("shell", "bash").ifBlank { "bash" })
        a.optString("cwd", "").takeIf { it.isNotBlank() }?.let { payload.put("cwd", it) }
        val timeout = a.optInt("timeout_ms", 30000).coerceIn(5000, 300000)
        payload.put("timeoutMs", timeout)
        val r = request("POST", baseUrl(ctx) + "/exec", t, payload.toString(), null, timeout + 15000)
        if (!r.ok) return ToolExecutionResult("POST /exec -> " + r.code + ": " + clip(r.text, 800), false, toolTitle = toolTitle)
        val j = r.json() ?: return ToolExecutionResult(clip(r.text), true, toolTitle = toolTitle)
        val exit = j.optInt("exitCode", -1)
        val out = j.optString("stdout", "")
        val err = j.optString("stderr", "")
        val sb = StringBuilder()
        sb.append("exit ").append(exit)
        if (j.optBoolean("timedOut")) sb.append(" (TIMEOUT)")
        sb.append(" in ").append(j.optLong("durationMs")).append("ms\n")
        if (out.isNotBlank()) sb.append(out.trimEnd()).append("\n")
        if (err.isNotBlank()) sb.append("[stderr] ").append(err.trimEnd())
        return ToolExecutionResult(clip(sb.toString().trim()), j.optBoolean("ok", false), toolTitle = toolTitle)
    }

    private fun doLs(ctx: Context, a: JSONObject, toolTitle: String): ToolExecutionResult {
        val t = requireToken(ctx) ?: return ToolExecutionResult(NO_TOKEN, false, toolTitle = toolTitle)
        val path = a.optString("path", "").trim()
        val url = baseUrl(ctx) + "/ls" + if (path.isEmpty()) "" else "?path=" + enc(path)
        val r = request("GET", url, t, null, null, 20000)
        if (!r.ok) return ToolExecutionResult("GET /ls -> " + r.code + ": " + clip(r.text, 500), false, toolTitle = toolTitle)
        val j = r.json() ?: return ToolExecutionResult(clip(r.text), true, toolTitle = toolTitle)
        val arr = j.optJSONArray("entries") ?: return ToolExecutionResult("empty", true, toolTitle = toolTitle)
        val sb = StringBuilder(j.optString("path")).append(" (").append(j.optInt("count")).append(")\n")
        for (i in 0 until minOf(arr.length(), 200)) {
            val e = arr.optJSONObject(i) ?: continue
            val dir = e.optString("type") == "dir"
            sb.append(if (dir) "d " else "  ").append(e.optString("name"))
            if (!dir) sb.append("  ").append(e.optLong("size")).append("B")
            sb.append("\n")
        }
        return ToolExecutionResult(clip(sb.toString().trimEnd()), true, toolTitle = toolTitle)
    }

    private fun doRead(ctx: Context, a: JSONObject, toolTitle: String): ToolExecutionResult {
        val t = requireToken(ctx) ?: return ToolExecutionResult(NO_TOKEN, false, toolTitle = toolTitle)
        val path = a.optString("path", "").trim()
        if (path.isEmpty()) return ToolExecutionResult("action=read needs `path`.", false, toolTitle = toolTitle)
        val r = request("GET", baseUrl(ctx) + "/read?path=" + enc(path) + "&maxBytes=200000", t, null, null, 25000)
        if (!r.ok) return ToolExecutionResult("GET /read -> " + r.code + ": " + clip(r.text, 500), false, toolTitle = toolTitle)
        val j = r.json() ?: return ToolExecutionResult(clip(r.text), true, toolTitle = toolTitle)
        val sb = StringBuilder(j.optString("path")).append("  (").append(j.optLong("size")).append("B")
        if (j.optBoolean("truncated")) sb.append(", truncated")
        sb.append(")\n").append(j.optString("content", ""))
        return ToolExecutionResult(clip(sb.toString()), true, toolTitle = toolTitle)
    }

    private fun doWrite(ctx: Context, a: JSONObject, toolTitle: String): ToolExecutionResult {
        val t = requireToken(ctx) ?: return ToolExecutionResult(NO_TOKEN, false, toolTitle = toolTitle)
        val path = a.optString("path", "").trim()
        if (path.isEmpty()) return ToolExecutionResult("action=write needs `path`.", false, toolTitle = toolTitle)
        val payload = JSONObject()
        payload.put("path", path)
        payload.put("content", a.optString("content", ""))
        payload.put("encoding", "utf8")
        payload.put("mode", a.optString("mode", "overwrite").ifBlank { "overwrite" })
        val r = request("POST", baseUrl(ctx) + "/write", t, payload.toString(), null, 30000)
        if (!r.ok) return ToolExecutionResult("POST /write -> " + r.code + ": " + clip(r.text, 500), false, toolTitle = toolTitle)
        val j = r.json()
        return ToolExecutionResult(
            "wrote " + (j?.optLong("bytes") ?: 0L) + "B to " + (j?.optString("path") ?: path),
            true, toolTitle = toolTitle,
        )
    }

    private fun doKeys(ctx: Context, a: JSONObject, toolTitle: String): ToolExecutionResult {
        val t = requireToken(ctx) ?: return ToolExecutionResult(NO_TOKEN, false, toolTitle = toolTitle)
        val text = a.optString("text", "")
        if (text.isEmpty()) return ToolExecutionResult("action=keys needs `text`.", false, toolTitle = toolTitle)
        val payload = JSONObject().put("text", text)
        val r = request("POST", baseUrl(ctx) + "/keys", t, payload.toString(), null, 20000)
        return if (r.ok) {
            ToolExecutionResult("keys sent", true, toolTitle = toolTitle)
        } else {
            ToolExecutionResult("POST /keys -> " + r.code + ": " + clip(r.text, 500), false, toolTitle = toolTitle)
        }
    }

    private fun doScreenshot(ctx: Context, toolTitle: String): ToolExecutionResult {
        val t = requireToken(ctx) ?: return ToolExecutionResult(NO_TOKEN, false, toolTitle = toolTitle)
        val r = request("GET", baseUrl(ctx) + "/screenshot", t, null, null, 40000)
        if (!r.ok) return ToolExecutionResult("GET /screenshot -> " + r.code + ": " + clip(r.text, 500), false, toolTitle = toolTitle)
        if (r.body.isEmpty()) return ToolExecutionResult("screenshot: empty image", false, toolTitle = toolTitle)
        val dir = File(ctx.filesDir, "pc-bridge")
        dir.mkdirs()
        val f = File(dir, "pc-" + System.currentTimeMillis() + ".png")
        f.writeBytes(r.body)
        return ToolExecutionResult(
            "screenshot of the PC screen (" + (r.body.size / 1024) + "KB) saved at " + f.absolutePath,
            true,
            imageData = r.body,
            imageMimeType = "image/png",
            toolTitle = toolTitle,
            imageFilePath = f.absolutePath,
        )
    }
}
