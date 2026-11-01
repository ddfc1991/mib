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
import kotlinx.coroutines.CompletableDeferred
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

    enum class Phase { WAITING, CONNECTING, NEEDS_YOU, CONNECTED, FAILED, SKIPPED }

    data class Task(
        val service: VaultService,
        val username: String? = null,
        val phase: Phase = Phase.WAITING,
        val detail: String = "",
        /** What the user is asked for while [phase] is NEEDS_YOU. */
        val ask: Ask? = null,
        val entryId: String? = null,
    )

    /** A web step the user completes in the built-in browser sheet. */
    data class Ask(val url: String, val hint: String, val captureKey: Boolean)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var runner: Job? = null
    @Volatile private var webResult: CompletableDeferred<WebOutcome>? = null

    sealed class WebOutcome {
        data class Key(val key: String) : WebOutcome()
        data class Cookies(val cookie: String) : WebOutcome()
        object Skip : WebOutcome()
        object Cancel : WebOutcome()
    }

    fun enqueue(ctx: Context, items: List<Pair<VaultService, String?>>) {
        val cur = _tasks.value.toMutableList()
        for ((s, u) in items) {
            val i = cur.indexOfFirst { it.service.id == s.id }
            if (i >= 0) { if (cur[i].phase in setOf(Phase.FAILED, Phase.SKIPPED)) cur[i] = Task(s, u) } else cur.add(Task(s, u))
        }
        _tasks.value = cur
        ensureRunning(ctx.applicationContext)
    }

    fun retry(ctx: Context, id: String) {
        update(id) { it.copy(phase = Phase.WAITING, detail = "", ask = null) }
        ensureRunning(ctx.applicationContext)
    }

    fun skip(id: String) {
        val t = _tasks.value.find { it.service.id == id } ?: return
        if (t.phase == Phase.NEEDS_YOU) webResult?.complete(WebOutcome.Skip)
        update(id) { it.copy(phase = Phase.SKIPPED, ask = null, detail = "") }
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filterNot { it.phase in setOf(Phase.CONNECTED, Phase.SKIPPED) }
    }

    /** Called by the built-in browser sheet. */
    fun deliverWeb(outcome: WebOutcome) { webResult?.complete(outcome) }

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

    private suspend fun runOne(app: Context, t: Task) {
        val s = t.service
        update(s.id) { it.copy(phase = Phase.CONNECTING, detail = tc(app, "连接中…", "Connecting…")) }
        try {
            val (secret, kind) = when {
                s.auth == "oauth_openrouter" -> {
                    update(s.id) { it.copy(phase = Phase.NEEDS_YOU, detail = tc(app, "在打开的页面里点「授权」", "Tap Authorize on the page that opened")) }
                    openRouterPkce(app) to "api_key"
                }
                browserHasLogin(s) && s.auth == "web" -> {
                    update(s.id) { it.copy(detail = tc(app, "复用内置浏览器的登录", "Reusing the built-in browser session")) }
                    cookiesFor(s) to "cookie"
                }
                else -> {
                    val captureKey = s.auth == "api_key"
                    val hint = if (captureKey)
                        tc(app, "登录后新建一个 API Key，出现在页面上会自动保存（验证码、短信码请你自己输入）", "Sign in and create an API key — it is saved as soon as it appears (you type any codes yourself)")
                    else tc(app, "请登录（验证码、短信码、二次验证由你完成），登录后自动保存", "Sign in (you complete any codes / 2FA) — saved automatically")
                    val d = CompletableDeferred<WebOutcome>()
                    webResult = d
                    update(s.id) { it.copy(phase = Phase.NEEDS_YOU, detail = hint, ask = Ask(s.url, hint, captureKey)) }
                    when (val r = d.await()) {
                        is WebOutcome.Key -> r.key to "api_key"
                        is WebOutcome.Cookies -> r.cookie to "cookie"
                        WebOutcome.Skip -> { update(s.id) { it.copy(phase = Phase.SKIPPED, ask = null, detail = "") }; return }
                        WebOutcome.Cancel -> { update(s.id) { it.copy(phase = Phase.FAILED, ask = null, detail = tc(app, "已取消", "Cancelled")) }; return }
                    }
                }
            }
            if (secret.isBlank()) error(tc(app, "没有拿到登录凭据", "No credential captured"))
            update(s.id) { it.copy(phase = Phase.CONNECTING, ask = null, detail = tc(app, "保存并检查状态…", "Saving and checking status…")) }
            val entry = save(app, s, t.username, secret, kind)
            VaultStore.log(app, entry, "user", "auto-connect ($kind)", "ok")
            val st = runCatching { VaultSites.refreshStatus(app, entry, "user") }.getOrDefault("")
            val ck = runCatching { VaultSites.checkin(app, entry, "user") }.getOrNull()
            val summary = buildString {
                append(tc(app, "已连接", "Connected"))
                if (kind == "cookie") append(tc(app, " · 有网站登录", " · web login"))
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
            update(s.id) { it.copy(phase = Phase.FAILED, ask = null, detail = e.message ?: e.javaClass.simpleName) }
        } finally {
            webResult = null
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
