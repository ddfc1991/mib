package com.openminis.app.mibot.v7

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.openminis.app.mibot.home.DestinyHomeHeader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.data.db.ChatSessionEntity
import com.openminis.app.data.repository.ChatRepository
import com.openminis.app.ui.navigation.Routes

/**
 * [destiny/v7-full] The 56-screen design spec, rendered as NATIVE Compose UI.
 *
 * This is the app's real interface, not a WebView, not a screenshot and not a
 * list of spec sentences. Colours/sizes are pinned to the design tokens so the
 * screens look like the HTML spec regardless of the app's own theme switch.
 */
object V7 {
    val Bg = Color(0xFF0A0A0B)
    val Bg2 = Color(0xFF0F0F11)
    val Sf = Color(0xFF17171A)
    val Sf2 = Color(0xFF1F1F23)
    val Ln = Color(0x15FFFFFF)
    val Tx = Color(0xFFF2F0EB)
    val Tx2 = Color(0xFF9E9C97)
    val Tx3 = Color(0xFF67655F)
    val Cu = Color(0xFFC8926A)
    val Ok = Color(0xFF63B87F)
    val Wa = Color(0xFFC9A24D)
    val Er = Color(0xFFD2604F)

    private val Tones = listOf(
        Color(0xFF2B2A33), Color(0xFF26332F), Color(0xFF3A2E25),
        Color(0xFF252F3A), Color(0xFF332C39), Color(0xFF2E3327),
        Color(0xFF3A2B2B), Color(0xFF293238),
    )

    fun tone(key: String): Color {
        val n = key.removePrefix("c").toIntOrNull() ?: 0
        return if (n in 1..Tones.size) Tones[n - 1] else Sf2
    }
}

/**
 * Wrap an existing real feature screen in the V7 dark palette without touching
 * its implementation — its MaterialTheme reads are re-bound to design tokens.
 */
@Composable
fun V7MaterialWrap(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = V7.Bg,
            surface = V7.Sf,
            surfaceVariant = V7.Sf2,
            primary = V7.Cu,
            onPrimary = V7.Bg,
            onBackground = V7.Tx,
            onSurface = V7.Tx,
            onSurfaceVariant = V7.Tx2,
            outlineVariant = V7.Ln,
            error = V7.Er,
        ),
    ) {
        // [v7-real-contrast] Legacy screens paint their cards through
        // Modifier.frosted(), which decides dark/light from LocalMibotDark — NOT
        // from MaterialTheme. Re-binding only colorScheme left a white frosted
        // card under white onSurface text (unreadable roster rows). Publish the
        // dark flag too so the frosted tokens match the scheme above.
        CompositionLocalProvider(
            com.openminis.app.ui.theme.LocalMibotDark provides true,
        ) {
            Box(Modifier.fillMaxSize().background(V7.Bg)) { content() }
        }
    }
}

/** All real feature hooks, so every design screen can reach real app functions. */
data class V7Actions(
    val onAction: (String) -> Unit = {},
    val onVoice: () -> Unit = {},
    val onServers: () -> Unit = {},
    val onNewChat: () -> Unit = {},
    val onFeed: () -> Unit = {},
    val onAgents: () -> Unit = {},
    val onMcp: () -> Unit = {},
    val onLegacy: () -> Unit = {},
    val onIndex: () -> Unit = {},
    // [destiny] 能力格直达真实功能页（不是让 Agent 打开）。
    val onPhoneControl: () -> Unit = {},
    val onWechat: () -> Unit = {},
    val onAppAccess: () -> Unit = {},
    val onWallet: () -> Unit = {},
    val onSandbox: () -> Unit = {},
    val onMonitor: () -> Unit = {},
    // [destiny/v7-real] Open a REAL session from the real database.
    val onOpenSession: (String) -> Unit = {},
    // [destiny/v7-real] Navigate to any REAL route (providers / skills / MCP /
    // memory / storage / network / …). The settings tab is built from this:
    // every row below it opens the actual app screen, nothing is a mock.
    val onNavigate: (String) -> Unit = {},
)

@Composable
fun DestinyV7Home(
    actions: V7Actions,
    initialScreen: String? = null,
    initialIndex: Boolean = false,
    chatRepository: ChatRepository? = null,
) {
    var tab by remember { mutableStateOf(0) }
    var screenId by remember { mutableStateOf(initialScreen ?: V7_TAB_SCREENS[0]) }
    var showIndex by remember { mutableStateOf(initialIndex) }

    if (showIndex) {
        DestinyV7Index(
            onBack = { showIndex = false },
            onOpen = { id -> screenId = id; showIndex = false },
            actions = actions,
        )
        return
    }

    val screen = v7Screen(screenId) ?: v7Screen(V7_TAB_SCREENS[tab]) ?: return
    val header = screen.elems.firstOrNull { it.kind == "H" }?.a ?: screen.title
    val draft = { s: String -> actions.onAction("【${screen.title}】$s\n按这一条执行；需要先确认的先列给我。") }

    // [destiny/v7-real] Screen 01 is wired to the REAL session database — no
    // sample rows. When a repository is provided the design-spec conversation
    // list is replaced by live data; empty state tells the truth.
    val realSessions = if (screen.id == "01" && chatRepository != null) {
        chatRepository.observeSessions().collectAsState(initial = null).value
    } else null

    Column(
        Modifier
            .fillMaxSize()
            .background(V7.Bg)
            .statusBarsPadding(),
    ) {
        // Top bar: big V7 title + 「all screens」 + 「real functions」 entries.
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                header,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = V7.Tx,
                letterSpacing = (-0.8).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The spec's own top-bar icon actions (search / plus / …).
            screen.elems.filter { it.kind == "I" }.take(2).forEach { ic ->
                IconButton(onClick = { draft(ic.a) }) {
                    Icon(
                        if (ic.a.contains("search")) Icons.Filled.Search else Icons.Filled.Add,
                        contentDescription = ic.a,
                        tint = V7.Tx2,
                    )
                }
            }
            IconButton(onClick = { showIndex = true }) {
                Icon(Icons.Filled.Menu, contentDescription = "全部 56 屏", tint = V7.Tx2)
            }
            IconButton(onClick = actions.onLegacy) {
                Icon(Icons.Filled.Settings, contentDescription = "原功能", tint = V7.Tx2)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))

        // [destiny/v7-real] The four tabs are the app's REAL surfaces now:
        //   0 对话 = real session database (observeSessions)
        //   1 动态 = real feed store (DestinyFeedHost)
        //   2 群聊 = real sub-agent roster (SubAgentsScreen)
        //   3 设置 = real routes, every row opens an existing screen
        // The 56 design screens are still reachable from the top-bar menu,
        // but they are no longer what the app *is*.
        when (tab) {
            1 -> Box(Modifier.weight(1f)) {
                V7MaterialWrap {
                    com.openminis.app.mibot.feed.DestinyFeedHost(
                        onDiscuss = { actions.onAction(it) },
                        onOpenSession = { actions.onOpenSession(it) },
                        onOpenGroup = { actions.onAgents() },
                        onChat = { actions.onAction(it) },
                    )
                }
            }
            2 -> Box(Modifier.weight(1f)) {
                V7MaterialWrap {
                    com.openminis.app.ui.settings.SubAgentsScreen(
                        onBack = {},
                        onOpen = { id -> actions.onNavigate("sub_agent/" + id) },
                    )
                }
            }
            3 -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp),
            ) {
                v7RealSettings(actions) { showIndex = true }
            }
            else -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
            // Screens without a big header already show their title in the
            // top bar — do not repeat it in the content area.
            // [destiny] 动态屏(03) 顶部插首页区：模型卡/隧道死活/能力网格。
            if (screen.id == "03") {
                item {
                    DestinyHomeHeader(
                        onPhoneControl = actions.onPhoneControl,
                        onWechat = actions.onWechat,
                        onAppAccess = actions.onAppAccess,
                        onWallet = actions.onWallet,
                        onSandbox = actions.onSandbox,
                        onMonitor = actions.onMonitor,
                        onSettings = { draft("打开设置") },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (realSessions != null) {
                // Screen 01 with the REAL session database — sample rows are gone.
                // [v7-real-clean] No spec links and no「第 01 组 · 01 屏」caption
                // here: this tab is the app's real conversation list, not a
                // design-spec page, and the caption made it read like a mockup.
                // [destiny/queue-real] 多任务排队就地体现：真实作业表里的
                // 执行中 / 排队中，不藏到二级页面里。
                item { V7QueuePanel(onOpen = { actions.onNavigate("destiny_queue") }) }
                v7RealSessions(realSessions, actions)
            } else {
                items(screen.elems.size) { i ->
                    V7Element(screen.elems[i], screen, draft, actions)
                }
                item {
                    Spacer(Modifier.height(20.dp))
                    V7FeatureLinks(screen, actions)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "第 ${screen.group} 组 · ${screen.id} 屏" +
                            " · ${screen.elems.size} 个元素 · 点任一行交给 Agent",
                        fontSize = 10.sp,
                        color = V7.Tx3,
                    )
                }
            }
            }
        }

        V7TabBar(tab) { i -> tab = i; screenId = V7_TAB_SCREENS[i] }
    }
}

@Composable
private fun V7TabBar(tab: Int, onTab: (Int) -> Unit) {
    val labels = listOf("对话", "动态", "群聊", "设置")
    val icons = listOf(Icons.Filled.Chat, Icons.Filled.List, Icons.Filled.People, Icons.Filled.Settings)
    Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
    Row(
        Modifier
            .fillMaxWidth()
            .background(V7.Bg)
            .navigationBarsPadding()
            .padding(vertical = 9.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == tab
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onTab(i) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icons[i], contentDescription = label,
                    tint = if (on) V7.Cu else V7.Tx3,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(label, fontSize = 10.sp, color = if (on) V7.Cu else V7.Tx3)
            }
        }
    }
}

@Composable
private fun V7Element(
    e: V7Elem,
    screen: V7Screen,
    draft: (String) -> Unit,
    actions: V7Actions,
) {
    when (e.kind) {
        "H", "I" -> Unit // header and its icon actions are drawn in the top bar
        "D" -> Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln).padding(vertical = 6.dp))
        "Q" -> V7Search(e.a) { draft("搜索：「${e.a}」") }
        "S" -> V7Section(e.a, e.b)
        "R" -> V7Row(e) { draft(listOf(e.a, e.b).filter { it.isNotBlank() }.joinToString(" · ")) }
        "P" -> V7Chip(e.a, e.c)
        "B" -> V7Button(e.a) { draft(e.a) }
        "G" -> V7Toggle(e.a, e.b == "1") { draft("切换：「${e.a}」") }
        "N" -> Text(
            e.a, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = V7.Tx,
            modifier = Modifier.padding(vertical = 5.dp).clickable { draft(e.a) },
        )
        else -> V7Text(e.a, e.c) { draft(e.a) }
    }
}

// ---------------------------------------------------------------------------
// [destiny/v7-real] Screen 01 backed by the real session database.
// ---------------------------------------------------------------------------

private fun LazyListScope.v7RealSessions(
    sessions: List<ChatSessionEntity>?,
    actions: V7Actions,
) {
    if (sessions == null) {
        item {
            Text(
                "读取会话中…", fontSize = 12.sp, color = V7.Tx3,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }
        return
    }
    if (sessions.isEmpty()) {
        item {
            V7Section("最近", "0 条")
            Text(
                "还没有真实对话。点右上角 ＋ 或下方按钮新建一个 —— 对话真实保存在本机数据库，之后会出现在这里。",
                fontSize = 12.5.sp, color = V7.Tx2, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(14.dp))
            V7Button("＋ 新建真实对话") { actions.onNewChat() }
        }
        return
    }
    val pinned = sessions.filter { it.pinnedAt != null }.sortedByDescending { it.pinnedAt ?: 0L }
    val rest = sessions.filter { it.pinnedAt == null }.sortedByDescending { it.updatedAt }
    if (pinned.isNotEmpty()) {
        item { V7Section("置顶", "${pinned.size}") }
        items(pinned, key = { "pinned-" + it.id }) { s -> V7SessionRow(s) { actions.onOpenSession(s.id) } }
    }
    item { V7Section("最近", "${rest.size}") }
    items(rest.take(80), key = { it.id }) { s -> V7SessionRow(s) { actions.onOpenSession(s.id) } }
}

@Composable
private fun V7SessionRow(s: ChatSessionEntity, onClick: () -> Unit) {
    val title = s.title?.takeIf { it.isNotBlank() } ?: "未命名对话"
    val sub = s.lastMessage?.takeIf { it.isNotBlank() } ?: s.modelId
    val time = v7RelTime(s.updatedAt)
    val toneKey = "c" + (1 + ((title.hashCode() and 0x7FFFFFFF) % 8))
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(V7.tone(toneKey)),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.take(2), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = V7.Tx)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = V7.Tx,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (sub.isNotBlank()) {
                    Text(
                        sub, fontSize = 11.5.sp, color = V7.Tx2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (time.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(time, fontSize = 10.5.sp, color = V7.Tx3)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
}

private fun v7IsSameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun v7RelTime(ms: Long): String {
    if (ms <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - ms
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        v7IsSameDay(now, ms) ->
            "今天 " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))
        v7IsSameDay(now - 86_400_000L, ms) -> "昨天"
        else -> java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()).format(java.util.Date(ms))
    }
}

@Composable
private fun V7Search(placeholder: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(V7.Sf)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = V7.Tx3, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(9.dp))
        Text(placeholder, fontSize = 13.sp, color = V7.Tx3)
    }
}

@Composable
private fun V7Section(label: String, meta: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = V7.Tx3, letterSpacing = 1.2.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(V7.Ln))
        if (meta.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(meta, fontSize = 10.5.sp, color = V7.Tx3)
        }
    }
}

@Composable
private fun V7Row(e: V7Elem, onClick: () -> Unit) {
    val bits = e.d.split("|")
    val avatar = bits.getOrNull(0).orEmpty()
    val tone = bits.getOrNull(1).orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatar.isNotBlank()) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(V7.tone(tone)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(avatar.take(2), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = V7.Tx)
                }
                Spacer(Modifier.width(13.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    e.a, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = V7.Tx,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (e.b.isNotBlank()) {
                    Text(
                        e.b, fontSize = 11.5.sp, color = V7.Tx2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (e.c.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(e.c, fontSize = 10.5.sp, color = V7.Tx3)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
}

@Composable
private fun V7Chip(text: String, kind: String) {
    val bg = if (kind == "ok") Color(0x1A63B87F) else V7.Sf
    val fg = if (kind == "ok") V7.Ok else V7.Tx2
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) { Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = fg) }
    }
}

@Composable
private fun V7Button(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(V7.Tx)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = V7.Bg) }
}

@Composable
private fun V7Toggle(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = V7.Tx, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(40.dp).height(24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) V7.Cu else V7.Sf2)
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(999.dp)).background(Color.White))
        }
    }
}

@Composable
private fun V7Text(text: String, hint: String, onClick: () -> Unit) {
    val size = when {
        hint.contains("d1") -> 22.sp
        hint.contains("num") -> 19.sp
        hint.contains("lb") -> 10.sp
        hint.contains("mt") || hint.contains("xs") || hint.contains("sm") || hint.contains(" s") -> 11.5.sp
        hint.contains("mono") -> 12.sp
        else -> 13.5.sp
    }
    val color = when {
        hint.contains("ok") -> V7.Ok
        hint.contains("wa") -> V7.Wa
        hint.contains("er") -> V7.Er
        hint.contains("cu") -> V7.Cu
        hint.contains("lb") || hint.contains("mt") || hint.contains("s") || hint.contains("ph") -> V7.Tx3
        else -> V7.Tx
    }
    val weight = when {
        hint.contains("d1") || hint.contains("lb") || hint.contains("num") -> FontWeight.Bold
        hint.contains("bold") -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    Text(
        text, fontSize = size, color = color, fontWeight = weight,
        lineHeight = (size.value * 1.55f).sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
    )
}

/** Every design screen keeps a path into the app's REAL functions. */
@Composable
private fun V7FeatureLinks(screen: V7Screen, actions: V7Actions) {
    val links = mutableListOf<Pair<String, () -> Unit>>()
    when (screen.id) {
        "01", "02" -> links += "＋ 新建真实对话" to actions.onNewChat
        "03", "04", "05", "05b" -> links += "打开动态三态 · 在看 / 想到的 / 能领的" to actions.onFeed
        "34" -> links += "打开真实语音 · 按住说" to actions.onVoice
        "45", "45b", "45c" -> links += "打开服务器管理 · 真实巡检（本屏指标是设计稿示例值）" to actions.onServers
        "46" -> links += "打开能力 / Agent 设置" to actions.onAgents
        "47" -> links += "打开 MCP 服务" to actions.onMcp
    }
    // Mika's whole block (后台屏 / 运行过程 / 教 Mika / 接管 / 询问与自主 /
    // 悬浮胶囊 / 应用访问 / 记忆 / AI 接打电话 / 微信派单 …) hands off to the
    // real Agent rather than faking a local result.
    if (screen.group == "06" || screen.group == "07") {
        links += "把 Mika 这一屏交给 Agent 执行" to {
            actions.onAction("【Mika · ${screen.title}】按这一屏的流程执行；需要确认的先列给我。")
        }
    }
    // Every screen can hand itself to the Agent as a real chat draft.
    links += "把这一屏交给 Agent 执行" to {
        actions.onAction("【${screen.title}】按这一屏的全部规格执行；需要先确认的逐条列给我。")
    }
    Column(Modifier.fillMaxWidth()) {
        links.forEach { (label, go) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(V7.Sf)
                    .clickable(onClick = go)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = V7.Cu, modifier = Modifier.weight(1f))
                Text("→", fontSize = 13.sp, color = V7.Tx3)
            }
        }
    }
}

/** Native index of all 56 design screens, in the same V7 visual language. */
@Composable
private fun DestinyV7Index(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    actions: V7Actions,
) {
    val groups = DESTINY_V7_SCREENS.groupBy { it.group }
    Column(Modifier.fillMaxSize().background(V7.Bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = V7.Tx2)
            }
            Text("全部 56 屏 · 设计规格", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = V7.Tx)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(V7.Cu))
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 30.dp),
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    "每一屏都是原生界面：大标题、搜索框、分区、头像行、chip、按钮、开关按设计稿重画。点任意一屏进去，也能直接交给 Agent 去办。",
                    fontSize = 12.sp, color = V7.Tx2,
                )
                Spacer(Modifier.height(8.dp))
            }
            groups.forEach { (group, list) ->
                item {
                    V7Section("第 $group 组 · ${list.size} 屏", "")
                }
                items(list, key = { it.id }) { s ->
                    V7Row(
                        V7Elem("R", s.title, "${s.elems.size} 个元素 · 第 ${s.group} 组", "", s.id + "|c3"),
                    ) { onOpen(s.id) }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(V7.Sf)
                        .clickable(onClick = actions.onVoice).padding(16.dp),
                ) { Text("打开真实语音 · 按住说", fontSize = 13.sp, color = V7.Cu, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(V7.Sf)
                        .clickable(onClick = actions.onServers).padding(16.dp),
                ) { Text("打开服务器管理", fontSize = 13.sp, color = V7.Cu, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}


// ---------------------------------------------------------------------------
// [destiny/v7-real] Settings tab — the app's REAL screens, grouped by domain.
// Every row navigates to a route that already exists in AppNavigation.
// Nothing in this catalog is a mock: if a row is here, tapping it opens the
// real screen (providers, model groups, skills, memory, MCP, storage,
// network, backup, logs, permissions, ...).
// ---------------------------------------------------------------------------

private data class V7SettingEntry(val label: String, val hint: String, val route: String)

private fun v7SettingsCatalog(): List<Pair<String, List<V7SettingEntry>>> = listOf(
    "模型" to listOf(
        V7SettingEntry("模型服务商", "API Key · 账号 · 余额", Routes.PROVIDER_LIST),
        V7SettingEntry("模型组", "主模型 · 备用 · 路由顺序", Routes.MODEL_GROUPS),
        V7SettingEntry("用量统计", "token · 调用次数 · 花费", Routes.USAGE_STATS),
    ),
    "能力" to listOf(
        V7SettingEntry("子 Agent", "派单用的 Agent 花名册", Routes.AGENTS),
        V7SettingEntry("技能", "装在设备上的技能包", Routes.SKILLS),
        V7SettingEntry("记忆", "长期记忆与文件", Routes.MEMORY),
        V7SettingEntry("MCP 服务器", "外部工具接入", Routes.MCP),
        V7SettingEntry("环境变量", "注入到运行时的变量", Routes.ENV_VARS),
    ),
    "设备与手机" to listOf(
        V7SettingEntry("语音", "转写 · 朗读 · 唤醒", "destiny_voice"),
        V7SettingEntry("手机控制", "无障碍 · 屏幕 · 点击", Routes.MIBOT_APP_ACCESS),
        V7SettingEntry("微信", "未接入 · 不显示假账号", "destiny_not_wired/wechat"),
        V7SettingEntry("任务队列", "真实作业 · 执行中 · 排队", "destiny_queue"),
        V7SettingEntry("钱包", "未接入 · 不显示假余额", "destiny_not_wired/wallet"),
    ),
    "存储与文件" to listOf(
        V7SettingEntry("存储", "占用 · 清理", Routes.STORAGE),
        V7SettingEntry("根文件系统", "容器镜像", Routes.ROOTFS_MANAGEMENT),
        V7SettingEntry("挂载目录", "宿主机目录映射", Routes.MOUNTED_FOLDERS),
        V7SettingEntry("文件浏览器", "浏览容器内文件", Routes.FILE_BROWSER),
        V7SettingEntry("备份", "导出 · 导入 · 目标", Routes.BACKUP),
    ),
    "网络" to listOf(
        V7SettingEntry("网络", "连通性 · DNS", Routes.NETWORK),
        V7SettingEntry("代理", "HTTP / SOCKS 代理", Routes.NETWORK_PROXIES),
        V7SettingEntry("Tailscale", "组网与设备", Routes.NETWORK_TAILSCALE),
        V7SettingEntry("容器 DNS", "容器内解析", Routes.CONTAINER_DNS),
    ),
    "系统" to listOf(
        V7SettingEntry("外观", "主题 · 字体 · 图标", Routes.APPEARANCE),
        V7SettingEntry("背景", "壁纸与模糊", Routes.BACKGROUND),
        V7SettingEntry("权限", "系统权限总览", Routes.PERMISSIONS),
        V7SettingEntry("日志", "运行日志与诊断", Routes.LOGS),
        V7SettingEntry("定时任务", "Cron 与触发", Routes.SCHEDULED_TASKS),
        V7SettingEntry("关于", "版本 · 构建 · 许可", Routes.ABOUT),
    ),
)

private fun LazyListScope.v7RealSettings(actions: V7Actions, onSpec: () -> Unit) {
    val catalog = v7SettingsCatalog()
    item {
        Text(
            "以下每一项都是真实功能页 —— 点进去是该功能的实际界面，不是示意图。",
            fontSize = 12.sp,
            color = V7.Tx2,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
    catalog.forEach { (title, items) ->
        item { V7Section(title, "${items.size} 项") }
        items(items, key = { "set-" + it.route }) { e ->
            V7SettingRow(e) { actions.onNavigate(e.route) }
        }
    }
    item {
        Spacer(Modifier.height(26.dp))
        V7Section("设计规格", "56 屏")
        Text(
            "这套 56 屏是产品设计稿（V7 规格），用来对齐视觉与信息架构，不是功能页。" +
                "上面那些入口才是能真正干活的地方。",
            fontSize = 12.sp,
            color = V7.Tx2,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(12.dp))
        V7Button("查看 56 屏设计规格") { onSpec() }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun V7SettingRow(e: V7SettingEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = V7.Tx)
            Text(e.hint, fontSize = 11.5.sp, color = V7.Tx3)
        }
        Text("\u203A", fontSize = 17.sp, color = V7.Tx3)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
}

// ─── [destiny/queue-real] 真实多任务队列 ─────────────────────────────────────
//
// 数据源是全局的 AgentJobRegistry.jobs（StateFlow<Map<String, AgentJob>>）：
// PENDING = 排队，RUNNING = 正在执行。它是派单 / 子 Agent / 定时任务真正在用的
// 那张表，所以这里显示的排队条数就是 App 此刻真实的排队量，不是示例数据。
// 队列空闲时也照实说「空闲」，不假装有任务。

private fun v7Dur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}

@Composable
private fun v7ActiveJobs(): List<com.openminis.app.agent.jobs.AgentJob> {
    val all by com.openminis.app.agent.jobs.AgentJobRegistry.jobs.collectAsState()
    return all.values
        .filter { it.isActive }
        .sortedWith(
            compareBy(
                { if (it.state == com.openminis.app.agent.jobs.AgentJobState.RUNNING) 0 else 1 },
                { it.createdAtMs },
            ),
        )
}

@Composable
private fun V7JobRow(job: com.openminis.app.agent.jobs.AgentJob) {
    val running = job.state == com.openminis.app.agent.jobs.AgentJobState.RUNNING
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (running) V7.Ok else V7.Wa),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (job.title.ifBlank { job.prompt ?: "任务" }).replace("\n", " ").take(30),
                fontSize = 13.sp,
                color = V7.Tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val waited = job.startedAtMs?.let { System.currentTimeMillis() - it }
            Text(
                when {
                    running && waited != null -> "执行中 · " + v7Dur(waited)
                    running -> "执行中"
                    waited == null -> "排队中"
                    else -> "排队中"
                },
                fontSize = 11.sp,
                color = V7.Tx3,
            )
        }
        if (!running) V7Tag("排队", V7.Wa)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
}

/**
 * [destiny/queue-real] 行内小标签。
 *
 * 不能用 V7Chip：它是 fillMaxWidth 的整行组件（给设置列表一行一个徽标用的），
 * 塞进 Row 里会把整行撑满、把左侧文字挤成零宽。这里必须是 wrap-content。
 */
@Composable
private fun V7Tag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

/** 对话页顶部的队列条：一行状态 + 前几条任务；点「查看全部」进任务队列页。 */
@Composable
private fun V7QueuePanel(onOpen: () -> Unit, expanded: Boolean = false) {
    val active = v7ActiveJobs()
    val running = active.count { it.state == com.openminis.app.agent.jobs.AgentJobState.RUNNING }
    val pending = active.size - running
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("任务队列", fontSize = 12.sp, color = V7.Tx2, letterSpacing = 0.4.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(1.dp).background(V7.Ln))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    active.isEmpty() -> "空闲"
                    pending == 0 -> "$running 执行中"
                    running == 0 -> "$pending 排队"
                    else -> "$running 执行 · $pending 排队"
                },
                fontSize = 11.sp,
                color = if (active.isEmpty()) V7.Tx3 else V7.Cu,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (active.isEmpty()) {
            Text(
                "现在没有任务在跑。发消息时如果一个任务还没跑完，新消息会排队，做完自动接下一个。",
                fontSize = 11.5.sp,
                color = V7.Tx3,
                lineHeight = 18.sp,
            )
        } else {
            val shown = if (expanded) active else active.take(3)
            shown.forEach { job -> V7JobRow(job) }
            if (!expanded && active.size > 3) {
                Text(
                    "查看全部 ${active.size} 项 \u203A",
                    fontSize = 12.sp,
                    color = V7.Cu,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpen)
                        .padding(vertical = 8.dp),
                )
            } else if (expanded) {
                Text(
                    "共 ${active.size} 项 · 执行中 $running · 排队 $pending",
                    fontSize = 11.sp,
                    color = V7.Tx3,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** 任务队列整页：真实作业 + 真实状态。 */
@Composable
fun DestinyV7QueueScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(V7.Bg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = V7.Tx)
            }
            Text(
                "任务队列",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = V7.Tx,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 24.dp),
        ) {
            item { V7QueuePanel(onOpen = {}, expanded = true) }
            item {
                Spacer(Modifier.height(18.dp))
                Text(
                    "这一页读的是派单引擎真实的作业表：每个子 Agent、每条后台任务都在里面。" +
                        "一个任务没跑完时新任务只会排队（PENDING），不会把上一条顶掉；" +
                        "跑完自动接下一个。",
                    fontSize = 11.5.sp,
                    color = V7.Tx3,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

// ─── [destiny/honest-state] 还没接后端的页，如实说没接 ──────────────────────
//
// 微信（ilinkai 通道）和钱包（支付通道）在这一版没有真实后端。之前的实现把它们
// 画成「已连接 destiny_bot」「¥ 1,284.50 / 尾号 8842」——那是写死的示例数据，
// 看起来像真的，实际什么都做不了。这里改成如实状态：没有任何编造的数字或账号。
@Composable
fun DestinyV7NotWired(
    which: String,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    val title = if (which == "wallet") "钱包" else "微信"
    val missing = if (which == "wallet") {
        "这一版没有接支付通道：没有余额、没有绑卡、没有账单。所以这里不显示金额 —— 之前的 ¥1,284.50 / 尾号 8842 是写死的示例，已经去掉。"
    } else {
        "这一版没有接微信（ilinkai）通道：没有已连接账号、没有扫码授权、没有聊天记录。之前的「已连接 destiny_bot」和两条「最近」是写死的示例，已经去掉。"
    }
    val needs = if (which == "wallet") {
        listOf("支付通道与商户号", "单笔限额与二次确认", "真实账单流水")
    } else {
        listOf("ilinkai 账号授权", "常驻通知权限（收消息）", "已配置的模型（派单执行）")
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(V7.Bg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = V7.Tx)
            }
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = V7.Tx, modifier = Modifier.weight(1f))
            V7Tag("未接入", V7.Wa)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
        Column(Modifier.weight(1f).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(missing, fontSize = 13.sp, color = V7.Tx, lineHeight = 21.sp)
            Spacer(Modifier.height(20.dp))
            Text("要接上还差", fontSize = 12.sp, color = V7.Tx2)
            Spacer(Modifier.height(8.dp))
            needs.forEach { s ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(V7.Wa))
                    Spacer(Modifier.width(9.dp))
                    Text(s, fontSize = 12.5.sp, color = V7.Tx2)
                }
            }
            Spacer(Modifier.height(22.dp))
            V7Button("看系统权限") { onOpenPermissions() }
            Spacer(Modifier.height(10.dp))
            V7Button("模型服务商") { onOpenProviders() }
        }
    }
}
