package com.openminis.app.mibot.phone

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.openminis.app.accessibility.MinisAccessibilityService
import com.openminis.app.mibot.design.tc
import com.openminis.app.mibot.vault.VaultConfirmBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [fork/mibot-v3] Background phone control ("后台操控 / 虚拟屏").
 *
 * Tiers, best first:
 *  1. HELPER — Mibot's built-in shell helper (started via the phone's own
 *     Wireless Debugging, or via Shizuku) owns a hidden virtual display. Apps
 *     run there, frames go to an ImageReader in this process, input is injected
 *     into that display only. The user's screen (display 0) is never touched.
 *  2. A11Y — no helper: the accessibility service drives the MAIN screen, only
 *     after the user confirms, with a visible indicator and a foreground
 *     notification.
 *
 * Ask-mode: taps/typing that look like payments, logins, passwords or
 * verification codes need the user's OK first (see [askIfSensitive]).
 */
object VdController {
    enum class Mode { NONE, HELPER, A11Y }

    data class State(
        val mode: Mode = Mode.NONE,
        val displayId: Int = -1,
        val width: Int = 0,
        val height: Int = 0,
        val app: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    @Volatile private var display: AgentDisplay? = null
    private val emptyFrames = MutableStateFlow<Bitmap?>(null)
    private val _framesFlow = MutableStateFlow<StateFlow<Bitmap?>>(emptyFrames)
    /** The current display's preview frames (switches when a display is created/closed). */
    val previewSource: StateFlow<StateFlow<Bitmap?>> = _framesFlow.asStateFlow()

    @Volatile private var a11yConfirmed = false

    fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.value = (_log.value + "$ts  $msg").takeLast(200)
    }

    fun helper(context: Context): IMibotShell? = MibotPhone.get(context).hub.shell.value

    // ------------------------------------------------------------ lifecycle

    /** Creates the agent display. Falls back to main-screen accessibility control after asking. */
    suspend fun create(context: Context, width: Int = 0, height: Int = 0): Result<State> = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        val phone = MibotPhone.get(app)
        val current = _state.value
        if (current.mode != Mode.NONE) return@withContext Result.success(current)
        var shell = phone.hub.shell.value
        if (shell == null && phone.supported) {
            // Wake the watchdog (paired) or try Shizuku when it is ready.
            phone.builtin.need()
            if (com.openminis.app.offload.ShizukuManager.isReady()) {
                log("Shizuku: starting helper")
                phone.launchViaShizuku()?.let { log("Shizuku: $it") }
            }
            shell = kotlinx.coroutines.withTimeoutOrNull(8_000) {
                phone.hub.shell.first { it != null }
            }
        }
        if (shell != null) {
            val dm = app.resources.displayMetrics
            // Phone-shaped display at ~2/3 of the real resolution: sharp enough for
            // the model, cheap to capture.
            val w = (if (width > 0) width else (dm.widthPixels * 2 / 3)).coerceIn(360, 1440) / 2 * 2
            val h = (if (height > 0) height else (dm.heightPixels * 2 / 3)).coerceIn(640, 3200) / 2 * 2
            val dpi = (dm.densityDpi * 2 / 3).coerceIn(160, 640)
            return@withContext runCatching {
                val d = AgentDisplay(shell, w, h, dpi)
                display = d
                _framesFlow.value = d.frames
                val s = State(Mode.HELPER, d.displayId, w, h)
                _state.value = s
                log("vd_create: display ${d.displayId} ${w}x$h@$dpi (${phone.hub.source})")
                VdForegroundService.start(app)
                s
            }.onFailure { log("vd_create failed: ${it.message}") }
        }
        // Tier 3: accessibility on the main screen, only with consent.
        val a11y = MinisAccessibilityService.getInstance()
            ?: return@withContext Result.failure(IllegalStateException(
                tc(app, "后台操控未就绪：请在「后台操控 / 虚拟屏」页完成无线调试配对，或授权 Shizuku，或开启无障碍。",
                    "Background control isn't ready: pair Wireless Debugging (or grant Shizuku, or enable Accessibility) on the Background control page."),
            ))
        if (!a11yConfirmed) {
            val ok = VaultConfirmBus.ask(
                tc(app, "允许 Mibot 操作主屏幕？", "Let Mibot operate your main screen?"),
                tc(app, "后台虚拟屏不可用（未配对无线调试 / 未授权 Shizuku）。改用无障碍在你正在看的屏幕上点按和输入，期间会显示悬浮提示，可随时在通知里停止。",
                    "The hidden display isn't available (no Wireless Debugging pairing / Shizuku). Mibot would tap and type on the screen you are using, with a floating indicator; stop any time from the notification."),
                danger = true,
            )
            if (!ok) return@withContext Result.failure(IllegalStateException("user declined main-screen control"))
            a11yConfirmed = true
        }
        val dm = app.resources.displayMetrics
        val s = State(Mode.A11Y, Display.DEFAULT_DISPLAY, dm.widthPixels, dm.heightPixels)
        _state.value = s
        log("vd_create: accessibility fallback on main screen (${a11y.javaClass.simpleName})")
        VdForegroundService.start(app)
        VdOverlay.show(app)
        Result.success(s)
    }

    fun close(context: Context) {
        val app = context.applicationContext
        display?.let { runCatching { it.release() } }
        display = null
        _framesFlow.value = emptyFrames
        val was = _state.value.mode
        _state.value = State()
        a11yConfirmed = false
        if (was != Mode.NONE) log("vd_close")
        VdOverlay.hide(app)
        VdForegroundService.stop(app)
    }

    private fun requireActive(): State {
        val s = _state.value
        check(s.mode != Mode.NONE) { "No virtual display. Call vd_create first." }
        return s
    }

    // ------------------------------------------------------------ actions

    suspend fun launch(context: Context, packageName: String): String = withContext(Dispatchers.IO) {
        val s = requireActive()
        val app = context.applicationContext
        if (packageName == app.packageName) return@withContext "refused: Mibot itself"
        if (BLOCKED_PACKAGES.any { packageName.startsWith(it) }) {
            val ok = VaultConfirmBus.ask(
                tc(app, "打开支付/银行类应用？", "Open a payment/banking app?"),
                tc(app, "Mibot 想在后台打开 $packageName。涉及付款的操作每一步都会再次询问你。", "Mibot wants to open $packageName in the background. Every payment step will ask you again."),
                danger = true,
            )
            if (!ok) return@withContext "user declined opening $packageName"
        }
        when (s.mode) {
            Mode.HELPER -> {
                val r = helper(app)?.startApp(s.displayId, packageName) ?: return@withContext "helper not connected"
                val msg = when (r) {
                    ShellService.START_OK -> "started $packageName on display ${s.displayId}"
                    ShellService.START_IN_USE_ON_MAIN -> "not started: $packageName is open on the user's screen (never taken over). Ask the user to leave it first."
                    ShellService.START_NOT_FOUND -> "no launcher activity for $packageName (not installed?)"
                    ShellService.START_IN_USE_BY_OTHER -> "already on another agent display"
                    else -> "start failed ($r)"
                }
                if (r == ShellService.START_OK) _state.value = s.copy(app = packageName)
                log("vd_launch_app $packageName -> $msg")
                msg
            }
            Mode.A11Y -> {
                val intent = app.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@withContext "no launcher activity for $packageName"
                app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                _state.value = s.copy(app = packageName)
                log("vd_launch_app (main screen) $packageName")
                "started $packageName on the main screen (accessibility mode)"
            }
            Mode.NONE -> "no display"
        }
    }

    /** JPEG of the agent display (or the main screen in A11Y mode), long side ≤ [maxSide]. */
    suspend fun screenshot(context: Context, maxSide: Int = 1280): ByteArray? = withContext(Dispatchers.Default) {
        val s = requireActive()
        val bmp: Bitmap? = when (s.mode) {
            Mode.HELPER -> {
                var b = display?.latestFrameBitmap()
                var tries = 0
                while (b == null && tries++ < 10) { delay(150); b = display?.latestFrameBitmap() }
                b
            }
            Mode.A11Y -> {
                val svc = MinisAccessibilityService.getInstance() ?: return@withContext null
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null
                svc.captureScreenshot(Display.DEFAULT_DISPLAY).bitmap
            }
            Mode.NONE -> null
        }
        bmp ?: return@withContext null
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        out.toByteArray()
    }

    suspend fun tap(context: Context, x: Int, y: Int): String = withContext(Dispatchers.IO) {
        val s = requireActive()
        val app = context.applicationContext
        askIfSensitive(app, s, x, y, null)?.let { return@withContext it }
        val ok = when (s.mode) {
            Mode.HELPER -> helper(app)?.injectTap(s.displayId, x, y) == true
            Mode.A11Y -> gesture(listOf(x to y, x to y), 60)
            Mode.NONE -> false
        }
        log("vd_tap $x,$y -> $ok")
        if (ok) "tapped ($x,$y)" else "tap failed"
    }

    suspend fun swipe(context: Context, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String = withContext(Dispatchers.IO) {
        val s = requireActive()
        val dur = durationMs.coerceIn(80, 3_000).toLong()
        val ok = when (s.mode) {
            Mode.HELPER -> {
                val n = (dur / 16).toInt().coerceIn(4, 120)
                val xs = IntArray(n + 1) { x1 + (x2 - x1) * it / n }
                val ys = IntArray(n + 1) { y1 + (y2 - y1) * it / n }
                val ts = LongArray(n + 1) { dur * it / n }
                helper(context)?.injectStroke(s.displayId, xs, ys, ts) == true
            }
            Mode.A11Y -> gesture(listOf(x1 to y1, x2 to y2), dur)
            Mode.NONE -> false
        }
        log("vd_swipe $x1,$y1→$x2,$y2 ${dur}ms -> $ok")
        if (ok) "swiped" else "swipe failed"
    }

    suspend fun type(context: Context, text: String, submit: Boolean): String = withContext(Dispatchers.IO) {
        val s = requireActive()
        val app = context.applicationContext
        askIfSensitive(app, s, null, null, text)?.let { return@withContext it }
        val ok = when (s.mode) {
            Mode.HELPER -> {
                val sh = helper(app) ?: return@withContext "helper not connected"
                val ascii = text.all { it.code in 32..126 }
                if (ascii && text.length <= 200) sh.injectText(s.displayId, text)
                else {
                    val r = sh.setClipText(text)
                    val pasted = r == ShellService.CLIP_SET && sh.injectCtrlKey(s.displayId, KeyEvent.KEYCODE_V)
                    delay(300)
                    sh.restoreClip()
                    pasted
                }
            }
            Mode.A11Y -> {
                val svc = MinisAccessibilityService.getInstance()
                val node = svc?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                svc != null && node != null && svc.setNodeText(node, text)
            }
            Mode.NONE -> false
        }
        if (ok && submit) key(app, "ENTER")
        log("vd_type (${text.length} chars) -> $ok")
        if (ok) "typed ${text.length} chars" else "type failed (is an input field focused? tap it first)"
    }

    suspend fun key(context: Context, name: String): String = withContext(Dispatchers.IO) {
        val s = requireActive()
        val code = KEYS[name.uppercase().removePrefix("KEYCODE_")] ?: name.toIntOrNull()
            ?: return@withContext "unknown key '$name' (allowed: ${KEYS.keys.joinToString()})"
        val ok = when (s.mode) {
            Mode.HELPER -> helper(context)?.injectKey(s.displayId, code) == true
            Mode.A11Y -> {
                val svc = MinisAccessibilityService.getInstance()
                when (code) {
                    KeyEvent.KEYCODE_BACK -> svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
                    else -> false
                }
            }
            Mode.NONE -> false
        }
        log("vd_key $name -> $ok")
        if (ok) "pressed $name" else "key failed"
    }

    /** Compact accessibility tree of the agent display (needs Mibot's accessibility service). */
    fun uiDump(context: Context, maxNodes: Int = 250): String {
        val s = requireActive()
        val svc = MinisAccessibilityService.getInstance()
            ?: return "Accessibility is off, so the UI tree can't be read. Use vd_screenshot (the model reads the image) or enable Mibot's accessibility service."
        val roots: List<AccessibilityNodeInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val all = svc.windowsOnAllDisplays
            (all.get(s.displayId) ?: emptyList()).mapNotNull { it.root }
        } else svc.windows.mapNotNull { it.root }
        if (roots.isEmpty()) return "No windows on display ${s.displayId} yet (app still starting?)."
        val sb = StringBuilder("display ${s.displayId} ${s.width}x${s.height}; top=${topActivity(context) ?: "?"}\n")
        var n = 0
        val r = Rect()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (n >= maxNodes) return
            val text = node.text?.toString()?.take(60)
            val desc = node.contentDescription?.toString()?.take(60)
            val id = node.viewIdResourceName?.substringAfter(":id/")
            val interesting = !text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable || node.isEditable || node.isScrollable
            if (interesting && node.isVisibleToUser) {
                node.getBoundsInScreen(r)
                n++
                sb.append("  ".repeat(depth.coerceAtMost(8)))
                sb.append("[${r.centerX()},${r.centerY()}] ")
                sb.append(node.className?.toString()?.substringAfterLast('.') ?: "View")
                if (!text.isNullOrBlank()) sb.append(" \"").append(if (node.isPassword) "••••" else text).append('"')
                if (!desc.isNullOrBlank()) sb.append(" desc=\"").append(desc).append('"')
                if (id != null) sb.append(" id=").append(id)
                if (node.isClickable) sb.append(" click")
                if (node.isEditable) sb.append(" edit")
                if (node.isScrollable) sb.append(" scroll")
                if (node.isChecked) sb.append(" checked")
                sb.append('\n')
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        roots.forEach { walk(it, 0) }
        lastScreenText = sb.toString()
        return sb.toString()
    }

    fun topActivity(context: Context): String? {
        val s = _state.value
        return if (s.mode == Mode.HELPER) runCatching { helper(context)?.topActivity(s.displayId) }.getOrNull() else null
    }

    // ------------------------------------------------------------ ask-mode

    @Volatile private var lastScreenText: String = ""

    private val SENSITIVE = Regex(
        "(支付|付款|确认付款|立即支付|收银台|转账|提现|充值|购买|下单|结算|密码|验证码|登录|授权|绑定银行卡|" +
            "pay\\b|payment|checkout|purchase|buy now|place order|transfer|password|passcode|verification code|one-time code|otp|sign in|log in|login)",
        RegexOption.IGNORE_CASE,
    )

    /** Returns a refusal message when the user declines; null when it may proceed. */
    private suspend fun askIfSensitive(app: Context, s: State, x: Int?, y: Int?, text: String?): String? {
        var reason: String? = null
        if (text != null) {
            if (Regex("^\\s*\\d{4,8}\\s*$").matches(text)) reason = tc(app, "输入看起来像验证码", "the text looks like a verification code")
            val focus = MinisAccessibilityService.getInstance()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focus?.isPassword == true) reason = tc(app, "当前输入框是密码框", "the focused field is a password field")
        }
        if (reason == null && x != null && y != null && s.mode == Mode.HELPER) {
            val label = runCatching { nodeLabelAt(s.displayId, x, y) }.getOrNull()
            if (label != null && SENSITIVE.containsMatchIn(label)) reason = tc(app, "要点的是「$label」", "the target is “$label”")
        }
        if (reason == null) return null
        val ok = VaultConfirmBus.ask(
            tc(app, "确认这一步？", "Confirm this step?"),
            tc(app, "Mibot 正在后台操作 ${s.app ?: "应用"}：$reason。付款、登录、密码和验证码类操作需要你确认。",
                "Mibot is operating ${s.app ?: "an app"} in the background: $reason. Payments, logins, passwords and codes need your OK."),
            danger = true,
        )
        log("ask-mode: $reason -> ${if (ok) "allowed" else "declined"}")
        return if (ok) null else "The user declined this step ($reason). Stop and ask the user how to continue."
    }

    private fun nodeLabelAt(displayId: Int, x: Int, y: Int): String? {
        val svc = MinisAccessibilityService.getInstance() ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val roots = (svc.windowsOnAllDisplays.get(displayId) ?: return null).mapNotNull { it.root }
        val r = Rect()
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        fun walk(n: AccessibilityNodeInfo) {
            n.getBoundsInScreen(r)
            if (r.contains(x, y)) {
                val area = r.width() * r.height()
                if ((n.text != null || n.contentDescription != null) && area < bestArea) { best = n; bestArea = area }
                for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
            }
        }
        roots.forEach { walk(it) }
        return best?.let { (it.text ?: it.contentDescription)?.toString() }
    }

    // ------------------------------------------------------------ a11y helpers

    private fun gesture(points: List<Pair<Int, Int>>, durationMs: Long): Boolean {
        val svc = MinisAccessibilityService.getInstance() ?: return false
        val p = Path().apply {
            moveTo(points[0].first.toFloat(), points[0].second.toFloat())
            points.drop(1).forEach { lineTo(it.first.toFloat(), it.second.toFloat()) }
        }
        return svc.dispatchSimpleGesture(p, 0, durationMs)
    }

    fun overlayGranted(context: Context) = Settings.canDrawOverlays(context)

    val KEYS: Map<String, Int> = linkedMapOf(
        "BACK" to KeyEvent.KEYCODE_BACK, "ENTER" to KeyEvent.KEYCODE_ENTER, "DEL" to KeyEvent.KEYCODE_DEL,
        "FORWARD_DEL" to KeyEvent.KEYCODE_FORWARD_DEL, "TAB" to KeyEvent.KEYCODE_TAB, "SPACE" to KeyEvent.KEYCODE_SPACE,
        "ESCAPE" to KeyEvent.KEYCODE_ESCAPE, "MENU" to KeyEvent.KEYCODE_MENU, "SEARCH" to KeyEvent.KEYCODE_SEARCH,
        "PAGE_UP" to KeyEvent.KEYCODE_PAGE_UP, "PAGE_DOWN" to KeyEvent.KEYCODE_PAGE_DOWN,
        "MOVE_HOME" to KeyEvent.KEYCODE_MOVE_HOME, "MOVE_END" to KeyEvent.KEYCODE_MOVE_END,
        "DPAD_UP" to KeyEvent.KEYCODE_DPAD_UP, "DPAD_DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "DPAD_LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "DPAD_RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT, "DPAD_CENTER" to KeyEvent.KEYCODE_DPAD_CENTER,
    )

    /** Payment / banking apps: opening them needs an explicit OK (every sensitive step asks again). */
    private val BLOCKED_PACKAGES = listOf(
        "com.eg.android.AlipayGphone", "com.unionpay", "cmb.pb", "com.icbc", "com.chinamworld", "com.android.bankabc",
        "com.paypal", "com.google.android.apps.walletnfcrel", "com.tencent.wallet",
    )
}

