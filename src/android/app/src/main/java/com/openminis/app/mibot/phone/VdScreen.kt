package com.openminis.app.mibot.phone

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openminis.app.accessibility.MinisAccessibilityService
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.PermState
import com.openminis.app.mibot.design.PermissionRow
import com.openminis.app.mibot.design.PermissionSummary
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotIcon
import com.openminis.app.mibot.design.MibotRadius
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotSpace
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.design.tc
import com.openminis.app.mibot.vault.VaultConfirmBus
import com.openminis.app.offload.ShizukuManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

object VdRoutes { const val PAGE = "mibot_virtual_display" }

/** Debug/CI: when set, the page creates a display and starts Settings on it once the helper is connected. */
object VdDemo { @Volatile var autoDemo = false }

private fun Context.openFirst(intents: List<Intent>) {
    for (i in intents) {
        if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

/**
 * [fork/mibot-v3] 后台操控 / 虚拟屏: permission status with one-tap fixes, the
 * Wireless Debugging pairing wizard (primary), Shizuku (alternative),
 * accessibility (fallback), a live preview of the hidden display, a log, Stop.
 */
@Composable
fun VdScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val phone = remember { MibotPhone.get(ctx) }
    val scope = rememberCoroutineScope()
    val status by phone.builtin.engine.status.collectAsState()
    val discovery by phone.builtin.engine.pairingDiscovery.collectAsState()
    val helper by phone.hub.shell.collectAsState()
    val shizuku by ShizukuManager.snapshot.collectAsState()
    val vd by VdController.state.collectAsState()
    val log by VdController.log.collectAsState()
    val source by VdController.previewSource.collectAsState()
    val frame by source.collectAsState()

    // Re-read the system toggles whenever the page resumes.
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.currentStateFlow.collect { if (it == androidx.lifecycle.Lifecycle.State.RESUMED) { tick++; ShizukuManager.refresh() } }
    }
    LaunchedEffect(Unit) { while (true) { delay(2_000); tick++ } }
    val a11yOn = remember(tick) { MinisAccessibilityService.getInstance() != null }
    val overlayOn = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifOn = remember(tick) { ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
    val batteryOn = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val devOn = remember(tick) { DeveloperSettings.developerOptions(ctx) }
    var a11yListed by remember { mutableStateOf(false) }
    LaunchedEffect(tick, helper) {
        val sh = helper
        a11yListed = if (sh == null) a11yOn else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val me = ComponentName(ctx.packageName, MinisAccessibilityService::class.java.name).flattenToString()
                sh.runCommand("settings get secure enabled_accessibility_services").getString("output").orEmpty()
                    .trim().split(':').any { it.equals(me, ignoreCase = true) }
            }.getOrDefault(false)
        }
    }
    val wdOn = remember(tick) { DeveloperSettings.wirelessDebugging(ctx) }

    LaunchedEffect(Unit) {
        if (VdDemo.autoDemo) {
            VdDemo.autoDemo = false
            val ok = withTimeoutOrNull(60_000) { phone.hub.shell.first { it != null } } != null
            VdController.log(if (ok) "demo: helper connected (${phone.hub.source})" else "demo: helper not connected")
            if (ok) {
                VdController.create(ctx).onSuccess {
                    VdController.launch(ctx, "com.android.settings")
                }
            }
        }
    }

    MibotScaffold(title = t("后台操控 / 虚拟屏", "Background control"), onBack = onBack) {
        // ---------------------------------------------------------- live
        MibotSectionHeader(t("虚拟屏", "Hidden display"))
        MibotCard(padding = androidx.compose.foundation.layout.PaddingValues(MibotSpace.M)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .width(108.dp)
                        .aspectRatio(if (vd.width > 0 && vd.height > 0) vd.width.toFloat() / vd.height else 9f / 19.5f)
                        .clip(RoundedCorner)
                        .background(Color.Black.copy(alpha = 0.82f))
                        .border(1.dp, MibotTokens.hairline(), RoundedCorner),
                    contentAlignment = Alignment.Center,
                ) {
                    val f = frame
                    if (f != null && !f.isRecycled) {
                        Image(f.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } else {
                        Text(
                            if (vd.mode == VdController.Mode.A11Y) t("主屏模式", "Main screen") else t("未运行", "Idle"),
                            color = Color.White.copy(alpha = 0.7f), fontSize = MibotType.Caption,
                        )
                    }
                }
                Spacer(Modifier.width(MibotSpace.M))
                Column(Modifier.weight(1f)) {
                    val (label, kind) = when (vd.mode) {
                        VdController.Mode.HELPER -> t("运行中 · 屏 #${vd.displayId}", "Running · display #${vd.displayId}") to ChipKind.OK
                        VdController.Mode.A11Y -> t("主屏无障碍模式", "Main-screen accessibility") to ChipKind.WARN
                        VdController.Mode.NONE -> t("空闲", "Idle") to ChipKind.NEUTRAL
                    }
                    StatusChip(label, kind)
                    Spacer(Modifier.height(MibotSpace.XS))
                    Text(
                        if (vd.mode == VdController.Mode.NONE) t("Agent 在隐藏的虚拟屏里打开和操作 App，你的屏幕不受影响。", "The agent opens and operates apps on a hidden display; your screen is untouched.")
                        else "${vd.width}×${vd.height}" + (vd.app?.let { " · $it" } ?: ""),
                        fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(MibotSpace.S))
                    var pkg by remember { mutableStateOf("com.android.settings") }
                    if (vd.mode != VdController.Mode.NONE) {
                        SmallField(pkg, { pkg = it }, t("包名", "Package"))
                        Spacer(Modifier.height(MibotSpace.S))
                        Row(horizontalArrangement = Arrangement.spacedBy(MibotSpace.S)) {
                            MibotButton(t("打开", "Open"), { scope.launch { VdController.launch(ctx, pkg.trim()) } }, tonal = true)
                            MibotButton(t("停止", "Stop"), { VdController.close(ctx) })
                        }
                    } else {
                        MibotButton(t("新建虚拟屏", "Create display"), {
                            scope.launch { VdController.create(ctx).onFailure { VdController.log(it.message ?: "failed") } }
                        })
                    }
                }
            }
        }

        // ---------------------------------------------------------- status
        // [fork/mibot-v4] Every row states clearly whether it is on; the
        // states are re-read on resume and every 2 s, so coming back from
        // system settings updates them at once.
        val helperOn = helper != null
        val granted = listOf(helperOn, a11yOn, overlayOn, notifOn, batteryOn).count { it }
        MibotSectionHeader(t("权限状态", "Permissions"))
        PermissionSummary(granted, 5)
        com.openminis.app.mibot.design.MibotSpacer(6.dp)
        MibotCard {
            val helperText = when {
                !phone.supported -> t("不可用", "Unavailable")
                helperOn -> t("已连接 · ${if (phone.hub.source == "shizuku") "Shizuku" else "无线调试"}", "Connected · ${phone.hub.source}")
                status is BuiltinShellStatus.Connecting -> t("连接中", "Connecting")
                status is BuiltinShellStatus.WirelessDebuggingOff -> t("无线调试已关", "Wireless debugging off")
                status is BuiltinShellStatus.Failed -> t("出错", "Error")
                else -> t("未连接", "Not connected")
            }
            PermissionRow(
                t("内置助手（ADB 级）", "Built-in helper (ADB level)"),
                if (!phone.supported) t("需要 Android 12 及以上", "Needs Android 12+")
                else (status as? BuiltinShellStatus.Failed)?.let { PairingCopy.failure(ctx, it.reason) }
                    ?: t("经本机无线调试启动，无需电脑，之后自动重连", "Started via this phone's Wireless Debugging — no PC; reconnects on its own"),
                Icons.Outlined.PhonelinkSetup,
                state = when { !phone.supported -> PermState.UNAVAILABLE; helperOn -> PermState.GRANTED; else -> PermState.MISSING },
                stateText = helperText,
                actionLabel = if (helperOn || !phone.supported) null else t("去配对", "Pair"),
                onAction = if (helperOn || !phone.supported) null else ({ scope.launch { phone.builtin.engine.retry() } }),
                showDivider = true,
            )
            PermissionRow(
                t("无障碍", "Accessibility"),
                t("读取虚拟屏界面树；无助手时在主屏操作（需确认）", "Reads the hidden display's UI; main-screen fallback (asks first)"),
                Icons.Outlined.Accessibility,
                state = if (a11yOn) PermState.GRANTED else PermState.MISSING,
                actionLabel = t("去开启", "Turn on"),
                onAction = { ctx.openFirst(listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) },
                showDivider = true,
            )
            PermissionRow(
                t("悬浮窗", "Overlay"),
                t("主屏模式下显示「正在操作」提示", "Shows the “operating” pill in main-screen mode"),
                Icons.Outlined.PictureInPicture,
                state = if (overlayOn) PermState.GRANTED else PermState.MISSING,
                stateText = if (overlayOn) t("已允许", "Allowed") else null,
                actionLabel = t("去开启", "Turn on"),
                onAction = { ctx.openFirst(listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))) },
                showDivider = true,
            )
            PermissionRow(
                t("通知", "Notifications"),
                t("配对码输入与常驻停止按钮", "Pairing-code input and the Stop button"),
                Icons.Outlined.Notifications,
                state = if (notifOn) PermState.GRANTED else PermState.MISSING,
                stateText = if (notifOn) t("已允许", "Allowed") else null,
                actionLabel = t("去开启", "Turn on"),
                onAction = { ctx.openFirst(listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))) },
                showDivider = true,
            )
            PermissionRow(
                t("电池优化豁免", "Battery exemption"),
                t("后台长时间操作不被系统杀掉", "Keeps long background tasks alive"),
                Icons.Outlined.BatteryChargingFull,
                state = if (batteryOn) PermState.GRANTED else PermState.MISSING,
                stateText = if (batteryOn) t("已豁免", "Exempt") else null,
                actionLabel = t("去开启", "Turn on"),
                onAction = { ctx.openFirst(listOf(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) },
                showDivider = true,
            )
            val shState = when (shizuku.state) {
                ShizukuManager.State.READY -> PermState.GRANTED
                ShizukuManager.State.NEED_PERMISSION, ShizukuManager.State.NOT_RUNNING -> PermState.MISSING
                ShizukuManager.State.NOT_INSTALLED -> PermState.UNAVAILABLE
            }
            PermissionRow(
                "Shizuku",
                t("可选：已装 Shizuku 时可用它启动助手", "Optional: start the helper through Shizuku"),
                Icons.Outlined.AdminPanelSettings,
                state = shState,
                stateText = when (shizuku.state) {
                    ShizukuManager.State.READY -> t("已授权", "Granted")
                    ShizukuManager.State.NEED_PERMISSION -> t("待授权", "Not granted")
                    ShizukuManager.State.NOT_RUNNING -> t("未运行", "Not running")
                    ShizukuManager.State.NOT_INSTALLED -> t("未安装（可选）", "Not installed (optional)")
                },
                actionLabel = if (shState == PermState.MISSING) t("去开启", "Turn on") else null,
                onAction = {
                    when (shizuku.state) {
                        ShizukuManager.State.NEED_PERMISSION -> ShizukuManager.requestPermission()
                        ShizukuManager.State.READY -> scope.launch {
                            VdController.log("Shizuku: starting helper")
                            VdController.log("Shizuku: " + (phone.launchViaShizuku() ?: "helper connected"))
                        }
                        ShizukuManager.State.NOT_RUNNING -> ShizukuManager.openShizukuApp(ctx)
                        ShizukuManager.State.NOT_INSTALLED -> ShizukuManager.openInstallPage(ctx)
                    }
                },
            )
        }

        // ---------------------------------------------------------- pairing wizard
        if (phone.supported && helper == null) {
            MibotSectionHeader(t("无线调试配对（一次即可）", "Pair Wireless Debugging (once)"))
            MibotCard {
                MibotRow(t("1. 打开开发者选项", "1. Turn on Developer options"),
                    t("设置 › 关于手机 › 连点「版本号」7 次", "Settings › About phone › tap Build number 7 times"),
                    Icons.Outlined.Layers, MibotTokens.Steel, showDivider = true,
                    onClick = { ctx.openFirst(listOf(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))) },
                    trailing = { StatusChip(if (devOn) t("已开", "On") else t("去开启", "Open"), if (devOn) ChipKind.OK else ChipKind.NEUTRAL) })
                MibotRow(t("2. 连接 Wi-Fi 并打开「无线调试」", "2. Join Wi-Fi, turn on Wireless debugging"),
                    t("任意 Wi-Fi 即可，不需要能上网", "Any Wi-Fi works; internet not needed"),
                    Icons.Outlined.Wifi, MibotTokens.Sky, showDivider = true,
                    onClick = { ctx.openFirst(DeveloperSettings.wirelessDebuggingPage()) },
                    trailing = { StatusChip(if (wdOn) t("已开", "On") else t("去开启", "Open"), if (wdOn) ChipKind.OK else ChipKind.NEUTRAL) })
                MibotRow(t("3. 点「使用配对码配对设备」", "3. Tap “Pair device with pairing code”"),
                    when (discovery) {
                        is PairingDiscovery.Found -> t("已找到配对窗口，请在弹出的通知里填 6 位码", "Pairing dialog found — type the 6-digit code into the notification")
                        PairingDiscovery.Searching -> t("正在找配对窗口… 码填到通知里，不用切回来", "Looking for the pairing dialog… type the code into the notification")
                        else -> t("点这里开始，Mibot 会弹出带输入框的通知", "Tap to start; Mibot posts a notification with a code field")
                    },
                    Icons.Outlined.Cable, MibotTokens.Brick,
                    onClick = {
                        phone.pairing.begin()
                        ctx.openFirst(DeveloperSettings.wirelessDebuggingPage())
                    },
                    trailing = { if (discovery is PairingDiscovery.Searching) StatusChip(t("查找中", "Searching"), ChipKind.WARN) })
            }
            var code by remember { mutableStateOf("") }
            var port by remember { mutableStateOf("") }
            MibotCard(Modifier.padding(top = MibotSpace.S), padding = androidx.compose.foundation.layout.PaddingValues(MibotSpace.M)) {
                Text(t("通知被屏蔽时：分屏打开设置，在这里填码", "If notifications are blocked: split-screen Settings and type it here"),
                    fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(MibotSpace.S))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MibotSpace.S)) {
                    Box(Modifier.weight(1f)) { SmallField(code, { code = it.take(8) }, t("6 位配对码", "6-digit code"), number = true) }
                    Box(Modifier.width(88.dp)) { SmallField(port, { port = it.take(5) }, t("端口", "Port"), number = true) }
                    MibotButton(t("配对", "Pair"), { phone.pairing.submit(code, PairingCode.port(port)) }, enabled = code.length >= 6)
                }
                Spacer(Modifier.height(MibotSpace.S))
                Row(horizontalArrangement = Arrangement.spacedBy(MibotSpace.S)) {
                    MibotButton(t("重新连接", "Reconnect"), { scope.launch { phone.builtin.engine.retry() } }, tonal = true)
                    MibotButton(t("用电脑启动", "Start from PC"), {
                        val cmd = phone.writeUsbScript()
                        VdController.log(cmd?.let { tc(ctx, "电脑上运行：$it", "Run on a PC: $it") } ?: "cannot write script")
                    }, tonal = true)
                }
            }
        } else if (helper != null) {
            MibotSectionHeader(t("提升权限（经助手，逐项确认）", "Raise permissions (via helper, each confirmed)"))
            val basicCount = listOf(notifOn, overlayOn, batteryOn).count { it }
            val secureOn = remember(tick) {
                ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            MibotCard {
                PermissionRow(
                    t("授予通知、悬浮窗、电池白名单", "Grant notifications, overlay, battery whitelist"),
                    t("为 Mibot 自己执行 pm grant / appops / deviceidle", "Runs pm grant / appops / deviceidle for Mibot itself"),
                    Icons.Outlined.Notifications,
                    state = when (basicCount) { 3 -> PermState.GRANTED; 0 -> PermState.MISSING; else -> PermState.PARTIAL },
                    stateText = when (basicCount) { 3 -> t("已授予 3/3", "Granted 3/3"); 0 -> t("未授予 0/3", "Not granted 0/3"); else -> t("部分 $basicCount/3", "Partial $basicCount/3") },
                    actionLabel = if (basicCount < 3) t("授予", "Grant") else null,
                    onAction = { scope.launch { elevate(ctx, ElevateKind.BASIC); tick++ } },
                    showDivider = true,
                )
                PermissionRow(
                    t("开启 Mibot 无障碍服务", "Enable Mibot's accessibility service"),
                    when {
                        a11yOn -> t("已在系统列表中，服务已绑定", "Listed and bound")
                        a11yListed -> t("已写入系统列表，等待系统绑定服务（可在无障碍设置里开关一次）", "Listed; waiting for the system to bind it (toggle it once in Accessibility settings)")
                        else -> t("追加到系统无障碍列表（保留其他服务），用于读取虚拟屏界面", "Appends it to the enabled list (keeps other services) to read the hidden display")
                    },
                    Icons.Outlined.Accessibility,
                    state = when { a11yOn -> PermState.GRANTED; a11yListed -> PermState.PARTIAL; else -> PermState.MISSING },
                    stateText = when { a11yOn -> t("已授予", "Granted"); a11yListed -> t("已写入·未绑定", "Listed · not bound"); else -> t("未授予", "Not granted") },
                    actionLabel = if (!a11yOn) t("开启", "Enable") else null,
                    onAction = { scope.launch { elevate(ctx, ElevateKind.A11Y); tick++ } },
                    showDivider = true,
                )
                PermissionRow(
                    t("授予 WRITE_SECURE_SETTINGS", "Grant WRITE_SECURE_SETTINGS"),
                    t("仅在需要自动恢复无障碍时使用，可随时撤销", "Only to restore accessibility on its own; revocable"),
                    Icons.Outlined.AdminPanelSettings,
                    state = if (secureOn) PermState.GRANTED else PermState.MISSING,
                    stateText = if (secureOn) t("已授予", "Granted") else t("未授予", "Not granted"),
                    actionLabel = if (!secureOn) t("授予", "Grant") else null,
                    onAction = { scope.launch { elevate(ctx, ElevateKind.SECURE); tick++ } },
                    showDivider = true,
                )
                PermissionRow(
                    t("断开助手", "Disconnect helper"),
                    t("释放所有虚拟屏并退出助手进程", "Releases every display and stops the helper"),
                    Icons.Outlined.Cable,
                    state = PermState.GRANTED,
                    stateText = t("已连接", "Connected"),
                    actionLabel = null,
                    onAction = { VdController.close(ctx); runCatching { helper?.destroy() } },
                )
            }
        }
        MibotFootnote(t(
            "安全：只操作 Mibot 自己建的虚拟屏，从不向主屏（屏 0）注入；你正在主屏用的 App 不会被拉走；付款、登录、密码、验证码类步骤会先问你；通知栏可随时停止。实现改编自 Mika（Apache-2.0）。",
            "Safety: only Mibot's own hidden display is ever touched — never your screen (display 0); apps you are using aren't pulled away; payment, login, password and code steps ask first; stop any time from the notification. Adapted from Mika (Apache-2.0).",
        ))

        // ---------------------------------------------------------- log
        MibotSectionHeader(t("日志", "Log"))
        MibotCard(padding = androidx.compose.foundation.layout.PaddingValues(MibotSpace.M)) {
            if (log.isEmpty()) {
                Text(t("还没有记录", "Nothing yet"), fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                log.takeLast(16).forEach {
                    Text(it, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface, softWrap = true,
                        modifier = Modifier.padding(vertical = 1.dp))
                }
            }
        }
    }
}

private val RoundedCorner = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

@Composable
private fun SmallField(value: String, onChange: (String) -> Unit, hint: String, number: Boolean = false) {
    val fg = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MibotRadius.RowShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .border(1.dp, MibotTokens.hairline(), MibotRadius.RowShape)
            .padding(horizontal = MibotSpace.M, vertical = 9.dp),
    ) {
        if (value.isEmpty()) Text(hint, fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = fg, fontSize = MibotType.Body),
            cursorBrush = SolidColor(MibotTokens.accent()),
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class ElevateKind { BASIC, A11Y, SECURE }

/** User-initiated privilege grants for Mibot itself, through the helper. Each one is confirmed with the exact commands. */
private suspend fun elevate(ctx: Context, kind: ElevateKind) {
    val sh = MibotPhone.get(ctx).hub.shell.value ?: return
    val pkg = ctx.packageName
    val cmds = when (kind) {
        ElevateKind.BASIC -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) "pm grant $pkg android.permission.POST_NOTIFICATIONS" else null,
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "dumpsys deviceidle whitelist +$pkg",
        )
        ElevateKind.A11Y -> {
            // Fully qualified "com.dl1991.mibot/com.openminis.app.accessibility.MinisAccessibilityService".
            val me = ComponentName(pkg, MinisAccessibilityService::class.java.name).flattenToString()
            val cls = MinisAccessibilityService::class.java.name
            val cur = sh.runCommand("settings get secure enabled_accessibility_services").getString("output").orEmpty().trim()
            // Keep every other service; drop our own entry and stale copies of
            // our service class registered under another (old) package name.
            val list = cur.split(':').map { it.trim() }.filter {
                it.isNotBlank() && it != "null" && !it.equals(me, ignoreCase = true) &&
                    !(it.substringAfter('/', "") == cls || it.substringAfter('/', "") == ".accessibility.MinisAccessibilityService")
            } + me
            VdController.log("a11y: component $me; keeping ${list.size - 1} other service(s)")
            listOf("settings put secure enabled_accessibility_services '${list.joinToString(":")}'", "settings put secure accessibility_enabled 1")
        }
        ElevateKind.SECURE -> listOf("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
    }
    val ok = VaultConfirmBus.ask(
        tc(ctx, "以 ADB 权限执行？", "Run with ADB privileges?"),
        tc(ctx, "将为 Mibot 执行：\n", "Mibot will run:\n") + cmds.joinToString("\n"),
    )
    if (!ok) return
    for (c in cmds) {
        val r = sh.runCommand(c)
        VdController.log("$ $c -> ${r.getInt("exitCode")}${r.getString("output")?.trim()?.take(200)?.let { if (it.isNotEmpty()) " $it" else "" } ?: ""}")
    }
}

/** Small live view of the hidden display, floating over every screen while it runs; tap to open the page. */
@Composable
fun VdMiniView(onOpen: () -> Unit) {
    val vd by VdController.state.collectAsState()
    if (vd.mode != VdController.Mode.HELPER) return
    val source by VdController.previewSource.collectAsState()
    val frame by source.collectAsState()
    Box(Modifier.fillMaxSize().padding(top = 96.dp, end = MibotSpace.M), contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .width(72.dp)
                .aspectRatio(vd.width.toFloat() / vd.height.coerceAtLeast(1))
                .clip(RoundedCorner)
                .background(Color.Black)
                .border(1.5.dp, MibotTokens.accent(), RoundedCorner)
                .clickable(onClick = onOpen),
        ) {
            frame?.takeIf { !it.isRecycled }?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                    .clip(MibotRadius.Pill).background(MibotTokens.accent()).padding(horizontal = 6.dp, vertical = 1.dp),
            ) { Text(t("后台", "BG"), color = Color.White, fontSize = MibotType.Caption) }
        }
    }
}
