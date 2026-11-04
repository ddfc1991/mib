package com.openminis.app.mibot.plugins

import android.content.Context
import android.util.Log
import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.mibot.design.tc
import com.openminis.app.mibot.vault.VaultBroker
import com.openminis.app.mibot.vault.VaultConfirmBus
import com.openminis.app.tools.ToolExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [fork/mibot-dsh-tools] D3 (notes/vd-mika-port.md §5): DSH plugin tools as agent
 * tools. AgentTools appends [definitions] (main agent only); ChatViewModel routes every
 * `plugin__*` call to [execute] (dispatch only, nothing else in chat is touched).
 *
 * Per call: resolve against the turn's frozen snapshot → validate args → policy
 * (installed.json, [DshToolPolicy]; ask through [VaultConfirmBus.askChoice]; a denial is
 * remembered for the run) → start the plugin's host if stopped → `{{vault:…}}`
 * substitution (vault permission applies) → `tools.execute` / `commands.run` with a
 * 60 s timeout → scrub every vault secret value → clip to 64 KB.
 */
object DshAgentTools {
    private const val TAG = "DshAgentTools"
    private const val TIMEOUT_MS = 60_000L
    private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024

    private val runMemory = DshToolPolicy.RunMemory()
    @Volatile private var frozen: DshToolSnapshot = DshToolSnapshot.EMPTY

    /** Called once per agent turn by AgentTools: freezes the plugin tool set for the turn. */
    fun definitions(): List<AgentToolDefinition> {
        val snap = DshToolRegistry.snapshot()
        frozen = snap
        return snap.definitions
    }

    fun isPluginTool(name: String) = name.startsWith(DshToolRegistry.PREFIX)

    /** Forget run-scoped denials of a tool (its switch was changed on the plugin page). */
    fun resetDeclined(agentName: String) = runMemory.forget(agentName)

    /**
     * Run one plugin tool. [runKey] identifies the agent run (the assistant message id) for
     * the "declined in this run" memory. Never throws except for cancellation.
     */
    suspend fun execute(ctx: Context, name: String, argsJson: String, callId: String?, runKey: String): ToolExecutionResult {
        val app = ctx.applicationContext
        val tool = frozen[name] ?: DshToolRegistry.snapshot()[name]
            ?: return ToolExecutionResult("Plugin tool $name is not available (the plugin was stopped or no longer offers it). Do not retry it this turn.", false)
        val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrElse { return ToolExecutionResult("Arguments must be a JSON object.", false) }
        val title = args.optString(DshToolRegistry.TITLE)
        DshToolRegistry.validateArgs(tool, args).takeIf { it.isNotEmpty() }?.let {
            return ToolExecutionResult("Invalid arguments for $name: ${it.joinToString("; ")}", false, toolTitle = title)
        }
        val plugin = PluginStore.get(app, tool.pluginFullName)
            ?: return ToolExecutionResult("The plugin ${tool.pluginFullName} is no longer installed.", false, toolTitle = title)

        // command action=list is answered locally: nothing runs, nothing to ask.
        if (tool.isCommand && args.optString("action") != "run") {
            val list = DshToolRegistry.commandsOf(tool.pluginFullName).ifEmpty { tool.commands.map { DshCommandSpec(it) } }
            val text = if (list.isEmpty()) "This plugin has no commands right now."
                else list.joinToString("\n") { c -> "- ${c.name}" + if (c.description.isNotBlank()) ": ${c.description}" else "" }
            return ToolExecutionResult(text, true, toolTitle = title)
        }

        // ---- policy
        val key = DshToolPolicy.keyOf(tool)
        val setting = DshToolPolicy.settingOf(plugin.toolPolicy, key, tool.risk)
        val label = pluginLabel(plugin)
        when (DshToolPolicy.decide(setting, tool.risk, runMemory.declined(runKey, name))) {
            DshToolPolicy.Decision.DENY -> return ToolExecutionResult(
                "The user has set plugin tool ${tool.hostTool ?: "command"} ($label) to 禁止 (deny). Do not call it; tell the user if it is needed (they can change it on the plugin page).",
                false, toolTitle = title)
            DshToolPolicy.Decision.DECLINED -> return ToolExecutionResult(
                "The user already declined $name in this run. Do not call it again; ask the user how to continue.", false, toolTitle = title)
            DshToolPolicy.Decision.ASK -> {
                val what = tool.hostTool ?: ("/" + args.optString("command"))
                val risk = when (tool.risk) {
                    DshToolRisk.READ -> tc(app, "只读", "read-only")
                    DshToolRisk.WRITE -> tc(app, "可能修改内容", "may change things")
                    DshToolRisk.EXTERNAL -> tc(app, "会对外部发起操作", "acts outside the phone")
                }
                val preview = DshToolPolicy.preview(args)
                val choice = VaultConfirmBus.askChoice(
                    tc(app, "允许插件工具运行？", "Allow a plugin tool?"),
                    tc(app, "插件：$label\n工具：$what（$risk）\n参数：$preview", "Plugin: $label\nTool: $what ($risk)\nArguments: $preview"),
                    danger = tool.risk == DshToolRisk.EXTERNAL,
                )
                when (choice) {
                    VaultConfirmBus.Choice.DENY -> {
                        runMemory.decline(runKey, name)
                        DshHostManager.log(app, tool.pluginFullName, "工具 $what：用户拒绝")
                        return ToolExecutionResult("The user declined $name. Do not call it again in this run; ask the user how to continue.", false, toolTitle = title)
                    }
                    VaultConfirmBus.Choice.ALWAYS -> setPolicy(app, tool.pluginFullName, key, tool.risk, DshToolPolicy.afterAlways(tool.risk))
                    VaultConfirmBus.Choice.ONCE -> Unit
                }
            }
            DshToolPolicy.Decision.RUN -> Unit
        }

        // ---- host
        if (!DshHostManager.isRunning(tool.pluginFullName)) {
            DshHostManager.log(app, tool.pluginFullName, "Agent 调用 ${tool.hostTool ?: "command"}：启动宿主 …")
            val r = DshHostManager.start(app, tool.pluginFullName)
            if (r?.state != "RUNNING" || !DshHostManager.isRunning(tool.pluginFullName)) {
                return ToolExecutionResult(hostDownText(label, r), false, toolTitle = title)
            }
        }

        // ---- vault placeholders (vault permission + audit apply), then run
        var secrets: List<String> = emptyList()
        val prepared = DshToolRegistry.prepareArgs(tool, args)
        val sendArgs = if (prepared.toString().contains("{{vault:")) {
            val sub = VaultBroker.substitute(app, prepared.toString(), actor = "plugin:${tool.pluginFullName}", purpose = name, jsonEscape = true)
            if (sub.error != null) return ToolExecutionResult(sub.error, false, toolTitle = title)
            secrets = sub.secrets
            runCatching { JSONObject(sub.text) }.getOrElse { return ToolExecutionResult("Arguments became invalid after vault substitution.", false, toolTitle = title) }
        } else prepared
        val timeout = (tool.timeoutMs ?: TIMEOUT_MS).coerceIn(1_000L, TIMEOUT_MS)

        val raw: JSONObject = try {
            withTimeout(timeout + 10_000) {
                if (tool.isCommand) {
                    val cmd = args.optString("command")
                    val cmdArgs = sendArgs.optJSONObject("args") ?: JSONObject()
                    val out = DshHostManager.runCommand(tool.pluginFullName, cmd, cmdArgs)
                    JSONObject().put("ok", true).put("output", out)
                } else {
                    DshHostManager.executeTool(tool.pluginFullName, tool.hostTool!!, sendArgs, callId, timeout)
                }
            }
        } catch (e: TimeoutCancellationException) {
            JSONObject().put("ok", false).put("timeout", true).put("output", "timed out after ${timeout / 1000}s")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "$name: ${t.message}")
            JSONObject().put("ok", false).put("output", "${t.javaClass.simpleName}: ${t.message}")
        }

        // ---- result: scrub every vault secret, clip to 64 KB
        val ok = raw.optBoolean("ok", false)
        val known = runCatching { VaultBroker.knownSecrets(app) }.getOrDefault(emptyList())
        val allSecrets = (secrets + known).distinct()
        val body = raw.opt("output")?.let { if (it is String) it else it.toString() }.orEmpty()
        val prefix = when {
            ok -> ""
            raw.optBoolean("denied") -> "Refused by the plugin: "
            raw.optBoolean("timeout") -> "Plugin tool timed out: "
            else -> "Plugin tool failed: "
        }
        val output = DshToolPolicy.finish(prefix + body.ifEmpty { if (ok) "(no output)" else "no details" }, allSecrets)
        DshHostManager.log(app, tool.pluginFullName, "工具 ${tool.hostTool ?: "/" + args.optString("command")}：${if (ok) "完成" else "失败"}（${output.length} 字）")

        val image = if (ok) firstImage(app, raw.optJSONArray("images")) else null
        return ToolExecutionResult(
            output, ok, toolTitle = title,
            imageData = image?.second, imageMimeType = image?.let { mime(it.first) }, imageFilePath = image?.first?.absolutePath,
        )
    }

    /** D4: change one tool's switch (and forget this run's denials of it). */
    fun setPolicy(ctx: Context, fullName: String, key: String, risk: DshToolRisk, setting: DshToolPolicy.Setting) {
        PluginStore.update(ctx, fullName) { it.copy(toolPolicy = DshToolPolicy.withSetting(it.toolPolicy, key, risk, setting)) }
        DshToolRegistry.snapshot().tools.filter { it.pluginFullName == fullName && DshToolPolicy.keyOf(it) == key }.forEach { runMemory.forget(it.agentName) }
    }

    /** D4: back to the defaults by risk, including 「始终允许」 picks. */
    fun resetPolicy(ctx: Context, fullName: String) {
        PluginStore.update(ctx, fullName) { it.copy(toolPolicy = emptyMap()) }
        DshToolRegistry.snapshot().tools.filter { it.pluginFullName == fullName }.forEach { runMemory.forget(it.agentName) }
    }

    private fun pluginLabel(p: InstalledPlugin) = p.fullName

    private fun hostDownText(label: String, r: HostResult?): String {
        if (r == null) return "Plugin $label could not be started (not installed or not a DSH plugin)."
        return when (r.state) {
            "PENDING" -> "Plugin $label cannot run in Mibot yet: it needs services Mibot does not provide (${r.missing.joinToString().ifEmpty { "unknown" }}). Tell the user; do not retry."
            "BLOCKED" -> "Plugin $label is not supported in Mibot: ${r.error ?: "blocked"}. Do not retry."
            "SKILLS" -> "Plugin $label only provides skills (already imported); it has no running tools."
            else -> "Plugin $label failed to start: ${r.error ?: r.state}. The user can see the log on the plugin page."
        }
    }

    /** First image the plugin returned (a sandbox path), if it is a readable file ≤ 5 MB inside the rootfs. */
    private suspend fun firstImage(ctx: Context, images: JSONArray?): Pair<File, ByteArray>? = withContext(Dispatchers.IO) {
        if (images == null || images.length() == 0) return@withContext null
        runCatching {
            val root = com.openminis.app.sandbox.RootfsManager.getInstance(ctx).rootfsDir.canonicalFile
            val path = images.optString(0).ifBlank { return@runCatching null }
            val f = File(root, path.removePrefix("/")).canonicalFile
            if (!f.path.startsWith(root.path + File.separator) || !f.isFile || f.length() > MAX_IMAGE_BYTES) return@runCatching null
            if (mime(f) == null) return@runCatching null
            f to f.readBytes()
        }.getOrNull()
    }

    private fun mime(f: File): String? = when (f.extension.lowercase()) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"; else -> null
    }
}
