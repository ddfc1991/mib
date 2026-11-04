package com.openminis.app.mibot.vault

import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import com.openminis.app.auth.OAuthCallbackServer
import com.openminis.app.auth.OAuthForegroundGate
import com.openminis.app.auth.OAuthTabLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [fork/mibot-v4] 自动发现并连接.
 *
 * Discovery reads only sources the user owns or grants:
 *  - Android AccountManager: accounts visible to Mibot, plus any the user
 *    picks in the system account chooser (newChooseAccountIntent);
 *  - installed apps matched against the services catalog (manifest <queries>);
 *  - sites already signed in inside Mibot's own WebView cookie store.
 * Nothing reads other apps' private data.
 *
 * The queue then connects one service at a time with the best method:
 * OpenRouter OAuth/PKCE; reusing an existing built-in-browser session; or the
 * service's own web login / key page in the built-in browser, where the user
 * types passwords, codes and solves captchas themselves (never bypassed) and a
 * freshly shown API key or the resulting session cookie is captured into the
 * Keystore-encrypted vault. Status/balance + check-in run right after.
 */
object VaultConnect {
    private const val TAG = "MibotConnect"

    // --------------------------------------------------------------- discovery

    enum class Source { APP, SYSTEM_ACCOUNT, BROWSER, CATALOG }

    data class Candidate(
        val service: VaultService,
        val sources: Set<Source>,
        val username: String? = null,
    )

    /** Account types of the system AccountManager mapped to catalog ids. */
    private val accountTypes = mapOf(
        "com.google" to "google",
        "com.microsoft.workaccount" to "outlook",
        "com.microsoft.office.outlook.USER_ACCOUNT" to "outlook",
        "com.github" to "github",
        "org.telegram.messenger" to "telegram",
        "com.twitter.android.auth.login" to "x",
        "com.dropbox.android.account" to "dropbox",
        "com.tencent.mm.account" to "wechat",
        "com.tencent.mobileqq.account" to "qq",
    )

    fun browserHasLogin(service: VaultService): Boolean {
        val cm = runCatching { CookieManager.getInstance() }.getOrNull() ?: return false
        return service.domains.any { d ->
            val c = cm.getCookie("https://$d").orEmpty()
            c.split(';').count { it.contains('=') } >= 2 && SESSION_HINTS.any { h -> c.contains(h, ignoreCase = true) }
        }
    }

    private val SESSION_HINTS = listOf("session", "sess", "token", "auth", "sid", "_u", "login", "jwt", "uid", "ssid", "passport")

    fun systemAccounts(ctx: Context): List<Pair<String, String>> = runCatching {
        AccountManager.get(ctx).accounts.map { it.type to it.name }
    }.getOrDefault(emptyList())

    /** Everything discoverable right now, best evidence first; connected / ignored services left out. */
    fun discover(ctx: Context, extraAccounts: List<Pair<String, String>> = emptyList()): List<Candidate> {
        val services = VaultServices.all(ctx)
        val connected = VaultServices.groups(ctx, VaultStore.entries(ctx).value).mapNotNull { it.service?.id }.toSet()
        val ignored = VaultServices.ignored(ctx)
        val pm = ctx.packageManager
        val users = HashMap<String, String>()
        for ((type, name) in systemAccounts(ctx) + extraAccounts) {
            val id = accountTypes[type] ?: continue
            users.putIfAbsent(id, name)
            if (id == "google") { users.putIfAbsent("gmail", name) }
        }
        val out = mutableListOf<Candidate>()
        for (s in services) {
            if (s.id in connected || s.id in ignored) continue
            val src = mutableSetOf<Source>()
            if (s.packages.any { installed(pm, it) }) src.add(Source.APP)
            if (users.containsKey(s.id)) src.add(Source.SYSTEM_ACCOUNT)
            if (browserHasLogin(s)) src.add(Source.BROWSER)
            if (src.isNotEmpty()) out.add(Candidate(s, src, users[s.id]))
        }
        return out.sortedByDescending { c -> c.sources.size * 10 + if (c.service.cat == "ai") 5 else 0 }
    }

    private fun installed(pm: PackageManager, pkg: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)) else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------------ queue

    enum class Phase { WAITING, CONNECTING, NEEDS_YOU, CONNECTED, UNVERIFIED, IN_APP, FAILED, SKIPPED }

    /** [fork/mibot-v5b2] AUTO = recipe / OAuth (hidden WebView, key fetched); WEB = visible web login (cookie). */
    enum class Mode { AUTO, WEB }

    data class Task(
        val service: VaultService,
        val username: String? = null,
        val phase: Phase = Phase.WAITING,
        val detail: String = "",
        val entryId: String? = null,
        val mode: Mode = Mode.AUTO,
        /** True only when a key was really captured AND the probe passed. */
        val autoKey: Boolean = false,
    )

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runner: Job? = null

    /** [fork/mibot-v5b2] Service whose 连接方式 sheet should show (from + / deep links). */
    val sheet = MutableStateFlow<String?>(null)
    @Volatile private var appLaunch: Pair<String, Long>? = null

    fun requestSheet(id: String) { sheet.value = id }

    fun enqueue(ctx: Context, items: List<Pair<VaultService, String?>>, mode: Mode = Mode.AUTO) {
        val cur = _tasks.value.toMutableList()
        for ((s, u) in items) {
            val i = cur.indexOfFirst { it.service.id == s.id }
            if (i >= 0) { if (cur[i].phase in setOf(Phase.FAILED, Phase.SKIPPED, Phase.IN_APP, Phase.UNVERIFIED)) cur[i] = Task(s, u, mode = mode) } else cur.add(Task(s, u, mode = mode))
        }
        _tasks.value = cur
        ensureRunning(ctx.applicationContext)
    }

    /** Opens the provider's own app for sign-in; status is set when the user comes back. */
    fun openApp(ctx: Context, s: VaultService, pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
        appLaunch = s.id to System.currentTimeMillis()
        val cur = _tasks.value.toMutableList()
        cur.removeAll { it.service.id == s.id && it.phase != Phase.CONNECTED }
        cur.add(Task(s, phase = Phase.NEEDS_YOU, detail = tc(ctx, "已打开 ${s.appName ?: s.name}，登录后回到 Mibot", "Opened the app — come back after signing in")))
        _tasks.value = cur
        sheet.value = null
        runCatching { ctx.startActivity(intent) }
    }

    fun returnedFromApp(ctx: Context) {
        val (id, at) = appLaunch ?: return
        if (System.currentTimeMillis() - at < 1500) return
        appLaunch = null
        update(id) { t ->
            t.copy(phase = Phase.IN_APP, detail = tc(ctx, "已在 App 登录（Mibot 无法读取其凭证）", "Signed in in the app (Mibot can't read its credentials)") +
                if (t.service.needsKey) tc(ctx, " · 当模型用需要网页获取 Key", " · a model key still needs the web console") else "")
        }
    }

    fun retry(ctx: Context, id: String) {
        update(id) { it.copy(phase = Phase.WAITING, detail = "") }
        ensureRunning(ctx.applicationContext)
    }

    fun skip(id: String) {
        val t = _tasks.value.find { it.service.id == id } ?: return
        if (t.phase == Phase.NEEDS_YOU || t.phase == Phase.CONNECTING) AutoKey.userCancel()
        update(id) { it.copy(phase = Phase.SKIPPED, detail = "") }
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filterNot { it.phase in setOf(Phase.CONNECTED, Phase.SKIPPED, Phase.IN_APP) }
    }

    private fun update(id: String, f: (Task) -> Task) {
        _tasks.value = _tasks.value.map { if (it.service.id == id) f(it) else it }
    }

    private fun ensureRunning(app: Context) {
        if (runner?.isActive == true) return
        runner = scope.launch {
            while (true) {
                val next = _tasks.value.firstOrNull { it.phase == Phase.WAITING } ?: break
                runOne(app, next)
            }
        }
    }

    /** [fork/mibot-autokey-v2] Hard ceiling per task, so one stuck service never blocks the queue. */
    private const val TASK_TIMEOUT_MS = 16 * 60_000L

    private suspend fun runOne(app: Context, t: Task) {
        val s = t.service
        val finished = try {
            withTimeoutOrNull(TASK_TIMEOUT_MS) { runOneBounded(app, t); true }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Throwable) {
            Log.w(TAG, "${s.id}: ${e.message}")
            update(s.id) { it.copy(phase = Phase.FAILED, detail = e.message ?: e.javaClass.simpleName) }
            true
        }
        if (finished == null) {
            Log.w(TAG, "${s.id}: task timed out")
            update(s.id) { it.copy(phase = Phase.FAILED, detail = tc(app, "超时（16 分钟）已停止，可点重试；队列继续下一个", "Timed out after 16 min — stopped; the queue moves on")) }
        }
        // Never leave a row spinning after its run ended.
        _tasks.value.find { it.service.id == s.id }?.takeIf { it.phase == Phase.CONNECTING }?.let {
            update(s.id) { it.copy(phase = Phase.FAILED, detail = it.detail.ifBlank { tc(app, "连接中断", "Connection interrupted") }) }
        }
    }

    private suspend fun runOneBounded(app: Context, t: Task) {
        val s = t.service
        update(s.id) { it.copy(phase = Phase.CONNECTING, detail = tc(app, "连接中…", "Connecting…")) }
        try {
            val recipe = if (t.mode == Mode.AUTO && s.auth == "api_key") KeyRecipes.forService(app, s) else null
            val (secret, kind, via) = when {
                t.mode == Mode.AUTO && s.auth == "oauth_openrouter" -> {
                    update(s.id) { it.copy(phase = Phase.NEEDS_YOU, detail = tc(app, "在打开的页面里点「授权」", "Tap Authorize on the page that opened")) }
                    Triple(openRouterPkce(app), "api_key", "oauth")
                }
                recipe != null -> when (val r = AutoKey.fetchKey(app, s, recipe) { step -> update(s.id) { it.copy(phase = Phase.CONNECTING, detail = step) } }) {
                    is AutoKey.Result.Key -> Triple(r.key, "api_key", r.via)
                    is AutoKey.Result.Failed -> error(r.reason)
                    is AutoKey.Result.Handoff -> { update(s.id) { it.copy(phase = Phase.NEEDS_YOU, detail = r.reason) }; return }
                    is AutoKey.Result.Cancelled -> { update(s.id) { it.copy(phase = Phase.SKIPPED, detail = tc(app, "已取消", "Cancelled")) }; return }
                    is AutoKey.Result.Cookies -> Triple(r.cookie, "cookie", "web")
                }
                else -> when (val r = AutoKey.webLogin(app, s)) {
                    is AutoKey.Result.Cookies -> Triple(r.cookie, "cookie", "web")
                    is AutoKey.Result.Key -> Triple(r.key, "api_key", r.via)
                    is AutoKey.Result.Failed -> error(r.reason)
                    is AutoKey.Result.Handoff -> { update(s.id) { it.copy(phase = Phase.NEEDS_YOU, detail = r.reason) }; return }
                    is AutoKey.Result.Cancelled -> { update(s.id) { it.copy(phase = Phase.SKIPPED, detail = tc(app, "已取消", "Cancelled")) }; return }
                }
            }
            if (secret.isBlank()) error(tc(app, "没有拿到登录凭据", "No credential captured"))
            update(s.id) { it.copy(phase = Phase.CONNECTING, detail = tc(app, "保存并探测…", "Saving and probing…")) }
            val entry = save(app, s, t.username, secret, kind)
            VaultStore.log(app, entry, "user", "auto-connect ($kind via $via)", "ok")
            if (kind == "api_key") {
                val pr = AutoKey.probe(s, secret)
                if (pr.ok == false) VaultStore.updateStatus(app, entry.id) { it.copy(error = "HTTP 401 · " + pr.detail, updatedAt = System.currentTimeMillis()) }
                val viaZh = when (via) { "clipboard" -> "复制按钮"; "clipboard-native" -> "剪贴板"; "paste" -> "粘贴"; "network" -> "页面接口"; "oauth" -> "OAuth"; else -> "页面" }
                when (pr.ok) {
                    true -> update(s.id) { it.copy(phase = Phase.CONNECTED, autoKey = true, entryId = entry.id, detail = tc(app, "已自动获取 Key · 来自$viaZh · ${pr.detail}", "Key fetched automatically · ${pr.detail}")) }
                    false -> update(s.id) { it.copy(phase = Phase.FAILED, entryId = entry.id, detail = tc(app, "读到的 Key 没通过探测：${pr.detail}", "Captured key failed the probe: ${pr.detail}")) }
                    null -> update(s.id) { it.copy(phase = Phase.UNVERIFIED, entryId = entry.id, detail = tc(app, "已保存 Key · ${pr.detail}", "Key saved · ${pr.detail}")) }
                }
                runCatching { VaultSites.refreshStatus(app, entry, "user") }
                return
            }
            val st = runCatching { VaultSites.refreshStatus(app, entry, "user") }.getOrDefault("")
            val ck = runCatching { VaultSites.checkin(app, entry, "user") }.getOrNull()
            val summary = buildString {
                append(tc(app, "已保存网站登录", "Web login saved"))
                VaultStore.get(app, entry.id)?.status?.let { stt ->
                    listOfNotNull(stt.balance, stt.credits, stt.points, stt.quota).firstOrNull()?.let { append(" · ").append(it) }
                }
                if (ck?.state == "ok") append(tc(app, " · 已签到", " · checked in"))
            }
            Log.i(TAG, "${s.id}: connected ($kind) status=$st")
            update(s.id) { it.copy(phase = Phase.CONNECTED, detail = summary, entryId = entry.id) }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Throwable) {
            Log.w(TAG, "${s.id}: ${e.message}")
            update(s.id) { it.copy(phase = Phase.FAILED, detail = e.message ?: e.javaClass.simpleName) }
        }
    }

    fun cookiesFor(s: VaultService): String {
        val cm = CookieManager.getInstance()
        return s.domains.mapNotNull { d -> cm.getCookie("https://$d")?.takeIf { it.isNotBlank() } }.distinct().joinToString("; ")
    }

    private fun save(app: Context, s: VaultService, username: String?, secret: String, kind: String): VaultEntry {
        val existing = VaultStore.entries(app).value.firstOrNull { (it.serviceId == s.id || s.matchesSite(it.site)) && it.kind == kind && (username == null || it.username == username) }
        val base = existing ?: VaultEntry(
            id = VaultStore.newId(),
            site = s.domains.firstOrNull() ?: s.name,
            username = username.orEmpty(),
            category = VaultServices.legacyCategory(s.cat),
            permission = VaultPermission.ASK,
            manifestId = s.manifest,
        )
        return VaultStore.upsert(app, base.copy(serviceId = s.id, kind = kind, status = base.status?.copy(error = null)), secret)
    }

    // ------------------------------------------------------- OpenRouter PKCE

    private suspend fun openRouterPkce(app: Context): String {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
        val verifier = b64(bytes)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        var server: OAuthCallbackServer? = null
        val code = try {
            withTimeout(10 * 60_000L) {
                suspendCancellableCoroutine<String> { cont ->
                    val srv = OAuthCallbackServer(3000, listOf(3001, 3002), onError = { err ->
                        if (cont.isActive) cont.resumeWithException(err.toException(app))
                    }) { c, _ -> if (cont.isActive) cont.resume(c) }
                    server = srv
                    srv.start()
                    cont.invokeOnCancellation { srv.stop() }
                    val uri = Uri.parse("https://openrouter.ai/auth").buildUpon()
                        .appendQueryParameter("callback_url", "http://localhost:${srv.boundPort}/callback")
                        .appendQueryParameter("code_challenge", challenge)
                        .appendQueryParameter("code_challenge_method", "S256")
                        .build()
                    OAuthTabLauncher.launch(app, uri, newTask = true)
                }
            }
        } finally {
            server?.stop()
        }
        OAuthForegroundGate.awaitForeground(TAG)
        return withContext(Dispatchers.IO) {
            val conn = (URL("https://openrouter.ai/api/v1/auth/keys").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 20_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("HTTP-Referer", "https://github.com/dl1991/OpenMinis")
                setRequestProperty("X-Title", "Mibot")
            }
            try {
                conn.outputStream.use { it.write(JSONObject().put("code", code).put("code_verifier", verifier).put("code_challenge_method", "S256").toString().toByteArray()) }
                val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (conn.responseCode !in 200..299) error("OpenRouter HTTP ${conn.responseCode}")
                JSONObject(body).getString("key")
            } finally { conn.disconnect() }
        }
    }

    // ----------------------------------------------------- key capture helper

    /** Finds a freshly shown API key in page text, by the service's key pattern. */
    fun findKey(s: VaultService, pageText: String): String? {
        val rx = s.keyRegex ?: return null
        return runCatching { Regex("(?<![A-Za-z0-9_\\-])$rx(?![A-Za-z0-9_\\-])").find(pageText)?.value }.getOrNull()
            ?.takeIf { it.length >= 20 && !it.contains("•") && !it.contains("*") && !it.contains("...") }
    }

    private fun tc(ctx: Context, zh: String, en: String) = com.openminis.app.mibot.design.tc(ctx, zh, en)
}
