package com.openminis.app.mibot.mail

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotDivider
import com.openminis.app.mibot.design.MibotFilterChips
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotSwitch
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.StatusChip
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MailRoutes {
    const val LEDGER = "mibot_mail"
    const val BOX = "mibot_mail_box/{id}"
    fun box(id: String) = "mibot_mail_box/$id"
    /** One-shot deep-link hints (CI / links). */
    @Volatile var pendingCli: String? = null
    @Volatile var pendingTab: String? = null
}

private fun fmt(t: Long) = if (t <= 0) "—" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(t))

internal fun probeChip(r: ProbeResult?): Pair<String, ChipKind> = when (r?.status) {
    null -> "未探测" to ChipKind.NEUTRAL
    ProbeStatus.OK -> "正常 · ${r.ms}ms" to ChipKind.OK
    ProbeStatus.TOKEN_EXPIRED, ProbeStatus.RATE_LIMITED, ProbeStatus.NEEDS_CONFIG -> r.status.zh to ChipKind.WARN
    else -> r.status.zh to ChipKind.ERR
}

private fun copy(ctx: Context, label: String, v: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, v))
    android.widget.Toast.makeText(ctx, "已复制$label", android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun Mono(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun SmallLink(text: String, onClick: () -> Unit) {
    Text(text, fontSize = MibotType.Secondary, fontWeight = MibotType.Semi, color = MibotTokens.accent(), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp))
}

/** 邮箱台账: AI 邮箱 (can send) + 临时邮箱 (receive-only), probe, keep-alive, platforms, CLI. */
@Composable
fun MailLedgerScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val boxes by remember { MailCore.boxes(ctx) }.collectAsState()
    var tab by remember { mutableStateOf(MailRoutes.pendingTab ?: "全部") }
    var busy by remember { mutableStateOf<String?>(null) }
    var keep by remember { mutableStateOf(MailKeepAlive.enabled(ctx)) }
    var hours by remember { mutableStateOf(MailKeepAlive.hours(ctx)) }
    val probes = remember { mutableStateListOf<Pair<MailProvider, ProbeResult>>() }
    var cliIn by remember { mutableStateOf(MailRoutes.pendingCli ?: "providers") }
    var cliOut by remember { mutableStateOf("") }

    fun runCli(cmd: String) {
        cliIn = cmd
        scope.launch {
            cliOut = "$ mibot-mail $cmd\n…"
            val argv = Regex("\"([^\"]*)\"|(\\S+)").findAll(cmd).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
            val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MailCli.run(ctx, argv) }
            cliOut = "$ mibot-mail $cmd\n" + r.second.take(6000) + if (r.first != 0) "\n(exit ${r.first})" else ""
        }
    }
    LaunchedEffect(Unit) {
        MailRoutes.pendingTab = null
        MailRoutes.pendingCli?.let { MailRoutes.pendingCli = null; runCli(it) }
        probes.clear()
        for (p in MailProviders.all(ctx)) probes.add(p to MailCore.probeProvider(ctx, p))
    }
    val shown = boxes.filter { b ->
        val p = MailProviders.get(ctx, b.provider)
        when (tab) { "AI 邮箱" -> p?.canSend == true; "临时邮箱" -> p?.canSend != true; else -> true }
    }.sortedByDescending { it.createdAt }

    MibotScaffold(title = "邮箱台账", onBack = onBack) {
        MibotFilterChips(listOf("全部", "AI 邮箱", "临时邮箱"), tab) { tab = it }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MibotButton(if (busy == "new") "建箱中…" else "新建临时邮箱", enabled = busy == null, modifier = Modifier.weight(1f), onClick = {
                busy = "new"; scope.launch {
                    runCatching { MailCore.create(ctx) }.onFailure { android.widget.Toast.makeText(ctx, it.message, android.widget.Toast.LENGTH_LONG).show() }
                    busy = null
                }
            })
            MibotButton(if (busy == "probe") "探测中…" else "全部探测", tonal = true, enabled = busy == null, modifier = Modifier.weight(1f), onClick = {
                busy = "probe"; scope.launch { MailCore.boxes(ctx).value.forEach { MailCore.probe(ctx, it) }; busy = null }
            })
        }
        MibotSectionHeader("邮箱 · ${shown.size}")
        MibotCard {
            if (shown.isEmpty()) Text("还没有邮箱。新建一个临时邮箱，或在「平台」里配置 AgentMail / 自建 Worker 后创建可发信的 AI 邮箱。", fontSize = MibotType.Secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
            shown.forEachIndexed { i, b ->
                val p = MailProviders.get(ctx, b.provider)
                val (label, kind) = probeChip(b.probe)
                Row(Modifier.fillMaxWidth().clickable { onOpen(b.id) }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.address, fontSize = MibotType.Body, fontWeight = MibotType.Medium, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        Text(listOfNotNull(p?.name ?: b.provider, if (p?.canSend == true) "可收发" else "仅收件", "${b.messageCount} 封",
                            b.linked.takeIf { it.isNotEmpty() }?.joinToString(prefix = "用于 "), b.lastCode?.let { "最近码 $it" }).joinToString(" · "),
                            fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(6.dp))
                    SmallLink("复制") { copy(ctx, "地址", b.address) }
                    StatusChip(label, kind)
                }
                if (i < shown.lastIndex) MibotDivider(inset = 14.dp)
            }
        }

        MibotSectionHeader("保活")
        MibotCard(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("每 $hours 小时自动保活", fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurface)
                    Text("刷新 token / 用保存的密码重新登录、读一次收件箱、快过期时提醒；失败发通知。上次：${fmt(MailKeepAlive.lastRun(ctx))}",
                        fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MibotSwitch(keep, { v -> keep = v; MailKeepAlive.configure(ctx, v, hours) })
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 3, 6, 12, 24).forEach { h ->
                    StatusChipButton("${h}h", h == hours) { hours = h; MailKeepAlive.configure(ctx, keep, h) }
                }
            }
        }

        MibotSectionHeader("平台 · ${probes.count { it.second.status == ProbeStatus.OK }} 个在线")
        MibotCard {
            probes.forEachIndexed { i, (p, r) ->
                val (label, kind) = probeChip(r)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name + if (p.canSend) " · 可发信" else " · 仅收件", fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurface)
                        Text(r.detail.ifBlank { p.verified.orEmpty() }, fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    StatusChip(label, kind)
                }
                if (i < probes.lastIndex) MibotDivider(inset = 14.dp)
            }
        }
        ProviderConfig()

        MibotSectionHeader("命令行（与沙箱里的 mibot-mail 同一实现）")
        MibotCard(padding = PaddingValues(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$ mibot-mail ", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(cliIn, { cliIn = it }, singleLine = true, modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MibotTokens.accent()))
                SmallLink("运行") { runCli(cliIn) }
            }
            if (cliOut.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(MibotTokens.tile()).padding(10.dp)) { Mono(cliOut) }
            }
        }
        MibotFootnote("临时邮箱平台只能收信；公开收件箱（Maildrop / Inboxes / TempMail.plus）任何人都能读，别用于重要账号。有的服务会拒绝一次性邮箱域名，Mibot 会如实告诉你。不解验证码，不接短信接码平台。")
    }
}

@Composable
private fun StatusChipButton(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, fontSize = MibotType.Caption, fontWeight = MibotType.Semi,
        color = if (on) MibotTokens.accent() else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) MibotTokens.selected() else MibotTokens.tile())
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun ProviderConfig() {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    MibotSectionHeader("平台配置（AgentMail / 自建 Worker / Key 平台）", if (open) "收起" else "展开") { open = !open }
    if (!open) return
    val needing = MailProviders.all(ctx).filter { it.needs.isNotEmpty() }
    MibotCard(padding = PaddingValues(12.dp)) {
        needing.forEach { p ->
            Text(p.name, fontSize = MibotType.Body, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
            p.needs.forEach { k ->
                var v by remember(p.id, k) { mutableStateOf("") }
                val has = MailProviders.config(ctx, p.id, k) != null
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(k, fontSize = MibotType.Caption, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
                    BasicTextField(v, { v = it }, singleLine = true, modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(MibotTokens.field())
                        .border(1.dp, MibotTokens.hairline(), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                        textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MibotTokens.accent()))
                    SmallLink(if (has && v.isBlank()) "已存" else "保存") { if (v.isNotBlank()) { MailProviders.setConfig(ctx, p.id, k, v); v = "" } }
                }
            }
        }
        Text("加密保存在本机（Android Keystore）。", fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

/** One mailbox: credentials (tap-to-reveal with device credential), probe, inbox, send, outbox log. */
@Composable
fun MailBoxScreen(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val boxes by remember { MailCore.boxes(ctx) }.collectAsState()
    val b = boxes.find { it.id == id }
    var msgs by remember { mutableStateOf<List<MailMessage>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<Pair<MailMessage, String>?>(null) }
    var to by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    val auth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> if (r.resultCode == Activity.RESULT_OK) revealed = true }
    LaunchedEffect(id, tick) {
        val m = MailCore.get(ctx, id) ?: return@LaunchedEffect
        runCatching { MailCore.inbox(ctx, m) }.onSuccess { msgs = it; err = null }.onFailure { err = it.message }
    }
    MibotScaffold(title = b?.address?.substringBefore('@') ?: "邮箱", onBack = onBack) {
        if (b == null) { MibotFootnote("邮箱已删除"); return@MibotScaffold }
        val p = MailProviders.get(ctx, b.provider)
        MibotCard(padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.address, fontFamily = FontFamily.Monospace, fontSize = MibotType.Body, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                SmallLink("复制") { copy(ctx, "地址", b.address) }
            }
            Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(p?.name ?: b.provider, ChipKind.NEUTRAL)
                StatusChip(if (p?.canSend == true) "可收发" else "仅收件", if (p?.canSend == true) ChipKind.ACCENT else ChipKind.NEUTRAL)
                probeChip(b.probe).let { (l, k) -> StatusChip(l, k) }
                if (p?.publicInbox == true) StatusChip("公开收件箱", ChipKind.WARN)
            }
            val pass = MailCore.reveal(b.passEnc); val tok = MailCore.reveal(b.tokenEnc)
            fun mask(s: String?) = when { s == null -> "—"; revealed -> s; else -> "••••••" + s.takeLast(4) }
            listOf("密码" to mask(pass), "Token / JWT" to mask(tok), "Token 到期" to fmt(b.tokenExpiry), "邮箱到期" to fmt(b.lifetimeEnd),
                "创建" to fmt(b.createdAt), "上次登录" to fmt(b.lastLogin), "上次探测" to (b.probe?.let { "${fmt(it.at)} · ${it.status.zh} · ${it.ms}ms" } ?: "—"),
                "邮件数" to b.messageCount.toString(), "用于注册" to b.linked.joinToString().ifBlank { "—" }, "最近验证码" to (b.lastCode ?: "—"),
                "备注 / 标签" to (listOf(b.notes) + b.tags).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" }).forEach { (k, v) ->
                Row(Modifier.padding(top = 5.dp)) {
                    Text(k, fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp))
                    Text(v, fontSize = MibotType.Secondary, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!revealed && (pass != null || tok != null)) SmallLink("显示密码/Token") {
                    val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    @Suppress("DEPRECATION")
                    val i = if (km.isDeviceSecure) km.createConfirmDeviceCredentialIntent("查看邮箱凭据", "验证身份后显示") else null
                    if (i != null) auth.launch(i) else revealed = true
                }
                if (revealed && pass != null) SmallLink("复制密码") { copy(ctx, "密码", pass) }
                SmallLink("探测") { scope.launch { MailCore.probe(ctx, b) } }
                SmallLink("刷新") { tick++ }
                SmallLink("删除") { scope.launch { MailCore.delete(ctx, b); onBack() } }
            }
        }
        if (p?.canSend == true) {
            MibotSectionHeader("自动化：${b.autonomy.zh}")
            MibotCard {
                MailAutonomy.values().forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().clickable { MailCore.update(ctx, b.id) { it.copy(autonomy = a) } }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.zh, fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        if (b.autonomy == a) StatusChip("当前", ChipKind.ACCENT)
                    }
                    if (i < 2) MibotDivider(inset = 14.dp)
                }
            }
            MibotSectionHeader("写信")
            MibotCard(padding = PaddingValues(12.dp)) {
                listOf("收件人" to to, "主题" to subject, "正文" to body).forEachIndexed { i, (k, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(k, fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                        BasicTextField(v, { n -> when (i) { 0 -> to = n; 1 -> subject = n; else -> body = n } }, singleLine = i < 2,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(MibotTokens.field()).padding(horizontal = 8.dp, vertical = 6.dp),
                            textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MibotTokens.accent()))
                    }
                }
                Spacer(Modifier.height(6.dp))
                MibotButton("发送（${MailOutbox.UNDO_SECONDS} 秒可撤销）", enabled = '@' in to, modifier = Modifier.fillMaxWidth(), onClick = {
                    val r = MailOutbox.submit(ctx, b, to.trim(), subject, body)
                    android.widget.Toast.makeText(ctx, r.message, android.widget.Toast.LENGTH_LONG).show()
                    if (r.record.state != "blocked") { subject = ""; body = "" }
                })
            }
            if (b.outbox.isNotEmpty()) {
                MibotSectionHeader("发件记录（完整内容）")
                MibotCard {
                    b.outbox.reversed().take(20).forEachIndexed { i, r ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("→ ${r.to}", fontSize = MibotType.Secondary, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
                                StatusChip(when (r.state) { "sent" -> "已发送"; "queued" -> "发送中"; "needs_confirm" -> "待确认"; "undone" -> "已撤销"; "blocked" -> "已拦截"; else -> "失败" },
                                    when (r.state) { "sent" -> ChipKind.OK; "needs_confirm", "queued" -> ChipKind.WARN; "undone" -> ChipKind.NEUTRAL; else -> ChipKind.ERR })
                            }
                            Text("${fmt(r.at)} · ${r.subject}\n${r.text}".take(400), fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.state == "needs_confirm") Row { SmallLink("确认发送") { MailOutbox.confirm(ctx, b.id, r.id) }; SmallLink("取消") { MailOutbox.undo(ctx, b.id, r.id) } }
                            if (r.state == "queued") SmallLink("撤销") { MailOutbox.undo(ctx, b.id, r.id) }
                            if (r.detail.isNotBlank() && r.state == "failed") Text(r.detail, fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.error)
                        }
                        if (i < minOf(19, b.outbox.lastIndex)) MibotDivider(inset = 14.dp)
                    }
                }
            }
        }
        MibotSectionHeader("收件箱 · ${msgs.size}")
        MibotCard {
            err?.let { Text(it, fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp)) }
            if (msgs.isEmpty() && err == null) Text("暂无邮件", fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(14.dp))
            msgs.forEachIndexed { i, m ->
                Column(Modifier.fillMaxWidth().clickable { scope.launch { opened = m to MailCore.read(ctx, b, m) } }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(m.subject.ifBlank { "(无主题)" }, fontSize = MibotType.Body, fontWeight = MibotType.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text("${m.from} · ${m.date}", fontSize = MibotType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (opened?.first?.id == m.id) {
                        val text = opened!!.second
                        val (code, link) = MailCore.extract(m.subject + "\n" + text)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            code?.let { c -> StatusChip("验证码 $c", ChipKind.ACCENT); SmallLink("复制") { copy(ctx, "验证码", c) } }
                            link?.let { l -> SmallLink("复制链接") { copy(ctx, "链接", l) } }
                        }
                        Text(text.take(3000), fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (i < msgs.lastIndex) MibotDivider(inset = 14.dp)
            }
        }
    }
}
