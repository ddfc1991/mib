package com.openminis.app.mibot.plugins

import android.content.Context
import android.util.Log
import com.openminis.app.sandbox.PRootKernel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * [fork/mibot-v5b4] One DSH host process (Node inside the PRoot sandbox) and its
 * NDJSON JSON-RPC 2.0 channel on stdin/stdout. The JS side is
 * assets/mibot/dsh-host/host.mjs. stderr and any stdout line that is not a JSON
 * object go to [onLog] as plain text.
 *
 * Kotlin → host: request(method, params) → result (JSONObject/JSONArray/primitive) or throws.
 * host → Kotlin: notifications (`hello`, `log`, `status`) via [onNotify].
 */
class DshHostBridge private constructor(
    private val process: Process,
    private val onNotify: (String, JSONObject?) -> Unit,
    private val onLog: (String) -> Unit,
    private val onExit: (Int) -> Unit,
) {
    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8))
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Any?>>()
    private val seq = AtomicLong(0)
    @Volatile var exitCode: Int? = null
        private set

    val isAlive: Boolean get() = exitCode == null && process.isAlive

    private fun startReaders() {
        Thread({
            runCatching {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { line -> handleLine(line) }
            }
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            exitCode = code
            pending.values.forEach { it.completeExceptionally(IllegalStateException("宿主进程已退出（$code）")) }
            pending.clear()
            onExit(code)
        }, "dsh-host-out").apply { isDaemon = true }.start()
        Thread({
            runCatching { process.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { onLog("[stderr] $it") } }
        }, "dsh-host-err").apply { isDaemon = true }.start()
    }

    private fun handleLine(line: String) {
        val t = line.trim()
        if (!t.startsWith("{")) { if (t.isNotEmpty()) onLog(t); return }
        val o = runCatching { JSONObject(t) }.getOrNull() ?: run { onLog(t); return }
        if (o.has("id") && (o.has("result") || o.has("error")) && !o.has("method")) {
            val d = pending.remove(o.optLong("id")) ?: return
            if (o.has("error")) d.completeExceptionally(RuntimeException(o.optJSONObject("error")?.optString("message") ?: "error"))
            else d.complete(o.opt("result"))
            return
        }
        val m = o.optString("method")
        if (m.isNotEmpty()) runCatching { onNotify(m, o.optJSONObject("params")) }.onFailure { Log.w(TAG, "notify $m: ${it.message}") }
    }

    suspend fun request(method: String, params: JSONObject? = null, timeoutMs: Long = 30_000): Any? {
        val id = seq.incrementAndGet()
        val d = CompletableDeferred<Any?>()
        pending[id] = d
        val msg = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method)
        if (params != null) msg.put("params", params)
        try {
            synchronized(writer) { writer.write(msg.toString()); writer.write("\n"); writer.flush() }
        } catch (e: Exception) {
            pending.remove(id); throw e
        }
        return try { withTimeout(timeoutMs) { d.await() } } finally { pending.remove(id) }
    }

    /** Polite shutdown (disposes the Cordis root), then kill. */
    suspend fun stop() {
        if (isAlive) runCatching { request("shutdown", timeoutMs = 4_000) }
        runCatching { synchronized(writer) { writer.close() } }
        repeat(20) { if (!process.isAlive) return@repeat; Thread.sleep(50) }
        if (process.isAlive) process.destroyForcibly()
    }

    companion object {
        private const val TAG = "DshHost"

        /**
         * Spawn [shellCommand] in its own PRoot process with the same environment
         * the sandbox shell uses (see ShellExecutor.runOnce), but with separate
         * stdout (RPC) and stderr (log) pipes, kept open for the process lifetime.
         */
        fun spawn(
            ctx: Context,
            shellCommand: String,
            onNotify: (String, JSONObject?) -> Unit,
            onLog: (String) -> Unit,
            onExit: (Int) -> Unit,
        ): DshHostBridge {
            check(PRootKernel.isBooted) { "PRootKernel must be booted" }
            val pb = ProcessBuilder(PRootKernel.buildProotCommand(shellCommand))
            pb.redirectErrorStream(false)
            val env = pb.environment()
            env["PROOT_TMP_DIR"] = PRootKernel.getProotTmpDir(ctx).absolutePath
            if (PRootKernel.nativeLibDir.isNotEmpty()) env["LD_LIBRARY_PATH"] = PRootKernel.nativeLibDir
            if (PRootKernel.prootLoaderPath.isNotEmpty()) env["PROOT_LOADER"] = PRootKernel.prootLoaderPath
            if (PRootKernel.prootLoader32Path.isNotEmpty()) env["PROOT_LOADER_32"] = PRootKernel.prootLoader32Path
            for ((k, v) in PRootKernel.customEnvironment) env[k] = v
            env.putAll(com.openminis.app.network.NetworkShellEnvironment.proxyVariables())
            val p = com.openminis.app.sandbox.SpawnSignalMask.start(pb)
            return DshHostBridge(p, onNotify, onLog, onExit).also { it.startReaders() }
        }
    }
}
