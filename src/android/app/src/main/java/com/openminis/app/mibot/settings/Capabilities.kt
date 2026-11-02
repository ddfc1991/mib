package com.openminis.app.mibot.settings

import android.app.AlarmManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotDivider
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotSpace
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.StatusChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [fork/mibot-v5b2] 自动化等级. MAX = the user granted Mibot everything and
 * wants it used: AI 邮箱 sends 全自动 (still logged, rate-limited, 10 s undo),
 * SMS codes from the user's own phone are filled automatically, keep-alive
 * ignores battery-saver when exempt. Captchas are never solved in any mode.
 */
object Automation {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("mibot_automation", Context.MODE_PRIVATE)
    fun isMax(ctx: Context) = prefs(ctx).getString("level", "standard") == "max"
    fun isChosen(ctx: Context) = prefs(ctx).contains("level")
    fun set(ctx: Context, max: Boolean) {
        prefs(ctx).edit().putString("level", if (max) "max" else "standard").apply()
        com.openminis.app.mibot.mail.MailKeepAlive.configure(ctx)
    }
}

/** What Mibot can use on this phone, and what it uses it for. */
object Capabilities {
    data class Cap(val key: String, val name: String, val granted: Boolean?, val usedFor: String, val settings: Intent?)

    fun batteryExempt(ctx: Context): Boolean = runCatching {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }.getOrDefault(false)

    private fun a11y(ctx: Context): Boolean = runCatching {
        val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        flat.split(':').any { it.equals(com.openminis.app.accessibility.MinisAccessibilityService.SERVICE_ID, true) }
    }.getOrDefault(false)

    private fun shizuku(): Boolean? = runCatching {
        if (!rikka.shizuku.Shizuku.pingBinder()) false
        else rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrNull()

    private fun root(): Boolean = listOf("/system/xbin/su", "/system/bin/su", "/sbin/su", "/debug_ramdisk/su", "/data/adb/magisk").any { runCatching { java.io.File(it).exists() }.getOrDefault(false) }

    fun list(ctx: Context): List<Cap> {
        val pkg = Uri.parse("package:" + ctx.packageName)
        fun app() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        val am = ctx.getSystemService(AlarmManager::class.java)
        return listOf(
            Cap("a11y", "无障碍", a11y(ctx), "后台操控 App、虚拟屏；（App 内自动登录尚未接入，见下方说明）", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            Cap("notif", "通知使用权", com.openminis.app.offload.MinisNotificationListenerService.isEnabled(ctx), "读取你本机短信 App 通知里的验证码，自动填入登录页", Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
            Cap("overlay", "悬浮窗", Settings.canDrawOverlays(ctx), "后台任务进度气泡", Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)),
            Cap("sms", "短信读取", null, "未申请 READ_SMS（应用商店政策）；改用通知使用权读取验证码", null),
            Cap("shizuku", "Shizuku / ADB", shizuku(), "虚拟屏与后台操控的高权限通道", null),
            Cap("root", "Root", root(), "检测到 su 时仅显示，Mibot 不主动使用 root", null),
            Cap("battery", "后台无限制（电池优化豁免）", batteryExempt(ctx), "邮箱保活与探针在省电模式下也照常运行", Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)),
            Cap("alarm", "精确闹钟", if (Build.VERSION.SDK_INT >= 31) am?.canScheduleExactAlarms() else true, "定时任务准点触发",
                if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg) else app()),
        )
    }
}

/**
 * Verification codes read from the notifications of the user's OWN SMS app
 * (notification-listener access). Only the code and its sender are kept, for
 * a few minutes, in memory.
 */
object SmsCodes {
    data class Code(val code: String, val from: String, val at: Long, val text: String)

    private val _codes = MutableSharedFlow<Code>(replay = 4, extraBufferCapacity = 8)
    val codes: SharedFlow<Code> = _codes.asSharedFlow()
    private val smsApps = listOf("messaging", "mms", ".sms", "message", "messenger", "contacts")
    private val codeRx = Regex("(?:验证码|校验码|动态码|code|Code|CODE|验证代码|驗證碼)[^0-9]{0,24}([0-9]{4,8})|([0-9]{4,8})[^0-9]{0,12}(?:是你的|为您的|is your).{0,8}(?:验证码|code)")

    fun onNotification(sbn: StatusBarNotification) {
        val pkg = sbn.packageName.lowercase()
        if (smsApps.none { it in pkg }) return
        val ex = sbn.notification.extras
        val text = listOfNotNull(ex.getCharSequence(Notification.EXTRA_TITLE), ex.getCharSequence(Notification.EXTRA_TEXT), ex.getCharSequence(Notification.EXTRA_BIG_TEXT)).joinToString(" ")
        val m = codeRx.find(text) ?: return
        val code = m.groupValues[1].ifBlank { m.groupValues[2] }
        _codes.tryEmit(Code(code, ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(), System.currentTimeMillis(), text.take(200)))
    }

    /** Latest code that arrived after [since] whose text mentions [hint] (brand/domain), or any if hint null. */
    fun latest(since: Long, hint: String?): Code? = codes.replayCache.lastOrNull { it.at >= since && (hint == null || it.text.contains(hint, true)) }
        ?: codes.replayCache.lastOrNull { it.at >= since }
}

object CapRoutes { const val PAGE = "mibot_capabilities" }

@Composable
fun CapabilitiesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var max by remember { mutableStateOf(Automation.isMax(ctx)) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val caps = remember(tick) { Capabilities.list(ctx) }
    MibotScaffold(title = "权限与自动化", onBack = onBack) {
        MibotSectionHeader("自动化等级")
        MibotCard {
            listOf(false to ("标准" to "发给真人的邮件先确认；验证码优先自动，拿不到就请你来"), true to ("最高" to "已给到最高权限时选它：AI 邮箱全自动发信（有记录、限速、10 秒可撤销），本机短信验证码自动填，保活不受省电限制")).forEachIndexed { i, (v, label) ->
                Row(Modifier.fillMaxWidth().clickable { max = v; Automation.set(ctx, v) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动化等级：" + label.first, fontSize = MibotType.Body, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                        Text(label.second, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (max == v) StatusChip("当前", ChipKind.ACCENT)
                }
                if (i == 0) MibotDivider()
            }
        }
        MibotFootnote("任何等级下：验证码图形/滑块永远由你完成，不接第三方接码平台。")
        MibotSectionHeader("已授予 · 实际用途")
        MibotCard {
            caps.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().then(if (c.settings != null) Modifier.clickable { runCatching { ctx.startActivity(c.settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = MibotType.Body, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(c.usedFor, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    when (c.granted) {
                        true -> StatusChip("已授予", ChipKind.OK)
                        false -> StatusChip(if (c.settings != null) "去授权" else "未检测到", ChipKind.NEUTRAL)
                        null -> StatusChip("不适用", ChipKind.NEUTRAL)
                    }
                }
                if (i < caps.lastIndex) MibotDivider(inset = 14.dp)
            }
        }
        MibotFootnote("未做：用无障碍自动操作第三方 App 的登录流程（各 App 界面不同、需逐个适配，本版未接入）。App 里登录的凭据 Mibot 读不到，Key 一律走官方网页控制台获取。")
    }
}
