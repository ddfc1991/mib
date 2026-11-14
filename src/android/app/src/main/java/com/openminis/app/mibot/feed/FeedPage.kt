package com.openminis.app.mibot.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.Glyph
import com.openminis.app.mibot.design.LineGlyph
import com.openminis.app.mibot.design.LineIcons
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotPageTitle
import com.openminis.app.mibot.home.DestinyHomeHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.v7.V7
import com.openminis.app.ui.glass.LocalGlassBottomInset
import kotlinx.coroutines.delay

private data class KindStyle(val glyph: LineGlyph, val color: Color, val zh: String, val en: String)

private fun kindStyle(kind: String): KindStyle = when (kind) {
    FeedKind.TASK_RESULT -> KindStyle(LineIcons.CalendarCheck, MibotTokens.Sky, "定时任务", "Scheduled task")
    FeedKind.KEY_ACQUIRED -> KindStyle(LineIcons.KeyRound, MibotTokens.Amber, "保险库", "Vault")
    FeedKind.PLUGIN_EVENT -> KindStyle(LineIcons.Puzzle, MibotTokens.Purple, "插件", "Plugin")
    FeedKind.FEIYU_DIARY -> KindStyle(LineIcons.Pencil, MibotTokens.Green, "日记", "Diary")
    FeedKind.GROUP_SUMMARY -> KindStyle(LineIcons.Users, MibotTokens.Teal, "群聊", "Group chat")
    else -> KindStyle(LineIcons.Activity, MibotTokens.Brick, "动态", "Activity")
}

/**
 * [fork/mibot-feed] 动态 tab: real entries from [Feed] (newest first), like,
 * 讨论 (new chat prefilled), delete with confirm. Tapping a card opens its
 * source chat / group when it has one.
 */
@Composable
fun FeedPage(
    onDiscuss: (String) -> Unit,
    onOpenSession: (String) -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
    // [destiny/real-home] 首页区（问候/最近对话/新对话）要真实数据，由调用方
    // 从会话库和当前状态算好传进来，这里只负责往下递。
    recents: List<com.openminis.app.mibot.home.HomeRecent> = emptyList(),
    statusLine: String = "",
    onNewChat: () -> Unit = {},
    dark: Boolean = false,
    // [destiny/ui-unify] V7 的外壳已经画了「动态」大标题，这里再画一次就是两层标题。
    // 由 V7 调用时传 false，页面只留内容；独立使用时保持原样。
    chrome: Boolean = true,
    // [destiny/ui-one-look] V7 皮肤：22dp 页边距、分组标题走 V7Section 规格、
    // 条目是平铺行 + 发丝线（不再是圆角卡片 + 阴影）。数据和交互完全不变。
    v7: Boolean = false,
) {
    val ctx = LocalContext.current
    val bottom = LocalGlassBottomInset.current
    val store = remember { Feed.store(ctx) }
    val entries by store.entries.collectAsState()
    var confirmDelete by remember { mutableStateOf<FeedEntry?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val zh = LocalConfiguration.current.locales.let { !it.isEmpty && it[0].language == "zh" }
    LaunchedEffect(Unit) {
        store.refresh()
        while (true) { delay(60_000); now = System.currentTimeMillis() }
    }
    val groups = remember(entries) { dayGroups(entries) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = if (v7) {
            PaddingValues(start = 22.dp, end = 22.dp, bottom = bottom + 24.dp)
        } else {
            PaddingValues(bottom = bottom + 24.dp)
        },
        verticalArrangement = Arrangement.spacedBy(if (v7) 0.dp else 12.dp),
    ) {
        if (chrome) {
            item { MibotPageTitle(t("动态", "Feed"), t("应用里发生的事，最新的在最上面", "What happened in the app, newest first")) }
        }
        item {
            DestinyHomeHeader(
                recents = recents,
                onOpenRecent = onOpenSession,
                statusLine = statusLine,
                onNewChat = onNewChat,
                dark = dark,
                onPhoneControl = { onDiscuss("打开手机操控（虚拟屏多开）") },
                onWechat = { onDiscuss("打开微信（ilinkai 绑定与收发）") },
                onAppAccess = { onDiscuss("打开应用访问（per-app 允许/禁用）") },
                onWallet = { onDiscuss("打开钱包（卡片与订阅）") },
                onSandbox = { onDiscuss("打开沙箱（镜像源与终端）") },
                onMonitor = { onDiscuss("打开监控（通道与用量）") },
                onSettings = { onDiscuss("打开设置") },
            )
            Spacer(Modifier.height(4.dp))
        }
        if (entries.isEmpty()) {
            item { if (v7) V7FeedEmpty() else FeedEmpty() }
        } else {
            groups.forEach { (label, list) ->
                item(key = "h_$label") {
                    if (v7) {
                        V7GroupHeader(label)
                    } else {
                        Text(
                            label,
                            fontSize = MibotType.Section,
                            fontWeight = MibotType.Semi,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { e ->
                    if (v7) {
                        V7FeedRow(
                            e = e,
                            timeLabel = FeedStore.relativeTime(e.ts, now, zh),
                            onLike = { store.toggleLike(e.id) },
                            onDiscuss = { onDiscuss(listOf(e.title, e.body).filter { it.isNotBlank() }.joinToString("\n")) },
                            onDelete = { confirmDelete = e },
                            onOpen = sourceOpener(e.sourceRef.orEmpty(), onOpenSession, onOpenGroup),
                        )
                    } else FeedCard(
                        e = e,
                        timeLabel = FeedStore.relativeTime(e.ts, now, zh),
                        onLike = { store.toggleLike(e.id) },
                        onDiscuss = { onDiscuss(listOf(e.title, e.body).filter { it.isNotBlank() }.joinToString("\n")) },
                        onDelete = { confirmDelete = e },
                        onOpen = sourceOpener(e.sourceRef.orEmpty(), onOpenSession, onOpenGroup),
                    )
                }
            }
        }
    }

    confirmDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(t("删除这条动态？", "Delete this entry?")) },
            text = { Text(e.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { store.delete(e.id); confirmDelete = null }) {
                    Text(t("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(t("取消", "Cancel")) } },
        )
    }
}

private fun sourceOpener(ref: String, onSession: (String) -> Unit, onGroup: (String) -> Unit): (() -> Unit)? = when {
    ref.startsWith("session:") -> ({ onSession(ref.removePrefix("session:")) })
    ref.startsWith("group:") -> ({ onGroup(ref.removePrefix("group:")) })
    else -> null
}

private fun dayGroups(entries: List<FeedEntry>): List<Pair<String, List<FeedEntry>>> {
    val cal = java.util.Calendar.getInstance()
    val now = System.currentTimeMillis()
    cal.timeInMillis = now
    val today = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterday = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
    val fmt = java.text.SimpleDateFormat("MM月dd日", java.util.Locale.getDefault())
    return entries.groupBy { e ->
        val c = java.util.Calendar.getInstance().apply { timeInMillis = e.ts }
        val day = c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
        when (day) {
            today -> "今天"
            yesterday -> "昨天"
            else -> fmt.format(java.util.Date(e.ts))
        }
    }.toList()
}

@Composable
private fun FeedEmpty() {
    MibotCard(Modifier.padding(top = 4.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Glyph(LineIcons.Newspaper, size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Text(t("还没有动态", "Nothing here yet"), fontSize = MibotType.Title, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                t("这里会自动出现：", "Things that will show up here:"),
                fontSize = MibotType.Secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            listOf(FeedKind.TASK_RESULT, FeedKind.KEY_ACQUIRED, FeedKind.PLUGIN_EVENT, FeedKind.GROUP_SUMMARY).forEach { k ->
                val s = kindStyle(k)
                val line = when (k) {
                    FeedKind.TASK_RESULT -> t("定时任务跑完后的结果", "Results when a scheduled task finishes")
                    FeedKind.KEY_ACQUIRED -> t("保险库里新保存的密钥（只显示服务名）", "Keys saved to the vault (service name only)")
                    FeedKind.PLUGIN_EVENT -> t("插件安装、启动、停止", "Plugins installed, started, stopped")
                    else -> t("群聊里 Bot 们的新回复", "New bot replies in group chats")
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindDot(s, 24)
                    Spacer(Modifier.width(10.dp))
                    Text(line, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t("点 讨论 可以把任意一条带进新对话。", "Tap Discuss to take any entry into a new chat."),
                fontSize = MibotType.Secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun KindDot(s: KindStyle, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(s.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Glyph(s.glyph, size = (size * 0.6f).dp, tint = s.color)
    }
}

@Composable
private fun FeedCard(
    e: FeedEntry,
    timeLabel: String,
    onLike: () -> Unit,
    onDiscuss: () -> Unit,
    onDelete: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val s = kindStyle(e.kind)
    var expanded by rememberSaveable(e.id) { mutableStateOf(false) }
    val tint = MibotTokens.iconTint()
    MibotCard(onClick = onOpen, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindDot(s, 30)
            Spacer(Modifier.width(10.dp))
            Text(t(s.zh, s.en), fontSize = MibotType.Secondary, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(timeLabel, fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(e.title, fontSize = MibotType.Body, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
        if (e.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                e.body,
                fontSize = MibotType.Body,
                lineHeight = MibotType.BodyLine,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val heart = if (e.liked) MibotTokens.Brick else tint
            ActionPill(LineIcons.Heart, if (e.liked) t("已赞", "Liked") else t("赞", "Like"), heart, filled = e.liked, onClick = onLike)
            ActionPill(LineIcons.MessageCircle, t("讨论", "Discuss"), tint, onClick = onDiscuss)
            Spacer(Modifier.weight(1f))
            ActionPill(LineIcons.Trash2, t("删除", "Delete"), tint, onClick = onDelete)
        }
    }
}

@Composable
private fun ActionPill(glyph: LineGlyph, label: String, tint: Color, filled: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, size = 16.dp, tint = tint, duotone = filled)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = MibotType.Secondary, color = tint)
    }
}

// ── [destiny/ui-one-look] V7 皮肤下的三个件：分组标题 / 条目行 / 空态 ──────────
// 规格与「设置」tab 完全一致：10sp 加宽分组标题 + 发丝线；条目 = 14sp 主标题 +
// 11.5sp 说明 + 右侧时间；行下 1dp 发丝线；22dp 页边距由调用方给。

@Composable
private fun V7GroupHeader(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = V7.Tx3, letterSpacing = 1.2.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(V7.Ln))
    }
}

@Composable
private fun V7FeedAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.5.sp,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    )
}

@Composable
private fun V7FeedRow(
    e: FeedEntry,
    timeLabel: String,
    onLike: () -> Unit,
    onDiscuss: () -> Unit,
    onDelete: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    var expanded by rememberSaveable(e.id) { mutableStateOf(false) }
    val s = kindStyle(e.kind)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(top = 13.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(V7.Sf2),
                contentAlignment = Alignment.Center,
            ) { Glyph(s.glyph, size = 14.dp, tint = V7.Tx2) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, fontSize = 14.sp, color = V7.Tx, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (e.body.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        e.body,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        color = V7.Tx2,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    V7FeedAction(if (e.liked) "已赞" else "赞", if (e.liked) V7.Cu else V7.Tx3, onLike)
                    V7FeedAction("讨论", V7.Tx3, onDiscuss)
                    V7FeedAction("删除", V7.Tx3, onDelete)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(timeLabel, fontSize = 10.5.sp, color = V7.Tx3)
                if (onOpen != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("\u203A", fontSize = 15.sp, color = V7.Tx3)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
    }
}

@Composable
private fun V7FeedEmpty() {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("还没有动态", fontSize = 13.sp, color = V7.Tx)
        Spacer(Modifier.height(4.dp))
        Text(
            "这里会自动出现：定时任务跑完的结果、保险库新保存的密钥（只显示服务名）、" +
                "插件安装与启停、群聊里 Bot 的新回复。点「讨论」可以把任意一条带进新对话。",
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = V7.Tx3,
        )
    }
}
