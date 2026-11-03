package com.openminis.app.mibot.plugins

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * [fork/mibot-v5] 插件市场 — data layer.
 *
 * Source: SkillHub's DSH Plugin 广场 (https://skillhub.cn/plugins). Its public
 * JSON API (the same one the site calls) lists DeepSeek Harness plugins that
 * are open source on GitHub (MIT in practice):
 *   GET https://api.skillhub.cn/api/v1/plugins?page&page_size&sort=stars|updated&scope=verified&q&category
 *   GET https://api.skillhub.cn/api/v1/plugins/categories
 *   GET https://api.skillhub.cn/api/v1/plugins/{owner}/{repo}/readme   (302 → markdown)
 *   GET https://api.skillhub.cn/api/v1/plugins/{owner}/{repo}/install-plan (exact commit)
 * Nothing from the plugins is bundled; they are downloaded on demand from
 * GitHub (codeload, then mirrors) into the app's plugin store.
 */
data class MarketPlugin(
    val fullName: String,
    val owner: String,
    val name: String,
    val description: String,
    val avatarUrl: String?,
    val stars: Int,
    val license: String,
    val categoryKey: String,
    val installability: String,
    val repoUrl: String,
    val defaultBranch: String,
    val pushedAt: Long,
    val topics: List<String>,
) {
    /** Stable store id (directory name). */
    val id: String get() = fullName.replace('/', '_').lowercase()
    val oneClick: Boolean get() = installability == "verified"

    companion object {
        fun from(j: JSONObject): MarketPlugin? {
            val full = j.optString("fullName").ifBlank { return null }
            val repo = j.optString("repositoryUrl").ifBlank { "https://github.com/$full" }
            // Keep the repo's own owner/name casing for GitHub URLs.
            val path = runCatching { URL(repo).path.trim('/') }.getOrNull()?.takeIf { it.count { c -> c == '/' } == 1 } ?: full
            return MarketPlugin(
                fullName = full,
                owner = path.substringBefore('/'),
                name = path.substringAfter('/'),
                description = j.optString("description"),
                avatarUrl = j.optString("avatarUrl").ifBlank { null },
                stars = j.optInt("stars"),
                license = j.optString("license").ifBlank { "—" },
                categoryKey = j.optString("categoryKey"),
                installability = j.optString("installability"),
                repoUrl = repo,
                defaultBranch = j.optString("defaultBranch").ifBlank { "main" },
                pushedAt = j.optLong("pushedAt"),
                topics = j.optJSONArray("topics")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
            )
        }
    }
}

enum class MarketSort(val api: String) { STARS("stars"), NEWEST("updated") }

/** Mibot's category tabs (Chinese), mapped from SkillHub categories + keywords. */
object PluginCategories {
    const val ALL = "全部"
    val tabs = listOf(ALL, "工作流", "开发编程", "知识管理", "AI Agent", "效率", "娱乐", "生活", "其他")

    private val fun_ = listOf("game", "galgame", "娱乐", "宠物", "鲸鱼娘", "wallpaper", "壁纸", "skin", "皮肤", "换装", "anime", "二次元", "启动动画")
    private val life = listOf("study", "学习", "bilibili", "b站", "抖音", "douyin", "生活", "health", "健康", "travel", "旅行", "美食", "记账", "resume", "简历", "日报")
    private val dev = listOf("code", "coding", "developer", "git", "lint", "review", "编程", "代码", "ssh", "devops", "security", "安全", "redteam", "红队", "compiler", "debug")

    fun of(p: MarketPlugin): String {
        val hay = (p.name + " " + p.description + " " + p.topics.joinToString(" ")).lowercase()
        return when {
            p.categoryKey == "fun-dressup" || fun_.any { it in hay } -> "娱乐"
            life.any { it in hay } -> "生活"
            p.categoryKey == "admin-security" || (p.categoryKey != "model-inference" && dev.any { it in hay }) -> "开发编程"
            p.categoryKey == "agent-workflow" -> "工作流"
            p.categoryKey == "model-inference" -> "AI Agent"
            p.categoryKey == "memory" -> "知识管理"
            p.categoryKey == "web-tools" || p.categoryKey == "client" -> "效率"
            else -> "其他"
        }
    }
}

/**
 * "手机能用" heuristic for the LIST (cheap, text only): no desktop-only signal
 * (Electron / native desktop binaries / Windows-macOS paths / desktop
 * automation / the DSH desktop web UI). The detail page and the converter
 * refine it from the real package.json / requirements.
 */
object PluginCompat {
    private val desktopWords = listOf(
        "electron", "desktop", "桌面", "windows", "win32", "macos", "mac os", ".exe", "powershell", "autohotkey",
        "pyautogui", "wallpaper engine", "tui", "terminal ui", "sidebar", "侧边栏", "界面", "client ui", "客户端",
        "workbuddy", "native app", "tray", "托盘",
    )

    /** Reasons this plugin needs a computer (empty = phone-usable). */
    fun desktopReasons(p: MarketPlugin): List<String> {
        val hay = (p.name + " " + p.description + " " + p.topics.joinToString(" ")).lowercase()
        val out = mutableListOf<String>()
        if (p.categoryKey == "client" || p.categoryKey == "fun-dressup") out += "改造 DSH 桌面/网页界面"
        if ("electron" in hay) out += "Electron 桌面窗口"
        if (listOf("windows", "win32", ".exe", "powershell", "autohotkey").any { it in hay }) out += "Windows 专用程序/路径"
        if (listOf("macos", "mac os").any { it in hay }) out += "macOS 专用"
        if ("pyautogui" in hay || "自动操作桌面" in hay) out += "桌面自动化"
        if (out.isEmpty() && desktopWords.any { it in hay }) out += "依赖桌面环境"
        return out.distinct()
    }

    fun phoneUsable(p: MarketPlugin, store: InstalledPlugin?): Boolean =
        verdict(p, store, PluginManifests.get(p.fullName)).kind == Fit.PHONE

    /** [fork/mibot-v5b4] PENDING = DSH plugin waiting for host services (待支持); UNSUPPORTED = block list (不支持). */
    enum class Fit { PHONE, DESKTOP, UNVERIFIED, PENDING, UNSUPPORTED }
    data class Verdict(val kind: Fit, val reasons: List<String>)

    /** List sections: PENDING / UNSUPPORTED sit with the desktop ones. */
    fun notPhone(f: Fit) = f == Fit.DESKTOP || f == Fit.PENDING || f == Fit.UNSUPPORTED

    /**
     * [fork/mibot-v5b2] Tightened 「手机能用」: needs positive evidence.
     * [fork/mibot-v5b4] DSH plugins are judged by the DSH host (stage 1): skill-only plugins and plugins
     * the host loaded are phone-usable; a plugin whose required services the host lacks is 「待支持：<services>」;
     * the block list (dsh-purge forks, dsh-market, host patchers) is 「不支持」.
     */
    fun verdict(p: MarketPlugin, store: InstalledPlugin?, m: ManifestFacts?): Verdict {
        (m?.blocked ?: DshPolicy.blockReason(p.fullName, null, null))?.let { return Verdict(Fit.UNSUPPORTED, listOf(it)) }
        if (PluginAdapters.hasAdapter(p.fullName)) return Verdict(Fit.PHONE, listOf("Mibot 原生适配"))
        store?.host?.let { h ->
            when (h.state) {
                "SKILLS" -> return Verdict(Fit.PHONE, listOf("已导入为 Mibot 技能"))
                "RUNNING", "STOPPED" -> return Verdict(Fit.PHONE, listOf("DSH 宿主已加载"))
                "PENDING" -> return Verdict(Fit.PENDING, listOf("待支持：" + h.missing.ifEmpty { listOf("宿主服务") }.joinToString(", ")))
                "BLOCKED" -> return Verdict(Fit.UNSUPPORTED, listOfNotNull(h.error ?: "不支持"))
                else -> Unit // FAILED: fall through to the static verdict; the detail page shows the log
            }
        }
        store?.conversion?.let { c ->
            return if (c.state == ConvState.FAIL) Verdict(Fit.DESKTOP, listOfNotNull(c.reason ?: "转换失败"))
            else Verdict(Fit.PHONE, listOf("已转换"))
        }
        val text = desktopReasons(p)
        if (m == null) return if (text.isNotEmpty()) Verdict(Fit.DESKTOP, text) else Verdict(Fit.UNVERIFIED, listOf("清单未读取"))
        if (m.skillOnly) return Verdict(Fit.PHONE, listOf("技能插件：可导入为 Mibot 技能"))
        val r = buildList {
            addAll(m.desktop)
            if (p.categoryKey == "client" || p.categoryKey == "fun-dressup") add("改造 DSH 桌面/网页界面")
        }
        if (r.isNotEmpty()) return Verdict(Fit.DESKTOP, r.distinct())
        if (m.dshHost) return Verdict(Fit.PENDING, listOf("待支持：DSH 宿主服务（试装后点「启动」可列出具体缺少的服务）"))
        if (!m.hasPackageJson && !m.hasRequirements) return Verdict(Fit.UNVERIFIED, listOf("仓库没有 package.json / requirements.txt"))
        return Verdict(Fit.PHONE, emptyList())
    }
}

object SkillHubApi {
    private const val BASE = "https://api.skillhub.cn/api/v1/plugins"
    private const val TAG = "MibotPlugins"

    data class Page(val items: List<MarketPlugin>, val total: Int)

    private fun get(url: String, timeoutMs: Int = 15_000): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs; readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/markdown, */*")
            setRequestProperty("User-Agent", "Mibot-Android")
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    suspend fun list(page: Int, sort: MarketSort, q: String?, pageSize: Int = 48): Page = withContext(Dispatchers.IO) {
        val sb = StringBuilder("$BASE?page=$page&page_size=$pageSize&sort=${sort.api}&scope=verified")
        if (!q.isNullOrBlank()) sb.append("&q=").append(URLEncoder.encode(q.trim(), "UTF-8"))
        val j = JSONObject(get(sb.toString()))
        val arr = j.optJSONArray("items") ?: JSONArray()
        Page((0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MarketPlugin::from) }.filter { !it.fullName.isBlank() }, j.optInt("total"))
    }

    suspend fun readme(p: MarketPlugin): String = withContext(Dispatchers.IO) {
        runCatching { get("$BASE/${p.owner.lowercase()}/${p.name.lowercase()}/readme", 20_000) }
            .recoverCatching {
                // Fall back to the repository's own README.
                get("https://raw.githubusercontent.com/${p.owner}/${p.name}/${p.defaultBranch}/README.md", 20_000)
            }.getOrElse { "" }
    }

    /** Exact commit SkillHub verified, if it says. */
    suspend fun headSha(p: MarketPlugin): String? = withContext(Dispatchers.IO) {
        runCatching {
            JSONObject(get("$BASE/${p.owner.lowercase()}/${p.name.lowercase()}/install-plan")).optJSONObject("plugin")
                ?.optString("headSha")?.takeIf { it.length >= 7 }
        }.onFailure { Log.w(TAG, "install-plan: ${it.message}") }.getOrNull()
    }

    /** Raw file from the repo (package.json, requirements.txt…), or null. */
    suspend fun rawFile(p: MarketPlugin, path: String): String? = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://raw.githubusercontent.com/${p.owner}/${p.name}/${p.defaultBranch}/$path",
            "https://ghfast.top/https://raw.githubusercontent.com/${p.owner}/${p.name}/${p.defaultBranch}/$path",
        )
        for (u in urls) {
            val r = runCatching { get(u, 10_000) }.getOrNull()
            if (r != null) return@withContext r
        }
        null
    }
}

// ------------------------------------------------------------ installed store

enum class PluginStatus(val zh: String) { PREPARING("准备中"), RUNNING("运行中"), STOPPED("已停止"), IDLE("已安装 · 未运行") }

enum class ConvState(val zh: String) { OK("已转换可用"), PARTIAL("部分可用"), FAIL("无法转换") }

data class Conversion(
    val state: ConvState,
    val missing: List<String>,
    val reason: String?,
    val log: String,
    val at: Long,
)

/** The per-plugin permission toggles (all default off). */
object PluginPerms {
    val all = listOf(
        "network" to "网络",
        "files" to "文件",
        "notify" to "通知",
        "overlay" to "悬浮窗",
        "control" to "无障碍/虚拟屏操控",
        "accounts" to "账号读取",
    )
}

data class InstalledPlugin(
    val fullName: String,
    val id: String,
    val installedAt: Long,
    val pushedAt: Long,
    val ref: String,
    val dir: String,
    val sizeBytes: Long,
    val status: PluginStatus = PluginStatus.STOPPED,
    val perms: Map<String, Boolean> = emptyMap(),
    val conversion: Conversion? = null,
    val note: String? = null,
    /** [fork/mibot-v5b4] Last DSH host load result (see DshHost.kt). */
    val host: HostResult? = null,
)

object PluginStore {
    private const val TAG = "MibotPlugins"
    private val state = MutableStateFlow<Map<String, InstalledPlugin>>(emptyMap())
    @Volatile private var loaded = false

    fun storeDir(ctx: Context) = File(ctx.filesDir, "mibot-plugins").apply { mkdirs() }
    private fun file(ctx: Context) = File(storeDir(ctx), "installed.json")

    fun flow(ctx: Context): StateFlow<Map<String, InstalledPlugin>> {
        ensure(ctx); return state.asStateFlow()
    }

    fun get(ctx: Context, fullName: String): InstalledPlugin? { ensure(ctx); return state.value[fullName] }

    private fun ensure(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            state.value = runCatching { parse(file(ctx).readText()) }.getOrDefault(emptyMap())
            loaded = true
        }
    }

    fun put(ctx: Context, p: InstalledPlugin) {
        ensure(ctx)
        synchronized(this) {
            state.value = state.value + (p.fullName to p)
            persist(ctx)
        }
    }

    fun update(ctx: Context, fullName: String, f: (InstalledPlugin) -> InstalledPlugin) {
        ensure(ctx)
        synchronized(this) {
            val cur = state.value[fullName] ?: return
            state.value = state.value + (fullName to f(cur))
            persist(ctx)
        }
    }

    fun remove(ctx: Context, fullName: String) {
        ensure(ctx)
        synchronized(this) {
            val cur = state.value[fullName] ?: return
            runCatching { File(cur.dir).deleteRecursively() }
            runCatching { sandboxDir(ctx, cur.id)?.deleteRecursively() }
            state.value = state.value - fullName
            persist(ctx)
        }
    }

    /** Where the sandbox sees the plugin (/root/.mibot/plugins/<id>), if the rootfs is installed. */
    fun sandboxDir(ctx: Context, id: String): File? {
        val rf = com.openminis.app.sandbox.RootfsManager.getInstance(ctx)
        if (!rf.isInstalled) return null
        return File(rf.rootfsDir, "root/.mibot/plugins/$id")
    }

    private fun persist(ctx: Context) {
        val o = JSONObject()
        state.value.values.forEach { p ->
            o.put(p.fullName, JSONObject().apply {
                put("id", p.id); put("installedAt", p.installedAt); put("pushedAt", p.pushedAt); put("ref", p.ref)
                put("dir", p.dir); put("size", p.sizeBytes); put("status", p.status.name)
                put("perms", JSONObject(p.perms.mapValues { it.value }))
                p.note?.let { put("note", it) }
                p.host?.let { put("host", it.toJson()) }
                p.conversion?.let { c ->
                    put("conv", JSONObject().put("state", c.state.name).put("missing", JSONArray(c.missing))
                        .put("reason", c.reason ?: "").put("log", c.log.takeLast(12_000)).put("at", c.at))
                }
            })
        }
        runCatching { file(ctx).writeText(o.toString()) }.onFailure { Log.w(TAG, "persist: ${it.message}") }
    }

    private fun parse(raw: String): Map<String, InstalledPlugin> {
        val o = JSONObject(raw)
        return o.keys().asSequence().mapNotNull { k ->
            runCatching {
                val j = o.getJSONObject(k)
                val perms = j.optJSONObject("perms")?.let { pj -> pj.keys().asSequence().associateWith { pj.optBoolean(it) } } ?: emptyMap()
                val conv = j.optJSONObject("conv")?.let { c ->
                    Conversion(
                        state = ConvState.valueOf(c.optString("state")),
                        missing = c.optJSONArray("missing")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
                        reason = c.optString("reason").ifBlank { null },
                        log = c.optString("log"),
                        at = c.optLong("at"),
                    )
                }
                k to InstalledPlugin(
                    fullName = k, id = j.optString("id"), installedAt = j.optLong("installedAt"), pushedAt = j.optLong("pushedAt"),
                    ref = j.optString("ref"), dir = j.optString("dir"), sizeBytes = j.optLong("size"),
                    // [fork/mibot-v5b2] Only a native adapter really runs; it survives restarts (its
                    // provider stays registered). Everything else is installed-not-running.
                    status = if (j.optString("note").startsWith("运行中")) PluginStatus.RUNNING else PluginStatus.IDLE,
                    perms = perms, conversion = conv, note = j.optString("note").ifBlank { null },
                    host = HostResult.from(j.optJSONObject("host")),
                )
            }.getOrNull()
        }.toMap()
    }
}

// ------------------------------------------------------------ install

object PluginInstaller {
    private const val TAG = "MibotPlugins"
    private const val MAX_BYTES = 120L * 1024 * 1024

    /** [fork/mibot-v5b2] Live download progress per plugin (list shows a thin bar + 「下载中 32%」). */
    data class Dl(val bytes: Long, val total: Long) {
        val fraction: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    }
    private val _downloads = MutableStateFlow<Map<String, Dl>>(emptyMap())
    val downloads: StateFlow<Map<String, Dl>> get() = _downloads

    /** Candidate download URLs for a ref: GitHub codeload first, then public mirrors. */
    fun zipUrls(p: MarketPlugin, ref: String): List<String> {
        val gh = "https://github.com/${p.owner}/${p.name}/archive/$ref.zip"
        return listOf(
            "https://codeload.github.com/${p.owner}/${p.name}/zip/$ref",
            "https://ghfast.top/$gh",
            "https://gh-proxy.com/$gh",
            gh,
        )
    }

    /**
     * Download the repo zip (exact verified commit when SkillHub gives one,
     * else the default branch), with retry + mirror fallback, unpack it into
     * the plugin store and (when the sandbox exists) mirror it to
     * /root/.mibot/plugins/<id>. Returns an error message or null.
     */
    suspend fun install(ctx: Context, p: MarketPlugin, progress: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        val ref = SkillHubApi.headSha(p) ?: p.defaultBranch
        val tmp = File(ctx.cacheDir, "plugin-${p.id}.zip")
        var lastErr = ""
        var ok = false
        loop@ for (u in zipUrls(p, ref) + if (ref != p.defaultBranch) zipUrls(p, p.defaultBranch) else emptyList()) {
            for (attempt in 1..2) {
                progress("下载中（${host(u)}${if (attempt > 1) " · 重试" else ""}）")
                val r = runCatching { download(u, tmp) { b, t -> _downloads.value = _downloads.value + (p.fullName to Dl(b, t)) } }
                _downloads.value = _downloads.value - p.fullName
                if (r.isSuccess) { ok = true; break@loop }
                lastErr = r.exceptionOrNull()?.message ?: "unknown"
                Log.w(TAG, "download $u: $lastErr")
            }
        }
        if (!ok) return@withContext "下载失败（已尝试 GitHub 与镜像）：$lastErr"
        progress("解压中")
        val dest = File(PluginStore.storeDir(ctx), p.id)
        val staging = File(PluginStore.storeDir(ctx), "${p.id}.staging")
        staging.deleteRecursively(); staging.mkdirs()
        val size = runCatching { unzipStripTop(tmp, staging) }.getOrElse {
            staging.deleteRecursively(); tmp.delete()
            return@withContext "解压失败：${it.message}"
        }
        tmp.delete()
        dest.deleteRecursively()
        if (!staging.renameTo(dest)) { staging.copyRecursively(dest, overwrite = true); staging.deleteRecursively() }
        PluginStore.sandboxDir(ctx, p.id)?.let { sb ->
            progress("同步到沙箱")
            runCatching { sb.deleteRecursively(); dest.copyRecursively(sb, overwrite = true) }
                .onFailure { Log.w(TAG, "sandbox copy: ${it.message}") }
        }
        val prev = PluginStore.get(ctx, p.fullName)
        PluginStore.put(ctx, InstalledPlugin(
            fullName = p.fullName, id = p.id, installedAt = System.currentTimeMillis(), pushedAt = p.pushedAt,
            ref = ref, dir = dest.absolutePath, sizeBytes = size,
            perms = prev?.perms ?: PluginPerms.all.associate { it.first to false },
            conversion = prev?.conversion,
        ))
        null
    }

    private fun host(u: String) = runCatching { URL(u).host }.getOrDefault(u)

    private fun download(u: String, out: File, onBytes: (Long, Long) -> Unit = { _, _ -> }) {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mibot-Android")
        }
        try {
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            var total = 0L
            val len = c.contentLengthLong
            var lastTick = 0L
            onBytes(0, len)
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw java.io.IOException("too large (>120 MB)")
                        o.write(buf, 0, n)
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 150) { lastTick = now; onBytes(total, len) }
                    }
                }
            }
            // Zip magic check (mirrors sometimes answer 200 with an HTML page).
            val head = out.inputStream().use { val b = ByteArray(2); it.read(b); b }
            if (head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) throw java.io.IOException("not a zip")
        } finally { c.disconnect() }
    }

    /** Unzip dropping the archive's single top-level folder; zip-slip safe. Returns bytes written. */
    private fun unzipStripTop(zip: File, dest: File): Long {
        var total = 0L
        val root = dest.canonicalPath + File.separator
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val rel = e.name.substringAfter('/', "")
                if (rel.isEmpty()) continue
                val f = File(dest, rel)
                if (!f.canonicalPath.startsWith(root)) throw SecurityException("bad entry ${e.name}")
                if (e.isDirectory) { f.mkdirs(); continue }
                f.parentFile?.mkdirs()
                f.outputStream().use { o -> total += zin.copyTo(o) }
            }
        }
        return total
    }
}

// ------------------------------------------------------------ runner

/**
 * [fork/mibot-v5b2] Mibot has no DeepSeek Harness host: plugin code is never
 * executed, so a plugin is never shown 运行中 unless a native adapter really
 * serves it (PluginAdapters — e.g. dsh-our-free-model's Kilo lane, whose
 * models are registered in the gateway and probed with a real request).
 */
object PluginRunner {
    const val IDLE_NOTE = "未启动。DSH 插件点「启动」由 Mibot 的 DSH 宿主（沙箱里的 Node 22）加载。"

    suspend fun start(ctx: Context, fullName: String) {
        val p = PluginStore.get(ctx, fullName) ?: return
        if (!PluginAdapters.hasAdapter(fullName)) {
            PluginStore.update(ctx, fullName) { it.copy(status = PluginStatus.IDLE, note = IDLE_NOTE) }
            return
        }
        PluginStore.update(ctx, fullName) { it.copy(status = PluginStatus.PREPARING, note = "审核源码并注册模型…") }
        val r = runCatching { PluginAdapters.enable(ctx, p) }.getOrNull()
        PluginStore.update(ctx, fullName) {
            when {
                r == null -> it.copy(status = PluginStatus.IDLE, note = "适配失败")
                r.ok -> it.copy(status = PluginStatus.RUNNING, note = "运行中 · ${r.models} 个模型（Kilo 免费池，来自插件）· ${r.probe}")
                else -> it.copy(status = PluginStatus.IDLE, note = "未运行：${r.probe}" + if (r.models > 0) "（已注册 ${r.models} 个模型）" else "")
            }
        }
    }

    fun stop(ctx: Context, fullName: String) {
        PluginStore.update(ctx, fullName) { it.copy(status = PluginStatus.IDLE, note = if (PluginAdapters.hasAdapter(fullName)) "已停用适配（模型仍在服务商列表，可在那里删除）" else IDLE_NOTE) }
    }
}
