package com.openminis.app.mibot.plugins

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * [fork/mibot-v5] 「尝试转换为手机版」(实验).
 *
 * 1. Analyse package.json / requirements.txt / pyproject + a bounded source
 *    scan, and classify blockers: Electron / desktop UI, OS-native binaries,
 *    Windows / macOS paths, desktop automation, the DSH desktop host.
 * 2. In the Mibot Linux sandbox (Alpine under PRoot): install Node / Python
 *    deps with an auto-selected mirror (fastest of npmmirror / npmjs, tuna /
 *    pypi), `npm rebuild` native modules for arm64 where a toolchain exists.
 * 3. Shims: `electron` → writes the window URL to .mibot-bridge/panel.json for
 *    Mibot's plugin panel (WebView); robotjs / nut-js / pyautogui / pynput →
 *    queue requests for the virtual-screen / accessibility bridge, refused
 *    unless the plugin's 无障碍/虚拟屏操控 toggle is on; HOME / APPDATA / XDG
 *    → the plugin's own sandbox dirs.
 * 4. Smoke test: load the entry module (Node) / byte-compile (Python).
 * Result: 已转换可用 / 部分可用（缺失功能）/ 无法转换（原因）. Honest by
 * design: the bridge consumer and the DSH host do not exist yet, so anything
 * relying on them is listed as missing, and the whole flow is labelled 实验.
 */
object PluginConverter {

    enum class BlockerKind(val zh: String) {
        ELECTRON("Electron / 桌面窗口"),
        NATIVE_BINARY("系统原生二进制"),
        NATIVE_MODULE("原生 Node 模块（需为 arm64 重编译）"),
        OS_PATH("Windows / macOS 路径"),
        AUTOMATION("桌面自动化"),
        DSH_HOST("依赖 DSH 宿主（桌面/网页客户端）"),
    }

    data class Blocker(val kind: BlockerKind, val detail: String)

    data class Analysis(
        val blockers: List<Blocker>,
        val nodeEntry: String?,
        val pyEntry: String?,
        val hasPackageJson: Boolean,
        val requirements: List<String>,
        val desktopOnlyPy: List<String>,
        val needsNetwork: Boolean,
        val needsFiles: Boolean,
    )

    private val ELECTRON_DEPS = setOf("electron", "electron-builder", "electron-store", "@electron/remote", "electron-updater")
    private val AUTOMATION_DEPS = setOf("robotjs", "@nut-tree/nut-js", "@nut-tree-fork/nut-js", "nut-js", "iohook", "uiohook-napi",
        "node-window-manager", "active-win", "screenshot-desktop", "node-key-sender")
    private val NATIVE_HINT_DEPS = setOf("ffi-napi", "ref-napi", "edge-js", "winreg", "node-pty", "sharp", "better-sqlite3", "sqlite3", "canvas")
    private val PY_DESKTOP = setOf("pyautogui", "pynput", "keyboard", "mouse", "pywinauto", "pywin32", "pypiwin32", "pyobjc", "pygetwindow", "pyscreeze", "wxpython", "pyqt5", "pyqt6", "pyside6", "tkinter")
    private val PATH_PATTERNS = listOf(
        Regex("""[A-Za-z]:\\\\"""), Regex("%APPDATA%", RegexOption.IGNORE_CASE), Regex("""process\.env\.(APPDATA|LOCALAPPDATA|USERPROFILE)"""),
        Regex("/Applications/"), Regex("Library/Application Support"), Regex("""process\.platform\s*===?\s*['"](win32|darwin)['"]"""),
    )
    private val AUTOMATION_PATTERNS = listOf(Regex("""require\(['"](robotjs|@nut-tree[^'"]*)['"]\)"""), Regex("""^\s*import\s+(pyautogui|pynput)""", RegexOption.MULTILINE))

    fun analyze(dir: File): Analysis {
        val blockers = mutableListOf<Blocker>()
        val pkgFile = File(dir, "package.json")
        var nodeEntry: String? = null
        var netHint = false
        var fileHint = false
        if (pkgFile.exists()) {
            val pkg = runCatching { JSONObject(pkgFile.readText()) }.getOrNull()
            if (pkg != null) {
                val deps = mutableSetOf<String>()
                listOf("dependencies", "optionalDependencies", "peerDependencies", "devDependencies").forEach { k ->
                    pkg.optJSONObject(k)?.keys()?.forEach { deps += it }
                }
                deps.filter { it in ELECTRON_DEPS }.takeIf { it.isNotEmpty() }?.let { blockers += Blocker(BlockerKind.ELECTRON, it.joinToString()) }
                deps.filter { it in AUTOMATION_DEPS }.takeIf { it.isNotEmpty() }?.let { blockers += Blocker(BlockerKind.AUTOMATION, it.joinToString()) }
                deps.filter { it in NATIVE_HINT_DEPS }.takeIf { it.isNotEmpty() }?.let { blockers += Blocker(BlockerKind.NATIVE_MODULE, it.joinToString()) }
                pkg.optJSONArray("os")?.let { a ->
                    val oses = List(a.length()) { a.optString(it) }
                    if (oses.isNotEmpty() && oses.none { it == "linux" || it == "android" }) blockers += Blocker(BlockerKind.OS_PATH, "package.json os=" + oses.joinToString())
                }
                if (pkg.has("dsh")) blockers += Blocker(BlockerKind.DSH_HOST, "package.json → dsh（DSH 插件清单）")
                netHint = deps.any { it in setOf("axios", "node-fetch", "undici", "got", "ws", "express") }
                fileHint = deps.any { it in setOf("fs-extra", "chokidar", "glob") }
                nodeEntry = pkg.optString("main").ifBlank { null }
                    ?: pkg.optJSONObject("exports")?.optString(".")?.ifBlank { null }
                    ?: listOf("index.js", "index.mjs", "dist/index.js", "src/index.js").firstOrNull { File(dir, it).exists() }
            }
        }
        val req = File(dir, "requirements.txt").takeIf { it.exists() }?.readLines()
            ?.map { it.substringBefore('#').trim() }?.filter { it.isNotEmpty() && !it.startsWith("-") } ?: emptyList()
        val pyName: (String) -> String = { it.split('=', '<', '>', '[', ';', '~', '!', ' ').first().lowercase() }
        val desktopPy = req.filter { pyName(it) in PY_DESKTOP }
        if (desktopPy.isNotEmpty()) blockers += Blocker(BlockerKind.AUTOMATION, desktopPy.joinToString { pyName(it) })
        val pyEntry = listOf("main.py", "app.py", "plugin.py", "src/main.py").firstOrNull { File(dir, it).exists() }

        // Bounded tree walk: binaries + a source scan for OS paths / automation.
        var scanned = 0
        val bins = mutableListOf<String>()
        val pathHits = mutableSetOf<String>()
        var autoHit = false
        var gyp = false
        dir.walkTopDown().onEnter { it.name != "node_modules" && it.name != ".git" && it.name != ".mibot-shim" }.take(6000).forEach { f ->
            if (!f.isFile) return@forEach
            val n = f.name.lowercase()
            if (n.endsWith(".exe") || n.endsWith(".dll") || n.endsWith(".dylib") || n.endsWith(".msi") || n.endsWith(".app")) bins += f.relativeTo(dir).path
            if (n == "binding.gyp") gyp = true
            if (scanned < 300 && f.length() < 256_000 && (n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".mjs") || n.endsWith(".cjs") || n.endsWith(".py"))) {
                scanned++
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                PATH_PATTERNS.forEach { r -> r.find(text)?.let { pathHits += it.value.take(40) } }
                if (AUTOMATION_PATTERNS.any { it.containsMatchIn(text) }) autoHit = true
                if (!netHint && (text.contains("fetch(") || text.contains("http.request") || text.contains("requests."))) netHint = true
                if (!fileHint && (text.contains("fs.writeFile") || text.contains("open(") && n.endsWith(".py"))) fileHint = true
            }
        }
        if (bins.isNotEmpty()) blockers += Blocker(BlockerKind.NATIVE_BINARY, bins.take(5).joinToString())
        if (gyp) blockers += Blocker(BlockerKind.NATIVE_MODULE, "binding.gyp")
        if (pathHits.isNotEmpty()) blockers += Blocker(BlockerKind.OS_PATH, pathHits.take(4).joinToString())
        if (autoHit && blockers.none { it.kind == BlockerKind.AUTOMATION }) blockers += Blocker(BlockerKind.AUTOMATION, "源码引用桌面自动化库")
        return Analysis(blockers.distinctBy { it.kind to it.detail }, nodeEntry, pyEntry, pkgFile.exists(), req, desktopPy, netHint, fileHint)
    }

    /** Fastest-responding mirror (HEAD/GET timing), else the first. */
    private fun pickMirror(candidates: List<String>): String {
        var best: Pair<String, Long>? = null
        for (u in candidates) {
            val t0 = System.nanoTime()
            val ok = runCatching {
                val c = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 4_000; readTimeout = 4_000; requestMethod = "GET" }
                try { c.responseCode in 200..399 } finally { c.disconnect() }
            }.getOrDefault(false)
            val dt = (System.nanoTime() - t0) / 1_000_000
            if (ok && (best == null || dt < best.second)) best = u to dt
        }
        return best?.first ?: candidates.first()
    }

    private fun writeShims(root: File, controlAllowed: Boolean) {
        val shim = File(root, ".mibot-shim").apply { mkdirs() }
        val nm = File(shim, "node_modules")
        val bridge = "process.env.MIBOT_BRIDGE || require('path').join(process.cwd(), '.mibot-bridge')"
        fun mod(name: String, body: String) {
            val d = File(nm, name).apply { mkdirs() }
            File(d, "package.json").writeText("""{"name":"$name","version":"0.0.0-mibot-shim","main":"index.js"}""")
            File(d, "index.js").writeText(body)
        }
        mod("electron", """
            // Mibot shim: desktop windows become a Mibot plugin panel (WebView).
            const fs = require('fs'); const path = require('path');
            const dir = $bridge; fs.mkdirSync(dir, { recursive: true });
            class BrowserWindow { constructor(o) { this.opts = o || {}; this.webContents = { on() {}, send() {}, openDevTools() {} }; }
              loadURL(u) { fs.writeFileSync(path.join(dir, 'panel.json'), JSON.stringify({ url: u, at: Date.now() })); return Promise.resolve(); }
              loadFile(f) { return this.loadURL('file://' + path.resolve(f)); }
              on() { return this; } once() { return this; } show() {} hide() {} close() {} setMenu() {} isDestroyed() { return false; } }
            const app = { whenReady: () => Promise.resolve(), on() { return app; }, quit() {}, getPath: (k) => path.join(process.env.HOME || '/tmp', k), isPackaged: false };
            module.exports = { app, BrowserWindow, ipcMain: { on() {}, handle() {} }, Menu: { setApplicationMenu() {}, buildFromTemplate: () => ({}) }, Tray: class {}, shell: { openExternal(u) { fs.appendFileSync(path.join(dir, 'open.jsonl'), JSON.stringify({ url: u }) + '\n'); } }, dialog: { showMessageBox: async () => ({ response: 0 }) }, __mibotShim: true };
        """.trimIndent())
        val auto = """
            // Mibot shim: desktop automation → Mibot virtual-screen / accessibility bridge queue.
            const fs = require('fs'); const path = require('path');
            const allowed = ${if (controlAllowed) "true" else "false"};
            function q(op, args) {
              if (!allowed) throw new Error('Mibot: 未授予「无障碍/虚拟屏操控」权限');
              const dir = $bridge; fs.mkdirSync(dir, { recursive: true });
              fs.appendFileSync(path.join(dir, 'control.jsonl'), JSON.stringify({ op, args, at: Date.now() }) + '\n');
            }
            const api = new Proxy({}, { get: (_, k) => (...a) => q(String(k), a) });
            module.exports = api; module.exports.default = api; module.exports.__mibotShim = true;
        """.trimIndent()
        listOf("robotjs", "@nut-tree/nut-js", "@nut-tree-fork/nut-js", "iohook", "uiohook-napi", "active-win", "screenshot-desktop").forEach { mod(it, auto) }
        val py = File(shim, "py").apply { mkdirs() }
        val pyAuto = """
            # Mibot shim: desktop automation -> Mibot bridge queue (gated by the plugin's permission toggle).
            import json, os, time
            _ALLOWED = ${if (controlAllowed) "True" else "False"}
            def _q(op, *a, **k):
                if not _ALLOWED:
                    raise PermissionError('Mibot: 未授予「无障碍/虚拟屏操控」权限')
                d = os.environ.get('MIBOT_BRIDGE', os.path.join(os.getcwd(), '.mibot-bridge'))
                os.makedirs(d, exist_ok=True)
                with open(os.path.join(d, 'control.jsonl'), 'a') as f:
                    f.write(json.dumps({'op': op, 'args': [str(x) for x in a], 'at': time.time()}) + '\n')
            def __getattr__(name):
                return lambda *a, **k: _q(name, *a, **k)
        """.trimIndent()
        File(py, "pyautogui.py").writeText(pyAuto)
        File(py, "pynput").apply { mkdirs() }.let { File(it, "__init__.py").writeText(pyAuto) }
        File(py, "keyboard.py").writeText(pyAuto)
    }

    private fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Run the whole flow for an installed plugin. [log] receives progress lines.
     * The result is also stored on the plugin's store record.
     */
    suspend fun convert(ctx: Context, fullName: String, log: (String) -> Unit): Conversion = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        fun say(s: String) { sb.append(s).append('\n'); log(s) }
        fun done(state: ConvState, missing: List<String>, reason: String?): Conversion {
            val c = Conversion(state, missing.distinct(), reason, sb.toString(), System.currentTimeMillis())
            PluginStore.update(ctx, fullName) { it.copy(conversion = c) }
            return c
        }
        val p = PluginStore.get(ctx, fullName) ?: return@withContext done(ConvState.FAIL, emptyList(), "插件尚未下载")
        val src = File(p.dir)
        say("① 分析清单与依赖 …")
        val a = analyze(src)
        if (a.blockers.isEmpty()) say("   未发现桌面专用依赖")
        a.blockers.forEach { say("   ⚠ ${it.kind.zh}：${it.detail}") }
        if (a.nodeEntry == null && a.pyEntry == null) {
            say("   没有找到可加载的 Node / Python 入口")
            return@withContext done(ConvState.FAIL, emptyList(), "没有可在沙箱运行的入口（非 Node / Python 插件，或仅含原生程序）")
        }
        val missing = mutableListOf<String>()
        a.blockers.forEach { b ->
            when (b.kind) {
                BlockerKind.NATIVE_BINARY -> missing += "原生程序不可用：${b.detail}"
                BlockerKind.DSH_HOST -> missing += "DSH 宿主 API / 界面挂载（Mibot 尚未提供）"
                BlockerKind.ELECTRON -> missing += "桌面窗口 → 插件面板（WebView，实验）"
                BlockerKind.AUTOMATION -> missing += "桌面自动化 → 虚拟屏/无障碍桥（请求只入队，执行端未接入）"
                BlockerKind.OS_PATH -> missing += "Windows/macOS 路径已重定向到沙箱目录，硬编码路径可能失效"
                BlockerKind.NATIVE_MODULE -> Unit // decided after rebuild
            }
        }

        val rf = com.openminis.app.sandbox.RootfsManager.getInstance(ctx)
        if (!rf.isInstalled || !com.openminis.app.sandbox.PRootKernel.isBooted) {
            say("② 沙箱未就绪")
            return@withContext done(ConvState.FAIL, missing, "Linux 沙箱未就绪：请先在「设置 → 沙箱」完成初始化后重试")
        }
        val sbDir = File(rf.rootfsDir, "root/.mibot/plugins/${p.id}")
        say("② 同步到沙箱 /root/.mibot/plugins/${p.id}")
        runCatching { sbDir.deleteRecursively(); src.copyRecursively(sbDir, overwrite = true) }
            .onFailure { return@withContext done(ConvState.FAIL, missing, "复制到沙箱失败：${it.message}") }
        val control = p.perms["control"] == true
        writeShims(sbDir, control)
        say("   已写入兼容层：electron → 插件面板，自动化 → 桥接队列（${if (control) "已授权" else "未授权，调用会被拒绝"}），路径 → 沙箱目录")

        val dirIn = "/root/.mibot/plugins/${p.id}"
        val env = "cd ${shq(dirIn)} && export HOME=${shq("$dirIn/.home")} XDG_CONFIG_HOME=${shq("$dirIn/.home/.config")} " +
            "APPDATA=${shq("$dirIn/.home/AppData")} LOCALAPPDATA=${shq("$dirIn/.home/AppData/Local")} " +
            "MIBOT_BRIDGE=${shq("$dirIn/.mibot-bridge")} NODE_PATH=${shq("$dirIn/.mibot-shim/node_modules")} " +
            "PYTHONPATH=${shq("$dirIn/.mibot-shim/py")} && mkdir -p \"\$HOME/.config\" \"\$APPDATA/Local\" && "

        suspend fun sh(cmd: String, timeoutMs: Long): com.openminis.app.sandbox.ShellExecutor.ShellResult =
            com.openminis.app.sandbox.ShellExecutor.execute(ctx, env + cmd, timeoutMs)

        var smokeOk = false
        if (a.nodeEntry != null && a.hasPackageJson) {
            val reg = pickMirror(listOf("https://registry.npmmirror.com/", "https://registry.npmjs.org/"))
            say("③ 安装 Node 依赖（镜像 ${URL(reg).host}）…")
            val inst = sh("(command -v node >/dev/null && command -v npm >/dev/null || apk add --no-cache nodejs npm >/dev/null 2>&1) && " +
                "npm install --omit=dev --ignore-scripts --no-audit --no-fund --registry=$reg 2>&1 | tail -n 15", 420_000)
            say(inst.output.trim().lines().takeLast(8).joinToString("\n") { "   $it" })
            if (inst.exitCode != 0) return@withContext done(ConvState.FAIL, missing, "依赖安装失败（退出码 ${inst.exitCode}）")
            if (a.blockers.any { it.kind == BlockerKind.NATIVE_MODULE }) {
                say("   重编译原生模块（arm64）…")
                val rb = sh("(command -v make >/dev/null || apk add --no-cache python3 make g++ >/dev/null 2>&1); npm rebuild --registry=$reg 2>&1 | tail -n 8", 420_000)
                if (rb.exitCode == 0) say("   原生模块重编译完成") else {
                    say("   原生模块重编译失败")
                    missing += "部分原生模块无法为 arm64 编译"
                }
            }
            // Desktop deps resolve to the shims, not the real packages.
            sh("for m in electron robotjs iohook uiohook-napi active-win screenshot-desktop; do [ -d node_modules/\$m ] && rm -rf node_modules/\$m; done; true", 30_000)
            say("④ 冒烟测试：加载入口 ${a.nodeEntry}")
            val smoke = sh("timeout 25 node -e \"require(require('path').resolve(process.argv[1])); console.log('MIBOT_SMOKE_OK')\" ${shq(a.nodeEntry)} 2>&1 | tail -n 12", 60_000)
            smokeOk = smoke.output.contains("MIBOT_SMOKE_OK")
            say(smoke.output.trim().lines().takeLast(6).joinToString("\n") { "   $it" })
            if (!smokeOk) {
                val m = Regex("Cannot find module '([^']+)'").find(smoke.output)?.groupValues?.get(1)
                return@withContext done(ConvState.FAIL, missing,
                    if (m != null) "入口加载失败：缺少模块 $m${if (m.contains("dsh", true)) "（DSH 宿主模块，Mibot 尚未提供）" else ""}" else "入口加载失败（见日志）")
            }
        } else if (a.pyEntry != null) {
            val idx = pickMirror(listOf("https://pypi.tuna.tsinghua.edu.cn/simple/", "https://pypi.org/simple/"))
            val reqs = a.requirements.filter { r -> a.desktopOnlyPy.none { it == r } }
            say("③ 安装 Python 依赖（镜像 ${URL(idx).host}，跳过桌面专用 ${a.desktopOnlyPy.size} 个）…")
            val install = if (reqs.isEmpty()) "true" else "pip install --no-cache-dir --break-system-packages -i $idx " + reqs.joinToString(" ") { shq(it) } + " 2>&1 | tail -n 10"
            val inst = sh("(command -v python3 >/dev/null || apk add --no-cache python3 py3-pip >/dev/null 2>&1) && (command -v pip >/dev/null || apk add --no-cache py3-pip >/dev/null 2>&1) && $install", 420_000)
            say(inst.output.trim().lines().takeLast(8).joinToString("\n") { "   $it" })
            if (inst.exitCode != 0) return@withContext done(ConvState.FAIL, missing, "依赖安装失败（退出码 ${inst.exitCode}）")
            say("④ 冒烟测试：编译 ${a.pyEntry}")
            val smoke = sh("timeout 25 python3 -m py_compile ${shq(a.pyEntry)} && echo MIBOT_SMOKE_OK", 60_000)
            smokeOk = smoke.output.contains("MIBOT_SMOKE_OK")
            if (!smokeOk) return@withContext done(ConvState.FAIL, missing, "Python 入口编译失败：" + smoke.output.trim().takeLast(160))
        }
        say("⑤ 结果")
        val state = if (smokeOk && missing.isEmpty()) ConvState.OK else ConvState.PARTIAL
        done(state, missing, null)
    }
}
