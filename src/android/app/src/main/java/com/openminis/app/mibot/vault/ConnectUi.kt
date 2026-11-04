package com.openminis.app.mibot.vault

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Message
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openminis.app.mibot.design.BrandLogo
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotDivider
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.StatusChip

/** Open [url] in a Custom Tab (fallback: any browser). Sessions are NOT shared with the WebView. */
internal fun openInBrowser(ctx: Context, url: String) {
    runCatching { com.openminis.app.auth.OAuthTabLauncher.launch(ctx, Uri.parse(url), newTask = true) }
        .recoverCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Chrome-like UA without the "; wv" WebView marker, so consoles serve their normal mobile page. */
internal fun mobileUa(default: String): String = default.replace("; wv)", ")").replace(Regex(" Version/\\d+\\.\\d+"), "")

/**
 * [fork/mibot-autokey-v2] UA for a URL in the connect WebView: the system
 * default (honest WebView UA) on Google sign-in hosts, where a spoofed UA
 * only makes Google's checks stricter; the Chrome-like mobile UA elsewhere.
 */
internal fun uaFor(ctx: Context, url: String?): String {
    val def = runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrDefault("")
    val host = runCatching { Uri.parse(url.orEmpty()).host }.getOrNull()
    return if (IntentLinks.isGoogleLogin(host) || def.isBlank()) def else mobileUa(def)
}

/** Settings shared by the connect WebView and its OAuth popups. */
@SuppressLint("SetJavaScriptEnabled")
private fun configureConnectWeb(w: WebView, c: Context) {
    w.settings.javaScriptEnabled = true
    w.settings.domStorageEnabled = true
    w.settings.databaseEnabled = true
    w.settings.loadWithOverviewMode = true
    w.settings.useWideViewPort = true
    w.settings.javaScriptCanOpenWindowsAutomatically = true
    // OAuth popups (GitHub / Microsoft / Apple): window.open gets a real child window, so window.opener works.
    w.settings.setSupportMultipleWindows(true)
    w.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    w.settings.userAgentString = mobileUa(w.settings.userAgentString)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
    w.webChromeClient = connectChrome(c)
    w.webViewClient = connectClient(c, popup = false)
}

private fun connectChrome(c: Context): WebChromeClient = object : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) { if (view === AutoKey.web) AutoKey.onProgress(newProgress) }
    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        val msg = resultMsg ?: return false
        val child = WebView(c)
        configureConnectWeb(child, c)
        child.webViewClient = connectClient(c, popup = true)
        child.webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) { AutoKey.onPopupClosed(window) }
        }
        val transport = msg.obj as? WebView.WebViewTransport ?: run { child.destroy(); return false }
        transport.webView = child
        msg.sendToTarget()
        AutoKey.onPopup(child)
        return true
    }
    override fun onCloseWindow(window: WebView?) { AutoKey.onPopupClosed(window) }
}

/** The connect WebViewClient (main page and popups). */
private fun connectClient(c: Context, popup: Boolean): WebViewClient = object : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (!popup) { AutoKey.onUrl(url.orEmpty()); AutoKey.onPageError(null) }
        if (IntentLinks.isGoogleLogin(runCatching { Uri.parse(url.orEmpty()).host }.getOrNull())) AutoKey.onGoogleLogin()
    }
    override fun onPageFinished(view: WebView?, url: String?) { if (!popup) AutoKey.onUrl(url.orEmpty()); CookieManager.getInstance().flush() }
    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) { if (!popup) AutoKey.onUrl(url.orEmpty()) }
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (!popup && request?.isForMainFrame == true) AutoKey.onPageError("${error?.description ?: "加载失败"}（${request.url.host}）")
    }
    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        if (!popup && request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 500) AutoKey.onPageError("HTTP ${errorResponse?.statusCode}（${request.url.host}）")
    }
    // The app's process-wide Browser proxy (Settings › Network) challenges every request with 407.
    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        if (handler != null && com.openminis.app.network.WebViewNetworkProxy.answerAuth(handler, host, realm, view?.url)) return
        super.onReceivedHttpAuthRequest(view, handler, host, realm)
    }
    // A crashed / killed renderer must not take the app down: drop this WebView and let the layer rebuild one.
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        AutoKey.onRendererGone(view)
        return true
    }
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (AutoKeyDemo.enabled && request?.url?.host == AutoKeyDemo.HOST) {
            return WebResourceResponse("text/html", "utf-8", AutoKeyDemo.html().byteInputStream())
        }
        return null
    }
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val req = request ?: return false
        val u = req.url ?: return false
        val url = u.toString()
        when (u.scheme?.lowercase()) {
            "http", "https" -> {
                // Default (honest) UA on Google sign-in, Chrome-like UA elsewhere. A UA change applies to the next load.
                if (view != null && req.isForMainFrame) {
                    val want = uaFor(c, url)
                    if (want.isNotBlank() && view.settings.userAgentString != want) {
                        view.settings.userAgentString = want
                        view.loadUrl(url)
                        return true
                    }
                }
                return false
            }
            "about", "data", "blob", "javascript" -> return false
            "intent" -> {
                // intent://…#Intent;scheme=…;package=…;S.browser_fallback_url=…;end (WeChat / Alipay / face-verify links).
                val it = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
                val ok = it != null && runCatching {
                    it.addCategory(Intent.CATEGORY_BROWSABLE); it.component = null; it.selector = null
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    c.startActivity(it); true
                }.getOrDefault(false)
                if (!ok) IntentLinks.fallback(url)?.let { fb -> view?.loadUrl(fb) }
                return true
            }
            else -> {
                // App deep links (weixin://, alipays://…) leave the WebView.
                runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }
        }
    }
}

/**
 * [fork/mibot-v5b2] The connect WebView, shared by every recipe run. While the
 * engine works it is laid out full-size but reports a 0×0 footprint and is
 * fully transparent (no touches, nothing drawn); when a human is needed it
 * becomes a bottom sheet ("需要你验证") and goes away again on its own.
 * Fixes from v5-B1: JS + DOM storage + third-party cookies + mixed-content
 * compat + mobile UA; load errors are shown with 重试 / 用浏览器打开; a top
 * progress bar; the header shows the host actually loaded (the old sheet
 * kept the previous provider's URL — 「连接 腾讯混元」 showed platform.openai.com).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConnectWebLayer() {
    val st by AutoKey.host.collectAsState()
    val gen by AutoKey.webGen.collectAsState()
    val popupWeb by AutoKey.popup.collectAsState()
    if (!st.active) return
    val ctx = LocalContext.current
    val visible = st.view != AutoKey.View.HIDDEN
    val s = st.service
    Box(Modifier.fillMaxSize()) {
        if (visible) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {})
        Column(
            if (visible) Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 1f))
            else Modifier.hiddenFullSize().graphicsLayer { alpha = 0f },
        ) {
            if (visible) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandLogo(s?.logo, s?.name ?: "", s?.color, size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(st.title, fontSize = MibotType.TitleSmall, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val shown = st.url.ifBlank { st.loadUrl }
                            Text(Uri.parse(shown).host.orEmpty() + Uri.parse(shown).path.orEmpty().take(40), fontSize = MibotType.Caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        StatusChip(if (st.mode == AutoKey.Mode.LOGIN) "登录" else "需要你", if (st.highlight) ChipKind.WARN else ChipKind.ACCENT)
                    }
                    if (st.hint.isNotBlank()) {
                        Text(st.hint, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine,
                            color = if (st.highlight) MibotTokens.warnColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp).then(if (st.highlight) Modifier.clip(RoundedCornerShape(8.dp)).background(MibotTokens.warnColor().copy(alpha = 0.12f))
                                .border(1.dp, MibotTokens.warnColor().copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(8.dp) else Modifier))
                    }
                }
                if (st.progress in 1..99) LinearProgressIndicator(progress = { st.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MibotTokens.accent())
                else MibotDivider(inset = 0.dp)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // key(webGen): a renderer crash bumps webGen, the old WebView is released and a fresh one built.
                key(gen) {
                    AndroidView(factory = { c ->
                        WebView(c).apply {
                            configureConnectWeb(this, c)
                            AutoKey.web = this
                        }
                    }, modifier = Modifier.fillMaxSize(), onRelease = { w -> if (AutoKey.web === w) AutoKey.web = null; runCatching { w.stopLoading() }; w.destroy() })
                }
                popupWeb?.let { pw ->
                    key(pw) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 1f))) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("登录窗口", fontSize = MibotType.Secondary, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Text("关闭", fontSize = MibotType.Secondary, color = MibotTokens.accent(), modifier = Modifier.clickable { AutoKey.closePopup() }.padding(6.dp))
                            }
                            AndroidView(factory = { (pw.parent as? android.view.ViewGroup)?.removeView(pw); pw }, modifier = Modifier.weight(1f).fillMaxWidth(),
                                onRelease = { w -> runCatching { w.stopLoading() }; w.destroy() })
                        }
                    }
                }
                if (visible && st.error != null) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 1f)).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("页面没有打开", fontSize = MibotType.Title, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                        Text(st.error ?: "", fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MibotButton("重试", { AutoKey.onPageError(null); AutoKey.web?.reload() })
                            MibotButton("用浏览器打开", { openInBrowser(ctx, st.url.ifBlank { st.loadUrl }) }, tonal = true)
                        }
                    }
                }
            }
            if (visible) {
                Row(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MibotButton("取消", { AutoKey.userCancel() }, tonal = true, modifier = Modifier.weight(1f))
                    if (st.handoff) MibotButton("用浏览器完成", { AutoKey.userHandoff(ctx) }, modifier = Modifier.weight(1f))
                    else if (st.canSaveLogin) MibotButton("我已登录，保存", { AutoKey.userSaveLogin() }, modifier = Modifier.weight(1f))
                    else MibotButton("用浏览器打开", { openInBrowser(ctx, st.url.ifBlank { st.loadUrl }) }, tonal = true, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Measures at the parent's full size (so pages lay out normally) but takes 0×0 (no touches). */
private fun Modifier.hiddenFullSize(): Modifier = this.layout { m, c ->
    val w = if (c.hasBoundedWidth) c.maxWidth else 1080
    val h = if (c.hasBoundedHeight) c.maxHeight else 1920
    val p = m.measure(Constraints.fixed(w, h))
    layout(0, 0) { p.place(0, 0) }
}

/**
 * [fork/mibot-v5b2] 连接方式 sheet for one service: app target vs web target,
 * honest about what each can give Mibot.
 */
@Composable
fun ConnectChoiceSheet(s: VaultService, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val installed = remember(s.id) { LoginTargets.installedPackage(ctx, s) }
    val recipe = remember(s.id) { KeyRecipes.forService(ctx, s) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) VaultConnect.returnedFromApp(ctx) }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }
    val webHost = Uri.parse(s.webStart).host.orEmpty()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 1f)).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(s.logo, s.name, s.color, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("连接 ${s.name}", fontSize = MibotType.Title, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (s.needsKey) "需要 API Key 才能当模型用" else "网页 / App 登录", fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            @Composable
            fun Option(title: String, sub: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (primary) MibotTokens.selected() else MibotTokens.tile())
                    .border(1.dp, if (primary) MibotTokens.accent().copy(alpha = 0.4f) else MibotTokens.hairline(), RoundedCornerShape(12.dp))
                    .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(12.dp)) {
                    Text(title, fontSize = MibotType.Body, fontWeight = MibotType.Semi, color = if (primary) MibotTokens.accent() else MaterialTheme.colorScheme.onSurface)
                    Text(sub, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val appName = s.appName ?: s.name
            if (s.auth == "oauth_openrouter") {
                Option("授权登录（OAuth）", "在浏览器里点「授权」，OpenRouter 直接把 Key 交给 Mibot · openrouter.ai", true) { VaultConnect.enqueue(ctx, listOf(s to null), VaultConnect.Mode.AUTO); onDismiss() }
            } else if (s.needsKey && recipe != null) {
                Option("自动获取 API Key", "后台打开 $webHost，登录后自动新建「Mibot」Key 并保存、探测；只有验证码 / 短信 / 扫码时才弹出来", true) {
                    VaultConnect.enqueue(ctx, listOf(s to null), VaultConnect.Mode.AUTO); onDismiss()
                }
            }
            if (s.hasApp) {
                if (installed != null) {
                    Option("打开 $appName App 登录", if (s.needsKey) "在 App 里登录不会把 API Key 交给 Mibot（读不到其他 App 的凭据），要当模型用还需上面的网页方式" else "跳到 $appName 登录；Mibot 读不到 App 里的凭据，只记录「已在 App 登录」",
                        primary = !s.needsKey) { VaultConnect.openApp(ctx, s, installed); }
                } else {
                    Option("去应用商店安装 $appName", "未安装 · 装好后可在 App 里登录（Mibot 读不到 App 内凭据）", false) { LoginTargets.openStore(ctx, s); onDismiss() }
                }
            }
            if (!s.needsKey || recipe == null) {
                Option("网页登录 · $webHost", "在内置浏览器里登录，登录状态加密存进台账", primary = !s.hasApp || installed == null) {
                    VaultConnect.enqueue(ctx, listOf(s to null), VaultConnect.Mode.WEB); onDismiss()
                }
            }
            Text("取消", fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).clickable(onClick = onDismiss).padding(8.dp))
        }
    }
}

/** App vs web login targets of a service. */
object LoginTargets {
    fun installedPackage(ctx: Context, s: VaultService): String? = s.packages.firstOrNull { pkg ->
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    }

    fun openStore(ctx: Context, s: VaultService) {
        val pkg = s.packages.firstOrNull() ?: return
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { openInBrowser(ctx, "https://play.google.com/store/apps/details?id=$pkg") }
    }
}
