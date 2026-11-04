package com.openminis.app.mibot.vault

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import com.openminis.app.mibot.gateway.GatewayNet
import com.openminis.app.mibot.mail.MailCore
import com.openminis.app.mibot.mail.Mailbox
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
        // [fork/mibot-autokey-v2] RecipeMerge: recipe lists first, then defaults; hosts = service domains + recipe hosts.
        val merged = RecipeMerge.merge(defaults(ctx), r, RecipeMerge.Svc(s.id, s.domains, s.keyRegex, s.webStart))
        return Recipe(s.id, r.optInt("v"), merged.getString("start"), merged.getString("keyRegex"), merged, r.optString("hint").ifBlank { null })
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
 *
 * [fork/mibot-autokey-v2] Every wait is bounded: the WebView is re-acquired
 * on each pass (it can be rebuilt after a renderer crash or destroyed when
 * the screen goes away), null evaluations are counted ([NullEvals]) and the
 * stall / 15-minute checks run on every pass. Steps have budgets
 * ([StepClock]); the create flow is the observation-driven [CreateFlow].
 * New page states: `challenge` (Cloudflare/WAF: wait, never reload), `qr`
 * (visible sheet asking the user to scan) and `google` (Google refuses
 * embedded sign-in: honest Custom Tab handoff).
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
        /** Google sign-in: offer the Custom Tab handoff for [handoffUrl]. */
        val handoff: Boolean = false,
        val handoffUrl: String = "",
    )

    private val _host = MutableStateFlow(Host())
    val host: StateFlow<Host> = _host.asStateFlow()
    @Volatile internal var web: WebView? = null
    /** Bumped when the renderer died: ConnectWebLayer rebuilds its WebView via key(webGen). */
    private val _webGen = MutableStateFlow(0)
    val webGen: StateFlow<Int> = _webGen.asStateFlow()
    /** OAuth popup (window.open) child WebView, shown over the page while open. */
    private val _popup = MutableStateFlow<WebView?>(null)
    val popup: StateFlow<WebView?> = _popup.asStateFlow()
    @Volatile private var cancelled = false
    @Volatile private var saveLoginRequested = false
    @Volatile private var handoffRequested = false
    /** Keys pushed by the document-start capture layer (any frame) via WebMessageListener. */
    private val pushed = java.util.concurrent.ConcurrentLinkedQueue<String>()

    sealed class Result {
        data class Key(val key: String, val via: String) : Result()
        data class Cookies(val cookie: String) : Result()
        data class Failed(val reason: String) : Result()
        /** The user moved the flow to the system browser (Google sign-in); nothing captured here. */
        data class Handoff(val reason: String) : Result()
        object Cancelled : Result()
    }

    const val RUN_CEILING_MS = 15 * 60_000L

    fun userCancel() { cancelled = true }
    fun userSaveLogin() { saveLoginRequested = true }
    fun onProgress(p: Int) { _host.value = _host.value.copy(progress = p) }
    fun onUrl(u: String) { _host.value = _host.value.copy(url = u) }
    fun onPageError(desc: String?) {
        val h = _host.value
        _host.value = if (desc == null) h.copy(error = null) else h.copy(error = desc, view = if (h.view == View.HIDDEN) View.ASSIST else h.view,
            title = if (h.view == View.HIDDEN) "页面没有打开" else h.title)
    }

    /** The renderer of [v] died (or was killed): drop it; ConnectWebLayer rebuilds a fresh WebView. */
    fun onRendererGone(v: WebView?) {
        if (v != null && _popup.value === v) { _popup.value = null; return }
        if (v != null && web != null && web !== v) return
        web = null
        _webGen.value = _webGen.value + 1
    }

    /** window.open from the page: the child WebView is shown on top until the site closes it. */
    fun onPopup(child: WebView) {
        _popup.value = child
        val h = _host.value
        if (h.active && h.view == View.HIDDEN) show(View.VERIFY, "登录窗口 · ${h.service?.name.orEmpty()}", "网站打开了一个登录 / 授权窗口，请在里面完成；完成后窗口会自己关闭并继续")
    }

    fun onPopupClosed(child: WebView?) { if (child == null || _popup.value === child) _popup.value = null }

    fun closePopup() { _popup.value = null }

    /** The page (or its popup) went to Google sign-in, which Google blocks in embedded WebViews. */
    fun onGoogleLogin() {
        val h = _host.value
        if (!h.active || h.handoff) return
        val svc = h.service?.name.orEmpty()
        _host.value = h.copy(view = View.ASSIST, handoff = true, highlight = true, title = "Google 登录需要在浏览器完成 · $svc",
            hint = if (h.mode == Mode.KEY) "Google 不允许在 App 内置浏览器里登录（这是 Google 的安全规定，Mibot 不会伪装绕过）。可以换用页面上的邮箱 / 其他登录方式；" +
                "或点「用浏览器完成」在浏览器里登录并新建、复制 Key。浏览器的登录状态不会回到 Mibot，复制的 Key 需要你回来后在台账里添加。"
            else "Google 不允许在 App 内置浏览器里登录。可以换用页面上的其他登录方式，或点「用浏览器完成」在浏览器里登录（登录状态不会回到 Mibot）。",
            handoffUrl = h.handoffUrl.ifBlank { h.loadUrl })
    }

    /** User chose the Custom Tab handoff from the Google sheet. */
    fun userHandoff(ctx: Context) {
        val h = _host.value
        openInBrowser(ctx, h.handoffUrl.ifBlank { h.url.ifBlank { h.loadUrl } })
        handoffRequested = true
    }

    /** A capture-layer message ({k, via, ...}) from any frame. */
    fun onPushed(data: String?) { if (!data.isNullOrBlank() && pushed.size < 200) pushed.add(data) }

    private fun show(view: View, title: String, hint: String, highlight: Boolean = false) {
        _host.value = _host.value.copy(view = view, title = title, hint = hint, highlight = highlight, handoff = if (view == View.HIDDEN) false else _host.value.handoff)
    }

    private suspend fun awaitWeb(ms: Long = 15_000): WebView? = web ?: withTimeoutOrNull(ms) { while (web == null) delay(150); web }

    private suspend fun eval(w: WebView, js: String): JSONObject? = withTimeoutOrNull(6000) {
        suspendCancellableCoroutine<String?> { c -> runCatching { w.evaluateJavascript(js) { raw -> if (c.isActive) c.resume(raw) } }.onFailure { if (c.isActive) c.resume(null) } }
    }?.let { raw -> runCatching { JSONObject(JSONArray("[$raw]").getString(0)) }.getOrNull() }

    private fun load(w: WebView, url: String) {
        _host.value = _host.value.copy(loadUrl = url, error = null)
        runCatching { w.settings.userAgentString = uaFor(w.context, url) }
        w.loadUrl(url)
    }

    private fun now() = System.currentTimeMillis()

    /** Document-start capture (every frame) + the `mibotKey` listener, when the WebView supports them. */
    private class Attached(val script: ScriptHandler?, val listener: Boolean)

    private fun attach(w: WebView, keyRegex: String): Attached {
        var sh: ScriptHandler? = null; var li = false
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(w, "mibotKey", setOf("*")) { _, msg, _, _, _ -> onPushed(msg.data) }
                li = true
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) sh = WebViewCompat.addDocumentStartJavaScript(w, AutoKeyJs.capture(keyRegex), setOf("*"))
        }
        return Attached(sh, li)
    }

    private fun detach(w: WebView?, a: Attached?) {
        a ?: return
        runCatching { a.script?.remove() }
        if (a.listener && w != null) runCatching { WebViewCompat.removeWebMessageListener(w, "mibotKey") }
    }

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
        cancelled = false; handoffRequested = false; pushed.clear()
        _host.value = Host(active = true, service = s, mode = Mode.KEY, view = View.HIDDEN, title = "连接 ${s.name}", hint = r.hint ?: "", url = r.start, handoffUrl = r.start)
        var attachedTo: WebView? = null; var attached: Attached? = null
        try {
            val t0 = now()
            if (r.json.optString("handoff") == "customtab") {
                // Google-only consoles: the whole flow runs in the system browser.
                onGoogleLogin()
                while (!handoffRequested) {
                    if (cancelled) return Result.Cancelled
                    if (now() - t0 > RUN_CEILING_MS) return Result.Failed(tc(app, "超时（15 分钟）", "Timed out (15 min)"))
                    delay(500)
                }
                return Result.Handoff(tc(app, "已在浏览器打开 ${hostOf(r.start)}：登录后新建并复制 Key，回到 Mibot 在台账里添加", "Opened ${hostOf(r.start)} in the browser: create and copy a key there, then add it in Mibot"))
            }
            val first = awaitWeb() ?: return Result.Failed(tc(app, "连接页面没有打开，请留在「自动发现并连接」页", "Keep the Discover & connect page open"))
            val cred = savedLogin(app, s)
            attached = attach(first, r.keyRegex); attachedTo = first
            load(first, r.start)
            onStep(tc(app, "后台打开 ${hostOf(r.start)}…", "Opening ${hostOf(r.start)} in the background…"))
            val budgets = AkStep.budgets(r.json)
            val clock = StepClock(budgets).also { it.enter(AkStep.OPEN, now()) }
            val flow = CreateFlow(budgets)
            val nulls = NullEvals()
            val cands = Candidates(KeyMatcher(r.keyRegex))
            var lastWeb: WebView? = first
            var fills = 0; var navs = 0; var lastNav = 0L; var assistAt: Long? = null; var stuckShown = false
            var mailbox: Mailbox? = null; var mailSent = 0L; var smsSince = 0L; var mailTries = 0
            while (true) {
                if (cancelled) return Result.Cancelled
                if (handoffRequested) return Result.Handoff(tc(app, "已转到浏览器完成 Google 登录：新建并复制 Key 后，回到 Mibot 在台账里添加", "Moved to the browser for Google sign-in: copy the key there and add it in Mibot"))
                if (now() - t0 > RUN_CEILING_MS) return Result.Failed(tc(app, "超时（15 分钟）", "Timed out (15 min)"))
                delay(1000)
                // Re-acquire every pass: the WebView may have been rebuilt (renderer crash) or destroyed (screen left).
                val w = web ?: awaitWeb(4_000)
                var st: JSONObject? = null
                var rebuilt = false
                if (w != null) {
                    if (w !== lastWeb) {
                        rebuilt = true
                        detach(attachedTo, attached)
                        attached = attach(w, r.keyRegex); attachedTo = w; lastWeb = w
                        onStep(tc(app, "页面进程已重建，重新打开…", "Page process rebuilt, reopening…"))
                        load(w, r.start); clock.enter(AkStep.OPEN, now()); navs = 0
                    } else st = eval(w, AutoKeyJs.script(r.json, "detect"))
                }
                when (nulls.onResult(st != null || rebuilt)) {
                    NullEvals.Verdict.FAIL -> return Result.Failed(if (w == null) tc(app, "连接页面被关闭了（离开了「自动发现并连接」页）", "The connect page was closed")
                        else tc(app, "页面一直没有响应", "The page stopped responding"))
                    NullEvals.Verdict.SHOW_STUCK -> if (!stuckShown) { stuckShown = true
                        show(View.ASSIST, "页面没有响应 · ${s.name}", if (w == null) "连接页面被关闭了，请回到「自动发现并连接」页" else "页面卡住了，可点「重试」或「用浏览器打开」", true) }
                    else -> {}
                }
                // Keys from any channel: the poll (main frame) and the document-start layer (any frame).
                st?.optJSONArray("keys")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { o -> cands.offer(o.optString("k"), o.optString("via", "page"), now()) } }
                while (true) { val m = pushed.poll() ?: break; runCatching { JSONObject(m) }.getOrNull()?.let { o -> cands.offer(o.optString("k"), o.optString("via", "page"), now()) } }
                val since = listOfNotNull(flow.createdAt, assistAt).minOrNull()
                cands.pick(since, flow.gaveUp)?.let { return Result.Key(it.key, it.via) }

                if (st != null) {
                    if (stuckShown) { stuckShown = false; if (_host.value.view == View.ASSIST && _host.value.title.startsWith("页面没有响应")) show(View.HIDDEN, "连接 ${s.name}", "") }
                    val obs = PageObs.parse(st)
                    val state = obs.state
                    val view = _host.value.view
                    if (state != "google" && _host.value.handoff && _popup.value == null) show(View.HIDDEN, "连接 ${s.name}", "")
                    StateSteps.stepFor(state, obs.onKeys, obs.rendered)?.let { clock.enter(it, now()) }
                    when (state) {
                        "loading", "unknown" -> {}
                        "challenge" -> {
                            onStep(tc(app, "等待页面安全检查…", "Waiting for the site's security check…"))
                            if (view == View.HIDDEN && clock.elapsed(now()) > (budgets[AkStep.CHALLENGE] ?: 20_000L))
                                show(View.VERIFY, "页面安全检查 · ${s.name}", "网站在做安全检查（Cloudflare 等）。如果出现「我是人类」勾选框请点一下；Mibot 不会刷新页面，通过后自动继续")
                        }
                        "google" -> onGoogleLogin()
                        "qr" -> if (view != View.VERIFY) show(View.VERIFY, "需要你扫码登录 · ${s.name}", "页面要求扫码登录：用另一台设备上的 App 扫描二维码，或在页面上切换到账号密码 / 手机号登录；登录后自动继续")
                        "verify" -> if (view != View.VERIFY) show(View.VERIFY, "需要你验证 · ${s.name}", "完成验证码 / 滑块后会自动继续，弹窗会自己收起")
                        "email_code" -> {
                            val box = mailbox
                            if (box == null) show(View.VERIFY, "需要你输入邮箱验证码 · ${s.name}", "这个账号的邮箱不是 Mibot 的临时邮箱，请输入邮件里的验证码")
                            else {
                                onStep(tc(app, "等待 ${box.address} 收到验证码…", "Waiting for the code in ${box.address}…"))
                                val f = MailCore.waitCode(app, box, s.domains.firstOrNull()?.substringBefore('/'), 120)
                                val wv = web
                                if (f?.code != null && wv != null) { eval(wv, AutoKeyJs.script(r.json, "code", f.code)); onStep(tc(app, "已自动填入邮件验证码", "Email code filled")); delay(2500) }
                                else if (f?.link != null && wv != null) { load(wv, f.link); delay(2500) }
                                else show(View.VERIFY, "没收到邮件验证码 · ${s.name}", "2 分钟内 ${box.address} 没有收到验证码，请检查页面", true)
                            }
                        }
                        "sms_code" -> {
                            if (smsSince == 0L) smsSince = now()
                            val code = com.openminis.app.mibot.settings.SmsCodes.latest(smsSince, s.name)
                            if (code != null && w != null) { eval(w, AutoKeyJs.script(r.json, "code", code.code)); onStep(tc(app, "已从你的短信通知填入验证码", "SMS code filled from your notifications")); smsSince = now(); delay(2500) }
                            else if (now() - smsSince > 60_000 || !com.openminis.app.offload.MinisNotificationListenerService.isEnabled(app)) {
                                if (view == View.HIDDEN) show(View.VERIFY, "需要你输入短信验证码 · ${s.name}", "没有在 60 秒内读到短信验证码（需要「通知使用权」），请手动输入")
                            } else onStep(tc(app, "等待短信验证码…", "Waiting for the SMS code…"))
                        }
                        "login" -> {
                            if (view == View.VERIFY || view == View.ASSIST) {
                                // The user is on it.
                            } else if (cred != null && fills < 2 && w != null) {
                                eval(w, AutoKeyJs.script(r.json, "fill", cred.first, cred.second)); fills++
                                onStep(tc(app, "自动填入已保存的账号…", "Filling the saved login…")); delay(2500)
                            } else if (st.optBoolean("emailEntry") && mailTries < 2 && TempMailPolicy.allowed(app) && w != null) {
                                val box = mailbox ?: runCatching { MailCore.create(app, null, s.id, exclude = TempMailPolicy.rejected(s.id)) }.getOrNull()
                                if (box == null) show(View.VERIFY, "需要你登录 · ${s.name}", "没有可用的临时邮箱平台，请手动登录")
                                else {
                                    mailbox = box; mailTries++
                                    onStep(tc(app, "用临时邮箱 ${box.address} 登录/注册…", "Using temp mailbox ${box.address}…"))
                                    eval(w, AutoKeyJs.script(r.json, "email", box.address)); mailSent = now(); delay(3500)
                                }
                            } else show(View.VERIFY, "需要你登录 · ${s.name}", if (cred == null) "Mibot 没有这个服务的已保存密码，请登录；登录后自动继续" else "自动登录没有通过，请完成登录")
                        }
                        "in" -> {
                            if (view == View.VERIFY && _popup.value == null) { show(View.HIDDEN, "连接 ${s.name}", ""); onStep(tc(app, "验证通过，继续获取 Key…", "Verified — continuing…")) }
                            mailbox?.let { MailCore.link(app, it, s.id) }
                            if (!obs.onKeys) {
                                when {
                                    now() - lastNav < 4_000 -> {}
                                    navs < 2 -> { onStep(tc(app, "打开 API Key 页面…", "Opening the API key page…")); w?.let { load(it, r.start) }; navs++; lastNav = now() }
                                    navs < 5 -> { w?.let { eval(it, AutoKeyJs.script(r.json, "nav")) }; navs++; lastNav = now() }
                                    view != View.ASSIST -> { assistAt = assistAt ?: now(); show(View.ASSIST, "需要你操作 · ${s.name}", "没找到 API Key 页面（页面可能改版）。请打开「API Keys」并新建一个，出现后会自动保存。", true) }
                                }
                            } else if (view != View.ASSIST && w != null) {
                                val d = flow.next(obs, false, now())
                                when (d.act) {
                                    CreateFlow.Act.CLICK_CREATE -> { eval(w, AutoKeyJs.script(r.json, "click_create")); onStep(tc(app, "新建 Key「Mibot」…", "Creating key \"Mibot\"…")) }
                                    CreateFlow.Act.FILL_NAME -> { eval(w, AutoKeyJs.script(r.json, "name")); onStep(tc(app, "填写名称…", "Naming the key…")) }
                                    CreateFlow.Act.CONFIRM -> { eval(w, AutoKeyJs.script(r.json, "confirm")); onStep(tc(app, "确认创建…", "Confirming…")) }
                                    CreateFlow.Act.REVEAL -> { eval(w, AutoKeyJs.script(r.json, "reveal")); onStep(tc(app, "读取 Key…", "Reading the key…")) }
                                    CreateFlow.Act.NEED_USER -> { assistAt = assistAt ?: now()
                                        show(View.ASSIST, "需要你操作 · ${s.name}", "${d.reason}（配方 v${r.version}）。请在页面上新建并复制 Key，出现后会自动保存。", true) }
                                    CreateFlow.Act.WAIT, CreateFlow.Act.DONE -> {}
                                }
                            }
                        }
                        "elsewhere" -> if (view == View.HIDDEN && clock.elapsed(now()) > 15_000) show(View.VERIFY, "需要你操作 · ${s.name}", "页面跳到了 ${st.optString("host")}，请完成登录")
                    }
                    if (st.optBoolean("disposableRejected") && mailbox != null && mailSent > 0 && state != "in") {
                        TempMailPolicy.markRejected(s.id, mailbox!!.provider); mailSent = 0
                        show(View.VERIFY, "${s.name} 拒绝了临时邮箱", "这个服务不接受一次性邮箱域名（${mailbox!!.domain}）。请用你自己的邮箱或手机号登录。", true)
                    }
                    if (mailSent > 0 && now() - mailSent > 200_000 && state != "in") mailSent = 0
                }
                // Step budget (tracked by step, so URL churn cannot reset it). The create flow has its own budgets.
                val onKeysNow = st != null && st.optBoolean("onKeys") && st.optString("state") == "in"
                if (!onKeysNow && _host.value.view == View.HIDDEN && _popup.value == null && clock.expiredOnce(now())) {
                    assistAt = assistAt ?: now()
                    show(View.ASSIST, "需要你操作 · ${s.name}", "「${clock.step.zh}」这一步 ${(budgets[clock.step] ?: clock.step.budgetMs) / 1000} 秒没有进展，请在页面上继续；Key 出现后会自动保存。", true)
                }
            }
        } finally {
            detach(attachedTo, attached)
            closePopup()
            _host.value = Host()
        }
    }

    /** Plain web login (no key): visible from the start, captures the session cookie. */
    suspend fun webLogin(app: Context, s: VaultService): Result {
        cancelled = false; saveLoginRequested = false; handoffRequested = false
        val r = KeyRecipes.generic(app, s)
        _host.value = Host(active = true, service = s, mode = Mode.LOGIN, view = View.ASSIST, title = "登录 ${s.name}",
            hint = "登录后自动保存登录状态（验证码、短信码、二次验证由你完成）", url = r.start, canSaveLogin = true, handoffUrl = r.start)
        try {
            val t0 = now()
            val first = awaitWeb() ?: return Result.Failed(tc(app, "连接页面没有打开", "Connect page not open"))
            load(first, r.start)
            var lastWeb: WebView? = first
            val nulls = NullEvals()
            var stable = 0
            while (true) {
                if (cancelled) return Result.Cancelled
                if (handoffRequested) return Result.Handoff(tc(app, "已转到浏览器登录（登录状态不会回到 Mibot）", "Moved to the browser (the session does not come back to Mibot)"))
                if (now() - t0 > RUN_CEILING_MS) return Result.Failed(tc(app, "超时（15 分钟）", "Timed out (15 min)"))
                delay(1500)
                if (saveLoginRequested) {
                    saveLoginRequested = false
                    CookieManager.getInstance().flush()
                    val ck = VaultConnect.cookiesFor(s)
                    if (ck.isNotBlank()) return Result.Cookies(ck)
                    show(View.ASSIST, "登录 ${s.name}", "还没有检测到登录（没有会话 Cookie）", true)
                }
                val w = web ?: awaitWeb(4_000)
                var st: JSONObject? = null
                if (w != null) {
                    if (w !== lastWeb) { lastWeb = w; load(w, _host.value.url.takeIf { it.startsWith("http") } ?: r.start) }
                    else st = eval(w, AutoKeyJs.script(r.json, "detect"))
                }
                if (nulls.onResult(st != null) == NullEvals.Verdict.FAIL)
                    return Result.Failed(if (w == null) tc(app, "登录页面被关闭了", "The login page was closed") else tc(app, "页面一直没有响应", "The page stopped responding"))
                if (st?.optString("state") == "google") onGoogleLogin()
                else if (_host.value.handoff && _popup.value == null) _host.value = _host.value.copy(handoff = false, highlight = false, title = "登录 ${s.name}", hint = "登录后自动保存登录状态（验证码、短信码、二次验证由你完成）")
                val signedIn = st?.optString("state") == "in" && VaultConnect.browserHasLogin(s)
                stable = if (signedIn) stable + 1 else 0
                if (stable >= 2) { CookieManager.getInstance().flush(); return Result.Cookies(VaultConnect.cookiesFor(s)) }
            }
        } finally { closePopup(); _host.value = Host() }
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
