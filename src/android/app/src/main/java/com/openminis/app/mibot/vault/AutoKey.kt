package com.openminis.app.mibot.vault

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import com.openminis.app.mibot.gateway.GatewayNet
import com.openminis.app.mibot.mail.MailCore
import com.openminis.app.mibot.mail.Mailbox
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume

/**
 * [fork/mibot-v5b2] Per-provider "get me an API key" recipes, as versioned data
 * (assets/mibot/key_recipes.json; a user/agent override with a higher "v" in
 * filesDir/vault/key_recipes.json wins per provider). URL patterns + DOM
 * selectors + button labels; defaults cover the common console layouts.
 */
object KeyRecipes {
    data class Recipe(val id: String, val version: Int, val start: String, val keyRegex: String, val json: JSONObject, val hint: String?)

    private fun userFile(ctx: Context) = File(ctx.applicationContext.filesDir, "vault/key_recipes.json")

    private fun asset(ctx: Context): JSONObject =
        runCatching { JSONObject(ctx.assets.open("mibot/key_recipes.json").bufferedReader().use { it.readText() }) }.getOrDefault(JSONObject())

    fun version(ctx: Context): Int = maxOf(asset(ctx).optInt("version"), runCatching { JSONObject(userFile(ctx).readText()).optInt("version") }.getOrDefault(0))

    fun defaults(ctx: Context): JSONObject = asset(ctx).optJSONObject("defaults") ?: JSONObject()

    fun forService(ctx: Context, s: VaultService): Recipe? {
        if (s.id == AutoKeyDemo.ID && AutoKeyDemo.enabled) return build(ctx, s, AutoKeyDemo.recipe())
        val a = asset(ctx).optJSONObject("recipes")?.optJSONObject(s.id)
        val u = runCatching { JSONObject(userFile(ctx).readText()).optJSONObject("recipes")?.optJSONObject(s.id) }.getOrNull()
        val r = listOfNotNull(a, u).maxByOrNull { it.optInt("v") } ?: return null
        return build(ctx, s, r)
    }

    /** A login-only recipe (no key) for plain web services: same detection JS. */
    fun generic(ctx: Context, s: VaultService): Recipe = build(ctx, s, JSONObject().put("v", 0).put("start", s.loginUrl ?: s.url).put("keyRegex", "(?!x)x"))

    private fun build(ctx: Context, s: VaultService, r: JSONObject): Recipe {
        val merged = JSONObject(defaults(ctx).toString())
        r.keys().forEach { k ->
            val v = r.get(k)
            // Lists in a recipe are tried first, then the defaults.
            if (v is JSONArray && merged.optJSONArray(k) != null) {
                val out = JSONArray(); for (i in 0 until v.length()) out.put(v.get(i))
                val d = merged.getJSONArray(k); for (i in 0 until d.length()) out.put(d.get(i))
                merged.put(k, out)
            } else merged.put(k, v)
        }
        val start = r.optString("start").ifBlank { s.webStart }
        merged.put("start", start)
        merged.put("keyRegex", r.optString("keyRegex").ifBlank { s.keyRegex ?: "(?!x)x" })
        merged.put("hosts", JSONArray(s.domains))
        if (!merged.has("keysMatch")) merged.put("keysMatch", deriveMatch(start))
        return Recipe(s.id, r.optInt("v"), start, merged.getString("keyRegex"), merged, r.optString("hint").ifBlank { null })
    }

    private fun deriveMatch(url: String): String {
        val u = android.net.Uri.parse(url)
        val seg = u.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() } ?: u.queryParameterNames.firstOrNull() ?: return ".*"
        return Regex.escape(seg).replace("\\Q", "").replace("\\E", "").replace(Regex("[-_]"), ".?")
    }

    /** Installs a recipes document (agent / CLI / future online update). */
    fun install(ctx: Context, json: String): String? {
        val o = runCatching { JSONObject(json) }.getOrElse { return "invalid JSON" }
        if (o.optInt("version") < 1 || o.optJSONObject("recipes") == null) return "needs version >= 1 and recipes{}"
        userFile(ctx).apply { parentFile?.mkdirs() }.writeText(o.toString(2))
        return null
    }
}

/**
 * The automation engine: runs a recipe in the shared connect WebView. The
 * WebView stays HIDDEN while it signs in, opens the key page, clicks "create
 * key" (name "Mibot") and reads the key (DOM, network response or the copy
 * button). It only pops up when a human is needed (captcha / SMS / QR / a
 * login it has no saved password for) and hides again once that is done.
 * Email codes are fetched from a temp mailbox, SMS codes from the user's own
 * SMS notifications. It never solves captchas, never claims success without a
 * real captured key.
 */
object AutoKey {
    enum class View { HIDDEN, VERIFY, ASSIST }
    enum class Mode { KEY, LOGIN }

    data class Host(
        val active: Boolean = false,
        val service: VaultService? = null,
        val mode: Mode = Mode.KEY,
        val view: View = View.HIDDEN,
        val title: String = "",
        val hint: String = "",
        val highlight: Boolean = false,
        val url: String = "",
        val progress: Int = 0,
        val error: String? = null,
        val loadUrl: String = "",
        val canSaveLogin: Boolean = false,
    )

    private val _host = MutableStateFlow(Host())
    val host: StateFlow<Host> = _host.asStateFlow()
    @Volatile internal var web: WebView? = null
    @Volatile private var cancelled = false
    @Volatile private var saveLoginRequested = false

    sealed class Result {
        data class Key(val key: String, val via: String) : Result()
        data class Cookies(val cookie: String) : Result()
        data class Failed(val reason: String) : Result()
        object Cancelled : Result()
    }

    fun userCancel() { cancelled = true }
    fun userSaveLogin() { saveLoginRequested = true }
    fun onProgress(p: Int) { _host.value = _host.value.copy(progress = p) }
    fun onUrl(u: String) { _host.value = _host.value.copy(url = u) }
    fun onPageError(desc: String?) {
        val h = _host.value
        _host.value = if (desc == null) h.copy(error = null) else h.copy(error = desc, view = if (h.view == View.HIDDEN) View.ASSIST else h.view,
            title = if (h.view == View.HIDDEN) "页面没有打开" else h.title)
    }

    private fun show(view: View, title: String, hint: String, highlight: Boolean = false) {
        _host.value = _host.value.copy(view = view, title = title, hint = hint, highlight = highlight)
    }

    private suspend fun awaitWeb(): WebView? = withTimeoutOrNull(15_000) { while (web == null) delay(150); web }

    private suspend fun eval(w: WebView, js: String): JSONObject? = withTimeoutOrNull(6000) {
        suspendCancellableCoroutine<String?> { c -> runCatching { w.evaluateJavascript(js) { raw -> if (c.isActive) c.resume(raw) } }.onFailure { if (c.isActive) c.resume(null) } }
    }?.let { raw -> runCatching { JSONObject(JSONArray("[$raw]").getString(0)) }.getOrNull() }

    private fun load(w: WebView, url: String) {
        _host.value = _host.value.copy(loadUrl = url, error = null)
        w.loadUrl(url)
    }

    private fun now() = System.currentTimeMillis()

    /** Saved login (username + password) for [s] from the vault, if any. */
    fun savedLogin(ctx: Context, s: VaultService): Pair<String, String>? {
        val rx = s.keyRegex?.let { runCatching { Regex(it) }.getOrNull() }
        val e = VaultStore.entries(ctx).value.firstOrNull { e ->
            (e.serviceId == s.id || s.matchesSite(e.site)) && e.username.isNotBlank() && e.hasSecret &&
                (e.kind == "password" || (e.kind == null && VaultStore.revealSecret(ctx, e)?.let { sec -> sec.length < 64 && (rx == null || !rx.matches(sec)) } == true))
        } ?: return null
        val pass = VaultStore.revealSecret(ctx, e) ?: return null
        return e.username to pass
    }

    /**
     * Fetches an API key for [s] with recipe [r]. [onStep] reports progress
     * text for the task row.
     */
    suspend fun fetchKey(app: Context, s: VaultService, r: KeyRecipes.Recipe, onStep: (String) -> Unit): Result {
        cancelled = false
        _host.value = Host(active = true, service = s, mode = Mode.KEY, view = View.HIDDEN, title = "连接 ${s.name}", hint = r.hint ?: "", url = r.start)
        try {
            val w = awaitWeb() ?: return Result.Failed(tc(app, "连接页面没有打开，请留在「自动发现并连接」页", "Keep the Discover & connect page open"))
            val cred = savedLogin(app, s)
            load(w, r.start)
            onStep(tc(app, "后台打开 ${hostOf(r.start)}…", "Opening ${hostOf(r.start)} in the background…"))
            val t0 = now(); var lastMove = now(); var lastSig = ""; var fills = 0; var navs = 0; var noCreate = 0
            var mailbox: Mailbox? = null; var mailSent = 0L; var smsSince = 0L; var mailTries = 0
            while (true) {
                if (cancelled) return Result.Cancelled
                delay(1000)
                val st = eval(w, AutoKeyJs.script(r.json, "detect")) ?: continue
                firstKey(st)?.let { return it }
                val state = st.optString("state"); val url = st.optString("url")
                val sig = "$state|$url|${st.optString("acted")}"
                if (sig != lastSig) { lastSig = sig; lastMove = now() }
                val view = _host.value.view
                when (state) {
                    "loading", "unknown" -> {}
                    "verify" -> if (view != View.VERIFY) show(View.VERIFY, "需要你验证 · ${s.name}", "完成验证码 / 滑块 / 扫码后会自动继续，弹窗会自己收起")
                    "email_code" -> {
                        val box = mailbox
                        if (box == null) show(View.VERIFY, "需要你输入邮箱验证码 · ${s.name}", "这个账号的邮箱不是 Mibot 的临时邮箱，请输入邮件里的验证码")
                        else {
                            onStep(tc(app, "等待 ${box.address} 收到验证码…", "Waiting for the code in ${box.address}…"))
                            val f = MailCore.waitCode(app, box, s.domains.firstOrNull()?.substringBefore('/'), 120)
                            if (f?.code != null) { eval(w, AutoKeyJs.script(r.json, "code", f.code)); onStep(tc(app, "已自动填入邮件验证码", "Email code filled")); delay(2500) }
                            else if (f?.link != null) { load(w, f.link); delay(2500) }
                            else show(View.VERIFY, "没收到邮件验证码 · ${s.name}", "2 分钟内 ${box.address} 没有收到验证码，请检查页面", true)
                        }
                    }
                    "sms_code" -> {
                        if (smsSince == 0L) smsSince = now()
                        val code = com.openminis.app.mibot.settings.SmsCodes.latest(smsSince, s.name)
                        if (code != null) { eval(w, AutoKeyJs.script(r.json, "code", code.code)); onStep(tc(app, "已从你的短信通知填入验证码", "SMS code filled from your notifications")); smsSince = now(); delay(2500) }
                        else if (now() - smsSince > 60_000 || !com.openminis.app.offload.MinisNotificationListenerService.isEnabled(app)) {
                            if (view == View.HIDDEN) show(View.VERIFY, "需要你输入短信验证码 · ${s.name}", "没有在 60 秒内读到短信验证码（需要「通知使用权」），请手动输入")
                        } else onStep(tc(app, "等待短信验证码…", "Waiting for the SMS code…"))
                    }
                    "login" -> {
                        if (view == View.VERIFY || view == View.ASSIST) {
                            // The user is on it.
                        } else if (cred != null && fills < 2) {
                            eval(w, AutoKeyJs.script(r.json, "fill", cred.first, cred.second)); fills++
                            onStep(tc(app, "自动填入已保存的账号…", "Filling the saved login…")); delay(2500)
                        } else if (st.optBoolean("emailEntry") && mailTries < 2 && TempMailPolicy.allowed(app)) {
                            val box = mailbox ?: runCatching { MailCore.create(app, null, s.id, exclude = TempMailPolicy.rejected(s.id)) }.getOrNull()
                            if (box == null) show(View.VERIFY, "需要你登录 · ${s.name}", "没有可用的临时邮箱平台，请手动登录")
                            else {
                                mailbox = box; mailTries++
                                onStep(tc(app, "用临时邮箱 ${box.address} 登录/注册…", "Using temp mailbox ${box.address}…"))
                                eval(w, AutoKeyJs.script(r.json, "email", box.address)); mailSent = now(); delay(3500)
                                val after = eval(w, AutoKeyJs.script(r.json, "detect"))
                                if (after?.optBoolean("disposableRejected") == true) {
                                    TempMailPolicy.markRejected(s.id, box.provider)
                                    show(View.VERIFY, "${s.name} 拒绝了临时邮箱", "这个服务不接受一次性邮箱域名（${box.domain}）。请用你自己的邮箱或手机号登录。", true)
                                }
                            }
                        } else show(View.VERIFY, "需要你登录 · ${s.name}", if (cred == null) "Mibot 没有这个服务的已保存密码，请登录；登录后自动继续" else "自动登录没有通过，请完成登录")
                    }
                    "in" -> {
                        if (view == View.VERIFY) { show(View.HIDDEN, "连接 ${s.name}", ""); onStep(tc(app, "验证通过，继续获取 Key…", "Verified — continuing…")) }
                        mailbox?.let { MailCore.link(app, it, s.id) }
                        if (!st.optBoolean("onKeys")) {
                            when {
                                navs < 2 -> { onStep(tc(app, "打开 API Key 页面…", "Opening the API key page…")); load(w, r.start); navs++; delay(1500) }
                                navs < 5 -> { eval(w, AutoKeyJs.script(r.json, "nav")); navs++ }
                                view != View.ASSIST -> show(View.ASSIST, "需要你操作 · ${s.name}", "没找到 API Key 页面（页面可能改版）。请打开「API Keys」并新建一个，出现后会自动保存。", true)
                            }
                        } else if (view != View.ASSIST) {
                            val a = eval(w, AutoKeyJs.script(r.json, "create"))?.optString("acted").orEmpty()
                            when (a) {
                                "create" -> onStep(tc(app, "新建 Key「Mibot」…", "Creating key \"Mibot\"…"))
                                "confirm" -> onStep(tc(app, "确认创建…", "Confirming…"))
                                "copy" -> onStep(tc(app, "读取 Key…", "Reading the key…"))
                                "no-create" -> noCreate++
                                "exhausted" -> show(View.ASSIST, "需要你操作 · ${s.name}", "已尝试新建 Key，但没在页面上读到它。请在页面上复制 Key，复制后会自动保存。", true)
                            }
                            if (noCreate >= 6) show(View.ASSIST, "需要你操作 · ${s.name}", "没找到「创建 Key」按钮（页面可能改版，配方 v${r.version}）。请手动新建，出现后自动保存。", true)
                        }
                    }
                    "elsewhere" -> if (view == View.HIDDEN && now() - lastMove > 15_000) show(View.VERIFY, "需要你操作 · ${s.name}", "页面跳到了 ${st.optString("host")}，请完成登录")
                }
                if (_host.value.view == View.HIDDEN && now() - lastMove > 45_000) show(View.ASSIST, "需要你操作 · ${s.name}", "自动步骤 45 秒没有进展，请在页面上继续；Key 出现后会自动保存。", true)
                if (now() - t0 > 15 * 60_000L) return Result.Failed(tc(app, "超时（15 分钟）", "Timed out"))
                if (mailSent > 0 && now() - mailSent > 200_000 && state != "in") mailSent = 0
            }
        } finally {
            _host.value = Host()
        }
    }

    private fun firstKey(st: JSONObject): Result.Key? {
        val keys = st.optJSONArray("keys") ?: return null
        if (keys.length() == 0) return null
        val o = keys.getJSONObject(keys.length() - 1)
        return Result.Key(o.getString("k"), o.optString("via"))
    }

    /** Plain web login (no key): visible from the start, captures the session cookie. */
    suspend fun webLogin(app: Context, s: VaultService): Result {
        cancelled = false; saveLoginRequested = false
        val r = KeyRecipes.generic(app, s)
        _host.value = Host(active = true, service = s, mode = Mode.LOGIN, view = View.ASSIST, title = "登录 ${s.name}",
            hint = "登录后自动保存登录状态（验证码、短信码、二次验证由你完成）", url = r.start, canSaveLogin = true)
        try {
            val w = awaitWeb() ?: return Result.Failed("connect page not open")
            load(w, r.start)
            var stable = 0
            while (true) {
                if (cancelled) return Result.Cancelled
                delay(1500)
                if (saveLoginRequested) {
                    saveLoginRequested = false
                    CookieManager.getInstance().flush()
                    val ck = VaultConnect.cookiesFor(s)
                    if (ck.isNotBlank()) return Result.Cookies(ck)
                    show(View.ASSIST, "登录 ${s.name}", "还没有检测到登录（没有会话 Cookie）", true)
                }
                val st = eval(w, AutoKeyJs.script(r.json, "detect"))
                val signedIn = st?.optString("state") == "in" && VaultConnect.browserHasLogin(s)
                stable = if (signedIn) stable + 1 else 0
                if (stable >= 2) { CookieManager.getInstance().flush(); return Result.Cookies(VaultConnect.cookiesFor(s)) }
            }
        } finally { _host.value = Host() }
    }

    /** Real probe of a captured key against the provider's API (GET models / whoami). */
    data class Probe(val ok: Boolean?, val detail: String)

    suspend fun probe(s: VaultService, key: String): Probe {
        if (s.id == AutoKeyDemo.ID && AutoKeyDemo.enabled) return Probe(true, "演示探测通过（本机）")
        val url = s.probeUrl ?: return Probe(null, "该服务没有探测接口")
        val headers = mutableMapOf(s.probeHeader to s.probePrefix + key)
        if (s.id == "claude") headers["anthropic-version"] = "2023-06-01"
        val o = GatewayNet.request(null, url, headers = headers)
        return when (o.kind) {
            GatewayNet.Kind.OK -> Probe(true, "探测通过 · ${o.latencyMs}ms")
            GatewayNet.Kind.AUTH -> Probe(false, "Key 被拒绝（${o.detail.take(60)}）")
            GatewayNet.Kind.QUOTA -> Probe(true, "Key 有效 · 余额不足")
            else -> Probe(null, "未能验证：${o.detail.take(80)}")
        }
    }

    private fun hostOf(u: String) = runCatching { android.net.Uri.parse(u).host }.getOrNull() ?: u
    private fun tc(ctx: Context, zh: String, en: String) = com.openminis.app.mibot.design.tc(ctx, zh, en)
}

/** Whether recipes may use temp mailboxes, and which platforms a service rejected. */
object TempMailPolicy {
    private val rejected = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    fun allowed(ctx: Context) = ctx.getSharedPreferences("mibot_mail", Context.MODE_PRIVATE).getBoolean("recipes_use_temp", true)
    fun setAllowed(ctx: Context, on: Boolean) = ctx.getSharedPreferences("mibot_mail", Context.MODE_PRIVATE).edit().putBoolean("recipes_use_temp", on).apply()
    fun rejected(service: String): Set<String> = rejected[service] ?: emptySet()
    fun markRejected(service: String, provider: String) { rejected.getOrPut(service) { mutableSetOf() }.add(provider) }
}

/**
 * Debug/CI only: a local "console" (served by the connect WebView itself for
 * host demo-console.mibot.invalid) that walks the real engine through
 * login → captcha (needs you) → create key → capture, so the popup and the
 * success state can be screenshotted. Labelled 演示 in the UI.
 */
object AutoKeyDemo {
    const val ID = "mibot-demo"
    const val HOST = "demo-console.mibot.invalid"
    @Volatile var enabled = false
    @Volatile var autoStart = false

    val service: VaultService get() = VaultService(
        id = ID, name = "演示控制台（CI）", sub = "本机演示页，验证自动获取 Key 流程", cat = "ai", domains = listOf(HOST), packages = emptyList(),
        auth = "api_key", url = "https://$HOST/console/api-keys", manifest = null, free = null, keyRegex = "sk-mibotdemo[0-9a-f]{32}",
        logo = null, color = "#6E7784", keysUrl = "https://$HOST/console/api-keys",
    )

    fun recipe(): JSONObject = JSONObject().put("v", 1).put("start", "https://$HOST/console/api-keys").put("keyRegex", "sk-mibotdemo[0-9a-f]{32}")

    fun seed(ctx: Context) {
        if (VaultStore.entries(ctx).value.any { it.serviceId == ID }) return
        VaultStore.upsert(ctx, VaultEntry(VaultStore.newId(), HOST, "demo@mibot.invalid", "AI 服务", serviceId = ID, kind = "password"), "demo-password")
    }

    fun html(): String = """<!doctype html><html lang="zh"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>演示控制台</title>
<style>body{font-family:sans-serif;margin:0;background:#f4f5f7;color:#222}header{background:#263146;color:#fff;padding:14px 18px;font-weight:600}
main{padding:18px}.card{background:#fff;border-radius:10px;padding:16px;margin-bottom:14px;box-shadow:0 1px 3px rgba(0,0,0,.08)}input{width:100%;box-sizing:border-box;padding:10px;margin:6px 0;border:1px solid #ccd;border-radius:6px}
button{padding:10px 16px;border:0;border-radius:6px;background:#3b6cf6;color:#fff;font-size:15px}.captcha-panel{border:2px dashed #e0a23b;padding:16px;border-radius:10px;background:#fff8ec}
.bar{height:36px;background:#eee;border-radius:18px;position:relative;margin-top:10px}.knob{position:absolute;left:0;top:0;width:60px;height:36px;border-radius:18px;background:#e0a23b;transition:left 6s linear}
[role=dialog]{position:fixed;left:16px;right:16px;top:30%;background:#fff;border-radius:12px;padding:18px;box-shadow:0 6px 30px rgba(0,0,0,.25)}code{word-break:break-all}</style></head>
<body><header>演示控制台 · Mibot CI（不是真实服务）</header><main id="m"></main><script>
var m=document.getElementById('m');function S(k,v){if(v===undefined)return sessionStorage.getItem(k);sessionStorage.setItem(k,v)}
function login(){m.innerHTML='<div class="card"><h3>登录</h3><form id="f"><input type="email" name="email" placeholder="邮箱"><input type="password" name="password" placeholder="密码"><button type="submit">登录</button></form></div>';
document.getElementById('f').onsubmit=function(e){e.preventDefault();captcha()}}
function captcha(){m.innerHTML='<div class="card captcha-panel"><h3>安全验证</h3><p>请完成验证：拖动滑块完成验证</p><div class="bar"><div class="knob" id="k"></div></div><p style="font-size:12px;color:#777">演示：人工验证约 9 秒（CI 自动模拟），Mibot 不会自己解验证码</p></div>';
setTimeout(function(){document.getElementById('k').style.left='calc(100% - 60px)'},300);setTimeout(function(){S('in','1');keys()},9000)}
function keys(){m.innerHTML='<div class="card"><h3>API Keys</h3><p>sk-demo••••••••a1b2 · default</p><button id="c">创建 API Key</button></div>';
document.getElementById('c').onclick=function(){var d=document.createElement('div');d.setAttribute('role','dialog');d.innerHTML='<h3>创建 API key</h3><input type="text" placeholder="名称"><button id="ok">创建</button>';document.body.appendChild(d);
document.getElementById('ok').onclick=function(){var h='';for(var i=0;i<32;i++)h+='0123456789abcdef'[Math.floor(Math.random()*16)];d.innerHTML='<h3>Key 已创建</h3><p>请立即复制，之后不再显示：</p><input readonly value="sk-mibotdemo'+h+'"><button>复制</button>'}}}
if(S('in'))keys();else login();</script></body></html>"""
}
