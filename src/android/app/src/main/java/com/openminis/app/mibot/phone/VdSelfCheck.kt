/*
 * Adapted from Mika (https://github.com/wangzan101/Mika), app/src/main/java/com/wangzan101/mika/selfcheck/AndroidSelfCheck.kt,
 * selfcheck/SelfCheckText.kt and cap/phone/display/FrameMath.kt @ 6661b9e
 * Copyright 2026 wangzan101 — Licensed under the Apache License, Version 2.0.
 * Modifications for Mibot (com.dl1991.mibot), distributed under GPL-3.0-or-later:
 * reduced to the virtual-display checks (pairing, helper, display trust/keep-awake/windowing,
 * Settings launch, frame grab, shell tap, accessibility tap, typing read-back); runs against the
 * system Settings app instead of Mika's SelfCheckTargetActivity; Mibot's own advice texts.
 */
package com.openminis.app.mibot.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.openminis.app.BuildConfig
import com.openminis.app.accessibility.MinisAccessibilityService
import com.openminis.app.mibot.design.tc
import com.openminis.app.offload.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 「虚拟屏自检」: one run answers which of the known virtual-screen failures this phone hits
 * (display refused / untrusted fallback, display asleep, ROM blocks shell injection, typing lost).
 * It creates its own agent display, opens the system Settings app on it (only looks, taps one
 * list entry and goes back, types a probe word into Settings search), then releases the display.
 * Reports carry states and numbers only, no user content.
 */
object VdSelfCheck {
    enum class Status { PASS, WARN, FAIL, SKIP }

    data class Check(val id: String, val title: String, val status: Status, val detail: String, val advice: String? = null)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _results = MutableStateFlow<List<Check>>(emptyList())
    val results: StateFlow<List<Check>> = _results.asStateFlow()

    @Volatile private var header: String = ""

    /** Runs every check in order; later checks are skipped with a reason when a prerequisite failed. */
    suspend fun run(context: Context): List<Check> {
        if (_running.value) return _results.value
        _running.value = true
        _results.value = emptyList()
        val app = context.applicationContext
        header = facts(app)
        VdController.log("selfcheck: start")
        val run = Run(app)
        try {
            run.all()
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { run.cleanup() }
            VdController.log("selfcheck: done")
            _running.value = false
        }
        return _results.value
    }

    /** Plain-text report for the copy-log button: device facts, every check, the recent control log. */
    fun reportText(context: Context): String {
        val sb = StringBuilder()
        sb.append(tc(context, "Mibot 虚拟屏自检", "Mibot virtual screen self-check")).append('\n')
        sb.append(header.ifEmpty { facts(context.applicationContext) }).append('\n')
        for (c in _results.value) {
            sb.append('[').append(c.status).append("] ").append(c.id).append(" · ").append(c.title).append(" — ").append(c.detail).append('\n')
            c.advice?.let { sb.append("    → ").append(it).append('\n') }
        }
        val log = VdController.log.value.takeLast(LOG_TAIL)
        if (log.isNotEmpty()) {
            sb.append("--- log ---\n")
            log.forEach { sb.append(it).append('\n') }
        }
        return sb.toString()
    }

    private fun facts(app: Context): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val m = app.resources.displayMetrics
        return "$ts · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT}) · " +
            "screen ${m.widthPixels}x${m.heightPixels}@${m.densityDpi} · Mibot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun add(c: Check) {
        _results.value = _results.value + c
        VdController.log("selfcheck: ${c.id} ${c.status} ${c.detail}")
    }

    private class Run(val app: Context) {
        val phone = MibotPhone.get(app)
        var shell: IMibotShell? = null
        var display: AgentDisplay? = null
        var settings: String? = null
        var launched = false

        fun t(zh: String, en: String) = tc(app, zh, en)

        val adviceInject get() = t(
            "系统拦截了 shell 注入输入。小米/HyperOS：开发者选项 → 打开「USB 调试（安全设置）」；其他系统查找类似开关。也可依靠无障碍操作。",
            "The system blocks shell input injection. Xiaomi/HyperOS: Developer options → turn on “USB debugging (Security settings)”; other ROMs may have a similar switch. Accessibility can act instead.",
        )
        val adviceA11y get() = t(
            "请在系统无障碍设置里开启 Mibot 的无障碍服务（或在本页「开启 Mibot 无障碍服务」）。",
            "Turn on Mibot's accessibility service in system Accessibility settings (or “Enable Mibot's accessibility service” on this page).",
        )
        val adviceHelper get() = t(
            "先完成本页的无线调试配对，或授权 Shizuku，然后再自检。",
            "Pair Wireless Debugging on this page (or grant Shizuku), then run the check again.",
        )

        suspend fun all() {
            add(pairing())
            val h = helper().also { add(it) }
            if (h.status == Status.FAIL) return skipRest("display", t("助手未运行", "helper not running"))
            val d = display().also { add(it) }
            if (d.status == Status.FAIL) return skipRest("launch", t("虚拟屏未建成", "no display"))
            val l = launch().also { add(it) }
            add(frame())
            if (l.status == Status.FAIL) return skipRest("shellTap", t("设置没有在虚拟屏上打开", "Settings did not open on the display"))
            add(shellTap())
            add(a11yTap())
            add(typing())
        }

        private val order = listOf("pairing", "helper", "display", "launch", "frame", "shellTap", "a11yTap", "typing")

        fun skipRest(from: String, why: String) {
            order.drop(order.indexOf(from)).filter { id -> id != "frame" || _results.value.none { it.id == "frame" } }
                .forEach { add(Check(it, title(it), Status.SKIP, why)) }
        }

        fun title(id: String) = when (id) {
            "pairing" -> t("无线调试配对", "Wireless Debugging pairing")
            "helper" -> t("助手进程", "Helper alive")
            "display" -> t("建虚拟屏", "Display created")
            "launch" -> t("在虚拟屏打开设置", "Open Settings on the display")
            "frame" -> t("取画面", "Frame grab")
            "shellTap" -> t("shell 点按", "Shell tap")
            "a11yTap" -> t("无障碍点按", "Accessibility tap")
            "typing" -> t("输入并回读", "Typing read-back")
            else -> id
        }

        fun check(id: String, status: Status, detail: String, advice: String? = null) = Check(id, title(id), status, detail, advice)

        // ---------------------------------------------------------- pairing

        fun pairing(): Check {
            if (!phone.supported) return check("pairing", Status.FAIL, "needs Android 12+, this is sdk ${Build.VERSION.SDK_INT}",
                t("本机系统版本不支持无线调试助手；可用 Shizuku 或电脑 USB 启动。", "This Android version can't run the Wireless Debugging helper; use Shizuku or start it from a PC."))
            val engine = phone.builtin.engine
            val st = engine.status.value
            val connected = phone.hub.shell.value != null
            val shizuku = ShizukuManager.snapshot.value.state == ShizukuManager.State.READY
            val detail = "paired ${if (engine.paired) "yes" else "no"}, status ${st.javaClass.simpleName}" +
                (if (connected) ", helper connected via ${phone.hub.source}" else "") + (if (shizuku) ", Shizuku ready" else "")
            return when {
                engine.paired || connected -> check("pairing", Status.PASS, detail)
                shizuku -> check("pairing", Status.WARN, detail, t("未配对，将用 Shizuku 启动助手。", "Not paired; Shizuku will start the helper."))
                else -> check("pairing", Status.FAIL, detail, adviceHelper)
            }
        }

        // ---------------------------------------------------------- helper

        suspend fun helper(): Check {
            var sh = phone.hub.shell.value
            if (sh == null && phone.supported) {
                phone.builtin.need()
                if (ShizukuManager.isReady()) phone.launchViaShizuku()
                sh = withTimeoutOrNull(HELPER_WAIT_MS) { phone.hub.shell.first { it != null } }
            }
            val conn: IMibotShell = sh
                ?: return check("helper", Status.FAIL, "not running (${phone.builtin.engine.status.value.javaClass.simpleName})", adviceHelper)
            shell = conn
            return withContext(Dispatchers.IO) {
                runCatching {
                    val uid = conn.identity().getOrNull(0) ?: -1
                    val times = List(LATENCY_CALLS) {
                        val t0 = SystemClock.elapsedRealtimeNanos()
                        conn.identity()
                        (SystemClock.elapsedRealtimeNanos() - t0) / 1_000
                    }.sorted()
                    val who = when (uid) { SHELL_UID -> "shell"; ROOT_UID -> "root"; else -> "uid $uid" }
                    check("helper", Status.PASS, "running as $who via ${phone.hub.source}, round trip ${times[times.size / 2]} µs")
                }.getOrElse { check("helper", Status.FAIL, "binder call failed (${it.javaClass.simpleName})", adviceHelper) }
            }
        }

        // ---------------------------------------------------------- display

        suspend fun display(): Check {
            val sh = shell ?: return check("display", Status.FAIL, "no helper", adviceHelper)
            // Same size rule as VdController.create, so the check sees what the agent gets.
            val (w, h, dpi) = VdController.displaySpec(app)
            val dmgr = app.getSystemService(android.hardware.display.DisplayManager::class.java)
            val made = withContext(NonCancellable + Dispatchers.IO) { runCatching { AgentDisplay(sh, w, h, dpi, dmgr) }.onSuccess { display = it } }
            val d = made.getOrElse {
                return check("display", Status.FAIL, "createDisplay failed (${it.message ?: it.javaClass.simpleName})",
                    t("系统拒绝了可信和普通两种虚拟屏。请复制日志发给开发者。", "The system refused both the trusted and the plain virtual display. Copy the log and send it to the developer."))
            }
            val info: Bundle? = withContext(Dispatchers.IO) { runCatching { sh.displayInfo(d.displayId) }.getOrNull() }
            if (info == null || info.isEmpty) {
                return check("display", Status.WARN, "display #${d.displayId} ${w}x$h@$dpi created; helper has no displayInfo (older helper)",
                    t("助手是旧版本：在本页「断开助手」后重新连接。", "The helper is an older build: “Disconnect helper” on this page, then reconnect."))
            }
            val trusted = info.getBoolean("trusted")
            val ownFocus = info.getBoolean("ownFocus")
            val awake = info.getBoolean("awake")
            val fullscreen = info.getBoolean("windowingModeSet")
            val group = info.getInt("group", -1)
            val rotation = info.getInt("rotation", -1)
            val sdk = Build.VERSION.SDK_INT
            val detail = "display #${d.displayId} ${w}x$h@$dpi, " +
                (if (sdk < 33) "plain (Android 12 has no trusted display)" else if (trusted) "trusted" else "UNTRUSTED (fallback)") +
                ", own focus ${yn(ownFocus)}, group ${if (group < 0) "?" else group}, keep-awake ${if (awake) "on" else "off"}" +
                ", fullscreen ${yn(fullscreen)}, rotation ${if (rotation < 0) "?" else rotation}, flags 0x${Integer.toHexString(info.getInt("flags"))}"
            return when {
                sdk >= 33 && !trusted -> check("display", Status.WARN, detail, t(
                    "系统不给 shell 建可信屏的权限，已退回普通虚拟屏：它没有独立焦点，打字可能落不到虚拟屏，主屏焦点也可能受影响。",
                    "The system won't let shell create a trusted display, so a plain one was used: it has no focus of its own, typing may not reach it and the main screen's focus may be affected."))
                sdk >= 33 && !awake -> check("display", Status.WARN, detail, t(
                    "保持唤醒不可用：到息屏时间后虚拟屏可能休眠（截图不变、点按无效）。",
                    "Keep-awake is unavailable: after the screen-off timeout the display may sleep (frozen screenshots, taps do nothing)."))
                !fullscreen -> check("display", Status.WARN, detail, t(
                    "没能设成全屏窗口模式：应用可能以小窗打开，坐标会偏。",
                    "Fullscreen windowing could not be set: apps may open as small windows and coordinates may be off."))
                else -> check("display", Status.PASS, detail)
            }
        }

        // ---------------------------------------------------------- launch Settings

        suspend fun launch(): Check = withContext(Dispatchers.IO) {
            val sh = shell!!
            val d = display!!
            val pkg = Intent(Settings.ACTION_SETTINGS).resolveActivity(app.packageManager)?.packageName ?: "com.android.settings"
            settings = pkg
            val r = runCatching { sh.startApp(d.displayId, pkg) }.getOrDefault(ShellService.START_ERROR)
            when (r) {
                ShellService.START_OK -> Unit
                ShellService.START_IN_USE_ON_MAIN -> return@withContext check("launch", Status.FAIL, "Settings is open on your screen (never taken over)",
                    t("先把主屏上的「设置」退到后台，再自检。", "Leave Settings on your screen first, then run the check again."))
                ShellService.START_IN_USE_BY_OTHER -> return@withContext check("launch", Status.FAIL, "Settings is on another agent display",
                    t("先在本页「停止」当前虚拟屏，再自检。", "Stop the current hidden display on this page first, then run the check again."))
                ShellService.START_NOT_FOUND -> return@withContext check("launch", Status.FAIL, "no launcher entry for $pkg")
                else -> return@withContext check("launch", Status.FAIL, "start failed ($r)")
            }
            val onTop = poll(LAUNCH_WAIT_MS) { topPackage() == pkg }
            launched = onTop
            if (onTop) check("launch", Status.PASS, "Settings opened on display #${d.displayId}")
            else check("launch", Status.FAIL, "started, but top of the display is ${topPackage() ?: "nothing"}",
                t("应用没有出现在虚拟屏上：系统可能把它开到了主屏，或阻止后台启动。", "The app did not appear on the hidden display: the system may have opened it on the main screen or blocked the background start."))
        }

        fun topPackage(): String? = runCatching { shell?.topActivity(display!!.displayId) }.getOrNull()?.substringBefore('/')

        // ---------------------------------------------------------- frame

        suspend fun frame(): Check {
            val d = display ?: return check("frame", Status.SKIP, "no display")
            var bmp: Bitmap? = null
            val deadline = SystemClock.uptimeMillis() + FRAME_WAIT_MS
            var blank = true
            var spread = 0
            while (SystemClock.uptimeMillis() < deadline) {
                bmp?.recycle()
                bmp = d.latestFrameBitmap()
                val b = bmp
                if (b != null) {
                    val grid = SelfCheckMath.lumaGrid(b.width, b.height, b::getPixel)
                    spread = grid.max() - grid.min()
                    blank = SelfCheckMath.isBlack(b.width, b.height, b::getPixel) || spread < SelfCheckMath.BLANK_SPREAD
                    if (!blank) break
                }
                delay(POLL_MS)
            }
            val b = bmp ?: return check("frame", Status.FAIL, "no frame from the display within ${FRAME_WAIT_MS / 1000} s",
                t("虚拟屏没有出画面：可能已休眠或系统阻止了截取。", "No picture from the hidden display: it may be asleep or the system blocks capture."))
            val size = "${b.width}x${b.height}"
            b.recycle()
            return if (blank) check("frame", Status.FAIL, "frame $size is blank or black (luma spread $spread)",
                t("画面全黑/空白：应用用了防截屏（FLAG_SECURE）或虚拟屏已休眠。", "Black/blank frames: the app uses FLAG_SECURE or the display is asleep."))
            else check("frame", Status.PASS, "frame $size, luma spread $spread")
        }

        // ---------------------------------------------------------- a11y helpers

        fun a11y(): MinisAccessibilityService? = MinisAccessibilityService.getInstance()

        fun roots(): List<AccessibilityNodeInfo> {
            val svc = a11y() ?: return emptyList()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
            val id = display?.displayId ?: return emptyList()
            return runCatching { (svc.windowsOnAllDisplays.get(id) ?: emptyList()).mapNotNull { it.root } }.getOrDefault(emptyList())
        }

        fun nodes(): List<AccessibilityNodeInfo> {
            val out = ArrayList<AccessibilityNodeInfo>()
            fun walk(n: AccessibilityNodeInfo) {
                if (out.size >= MAX_NODES) return
                out += n
                for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
            }
            roots().forEach { walk(it) }
            return out
        }

        /** What is on the display now: top activity + visible texts. Changes when a tap navigates. */
        fun signature(): String {
            val texts = nodes().filter { it.isVisibleToUser }.mapNotNull { (it.text ?: it.contentDescription)?.toString() }.take(60)
            return (topActivityName() ?: "") + "|" + texts.joinToString("|")
        }

        fun topActivityName(): String? = runCatching { shell?.topActivity(display!!.displayId) }.getOrNull()

        /** A Settings list entry: clickable, visible, below the top third (not the toolbar or search bar), not editable. */
        fun tapTarget(): Pair<AccessibilityNodeInfo, Rect>? {
            val d = display ?: return null
            val r = Rect()
            val candidates = nodes().filter { it.isClickable && it.isVisibleToUser && !it.isEditable }.mapNotNull { n ->
                n.getBoundsInScreen(r)
                if (r.width() > 8 && r.height() > 8 && r.centerY() in d.height / 3 until d.height * 9 / 10 && r.centerX() in 0 until d.width) n to Rect(r) else null
            }
            return candidates.minByOrNull { it.second.top }
        }

        fun label(n: AccessibilityNodeInfo): String {
            fun first(x: AccessibilityNodeInfo, depth: Int): String? {
                (x.text ?: x.contentDescription)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
                if (depth > 3) return null
                for (i in 0 until x.childCount) x.getChild(i)?.let { c -> first(c, depth + 1)?.let { return it } }
                return null
            }
            return first(n, 0)?.take(24) ?: "item"
        }

        /** Back to the Settings home page on the display, as a fresh task. */
        suspend fun resetSettings() {
            val id = display?.displayId ?: return
            val pkg = settings ?: return
            withContext(Dispatchers.IO) {
                // NEW_TASK | CLEAR_TASK: start over instead of paging back.
                runCatching { shell?.runCommand("am start --display $id -W -a ${Settings.ACTION_SETTINGS} -f 0x10008000") }
            }
            poll(LAUNCH_WAIT_MS) { topPackage() == pkg }
            delay(SETTLE_MS)
        }

        // ---------------------------------------------------------- shell tap

        suspend fun shellTap(): Check = withContext(Dispatchers.IO) {
            val sh = shell!!
            val d = display!!
            delay(SETTLE_MS)
            val target = tapTarget()
            if (target == null) {
                // No accessibility (or no tree): the injection result is all we can see.
                val ok = sh.injectTap(d.displayId, d.width / 2, d.height / 2)
                return@withContext if (!ok) check("shellTap", Status.FAIL, "injection rejected", adviceInject)
                else check("shellTap", Status.WARN, "injected at the centre; effect not verifiable (no accessibility tree on the display)", adviceA11y)
                    .also { resetSettings() }
            }
            val (node, r) = target
            val name = label(node)
            val before = signature()
            val ok = sh.injectTap(d.displayId, r.centerX(), r.centerY())
            if (!ok) return@withContext check("shellTap", Status.FAIL, "injection rejected (tap on \"$name\")", adviceInject)
            val changed = poll(CHANGE_WAIT_MS) { signature() != before }
            val result = if (changed) check("shellTap", Status.PASS, "tapped \"$name\" at ${r.centerX()},${r.centerY()}; screen changed")
            else check("shellTap", Status.FAIL, "injected a tap on \"$name\" but nothing changed", adviceInject)
            resetSettings()
            result
        }

        // ---------------------------------------------------------- accessibility tap

        suspend fun a11yTap(): Check = withContext(Dispatchers.IO) {
            val svc = a11y() ?: return@withContext check("a11yTap", Status.FAIL, "accessibility service not connected", adviceA11y)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext check("a11yTap", Status.SKIP, "needs Android 11+")
            val d = display!!
            val target = tapTarget() ?: return@withContext check("a11yTap", Status.FAIL, "no windows of display #${d.displayId} in the accessibility tree",
                t("无障碍读不到虚拟屏：在系统无障碍设置里关掉再打开 Mibot 的服务。", "Accessibility can't see the hidden display: turn Mibot's service off and on again in Accessibility settings."))
            val (node, r) = target
            val name = label(node)
            var before = signature()
            val gestured = gesture(svc, d.displayId, r.centerX().toFloat(), r.centerY().toFloat())
            if (gestured && poll(CHANGE_WAIT_MS) { signature() != before }) {
                resetSettings()
                return@withContext check("a11yTap", Status.PASS, "gesture on display #${d.displayId} tapped \"$name\"; screen changed")
            }
            // Gesture missed: try the node action, which does not depend on input injection at all.
            resetSettings()
            val again = tapTarget()
            before = signature()
            val clicked = again != null && again.first.performAction(AccessibilityNodeInfo.ACTION_CLICK) &&
                poll(CHANGE_WAIT_MS) { signature() != before }
            resetSettings()
            val g = if (gestured) "gesture dispatched but nothing changed" else "gesture cancelled"
            if (clicked) check("a11yTap", Status.WARN, "$g; ACTION_CLICK on \"${again?.let { label(it.first) }}\" worked",
                t("无障碍手势不生效，但节点点击可用。", "Accessibility gestures don't land, but node clicks work."))
            else check("a11yTap", Status.FAIL, "$g; ACTION_CLICK did not change the screen either", adviceA11y)
        }

        fun gesture(svc: AccessibilityService, displayId: Int, x: Float, y: Float): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            val path = Path().apply { moveTo(x, y) }
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS))
                .setDisplayId(displayId)
                .build()
            val done = CountDownLatch(1)
            val ok = BooleanArray(1)
            val sent = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { ok[0] = true; done.countDown() }
                override fun onCancelled(gestureDescription: GestureDescription?) { done.countDown() }
            }, null)
            if (!sent) return false
            done.await(GESTURE_WAIT_MS, TimeUnit.MILLISECONDS)
            return ok[0]
        }

        // ---------------------------------------------------------- typing

        fun editable(): AccessibilityNodeInfo? {
            val all = nodes().filter { it.isEditable && it.isVisibleToUser }
            return all.firstOrNull { it.isFocused } ?: all.firstOrNull()
        }

        fun searchEntry(): AccessibilityNodeInfo? = nodes().firstOrNull { n ->
            val s = listOfNotNull(n.text, n.contentDescription, n.viewIdResourceName).joinToString(" ").lowercase(Locale.ROOT)
            n.isVisibleToUser && ("search" in s || "搜索" in s || "搜尋" in s)
        }?.let { clickableAncestor(it) }

        fun clickableAncestor(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
            var cur: AccessibilityNodeInfo? = n
            var depth = 0
            while (cur != null && depth++ < 6) {
                if (cur.isClickable) return cur
                cur = cur.parent
            }
            return n
        }

        suspend fun typing(): Check = withContext(Dispatchers.IO) {
            val sh = shell!!
            val d = display!!
            if (a11y() == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return@withContext check("typing", Status.SKIP, "reading the field back needs Mibot's accessibility service", adviceA11y)
            }
            var field = editable()
            if (field == null) {
                val entry = searchEntry() ?: return@withContext check("typing", Status.SKIP, "no search field found in Settings")
                if (!entry.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    val r = Rect().also { entry.getBoundsInScreen(it) }
                    sh.injectTap(d.displayId, r.centerX(), r.centerY())
                }
                poll(FIELD_WAIT_MS) { editable() != null }
                field = editable() ?: return@withContext check("typing", Status.SKIP, "Settings search opened no text field")
            }
            if (!field.isFocused) {
                field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                field.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(SETTLE_MS)
            }
            // 1) What vd_type uses today: shell key events (now WAIT_FOR_RESULT, so a dropped key is a failure).
            val keysOk = sh.injectText(d.displayId, PROBE_KEYS)
            val keysRead = keysOk && poll(CHANGE_WAIT_MS) { editable()?.text?.toString()?.let { SelfCheckMath.contains(it, PROBE_KEYS) } == true }
            if (keysRead) {
                sh.injectKey(d.displayId, KeyEvent.KEYCODE_BACK)
                return@withContext check("typing", Status.PASS, "shell key typing reached the field and read back")
            }
            // 2) Accessibility SET_TEXT, independent of focus and injection.
            val f = editable()
            val setOk = f != null && a11y()?.setNodeText(f, PROBE_SET) == true
            val setRead = setOk && poll(CHANGE_WAIT_MS) { editable()?.text?.toString()?.let { SelfCheckMath.contains(it, PROBE_SET) } == true }
            val keys = if (!keysOk) "shell key injection rejected" else "shell keys sent but the field did not show them"
            if (setRead) check("typing", Status.WARN, "$keys; accessibility SET_TEXT worked",
                (if (!keysOk) adviceInject + " " else "") + t("打字可改用无障碍直接写入。", "Typing can use accessibility SET_TEXT instead."))
            else check("typing", Status.FAIL, "$keys; accessibility SET_TEXT ${if (setOk) "did not read back" else "failed"}",
                if (!keysOk) adviceInject else t("输入没有落到虚拟屏的输入框：虚拟屏可能没有独立焦点（见「建虚拟屏」）。", "Input did not reach the field on the hidden display: it may have no focus of its own (see “Display created”)."))
        }

        suspend fun cleanup() {
            display?.let { runCatching { it.release() } }
            display = null
        }
    }

    private suspend fun poll(timeoutMs: Long, cond: suspend () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            if (runCatching { cond() }.getOrDefault(false)) return true
            if (SystemClock.uptimeMillis() >= end) return false
            delay(POLL_MS)
        }
    }

    private fun yn(b: Boolean) = if (b) "yes" else "no"

    private const val HELPER_WAIT_MS = 20_000L
    private const val LAUNCH_WAIT_MS = 5_000L
    private const val FRAME_WAIT_MS = 5_000L
    private const val CHANGE_WAIT_MS = 3_000L
    private const val FIELD_WAIT_MS = 4_000L
    private const val GESTURE_WAIT_MS = 3_000L
    private const val SETTLE_MS = 600L
    private const val POLL_MS = 150L
    private const val TAP_MS = 60L
    private const val LATENCY_CALLS = 5
    private const val MAX_NODES = 400
    private const val LOG_TAIL = 60
    private const val SHELL_UID = 2000
    private const val ROOT_UID = 0
    private const val PROBE_KEYS = "mibot42"
    private const val PROBE_SET = "mibot43"
}

/** Pure helpers for the self-check (frame blankness, text match), unit-tested. From Mika's FrameMath / SelfCheckText. */
internal object SelfCheckMath {
    private const val GRID_W = 12
    private const val GRID_H = 24
    private const val CELL_SAMPLES = 4
    private const val BLACK_SAMPLES = 24
    private const val BLACK_LEVEL = 8
    const val BLANK_SPREAD = 8
    private val SPACES = Regex("[\\s\\p{Z}]+")

    private fun luma(p: Int): Int = ((p shr 16 and 0xff) * 3 + (p shr 8 and 0xff) * 6 + (p and 0xff)) / 10

    /** Mean luma of a 12×24 grid of cells, each sampled 4×4. */
    fun lumaGrid(width: Int, height: Int, pixel: (Int, Int) -> Int): IntArray {
        val out = IntArray(GRID_W * GRID_H)
        val cw = width / GRID_W
        val ch = height / GRID_H
        if (cw == 0 || ch == 0) return out
        for (gy in 0 until GRID_H) for (gx in 0 until GRID_W) {
            var sum = 0L
            for (sy in 0 until CELL_SAMPLES) for (sx in 0 until CELL_SAMPLES) {
                sum += luma(pixel(gx * cw + sx * cw / CELL_SAMPLES, gy * ch + sy * ch / CELL_SAMPLES))
            }
            out[gy * GRID_W + gx] = (sum / (CELL_SAMPLES * CELL_SAMPLES)).toInt()
        }
        return out
    }

    /** ≥ 99.5 % of a 24×24 sample is (near) black. */
    fun isBlack(width: Int, height: Int, pixel: (Int, Int) -> Int): Boolean {
        if (width <= 1 || height <= 1) return false
        var dark = 0
        val total = BLACK_SAMPLES * BLACK_SAMPLES
        for (gy in 0 until BLACK_SAMPLES) for (gx in 0 until BLACK_SAMPLES) {
            val p = pixel(gx * (width - 1) / (BLACK_SAMPLES - 1), gy * (height - 1) / (BLACK_SAMPLES - 1))
            if ((p shr 16 and 0xff) <= BLACK_LEVEL && (p shr 8 and 0xff) <= BLACK_LEVEL && (p and 0xff) <= BLACK_LEVEL) dark++
        }
        return dark * 1000 >= total * 995
    }

    fun normalize(s: String): String = s.replace(SPACES, " ").trim().lowercase(Locale.ROOT)

    fun contains(text: String, part: String): Boolean = normalize(text).contains(normalize(part))
}
