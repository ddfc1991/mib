package com.openminis.app.mibot.phone

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mibot.core.cap.phone.AppPacing
import com.mibot.core.cap.phone.OperatingPace
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

    // [fork/mibot-destiny] 丝滑引擎:操作节奏(OperatingPace/AppPacing)。FAST 默认(反应快),
    // STANDARD/HUMAN 像人(步间延迟、限屏数,防风控)。设置页可调。
    @Volatile private var operatingPace: OperatingPace = OperatingPace.FAST
    @Volatile private var _appPacing: AppPacing = OperatingPace.FAST.appPacing()
    private val appPacing: AppPacing get() = _appPacing

    /** 设置操作节奏(设置页调用):重建节奏器(状态重置)。 */
    fun setOperatingPace(p: OperatingPace) { operatingPace = p; _appPacing = p.appPacing() }
    fun currentOperatingPace(): OperatingPace = operatingPace

    /** 按节奏等上一个动作停够(FAST 恒 0,不改当前响应速度)。 */
    private suspend fun paceWait() {
        val w = appPacing.waitBefore(SystemClock.elapsedRealtime())
        if (w > 0) delay(w)
    }

    /** 记下一个动作完成(节奏据此定下一步的停留)。 */
    private fun paceNote(newScreen: Boolean) {
        appPacing.noteAction(_state.value.app, SystemClock.elapsedRealtime(), newScreen = newScreen)
    }

    @Volatile private var display: AgentDisplay? = null
    // [fork/mibot-destiny] Multi-display pool: several hidden virtual displays may
    // run at once (vd_create → first display; createExtra → more). `display` stays
    // as the ACTIVE one for backwards-compatible callers; `displays` holds every live
    // AgentDisplay keyed by its displayId, and `activeDisplayId` names the one all
    // tools operate on.
    private val displays = LinkedHashMap<Int, AgentDisplay>()
    @Volatile private var activeDisplayId: Int = -1
    private val emptyFrames = MutableStateFlow<Bitmap?>(null)
    private val _framesFlow = MutableStateFlow<StateFlow<Bitmap?>>(emptyFrames)
    /** The active display's preview frames (switches when the active display changes). */
    val previewSource: StateFlow<StateFlow<Bitmap?>> = _framesFlow.asStateFlow()

    /** Ids of every live hidden display, insertion order. */
    val displayIds: List<Int> get() = synchronized(displays) { displays.keys.toList() }
    /** The active display id, or -1 when none. */
    val activeDisplay: Int get() = activeDisplayId
    /** Number of live hidden displays. */
    val displayCount: Int get() = synchronized(displays) { displays.size }

    private fun currentDisplay(): AgentDisplay? = synchronized(displays) { displays[activeDisplayId] ?: displays.values.firstOrNull() }

    private fun setActive(id: Int) {
        synchronized(displays) {
            if (displays.containsKey(id)) { activeDisplayId = id; display = displays[id]; _framesFlow.value = display!!.frames }
        }
    }

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
        // [fork/mibot-destiny] Multi-display: create() is the FIRST display (or returns it
        // when already up). Extra displays go through createExtra(). Kept for compat.
        if (current.mode != Mode.NONE && display != null) return@withContext Result.success(current)
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
            // S3: real resolution and dpi (Mika), portrait buffer; screenshots are downscaled separately.
            val (w, h, dpi) = displaySpec(app, width, height)
            val dmgr = app.getSystemService(DisplayManager::class.java)
            return@withContext runCatching {
                val d = AgentDisplay(shell, w, h, dpi, dmgr)
                synchronized(displays) { displays[d.displayId] = d }
                activeDisplayId = d.displayId
                display = d
                _framesFlow.value = d.frames
                val s = State(Mode.HELPER, d.displayId, d.width, d.height)
                _state.value = s
                log("vd_create: display ${d.displayId} ${w}x$h@$dpi (${phone.hub.source})")
                // S2: which display the ROM actually gave us (trusted or untrusted fallback, keep-awake, fullscreen).
                runCatching { shell.displayInfo(d.displayId) }.getOrNull()?.takeIf { !it.isEmpty }?.let { i ->
                    log("vd_create: trusted=${i.getBoolean("trusted")} ownFocus=${i.getBoolean("ownFocus")} awake=${i.getBoolean("awake")} " +
                        "fullscreen=${i.getBoolean("windowingModeSet")} group=${i.getInt("group", -1)} flags=0x${Integer.toHexString(i.getInt("flags"))}")
                }
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

    /**
     * [fork/mibot-destiny] Create an ADDITIONAL hidden display (the first one uses
     * [create]). Each extra display runs its own AgentDisplay with its own frames
     * and app. Returns the new display's state; the new display becomes active.
     */
    suspend fun createExtra(context: Context, width: Int = 0, height: Int = 0): Result<State> = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        val phone = MibotPhone.get(app)
        if (_state.value.mode != Mode.HELPER) {
            // No helper session yet — the first display must be up (HELPER mode).
            val first = create(context, width, height).getOrNull()
            if (first == null || first.mode != Mode.HELPER) {
                return@withContext Result.failure(IllegalStateException("multi-display needs the helper session; create the first display first"))
            }
        }
        val shell = phone.hub.shell.value ?: return@withContext Result.failure(IllegalStateException("helper not connected"))
        return@withContext runCatching {
            val (w, h, dpi) = displaySpec(app, width, height)
            val dmgr = app.getSystemService(DisplayManager::class.java)
            val d = AgentDisplay(shell, w, h, dpi, dmgr)
            synchronized(displays) { displays[d.displayId] = d }
            setActive(d.displayId)
            val s = State(Mode.HELPER, d.displayId, d.width, d.height)
            // Keep the session mode; the state's app/display reflect the active one.
            _state.value = _state.value.copy(mode = Mode.HELPER, displayId = d.displayId, width = d.width, height = d.height)
            log("vd_createExtra: display ${d.displayId} ${w}x$h@$dpi (${displays.size} live)")
            VdForegroundService.start(app)
            s
        }.onFailure { log("vd_createExtra failed: ${it.message}") }
    }

    /** [fork/mibot-destiny] Switch the ACTIVE display (all tools operate on it). */
    fun switchTo(displayId: Int): Boolean {
        synchronized(displays) {
            if (!displays.containsKey(displayId)) return false
            setActive(displayId)
            val d = displays[displayId]!!
            _state.value = _state.value.copy(mode = Mode.HELPER, displayId = displayId, width = d.width, height = d.height)
        }
        log("vd_switchTo: display $displayId (active; ${displays.size} live)")
        return true
    }

    /** [fork/mibot-destiny] Close ONE display; the last one also ends the session. */
    fun closeDisplay(context: Context, displayId: Int): Boolean {
        val d = synchronized(displays) { displays.remove(displayId) } ?: return false
        runCatching { d.release() }
        if (activeDisplayId == displayId) {
            val next = synchronized(displays) { displays.keys.firstOrNull() }
            if (next != null) setActive(next)
            else { activeDisplayId = -1; display = null; _framesFlow.value = emptyFrames }
        }
        if (synchronized(displays) { displays.isEmpty() }) {
            val was = _state.value.mode
            _state.value = State()
            a11yConfirmed = false
            if (was != Mode.NONE) log("vd_close (last display)")
            VdOverlay.hide(context.applicationContext)
            VdForegroundService.stop(context.applicationContext)
        } else {
            log("vd_closeDisplay: display $displayId closed (${synchronized(displays) { displays.size }} live)")
        }
        return true
    }

    fun close(context: Context) {
        val app = context.applicationContext
        // [fork/mibot-destiny] Close every live display.
        val all = synchronized(displays) { displays.values.toList() }
        all.forEach { runCatching { it.release() } }
        synchronized(displays) { displays.clear() }
        display = null
        activeDisplayId = -1
        _framesFlow.value = emptyFrames
        val was = _state.value.mode
        _state.value = State()
        a11yConfirmed = false
        if (was != Mode.NONE) log("vd_close")
        VdOverlay.hide(app)
        VdForegroundService.stop(app)
    }

    /** Size of the agent display for this phone: real resolution and dpi, portrait (see [AgentDisplaySpec]). */
    @Suppress("DEPRECATION")
    fun displaySpec(context: Context, width: Int = 0, height: Int = 0): Triple<Int, Int, Int> {
        val app = context.applicationContext
        val real = android.util.DisplayMetrics()
        val main = app.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        if (main != null) main.getRealMetrics(real) else real.setTo(app.resources.displayMetrics)
        return AgentDisplaySpec.of(real.widthPixels, real.heightPixels, real.densityDpi, width, height)
    }

    /** The active state; the agent display's size is read live because it rotates with landscape apps. */
    private fun requireActive(): State {
        val s = _state.value
        check(s.mode != Mode.NONE) { "No virtual display. Call vd_create first." }
        val d = currentDisplay()
        if (s.mode == Mode.HELPER && d != null) {
            val w = d.width
            val h = d.height
            if (w != s.width || h != s.height) {
                val n = s.copy(width = w, height = h)
                _state.value = n
                log("display ${s.displayId} rotated: ${w}x$h")
                return n
            }
        }
        return s
    }

    /** Current state with the live display size (for tools that report it). */
    fun current(): State = if (_state.value.mode == Mode.NONE) _state.value else requireActive()

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
                if (r == ShellService.START_OK) { _state.value = s.copy(app = packageName); paceNote(true) }
                log("vd_launch_app $packageName -> $msg")
                msg
            }
            Mode.A11Y -> {
                val intent = app.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@withContext "no launcher activity for $packageName"
                app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                _state.value = s.copy(app = packageName)
                paceNote(true)
                log("vd_launch_app (main screen) $packageName")
                "started $packageName on the main screen (accessibility mode)"
            }
            Mode.NONE -> "no display"
        }
    }

    /** Scale of the last screenshot (image = display × scale), so image points can be mapped back. */
    @Volatile var lastShotScale: Float = 1f
        private set

    /** JPEG of the agent display (or the main screen in A11Y mode), long side ≤ [maxSide]. */
    suspend fun screenshot(context: Context, maxSide: Int = 1280): ByteArray? = withContext(Dispatchers.Default) {
        val s = requireActive()
        val bmp: Bitmap? = when (s.mode) {
            Mode.HELPER -> {
                val d = currentDisplay()
                var b = d?.latestFrameBitmap()
                var tries = 0
                while (b == null && tries++ < 10) { delay(150); b = d?.latestFrameBitmap() }
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
        val scale = ShotScale.scale(bmp.width, bmp.height, maxSide)
        lastShotScale = scale
        val (sw, sh) = ShotScale.size(bmp.width, bmp.height, scale)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, sw, sh, true) else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        out.toByteArray()
    }

    // S5: accessibility first on the agent display, shell injection as the fallback (Mika's input model).
    // Every result names the path that worked, e.g. "tapped (300,500) via a11y", so logs answer which
    // path a given phone supports.

    private fun a11yOn(): Boolean = VdA11y.service()?.also { VdA11y.ensureFlags(it) } != null

    suspend fun tap(context: Context, x: Int, y: Int): String = withContext(Dispatchers.IO) {
        paceWait()
        val s = requireActive()
        val app = context.applicationContext
        if (x < 0 || y < 0 || x >= s.width || y >= s.height) return@withContext "tap failed: ($x,$y) is outside the display (${s.width}x${s.height})"
        askIfSensitive(app, s, x, y, null)?.let { return@withContext it }
        val sh = if (s.mode == Mode.HELPER) helper(app) else null
        val order = VdInputOrder.tap(a11y = a11yOn(), helper = sh != null)
        val r = VdInputOrder.firstOk(order, listOf(
            VdInputOrder.Step(VdInputOrder.A11Y_GESTURE) { VdA11y.taps(s.displayId, x.toFloat(), y.toFloat()) },
            VdInputOrder.Step(VdInputOrder.NODE_CLICK) { VdA11y.clickAt(s.displayId, x, y) },
            VdInputOrder.Step(VdInputOrder.SHELL) { sh?.injectTap(s.displayId, x, y) == true },
        ))
        log("vd_tap $x,$y -> ${r.describe()}")
        if (r.ok) { paceNote(false); "tapped ($x,$y) via ${r.describe()}" } else "tap failed (${r.describe()})"
    }

    suspend fun swipe(context: Context, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String = withContext(Dispatchers.IO) {
        paceWait()
        val s = requireActive()
        val dur = durationMs.coerceIn(80, 3_000).toLong()
        val sh = if (s.mode == Mode.HELPER) helper(context) else null
        val order = VdInputOrder.swipe(a11y = a11yOn(), helper = sh != null)
        val r = VdInputOrder.firstOk(order, listOf(
            VdInputOrder.Step(VdInputOrder.A11Y_GESTURE) { VdA11y.line(s.displayId, listOf(x1 to y1, x2 to y2), dur) },
            VdInputOrder.Step(VdInputOrder.SHELL) {
                val n = (dur / 16).toInt().coerceIn(4, 120)
                val xs = IntArray(n + 1) { x1 + (x2 - x1) * it / n }
                val ys = IntArray(n + 1) { y1 + (y2 - y1) * it / n }
                val ts = LongArray(n + 1) { dur * it / n }
                sh?.injectStroke(s.displayId, xs, ys, ts) == true
            },
        ))
        log("vd_swipe $x1,$y1→$x2,$y2 ${dur}ms -> ${r.describe()}")
        if (r.ok) { paceNote(false); "swiped via ${r.describe()}" } else "swipe failed (${r.describe()})"
    }

    suspend fun type(context: Context, text: String, submit: Boolean): String = withContext(Dispatchers.IO) {
        paceWait()
        val s = requireActive()
        val app = context.applicationContext
        askIfSensitive(app, s, null, null, text)?.let { return@withContext it }
        val sh = if (s.mode == Mode.HELPER) helper(app) else null
        val a11y = a11yOn()
        val field = if (a11y) VdA11y.focusedEditable(s.displayId) else null
        // Web editors (contenteditable) only take real input: SET_TEXT changes the page text without
        // input events, so they go straight to paste when the helper can do it. Passwords never use the clipboard.
        val web = field?.let { VdA11y.inWebView(it) } == true
        val password = field?.isPassword == true
        val ascii = text.all { it.code in 32..126 } && text.length <= 200
        var order = VdInputOrder.type(a11y = a11y, hasField = field != null, helper = sh != null, ascii = ascii)
        if (web && !password && sh != null) order = order.filter { it != VdInputOrder.SET_TEXT } + VdInputOrder.SET_TEXT
        if (password) order = order.filter { it != VdInputOrder.PASTE }
        val r = VdInputOrder.firstOk(order, listOf(
            VdInputOrder.Step(VdInputOrder.SET_TEXT) { field?.let { VdA11y.setTextVerified(it, text) } == true },
            VdInputOrder.Step(VdInputOrder.PASTE) { sh != null && paste(sh, s.displayId, field, text) },
            VdInputOrder.Step(VdInputOrder.KEYS) { sh?.injectText(s.displayId, text) == true },
        ))
        var msg = if (r.ok) "typed ${text.length} chars via ${r.describe()}" else
            "type failed (${r.describe()}${if (field == null) "; no focused input field on the display — tap the field first" else ""})"
        if (r.ok) { paceNote(false); if (submit) msg += "; " + key(app, "ENTER") }
        log("vd_type (${text.length} chars) -> ${r.describe()}")
        msg
    }

    /** Clipboard (through the helper, restored afterwards) → ACTION_PASTE on the field, or Ctrl+A/Ctrl+V on the display. */
    private suspend fun paste(sh: IMibotShell, displayId: Int, field: AccessibilityNodeInfo?, text: String): Boolean {
        if (runCatching { sh.setClipText(text) }.getOrDefault(ShellService.CLIP_FAILED) != ShellService.CLIP_SET) return false
        try {
            if (field != null) {
                val before = field.text?.toString()
                VdA11y.selectAll(field)
                if (VdA11y.paste(field)) {
                    // The app reads the clipboard asynchronously: wait for the field to change before restoring.
                    if (VdA11y.awaitText(field, text)) return true
                    field.refresh()
                    if (field.text?.toString() != before) return true
                }
            }
            sh.injectCtrlKey(displayId, KeyEvent.KEYCODE_A)
            val ok = sh.injectCtrlKey(displayId, KeyEvent.KEYCODE_V)
            delay(300)
            return ok
        } finally {
            runCatching { sh.restoreClip() }
        }
    }

    suspend fun key(context: Context, name: String): String = withContext(Dispatchers.IO) {
        paceWait()
        val s = requireActive()
        val code = KEYS[name.uppercase().removePrefix("KEYCODE_")] ?: name.toIntOrNull()
            ?: return@withContext "unknown key '$name' (allowed: ${KEYS.keys.joinToString()})"
        val sh = if (s.mode == Mode.HELPER) helper(context) else null
        val a11y = a11yOn()
        val field = if (a11y && code == KeyEvent.KEYCODE_ENTER) VdA11y.focusedEditable(s.displayId) else null
        val order = when (code) {
            KeyEvent.KEYCODE_BACK -> VdInputOrder.back(a11y = a11y, displayTopFocused = a11y && VdA11y.isTopFocused(s.displayId), helper = sh != null)
            KeyEvent.KEYCODE_ENTER -> VdInputOrder.enter(a11y = a11y, hasField = field != null, helper = sh != null)
            else -> VdInputOrder.key(helper = sh != null)
        }
        val r = VdInputOrder.firstOk(order, listOf(
            VdInputOrder.Step(VdInputOrder.GLOBAL_BACK) { VdA11y.globalBack() },
            VdInputOrder.Step(VdInputOrder.IME_ENTER) { field?.let { VdA11y.imeEnter(it) } == true },
            VdInputOrder.Step(VdInputOrder.SHELL) { sh?.injectKey(s.displayId, code) == true },
        ))
        log("vd_key $name -> ${r.describe()}")
        if (r.ok) { paceNote(code == KeyEvent.KEYCODE_BACK); "pressed $name via ${r.describe()}" } else "key failed (${r.describe()})"
    }

    /** Compact accessibility tree of the agent display (needs Mibot's accessibility service). */
    fun uiDump(context: Context, maxNodes: Int = 250): String {
        val s = requireActive()
        val svc = MinisAccessibilityService.getInstance()
            ?: return "Accessibility is off, so the UI tree can't be read. Use vd_screenshot (the model reads the image) or enable Mibot's accessibility service."
        VdA11y.ensureFlags(svc)
        val roots: List<AccessibilityNodeInfo> = VdA11y.roots(s.displayId)
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
            val focus = VdA11y.focusedEditable(s.displayId)
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

