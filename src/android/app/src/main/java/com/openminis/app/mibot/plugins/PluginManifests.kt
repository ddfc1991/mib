package com.openminis.app.mibot.plugins

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [fork/mibot-v5b2] Cached manifest facts (package.json / requirements.txt) per plugin,
 * used by the market LIST to decide 「手机能用」 from real data instead of description words.
 *
 * Source order: the installed copy on disk, else the repo's raw files (GitHub, then a mirror),
 * fetched once per `pushedAt` and kept in filesDir/mibot-plugins/manifests.json.
 */
data class ManifestFacts(
    val pushedAt: Long,
    val hasPackageJson: Boolean,
    val hasRequirements: Boolean,
    /** Plugin is written against the DeepSeek Harness host (dsh manifest / @deepseek-ai/… peer packages). */
    val dshHost: Boolean,
    val hostDetail: String?,
    /** Desktop-only blockers found in the manifests (Electron, automation, OS-pinned, desktop Python libs). */
    val desktop: List<String>,
    val checkedAt: Long,
)

object PluginManifests {
    private const val TAG = "MibotPlugins"
    private val state = MutableStateFlow<Map<String, ManifestFacts>>(emptyMap())
    val facts: StateFlow<Map<String, ManifestFacts>> get() = state
    @Volatile private var loaded = false
    private val gate = Semaphore(4)
    private val inFlight = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun file(ctx: Context) = File(PluginStore.storeDir(ctx), "manifests.json")

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val f = file(ctx)
            if (f.exists()) runCatching {
                val o = JSONObject(f.readText())
                state.value = o.keys().asSequence().mapNotNull { k ->
                    runCatching {
                        val j = o.getJSONObject(k)
                        k to ManifestFacts(
                            pushedAt = j.optLong("pushedAt"), hasPackageJson = j.optBoolean("pkg"), hasRequirements = j.optBoolean("req"),
                            dshHost = j.optBoolean("dsh"), hostDetail = j.optString("dshDetail").ifBlank { null },
                            desktop = j.optJSONArray("desktop")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
                            checkedAt = j.optLong("at"),
                        )
                    }.getOrNull()
                }.toMap()
            }.onFailure { Log.w(TAG, "manifests load: ${it.message}") }
            loaded = true
        }
    }

    private fun persist(ctx: Context) {
        val o = JSONObject()
        state.value.forEach { (k, m) ->
            o.put(k, JSONObject().put("pushedAt", m.pushedAt).put("pkg", m.hasPackageJson).put("req", m.hasRequirements)
                .put("dsh", m.dshHost).put("dshDetail", m.hostDetail ?: "").put("desktop", JSONArray(m.desktop)).put("at", m.checkedAt))
        }
        runCatching { file(ctx).writeText(o.toString()) }
    }

    fun get(fullName: String): ManifestFacts? = state.value[fullName]

    /** Fetch facts for [plugins] that have none yet (or are stale for their pushedAt). */
    suspend fun ensure(ctx: Context, plugins: List<MarketPlugin>) = withContext(Dispatchers.IO) {
        ensureLoaded(ctx)
        val todo = plugins.filter { p ->
            val f = state.value[p.fullName]
            (f == null || f.pushedAt < p.pushedAt) && inFlight.add(p.fullName)
        }
        if (todo.isEmpty()) return@withContext
        coroutineScope {
            todo.map { p ->
                async {
                    try {
                        gate.withPermit {
                            val m = runCatching { read(ctx, p) }.getOrNull()
                            if (m != null) state.value = state.value + (p.fullName to m)
                        }
                    } finally { inFlight.remove(p.fullName) }
                }
            }.awaitAll()
        }
        persist(ctx)
    }

    private suspend fun read(ctx: Context, p: MarketPlugin): ManifestFacts? {
        val local = PluginStore.get(ctx, p.fullName)?.dir?.let { File(it) }?.takeIf { it.isDirectory }
        val pkg: String?
        val req: String?
        if (local != null) {
            pkg = File(local, "package.json").takeIf { it.isFile }?.readText()
            req = File(local, "requirements.txt").takeIf { it.isFile }?.readText()
        } else {
            pkg = SkillHubApi.rawFile(p, "package.json")
            req = if (pkg == null) SkillHubApi.rawFile(p, "requirements.txt") else null
        }
        // Both missing from the network is ambiguous (no manifest vs. unreachable): don't cache.
        if (local == null && pkg == null && req == null) return null
        return analyze(p.pushedAt, pkg, req)
    }

    private val ELECTRON = setOf("electron", "electron-builder", "electron-store", "@electron/remote", "electron-updater")
    private val AUTOMATION = setOf("robotjs", "@nut-tree/nut-js", "@nut-tree-fork/nut-js", "nut-js", "iohook", "uiohook-napi", "active-win", "node-window-manager", "screenshot-desktop")
    private val PY_DESKTOP = setOf("pyautogui", "pynput", "keyboard", "mouse", "pywinauto", "pywin32", "pypiwin32", "pyobjc", "pygetwindow", "wxpython", "pyqt5", "pyqt6", "pyside6")

    /** Pure function (unit-testable): manifest texts → facts. */
    fun analyze(pushedAt: Long, pkgText: String?, reqText: String?): ManifestFacts {
        val desktop = mutableListOf<String>()
        var dsh = false
        var dshDetail: String? = null
        val pkg = pkgText?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (pkg != null) {
            val runtime = mutableSetOf<String>()
            listOf("dependencies", "optionalDependencies", "peerDependencies").forEach { k -> pkg.optJSONObject(k)?.keys()?.forEach { runtime += it } }
            val hostPeers = runtime.filter { it.startsWith("@deepseek-ai/") }
            when {
                pkg.has("dsh") -> { dsh = true; dshDetail = "package.json 含 dsh 插件清单" }
                hostPeers.isNotEmpty() -> { dsh = true; dshDetail = "依赖 " + hostPeers.take(2).joinToString() }
                pkg.has("dshTarget") || pkg.has("dshTargets") -> { dsh = true; dshDetail = "package.json 指定 DSH 版本" }
            }
            runtime.filter { it in ELECTRON }.takeIf { it.isNotEmpty() }?.let { desktop += "Electron（${it.joinToString()}）" }
            runtime.filter { it in AUTOMATION }.takeIf { it.isNotEmpty() }?.let { desktop += "桌面自动化（${it.joinToString()}）" }
            pkg.optJSONArray("os")?.let { a ->
                val oses = List(a.length()) { a.optString(it) }
                if (oses.isNotEmpty() && oses.none { it == "linux" || it == "android" }) desktop += "限定系统 " + oses.joinToString()
            }
        }
        if (reqText != null) {
            val names = reqText.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() && !it.startsWith("-") }
                .map { it.split('=', '<', '>', '[', ';', '~', '!', ' ').first().lowercase() }
            names.filter { it in PY_DESKTOP }.takeIf { it.isNotEmpty() }?.let { desktop += "桌面专用 Python 库（${it.joinToString()}）" }
        }
        return ManifestFacts(pushedAt, pkgText != null, reqText != null, dsh, dshDetail, desktop, System.currentTimeMillis())
    }
}
