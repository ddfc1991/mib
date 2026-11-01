package com.openminis.app.mibot.vault

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openminis.app.mibot.design.BrandLogo
import com.openminis.app.mibot.design.CheckMark
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.Glyph
import com.openminis.app.mibot.design.LineIcons
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotDivider
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotIcon
import com.openminis.app.mibot.design.MibotRadius
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotSpace
import com.openminis.app.mibot.design.MibotSpacer
import com.openminis.app.mibot.design.MibotSwitch
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.PlusButton
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.design.tc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =====================================================================
// 账号 page
// =====================================================================

@Composable
private fun healthChip(g: VaultServices.Group) {
    when (g.worst) {
        VaultServices.Health.OK -> StatusChip(t("正常", "OK"), ChipKind.OK)
        VaultServices.Health.WARN -> StatusChip(t("要注意", "Check"), ChipKind.WARN)
        VaultServices.Health.INVALID -> StatusChip(t("key 无效", "Key invalid"), ChipKind.ERR)
    }
}

@Composable
private fun groupSubtitle(g: VaultServices.Group, masked: Boolean): String {
    val n = g.entries.size
    val web = if (g.hasWebLogin) t(" · 有网站登录", " · web login") else ""
    if (n == 1) {
        val e = g.entries.first()
        val state = when (VaultServices.health(e)) {
            VaultServices.Health.OK -> t("正常", "OK")
            VaultServices.Health.WARN -> t("要注意", "Needs attention")
            VaultServices.Health.INVALID -> t("key 无效", "Key invalid")
        }
        val bal = e.status?.let { listOfNotNull(it.balance, it.credits, it.points).firstOrNull() }?.let { " · $it" } ?: ""
        val user = e.username.takeIf { it.isNotBlank() }?.let { " · " + if (masked) VaultServices.mask(it) else it } ?: ""
        return state + bal + web + user
    }
    val bad = g.warn + g.invalid
    return t("$n 个账号", "$n accounts") + if (bad == 0) t(" · 全部正常", " · all OK") + web else t(" · $bad 个要注意", " · $bad need attention") + web
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MibotSpace.Screen)
            .clip(MibotRadius.Pill)
            .background(MibotTokens.card())
            .border(1.dp, MibotTokens.hairline(), MibotRadius.Pill)
            .padding(horizontal = MibotSpace.M, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcons.Search, size = MibotIcon.Dense, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(MibotSpace.S))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(t("搜索服务", "Search services"), fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MibotType.Body),
                cursorBrush = SolidColor(MibotTokens.accent()), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = MibotSpace.M + 2.dp, top = MibotSpace.M, bottom = 2.dp),
        fontSize = MibotType.Section, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ServiceRow(
    logo: String?, color: String?, name: String, subtitle: String, subtitleWarn: Boolean = false,
    trailing: @Composable () -> Unit, chevron: Boolean, onClick: (() -> Unit)?, showDivider: Boolean,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = MibotSpace.M + 2.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLogo(logo, name, color, size = 36.dp)
            Spacer(Modifier.width(MibotSpace.M))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = MibotType.Body, lineHeight = MibotType.BodyLine, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (subtitleWarn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(MibotSpace.S))
            trailing()
            if (chevron) {
                Spacer(Modifier.width(4.dp))
                Glyph(LineIcons.ChevronRight, size = MibotIcon.Small, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        if (showDivider) MibotDivider(inset = MibotSpace.M * 2 + 38.dp)
    }
}

@Composable
fun VaultListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit, onAudit: () -> Unit, onDiscover: () -> Unit = {}, onService: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val entries by remember { VaultStore.entries(ctx) }.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var masked by remember { mutableStateOf(VaultServices.masked(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var freeOpen by remember { mutableStateOf(false) }
    var showIgnored by remember { mutableStateOf(false) }
    var ignoredTick by remember { mutableIntStateOf(0) }
    var dailyOn by remember { mutableStateOf(VaultCheckin.enabled(ctx)) }
    val services = remember { VaultServices.all(ctx) }
    val ignored = remember(ignoredTick) { VaultServices.ignored(ctx) }
    val groups = remember(entries) { VaultServices.groups(ctx, entries) }
    val q = query.trim().lowercase()
    fun hit(name: String, sub: String) = q.isEmpty() || name.lowercase().contains(q) || sub.lowercase().contains(q)
    val shownGroups = groups.filter { hit(it.name, it.entries.joinToString { e -> e.site + e.username }) }
    val connectedIds = groups.mapNotNull { it.service?.id }.toSet()
    // [fork/mibot-v4] AI suggestions only list services with free sign-up AND
    // free models; paid-only AI providers can still be added by hand / search.
    val notConnected = services.filter {
        it.id !in connectedIds && it.id !in ignored && hit(it.name, it.sub) && (it.cat != "ai" || it.freeModels || q.isNotEmpty())
    }
    val freeOnes = services.filter { it.freeModels && it.id !in connectedIds }

    MibotScaffold(
        title = t("账号", "Accounts"),
        onBack = onBack,
        actions = {
            Box(Modifier.size(MibotIcon.TopButton).clip(CircleShape).clickable(onClick = onAudit).semantics { contentDescription = "audit" }, contentAlignment = Alignment.Center) {
                Glyph(LineIcons.History, size = MibotIcon.Standard, tint = MaterialTheme.colorScheme.onBackground)
            }
        },
    ) {
        Spacer(Modifier.height(4.dp))
        SearchBox(query) { query = it }

        // Primary entry: auto-discover & connect.
        MibotSpacer(10.dp)
        MibotCard(onClick = onDiscover, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(MibotTokens.accent().copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Glyph(LineIcons.Radar, size = MibotIcon.Standard, tint = MibotTokens.accent(), duotone = true)
                }
                Spacer(Modifier.width(MibotSpace.M))
                Column(Modifier.weight(1f)) {
                    Text(t("自动发现并连接", "Discover & connect"), fontSize = MibotType.TitleSmall, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                    Text(t("从手机上的 App、系统账户和内置浏览器的登录里找出账号，逐个自动连接", "Finds accounts from your apps, system accounts and built-in browser logins, then connects them one by one"),
                        fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Glyph(LineIcons.ChevronRight, size = MibotIcon.Small, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = MibotSpace.Screen + MibotSpace.M, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (busy) t("巡查中…", "Checking…") else t("立即巡查所有账号", "Check all accounts now"),
                modifier = Modifier.clickable(enabled = !busy && entries.isNotEmpty()) {
                    busy = true
                    scope.launch {
                        var bad = 0
                        for (e in entries) {
                            VaultSites.refreshStatus(ctx, e, "user")
                            if (VaultSites.checkin(ctx, e, "user").state == "error") bad++
                        }
                        busy = false
                        Toast.makeText(ctx, tc(ctx, "巡查完成：${entries.size} 个账号", "Checked ${entries.size} accounts"), Toast.LENGTH_SHORT).show()
                    }
                },
                fontSize = MibotType.Body, fontWeight = MibotType.Semi, color = MibotTokens.accent(),
            )
            Spacer(Modifier.weight(1f))
            Text(t("每日自动签到", "Daily check-in"), fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            MibotSwitch(dailyOn, { v -> dailyOn = v; VaultCheckin.setEnabled(ctx, v) })
        }
        if (ignored.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = MibotSpace.Screen + MibotSpace.M, vertical = 2.dp)) {
                Text(t("已忽略 ${ignored.size} 个", "${ignored.size} ignored"), fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(if (showIgnored) t("收起", "Hide") else t("查看", "View"), fontSize = MibotType.Secondary, color = MibotTokens.accent(), modifier = Modifier.clickable { showIgnored = !showIgnored })
            }
            AnimatedVisibility(showIgnored) {
                MibotCard(Modifier.padding(top = 6.dp)) {
                    val list = services.filter { it.id in ignored }
                    list.forEachIndexed { i, s ->
                        ServiceRow(s.logo, s.color, s.name, t("已忽略", "Ignored"), trailing = {
                            Text(t("恢复", "Restore"), fontSize = MibotType.Secondary, color = MibotTokens.accent(), modifier = Modifier.clickable { VaultServices.setIgnored(ctx, s.id, false); ignoredTick++ })
                        }, chevron = false, onClick = null, showDivider = i < list.lastIndex)
                    }
                }
            }
        }

        // Connected services, grouped.
        Row(Modifier.fillMaxWidth().padding(start = MibotSpace.Screen + MibotSpace.M, end = MibotSpace.Screen + MibotSpace.M, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("已连接 · ${groups.size} 个服务", "Connected · ${groups.size} services"), fontSize = MibotType.Section, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Box(Modifier.clip(CircleShape).clickable {
                Toast.makeText(ctx, tc(ctx, "密钥用 Android Keystore 加密保存；AI 只能按每个账号的权限请求使用，永远看不到明文。", "Secrets are Keystore-encrypted; the AI can only request use under each account's permission and never sees plaintext."), Toast.LENGTH_LONG).show()
            }.padding(2.dp)) { Glyph(LineIcons.Info, size = MibotIcon.Small, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        MibotCard {
            Row(Modifier.fillMaxWidth().clickable { masked = !masked; VaultServices.setMasked(ctx, masked) }.padding(horizontal = MibotSpace.M + 2.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (masked) t("账号已打码", "Accounts masked") else t("显示账号", "Accounts shown"), fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Glyph(if (masked) LineIcons.EyeOff else LineIcons.Eye, size = MibotIcon.Standard, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (shownGroups.isEmpty()) {
                Text(
                    t("还没有连接的账号。点上面的「自动发现并连接」，或在下面的列表里点 +。", "Nothing connected yet. Use Discover & connect above, or tap + below."),
                    fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MibotSpace.M + 2.dp, vertical = 10.dp),
                )
            }
            VaultServices.categories.forEach { (cat, zh, en) ->
                val inCat = shownGroups.filter { it.cat == cat }
                if (inCat.isEmpty()) return@forEach
                SubHeader(t(zh, en))
                inCat.forEachIndexed { i, g ->
                    ServiceRow(
                        g.service?.logo, g.service?.color, g.name, groupSubtitle(g, masked),
                        subtitleWarn = g.worst == VaultServices.Health.INVALID,
                        trailing = { healthChip(g) }, chevron = true,
                        onClick = { if (g.entries.size == 1) onOpen(g.entries.first().id) else onService(g.key) },
                        showDivider = i < inCat.lastIndex,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // Free credits worth claiming.
        if (freeOnes.isNotEmpty()) {
            MibotSectionHeader(t("免费额度 · 值得关注", "Free credits worth a look"))
            MibotCard {
                Row(Modifier.fillMaxWidth().clickable { freeOpen = !freeOpen }.padding(horizontal = MibotSpace.M + 2.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("${freeOnes.size} 个可以免费领", "${freeOnes.size} you can claim free"), fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Glyph(LineIcons.ChevronDown, size = MibotIcon.Dense, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(freeOpen) {
                    Column {
                        freeOnes.forEachIndexed { i, s ->
                            ServiceRow(s.logo, s.color, s.name, s.free.orEmpty(), trailing = {
                                PlusButton({ VaultConnect.enqueue(ctx, listOf(s to null)); onDiscover() }, t("连接 ${s.name}", "Connect ${s.name}"))
                            }, chevron = false, onClick = null, showDivider = i < freeOnes.lastIndex)
                        }
                    }
                }
            }
        }

        // Not connected yet: tap + to auto-connect.
        VaultServices.addCategories.forEach { (cat, label) ->
            val list = notConnected.filter { it.cat == cat }
            if (list.isEmpty()) return@forEach
            MibotSectionHeader(t(label.first, label.second))
            MibotCard {
                list.forEachIndexed { i, s ->
                    ServiceRow(s.logo, s.color, s.name, s.sub, trailing = {
                        PlusButton({ VaultConnect.enqueue(ctx, listOf(s to null)); onDiscover() }, t("连接 ${s.name}", "Connect ${s.name}"))
                    }, chevron = false, onClick = null, showDivider = i < list.lastIndex)
                }
            }
        }

        MibotSectionHeader(t("其他网站", "Other sites"))
        MibotCard(onClick = onNew) {
            Row(Modifier.fillMaxWidth().padding(horizontal = MibotSpace.M + 2.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(MibotRadius.RowShape).background(MibotTokens.accent().copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Glyph(LineIcons.Plus, size = MibotIcon.Dense, tint = MibotTokens.accent())
                }
                Spacer(Modifier.width(MibotSpace.M))
                Text(t("手动添加账号 / 网站登录", "Add an account manually"), fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        MibotFootnote(t(
            "AI 通过 {{vault:ID}} 占位符或沙箱里的 vault 命令请求使用账号；每次使用都记入使用记录。支付、删除、以你名义发送等高风险操作总是需要确认。Logo 来自 Simple Icons（CC0）与 LobeHub Icons（MIT）。",
            "The AI requests accounts via {{vault:ID}} placeholders or the `vault` CLI; every use is logged. Paying, deleting and sending as you always need confirmation. Logos: Simple Icons (CC0), LobeHub Icons (MIT).",
        ))
    }
}

// =====================================================================
// One service with several accounts
// =====================================================================

@Composable
fun VaultServiceScreen(key: String, onBack: () -> Unit, onOpen: (String) -> Unit, onDiscover: () -> Unit) {
    val ctx = LocalContext.current
    val entries by remember { VaultStore.entries(ctx) }.collectAsState()
    val g = remember(entries) { VaultServices.groups(ctx, entries).find { it.key == key } }
    val masked = remember { VaultServices.masked(ctx) }
    MibotScaffold(title = g?.name ?: t("服务", "Service"), onBack = onBack) {
        MibotSpacer(6.dp)
        MibotCard {
            g?.entries?.forEachIndexed { i, e ->
                val h = VaultServices.health(e)
                val bal = e.status?.let { listOfNotNull(it.balance, it.credits, it.points, it.quota).joinToString(" · ") }.orEmpty()
                ServiceRow(
                    g.service?.logo, g.service?.color,
                    (if (masked) VaultServices.mask(e.username) else e.username).ifBlank { e.site },
                    listOf(if (e.kind == "cookie") t("网站登录", "Web login") else t("密钥", "Key"), bal, e.status?.error.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                    trailing = {
                        when (h) {
                            VaultServices.Health.OK -> StatusChip(t("正常", "OK"), ChipKind.OK)
                            VaultServices.Health.WARN -> StatusChip(t("要注意", "Check"), ChipKind.WARN)
                            VaultServices.Health.INVALID -> StatusChip(t("key 无效", "Key invalid"), ChipKind.ERR)
                        }
                    },
                    chevron = true, onClick = { onOpen(e.id) }, showDivider = i < g.entries.lastIndex,
                )
            }
        }
        g?.service?.let { s ->
            MibotSpacer(12.dp)
            Row(Modifier.padding(horizontal = 16.dp)) {
                MibotButton(t("再连接一个账号", "Connect another account"), { VaultConnect.enqueue(ctx, listOf(s to null)); onDiscover() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// =====================================================================
// 自动发现并连接
// =====================================================================

@Composable
private fun SourceTag(text: String) {
    Text(
        text,
        modifier = Modifier.clip(MibotRadius.Pill).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)).padding(horizontal = 6.dp, vertical = 1.dp),
        fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun VaultDiscoverScreen(onBack: () -> Unit, onManual: () -> Unit) {
    val ctx = LocalContext.current
    val tasks by VaultConnect.tasks.collectAsState()
    var picked by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    val candidates = remember(tick, picked, tasks.size) { VaultConnect.discover(ctx, picked) }
    val checked = remember { mutableStateListOf<String>() }
    var initialised by remember { mutableStateOf(false) }
    LaunchedEffect(candidates) {
        if (!initialised && candidates.isNotEmpty()) { checked.addAll(candidates.map { it.service.id }); initialised = true }
    }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val name = r.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            val type = r.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
            if (name != null && type != null) { picked = picked + (type to name); tick++ }
        }
    }
    val waitingWeb = tasks.firstOrNull { it.phase == VaultConnect.Phase.NEEDS_YOU && it.ask != null }

    MibotScaffold(title = t("自动发现并连接", "Discover & connect"), onBack = onBack) {
        if (tasks.isNotEmpty()) {
            val done = tasks.count { it.phase == VaultConnect.Phase.CONNECTED }
            MibotSectionHeader(t("连接进度 · $done/${tasks.size}", "Progress · $done/${tasks.size}"), t("清除已完成", "Clear done")) { VaultConnect.clearFinished() }
            MibotCard {
                tasks.forEachIndexed { i, tk ->
                    val (label, kind) = when (tk.phase) {
                        VaultConnect.Phase.WAITING -> t("等待", "Waiting") to ChipKind.NEUTRAL
                        VaultConnect.Phase.CONNECTING -> t("连接中", "Connecting") to ChipKind.ACCENT
                        VaultConnect.Phase.NEEDS_YOU -> t("需要你", "Needs you") to ChipKind.WARN
                        VaultConnect.Phase.CONNECTED -> t("已连接", "Connected") to ChipKind.OK
                        VaultConnect.Phase.FAILED -> t("失败", "Failed") to ChipKind.ERR
                        VaultConnect.Phase.SKIPPED -> t("已跳过", "Skipped") to ChipKind.NEUTRAL
                    }
                    ServiceRow(
                        tk.service.logo, tk.service.color, tk.service.name,
                        tk.detail.ifBlank { tk.service.sub },
                        subtitleWarn = tk.phase == VaultConnect.Phase.FAILED,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (tk.phase == VaultConnect.Phase.CONNECTING) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.6.dp, color = MibotTokens.accent())
                                StatusChip(label, kind)
                                when (tk.phase) {
                                    VaultConnect.Phase.FAILED, VaultConnect.Phase.SKIPPED ->
                                        Text(t("重试", "Retry"), fontSize = MibotType.Secondary, color = MibotTokens.accent(), modifier = Modifier.clickable { VaultConnect.retry(ctx, tk.service.id) })
                                    VaultConnect.Phase.WAITING, VaultConnect.Phase.NEEDS_YOU, VaultConnect.Phase.CONNECTING ->
                                        Text(t("跳过", "Skip"), fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { VaultConnect.skip(tk.service.id) })
                                    else -> {}
                                }
                            }
                        },
                        chevron = false, onClick = null, showDivider = i < tasks.lastIndex,
                    )
                }
            }
        }

        MibotSectionHeader(t("发现的账号 · ${candidates.size}", "Found · ${candidates.size}"), t("重新扫描", "Rescan")) { tick++ }
        MibotCard {
            if (candidates.isEmpty()) {
                Text(
                    t("没有发现新的账号。可以从系统账户里选，或在账号页的列表里点 + 逐个连接。", "Nothing new found. Pick a system account, or tap + on the Accounts page."),
                    fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            candidates.forEachIndexed { i, c ->
                val on = c.service.id in checked
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { if (on) checked.remove(c.service.id) else checked.add(c.service.id) }
                            .padding(horizontal = MibotSpace.M + 2.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckMark(on, size = 20.dp, ringWhenOff = true)
                        Spacer(Modifier.width(MibotSpace.M))
                        BrandLogo(c.service.logo, c.service.name, c.service.color, size = 32.dp)
                        Spacer(Modifier.width(MibotSpace.M))
                        Column(Modifier.weight(1f)) {
                            Text(c.service.name, fontSize = MibotType.Body, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                                c.sources.forEach { src ->
                                    SourceTag(when (src) {
                                        VaultConnect.Source.APP -> t("已装 App", "App installed")
                                        VaultConnect.Source.SYSTEM_ACCOUNT -> t("系统账户", "System account")
                                        VaultConnect.Source.BROWSER -> t("浏览器已登录", "Signed in (browser)")
                                        VaultConnect.Source.CATALOG -> t("目录", "Catalog")
                                    })
                                }
                                c.username?.let { SourceTag(VaultServices.mask(it)) }
                            }
                        }
                        Text(t("忽略", "Ignore"), fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { VaultServices.setIgnored(ctx, c.service.id, true); checked.remove(c.service.id); tick++ })
                    }
                    if (i < candidates.lastIndex) MibotDivider(inset = MibotSpace.M * 2 + 64.dp)
                }
            }
        }
        MibotSpacer(10.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MibotButton(t("开始连接（${checked.size}）", "Connect (${checked.size})"), enabled = checked.isNotEmpty(), modifier = Modifier.weight(1f), onClick = {
                val sel = candidates.filter { it.service.id in checked }
                VaultConnect.enqueue(ctx, sel.map { it.service to it.username })
                checked.clear()
            })
            MibotButton(t("从系统账户选择", "Pick system account"), tonal = true, modifier = Modifier.weight(1f), onClick = {
                runCatching { chooser.launch(AccountManager.newChooseAccountIntent(null, null, null, null, null, null, null)) }
                    .onFailure { Toast.makeText(ctx, it.message, Toast.LENGTH_SHORT).show() }
            })
        }
        MibotSpacer(8.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            MibotButton(t("手动添加账号", "Add manually"), tonal = true, modifier = Modifier.fillMaxWidth(), onClick = onManual)
        }
        MibotFootnote(t(
            "只读取你授权的来源：本机已装 App 列表、你在系统里选中的账户、Mibot 内置浏览器自己的登录。逐个连接：能用 OAuth 的走授权页（如 OpenRouter）；内置浏览器已登录的直接复用；其余在内置浏览器里打开官方登录/密钥页，验证码、短信码、二次验证和密码都由你来完成，Mibot 绝不绕过；拿到的 Key 或登录会话加密存进台账，随后自动查余额、签到。",
            "Only sources you grant are read: installed apps, system accounts you pick, and Mibot's own browser logins. One at a time: OAuth where available (e.g. OpenRouter); an existing built-in-browser session is reused; otherwise the official login / key page opens in the built-in browser — you enter any codes, 2FA and passwords yourself, nothing is bypassed. The captured key or session is encrypted into the vault, then balance and check-in run.",
        ))
    }

    if (waitingWeb != null) ConnectWebSheet(waitingWeb)
}

/** Built-in browser sheet for one service's login / key page. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ConnectWebSheet(task: VaultConnect.Task) {
    val ctx = LocalContext.current
    val ask = task.ask ?: return
    var web by remember { mutableStateOf<WebView?>(null) }
    var pasted by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(ask.url) }
    // Watch the page: a newly shown API key, or a signed-in session.
    LaunchedEffect(task.service.id) {
        val loginRx = Regex("login|signin|sign-in|sign_in|passport|auth|account/ak", RegexOption.IGNORE_CASE)
        var stable = 0
        while (true) {
            delay(1500)
            val w = web ?: continue
            if (ask.captureKey) {
                w.evaluateJavascript(
                    "(function(){var t=document.body?document.body.innerText:'';document.querySelectorAll('input,textarea,code').forEach(function(e){t+='\\n'+(e.value||e.textContent||'')});return t.slice(0,200000);})()",
                ) { raw ->
                    val text = runCatching { org.json.JSONArray("[$raw]").getString(0) }.getOrDefault("")
                    VaultConnect.findKey(task.service, text)?.let { VaultConnect.deliverWeb(VaultConnect.WebOutcome.Key(it)) }
                }
            } else {
                val url = w.url.orEmpty()
                val signedIn = VaultConnect.browserHasLogin(task.service) && !loginRx.containsMatchIn(url.substringAfter("://").substringAfter('/'))
                stable = if (signedIn) stable + 1 else 0
                if (stable >= 2) {
                    CookieManager.getInstance().flush()
                    VaultConnect.deliverWeb(VaultConnect.WebOutcome.Cookies(VaultConnect.cookiesFor(task.service)))
                }
            }
        }
    }
    Dialog(onDismissRequest = { VaultConnect.deliverWeb(VaultConnect.WebOutcome.Cancel) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandLogo(task.service.logo, task.service.name, task.service.color, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tc(ctx, "连接 ", "Connect ") + task.service.name, fontSize = MibotType.TitleSmall, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                        Text(currentUrl.substringAfter("://").substringBefore('?').take(60), fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    StatusChip(tc(ctx, "需要你", "Needs you"), ChipKind.WARN)
                }
                Spacer(Modifier.height(6.dp))
                Text(ask.hint, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) { currentUrl = url.orEmpty() }
                        }
                        loadUrl(ask.url)
                        web = this
                    }
                }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
            }
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp)) {
                if (ask.captureKey) {
                    com.openminis.app.mibot.design.GlassField(pasted, { pasted = it.trim() }, tc(ctx, "或把 Key 粘贴到这里", "Or paste the key here"), secret = true)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MibotButton(tc(ctx, "跳过", "Skip"), { VaultConnect.deliverWeb(VaultConnect.WebOutcome.Skip) }, tonal = true, modifier = Modifier.weight(1f))
                    if (ask.captureKey && pasted.length >= 16) {
                        MibotButton(tc(ctx, "保存 Key", "Save key"), { VaultConnect.deliverWeb(VaultConnect.WebOutcome.Key(pasted)) }, modifier = Modifier.weight(1f))
                    } else {
                        MibotButton(tc(ctx, "我已登录，保存", "I'm signed in — save"), {
                            CookieManager.getInstance().flush()
                            val ck = VaultConnect.cookiesFor(task.service)
                            if (ck.isBlank()) Toast.makeText(ctx, tc(ctx, "还没有检测到登录", "No sign-in detected yet"), Toast.LENGTH_SHORT).show()
                            else VaultConnect.deliverWeb(VaultConnect.WebOutcome.Cookies(ck))
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
