package com.openminis.app.mibot.plugins

import android.content.Context
import android.util.Log
import com.openminis.app.sandbox.PRootKernel
import com.openminis.app.sandbox.RootfsManager
import com.openminis.app.sandbox.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * [fork/mibot-v5b4] DSH plugin host — stage 1 (see notes/dsh-host-design.md §8).
 *
 *  - Node 22 from Alpine (`apk add nodejs`) in the existing PRoot sandbox, installed on first use.
 *  - Host kit (assets/mibot/dsh-host: real @deepseek-ai/cordis + Mibot services) copied to
 *    /root/.mibot/dsh/kit; each plugin is staged as a DSH-style profile
 *    /root/.mibot/dsh/p/<id>/node_modules/<package>.
 *  - One Node process per plugin, JSON-RPC over stdio ([DshHostBridge]).
 *  - Stage-1 services: logger, locale, config, commands. Anything else a plugin injects is
 *    reported as missing → 「待支持：<services>」.
 *  - Skill-only plugins (rows mounting @deepseek-ai/dsh-skill-filesystem) are imported as Mibot skills.
 *  - Block list ([DshPolicy]) → 「不支持」.
 */
object DshPolicy {
    /** Reason this plugin must not run in Mibot, or null. Pure (unit-testable). */
    fun blockReason(fullName: String, pkgText: String?, patchText: String?): String? {
        val repo = fullName.substringAfter('/').lowercase()
        val pkgName = pkgText?.let { runCatching { JSONObject(it).optString("name") }.getOrNull() }?.lowercase().orEmpty()
        if ("dsh-purge" in repo || pkgName == "dsh-purge" || pkgName.endsWith("/dsh-purge"))
            return "dsh-purge（及其分叉/镜像）是越狱「破甲」插件，并改写 DSH 宿主自身文件，Mibot 不运行"
        if (repo == "dsh-market" || pkgName == "dsh-market" || pkgName.endsWith("/dsh-market"))
            return "dsh-market 管理的是 DSH 桌面配置，Mibot 有自己的插件市场"
        if (patchText != null) {
            val foreign = DshPatch.analyze(patchText).foreignOverrides
            if (foreign.isNotEmpty()) return "改写 DSH 内部条目（${foreign.take(4).joinToString()}），Mibot 不允许插件改写宿主"
        }
        val hay = (pkgText ?: "").lowercase()
        if (listOf("original-fs", "restart-desktop", "asar extract", "\"asar\"").any { it in hay })
            return "会修改 DSH 桌面安装（asar / original-fs / 重启桌面端）"
        return null
    }
}

/** Static reading of a cordis.patch.yml (no YAML library: rows are simple). Pure. */
object DshPatch {
    const val SKILL_FS = "@deepseek-ai/dsh-skill-filesystem"

    data class Info(
        /** Names of inserted rows (plugins to mount). */
        val inserted: List<String>,
        /** Ids of rows that modify entries this patch did not insert. */
        val foreignOverrides: List<String>,
    ) {
        val skillOnly: Boolean get() = inserted.isNotEmpty() && inserted.all { it == SKILL_FS }
    }

    fun analyze(text: String): Info {
        val inserted = mutableListOf<String>()
        val insertedIds = mutableSetOf<String>()
        val topIds = mutableListOf<String>()
        var inInsert = false
        var insertIndent = -1
        for (raw in text.lines()) {
            val line = raw.substringBefore(" #").trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val indent = line.length - line.trimStart().length
            val t = line.trim()
            // Top-level row start: "- insert:" or "- id: x"
            if (indent == 0 && t.startsWith("-")) {
                val body = t.removePrefix("-").trim()
                inInsert = body.startsWith("insert:")
                insertIndent = -1
                if (body.startsWith("id:")) topIds += unq(body.removePrefix("id:"))
                continue
            }
            if (indent > 0 && !inInsert && t.startsWith("insert:") && indent <= 2) { inInsert = true; insertIndent = -1; continue }
            if (inInsert) {
                val b = t.removePrefix("-").trim()
                if (t.startsWith("-") && (insertIndent < 0 || indent <= insertIndent)) insertIndent = indent
                if (insertIndent >= 0 && indent <= insertIndent + 2) {
                    if (b.startsWith("name:")) inserted += unq(b.removePrefix("name:"))
                    if (b.startsWith("id:")) insertedIds += unq(b.removePrefix("id:"))
                }
            }
        }
        return Info(inserted, topIds.filter { it !in insertedIds })
    }

    private fun unq(s: String) = s.trim().trim('\'', '"')
}

/** What the host reported for a plugin (persisted on the store record). */
data class HostResult(
    /** RUNNING | SKILLS | PENDING | FAILED | BLOCKED */
    val state: String,
    val missing: List<String>,
    val requested: List<String>,
    val error: String?,
    val skills: List<String>,
    val node: String?,
    val at: Long,
) {
    fun toJson(): JSONObject = JSONObject().put("state", state).put("missing", JSONArray(missing)).put("requested", JSONArray(requested))
        .put("error", error ?: "").put("skills", JSONArray(skills)).put("node", node ?: "").put("at", at)

    companion object {
        fun from(j: JSONObject?): HostResult? = j?.let {
            fun arr(k: String) = it.optJSONArray(k)?.let { a -> List(a.length()) { i -> a.optString(i) } } ?: emptyList()
            HostResult(it.optString("state"), arr("missing"), arr("requested"), it.optString("error").ifBlank { null }, arr("skills"),
                it.optString("node").ifBlank { null }, it.optLong("at"))
        }
    }
}

/** The 45-entry npm `dsh-mobile-plugin-catalog` (CC0): extra evidence for 手机能用. */
object DshMobileCatalog {
    private val state = MutableStateFlow<Set<String>>(emptySet())
    val entries: StateFlow<Set<String>> get() = state
    @Volatile private var loading = false

    fun contains(fullName: String) = fullName.lowercase() in state.value

    suspend fun ensure(ctx: Context) = withContext(Dispatchers.IO) {
        if (state.value.isNotEmpty() || loading) return@withContext
        loading = true
        try {
            val f = File(PluginStore.storeDir(ctx), "mobile-catalog.json")
            val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 24 * 3600_000L
            val text = (if (fresh) f.readText() else null) ?: listOf(
                "https://registry.npmmirror.com/dsh-mobile-plugin-catalog/latest/files/plugins.json",
                "https://cdn.jsdelivr.net/npm/dsh-mobile-plugin-catalog/plugins.json",
                "https://unpkg.com/dsh-mobile-plugin-catalog/plugins.json",
            ).firstNotNullOfOrNull { u -> runCatching { get(u) }.getOrNull()?.takeIf { it.trimStart().startsWith("{") } }
                ?.also { runCatching { f.writeText(it) } }
            ?: f.takeIf { it.exists() }?.readText()
            if (text != null) state.value = parse(text)
        } finally { loading = false }
    }

    /** plugins[].{owner,name} or plugins[].url → "owner/name" (lowercase). Pure. */
    fun parse(text: String): Set<String> = runCatching {
        val a = JSONObject(text).optJSONArray("plugins") ?: JSONArray()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val owner = o.optString("owner"); val name = o.optString("name")
            if (owner.isNotBlank() && name.isNotBlank()) "$owner/$name".lowercase()
            else o.optString("url").removePrefix("https://github.com/").trim('/').lowercase().takeIf { it.count { c -> c == '/' } == 1 }
        }.toSet()
    }.getOrDefault(emptySet())

    private fun get(u: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 15_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mibot-Android")
        }
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}

object DshHostManager {
    private const val TAG = "DshHost"
    private const val KIT_ASSET = "mibot/dsh-host"
    private const val KIT_IN = "/root/.mibot/dsh/kit"
    private const val MIN_NODE = 22

    /** Live state of one plugin's host (UI). */
    data class Live(
        /** 未启动 | 准备中 | 运行中 | 待支持 | 失败 | 不支持 */
        val phase: Phase,
        val progress: String? = null,
        val commands: List<String> = emptyList(),
    )
    enum class Phase { IDLE, PREPARING, RUNNING, PENDING, FAILED, BLOCKED }

    private val _live = MutableStateFlow<Map<String, Live>>(emptyMap())
    val live: StateFlow<Map<String, Live>> get() = _live
    private val _logs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val logs: StateFlow<Map<String, List<String>>> get() = _logs
    private val bridges = java.util.concurrent.ConcurrentHashMap<String, DshHostBridge>()
    private val nodeLock = Mutex()
    @Volatile private var nodeVersion: String? = null

    private fun setLive(fullName: String, l: Live) { _live.value = _live.value + (fullName to l) }

    fun log(ctx: Context, fullName: String, line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val l = "$ts $line"
        _logs.value = _logs.value + (fullName to ((_logs.value[fullName] ?: loadLog(ctx, fullName)) + l).takeLast(400))
        runCatching { logFile(ctx, fullName).appendText(l + "\n") }
    }

    fun logLines(ctx: Context, fullName: String): List<String> = _logs.value[fullName] ?: loadLog(ctx, fullName)

    private fun logFile(ctx: Context, fullName: String) = File(PluginStore.storeDir(ctx), fullName.replace('/', '_').lowercase() + ".host.log")
    private fun loadLog(ctx: Context, fullName: String): List<String> = runCatching {
        val f = logFile(ctx, fullName)
        if (f.length() > 256_000) { val keep = f.readLines().takeLast(400); f.writeText(keep.joinToString("\n", postfix = "\n")); keep }
        else f.readLines().takeLast(400)
    }.getOrDefault(emptyList())

    fun clearLog(ctx: Context, fullName: String) { _logs.value = _logs.value + (fullName to emptyList()); runCatching { logFile(ctx, fullName).delete() } }

    fun isRunning(fullName: String) = bridges[fullName]?.isAlive == true

    // ------------------------------------------------------------ static facts of an installed plugin

    data class Local(val pkg: JSONObject?, val pkgText: String?, val patchText: String?, val patch: DshPatch.Info?)

    fun readLocal(p: InstalledPlugin): Local {
        val dir = File(p.dir)
        val pkgText = File(dir, "package.json").takeIf { it.isFile }?.readText()
        val pkg = pkgText?.let { runCatching { JSONObject(it) }.getOrNull() }
        val rel = pkg?.optJSONObject("dsh")?.optJSONObject("bundle")?.optString("patch")?.ifBlank { null }
        val patchText = rel?.let { File(dir, it.removePrefix("./")).takeIf { f -> f.isFile && f.canonicalPath.startsWith(dir.canonicalPath) }?.readText() }
        return Local(pkg, pkgText, patchText, patchText?.let { DshPatch.analyze(it) })
    }

    fun isDshPlugin(p: InstalledPlugin): Boolean = readLocal(p).pkg?.has("dsh") == true

    // ------------------------------------------------------------ start / stop

    /** Never throws (UI launches it from a composition scope). */
    suspend fun start(ctx: Context, fullName: String): HostResult? = try {
        startInner(ctx, fullName)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "start $fullName", e)
        runCatching { bridges.remove(fullName)?.stop() }
        fail(ctx, fullName, "宿主异常：${e.javaClass.simpleName}: ${e.message}")
    }

    private suspend fun startInner(ctx: Context, fullName: String): HostResult? = withContext(Dispatchers.IO) {
        val p = PluginStore.get(ctx, fullName) ?: return@withContext null
        if (isRunning(fullName)) return@withContext p.host
        val local = readLocal(p)
        DshPolicy.blockReason(fullName, local.pkgText, local.patchText)?.let { why ->
            log(ctx, fullName, "不支持：$why")
            return@withContext finish(ctx, fullName, HostResult("BLOCKED", emptyList(), emptyList(), why, emptyList(), null, now()))
        }
        val pkg = local.pkg
            ?: return@withContext finish(ctx, fullName, HostResult("FAILED", emptyList(), emptyList(), "缺少 package.json（不是 DSH 插件）", emptyList(), null, now()))
        val pkgName = pkg.optString("name").ifBlank { null }
            ?: return@withContext finish(ctx, fullName, HostResult("FAILED", emptyList(), emptyList(), "package.json 没有 name", emptyList(), null, now()))
        if (pkg.optJSONObject("dsh") == null)
            return@withContext finish(ctx, fullName, HostResult("FAILED", emptyList(), emptyList(), "package.json 没有 dsh 清单，不是 DSH 插件", emptyList(), null, now()))

        fun prog(s: String) { setLive(fullName, Live(Phase.PREPARING, s)); log(ctx, fullName, s) }
        prog("检查 Linux 沙箱 …")
        val rf = RootfsManager.getInstance(ctx)
        if (!rf.isInstalled) return@withContext fail(ctx, fullName, "Linux 沙箱未初始化：请先在「设置 → 沙箱」完成初始化")
        if (!PRootKernel.isBooted) runCatching { PRootKernel.boot(ctx) }.onFailure { return@withContext fail(ctx, fullName, "沙箱启动失败：${it.message}") }

        val node = ensureNode(ctx) { prog(it) } ?: return@withContext fail(ctx, fullName, nodeError ?: "Node 22 安装失败")
        prog("准备宿主套件 …")
        runCatching { installKit(ctx, rf) }.onFailure { return@withContext fail(ctx, fullName, "宿主套件安装失败：${it.message}") }

        val profileIn = "/root/.mibot/dsh/p/${p.id}"
        val profile = File(rf.rootfsDir, profileIn.removePrefix("/"))
        prog("同步插件到 $profileIn …")
        runCatching {
            val dest = File(profile, "node_modules/$pkgName")
            dest.deleteRecursively(); dest.parentFile?.mkdirs()
            File(p.dir).copyRecursively(dest, overwrite = true)
            File(profile, ".data").mkdirs()
            File(profile, "host.json").writeText(JSONObject().put("id", p.id).put("pkg", pkgName).put("profile", profileIn)
                .put("dataDir", "$profileIn/.data").put("locale", appLocale(ctx)).toString())
        }.onFailure { return@withContext fail(ctx, fullName, "同步失败：${it.message}") }

        // Runtime deps for code plugins only (skill-only plugins need none). Never run install scripts.
        val deps = pkg.optJSONObject("dependencies")?.keys()?.asSequence()?.filterNot { it.startsWith("@deepseek-ai/") }?.toList().orEmpty()
        if (local.patch?.skillOnly != true && deps.isNotEmpty()) {
            prog("安装依赖（npm --ignore-scripts，${deps.size} 个）…")
            val reg = "https://registry.npmmirror.com/"
            val r = ShellExecutor.execute(ctx, "cd ${shq("$profileIn/node_modules/$pkgName")} && (command -v npm >/dev/null || apk add --no-cache npm >/dev/null 2>&1) && " +
                "npm install --omit=dev --omit=peer --ignore-scripts --no-audit --no-fund --registry=$reg 2>&1 | tail -n 6", 420_000) { log(ctx, fullName, "npm: $it") }
            if (r.exitCode != 0) log(ctx, fullName, "依赖安装退出码 ${r.exitCode}（继续尝试加载）")
        }

        prog("启动 Node $node 宿主 …")
        val statusLatch = kotlinx.coroutines.CompletableDeferred<JSONObject>()
        val cmd = "cd ${shq(profileIn)} && export HOME=${shq("$profileIn/.data/home")} NODE_PATH=$KIT_IN/node_modules LANG=C.UTF-8 && mkdir -p \"\$HOME\" && " +
            "exec node --import $KIT_IN/bootstrap.mjs $KIT_IN/host.mjs ${shq("$profileIn/host.json")}"
        val bridge = runCatching {
            DshHostBridge.spawn(ctx, cmd,
                onNotify = { m, params ->
                    when (m) {
                        "log" -> log(ctx, fullName, "[${params?.optString("level")}] ${params?.optString("name")}: ${params?.optString("text")}")
                        "hello" -> log(ctx, fullName, "宿主就绪：Node ${params?.optString("node")} · cordis ${params?.optString("cordis")}")
                        "status" -> if (params != null) {
                            if (params.optString("phase") != "starting") statusLatch.complete(params)
                            val cmds = params.optJSONArray("commands")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                            _live.value[fullName]?.let { if (it.phase == Phase.RUNNING) setLive(fullName, it.copy(commands = cmds)) }
                        } else Unit
                        else -> Unit
                    }
                },
                onLog = { log(ctx, fullName, it) },
                onExit = { code ->
                    bridges.remove(fullName)
                    log(ctx, fullName, "宿主进程退出（$code）")
                    statusLatch.completeExceptionally(IllegalStateException("宿主进程退出（$code）"))
                    val cur = _live.value[fullName]
                    if (cur?.phase == Phase.RUNNING) setLive(fullName, Live(if (code == 0) Phase.IDLE else Phase.FAILED))
                })
        }.getOrElse { return@withContext fail(ctx, fullName, "无法启动宿主：${it.message}") }
        bridges[fullName] = bridge

        val st = runCatching { kotlinx.coroutines.withTimeout(90_000) { statusLatch.await() } }.getOrElse {
            bridge.stop(); bridges.remove(fullName)
            return@withContext fail(ctx, fullName, "宿主未返回状态：${it.message}")
        }
        val missing = (st.optJSONArray("missing").strings() + st.optJSONArray("missingPackages").strings()).distinct()
        val requested = st.optJSONArray("requested").strings()
        val state = st.optString("state")
        val error = st.optString("error").ifBlank { null }
        log(ctx, fullName, "宿主报告：$state" + (if (missing.isNotEmpty()) " · 缺少 ${missing.joinToString()}" else ""))

        // Skills → Mibot skill repository.
        val imported = mutableListOf<String>()
        st.optJSONArray("skills")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.optJSONArray("entries").strings().forEach { inPath ->
                val hostDir = File(rf.rootfsDir, inPath.removePrefix("/"))
                importSkill(ctx, p, hostDir)?.let { imported += it }
            }
        }
        if (imported.isNotEmpty()) log(ctx, fullName, "已导入技能：${imported.joinToString()}")

        val result = when (state) {
            "SKILLS" -> {
                bridge.stop(); bridges.remove(fullName) // nothing left to run once skills are imported
                if (imported.isEmpty()) HostResult("FAILED", emptyList(), requested, "没有可导入的 SKILL.md", emptyList(), node, now())
                else HostResult("SKILLS", emptyList(), requested, null, imported, node, now())
            }
            "ACTIVE" -> HostResult("RUNNING", emptyList(), requested, null, imported, node, now())
            "PENDING" -> { bridge.stop(); bridges.remove(fullName); HostResult("PENDING", missing, requested, error, imported, node, now()) }
            "BLOCKED" -> { bridge.stop(); bridges.remove(fullName); HostResult("BLOCKED", emptyList(), requested, error, emptyList(), node, now()) }
            else -> { bridge.stop(); bridges.remove(fullName); HostResult("FAILED", missing, requested, error ?: "加载失败（见日志）", imported, node, now()) }
        }
        finish(ctx, fullName, result, st.optJSONArray("commands").strings())
    }

    suspend fun stop(ctx: Context, fullName: String) = withContext(Dispatchers.IO) {
        runCatching { bridges.remove(fullName)?.stop() }
        val p = PluginStore.get(ctx, fullName)
        // Kill switch: imported skills are disabled (kept on disk; removed on uninstall).
        val skills = p?.host?.skills.orEmpty()
        skills.forEach { id -> runCatching { skillRepo(ctx)?.setEnabled(id, false) } }
        if (skills.isNotEmpty()) log(ctx, fullName, "已停用技能：${skills.joinToString()}")
        setLive(fullName, Live(Phase.IDLE))
        PluginStore.update(ctx, fullName) { cur ->
            val h = cur.host
            cur.copy(status = PluginStatus.IDLE, note = null,
                host = h?.copy(state = if (h.state == "RUNNING" || h.state == "SKILLS") "STOPPED" else h.state))
        }
        log(ctx, fullName, "已停止")
    }

    /** Uninstall hook: stop and delete imported skills + staged profile. */
    suspend fun forget(ctx: Context, fullName: String) = withContext(Dispatchers.IO) {
        runCatching { bridges.remove(fullName)?.stop() }
        val p = PluginStore.get(ctx, fullName) ?: return@withContext
        p.host?.skills.orEmpty().forEach { id -> runCatching { skillRepo(ctx)?.delete(id) } }
        runCatching {
            val rf = RootfsManager.getInstance(ctx)
            if (rf.isInstalled) File(rf.rootfsDir, "root/.mibot/dsh/p/${p.id}").deleteRecursively()
        }
        _live.value = _live.value - fullName
        clearLog(ctx, fullName)
    }

    suspend fun runCommand(fullName: String, name: String): String {
        val b = bridges[fullName] ?: throw IllegalStateException("插件未运行")
        val r = b.request("commands.run", JSONObject().put("name", name).put("args", JSONObject()), 60_000)
        return r?.toString() ?: "（无返回）"
    }

    private fun finish(ctx: Context, fullName: String, r: HostResult, commands: List<String> = emptyList()): HostResult {
        val phase = when (r.state) {
            "RUNNING", "SKILLS" -> Phase.RUNNING
            "PENDING" -> Phase.PENDING
            "BLOCKED" -> Phase.BLOCKED
            else -> Phase.FAILED
        }
        setLive(fullName, Live(phase, commands = commands))
        PluginStore.update(ctx, fullName) {
            it.copy(host = r, status = if (phase == Phase.RUNNING) PluginStatus.RUNNING else PluginStatus.IDLE,
                note = when (r.state) { "SKILLS" -> "运行中 · 已导入 ${r.skills.size} 个技能"; "RUNNING" -> null; else -> null })
        }
        return r
    }

    private fun fail(ctx: Context, fullName: String, why: String): HostResult {
        log(ctx, fullName, "失败：$why")
        return finish(ctx, fullName, HostResult("FAILED", emptyList(), emptyList(), why, emptyList(), nodeVersion, now()))
    }

    // ------------------------------------------------------------ node + kit

    @Volatile private var nodeError: String? = null

    /** Node ≥ 22 inside the sandbox; installs `nodejs` from Alpine on first use. Returns the version. */
    private suspend fun ensureNode(ctx: Context, progress: (String) -> Unit): String? = nodeLock.withLock {
        nodeVersion?.let { return it }
        nodeError = null
        fun parse(out: String) = Regex("""v(\d+)\.(\d+)\.(\d+)""").find(out)?.let { it.value.removePrefix("v") to it.groupValues[1].toInt() }
        var v = parse(ShellExecutor.execute(ctx, "node --version 2>/dev/null", 30_000).output)
        if (v == null || v.second < MIN_NODE) {
            progress("首次使用：在沙箱安装 Node 22（约 17 MB）…")
            val r = ShellExecutor.execute(ctx, "apk add --no-cache --progress nodejs 2>&1", 600_000) { line ->
                val l = line.trim()
                if (l.isNotEmpty()) progress("安装 Node：" + l.takeLast(60))
            }
            if (r.exitCode != 0) { nodeError = "apk add nodejs 失败（退出码 ${r.exitCode}）：" + r.output.trim().takeLast(200); return null }
            v = parse(ShellExecutor.execute(ctx, "node --version 2>/dev/null", 30_000).output)
        }
        if (v == null) { nodeError = "沙箱里没有可用的 node"; return null }
        if (v.second < MIN_NODE) { nodeError = "沙箱的 Node 版本 ${v.first} 太旧（需要 ≥ 22）"; return null }
        nodeVersion = v.first
        v.first
    }

    private fun installKit(ctx: Context, rf: RootfsManager) {
        val dest = File(rf.rootfsDir, KIT_IN.removePrefix("/"))
        val stamp = File(dest, ".mibot-kit-stamp")
        val want = "${com.openminis.app.BuildConfig.VERSION_CODE}:" + runCatching { ctx.assets.open("$KIT_ASSET/package.json").bufferedReader().readText().hashCode() }.getOrDefault(0)
        if (stamp.isFile && stamp.readText() == want && File(dest, "host.mjs").isFile) return
        dest.deleteRecursively(); dest.mkdirs()
        copyAssets(ctx, KIT_ASSET, dest)
        stamp.writeText(want)
    }

    private fun copyAssets(ctx: Context, path: String, dest: File) {
        val kids = ctx.assets.list(path) ?: emptyArray()
        if (kids.isEmpty()) {
            dest.parentFile?.mkdirs()
            ctx.assets.open(path).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            return
        }
        dest.mkdirs()
        kids.forEach { copyAssets(ctx, "$path/$it", File(dest, it)) }
    }

    // ------------------------------------------------------------ skills

    private fun skillRepo(ctx: Context) = runCatching { (ctx.applicationContext as com.openminis.app.MinisApp).skillRepository }.getOrNull()

    /** Import one SKILL.md directory as a Mibot skill (+ its sibling files). Returns the skill id. */
    fun importSkill(ctx: Context, p: InstalledPlugin, dir: File): String? {
        val repo = skillRepo(ctx) ?: return null
        val md = File(dir, "SKILL.md").takeIf { it.isFile }?.readText() ?: return null
        val skill = runCatching { repo.importFromContent(md, com.openminis.app.data.repository.SkillRepository.ImportSource.URL, "https://github.com/${p.fullName}") }
            .onFailure { Log.w(TAG, "skill import: ${it.message}") }.getOrNull() ?: return null
        var budget = 20L * 1024 * 1024
        dir.walkTopDown().onEnter { it == dir || (!it.name.startsWith(".") && it.name != "node_modules") }.forEach { f ->
            if (!f.isFile || f.name == "SKILL.md" || f.name.startsWith(".")) return@forEach
            if (f.length() > 2 * 1024 * 1024 || f.length() > budget) return@forEach
            val rel = f.relativeTo(dir).path
            if (rel.contains("..")) return@forEach
            runCatching {
                val out = File(repo.skillFileHostPath(skill.id, rel))
                out.parentFile?.mkdirs(); f.copyTo(out, overwrite = true); budget -= f.length()
            }
        }
        runCatching { repo.setEnabled(skill.id, true) }
        return skill.id
    }

    private fun appLocale(ctx: Context): String = runCatching {
        val l = ctx.resources.configuration.locales[0]
        if (l.language == "zh") (if (l.country == "TW" || l.country == "HK" || l.script == "Hant") "zh-TW" else "zh-CN") else l.toLanguageTag()
    }.getOrDefault("zh-CN")

    private fun JSONArray?.strings(): List<String> = this?.let { a -> List(a.length()) { a.optString(it) } }?.filter { it.isNotBlank() } ?: emptyList()
    private fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"
    private fun now() = System.currentTimeMillis()
}
