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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.MinisApp
import com.openminis.app.data.db.ChatSessionEntity
import com.openminis.app.data.db.MessageEntity
import com.openminis.app.data.model.ProviderConfig
import com.openminis.app.mibot.servers.ServerVaultStore
import com.openminis.app.mibot.servers.inspectPrompt
import com.openminis.app.mibot.servers.metricsPrompt
import com.openminis.app.mibot.vault.VaultStore
import com.openminis.app.ui.settings.getThemeMode
import java.util.Calendar
import java.util.concurrent.TimeUnit

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
        Box(Modifier.fillMaxSize().background(V7.Bg)) { content() }
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
    /** [destiny/v7] 01/02 屏点会话行 → 真实 ChatScreen。 */
    val onOpenSession: (String) -> Unit = {},
    /** [destiny/v7] 16 屏分组行 → 真实设置入口（route 字符串）。 */
    val onOpenRoute: (String) -> Unit = {},
)

@Composable
fun DestinyV7Home(
    actions: V7Actions,
    initialScreen: String? = null,
    initialIndex: Boolean = false,
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

    // [destiny/v7] 34 语音屏：整屏接上真实 DestinyVoiceScreen（系统
    // SpeechRecognizer / TextToSpeech），「按住说」直接触发真实识别。
    if (screen.id == "34") {
        V7MaterialWrap {
            com.openminis.app.mibot.voice.DestinyVoiceScreen(
                // 34 是从索引/标签进入的，返回回到当前标签屏而不是弹出导航栈。
                onBack = { screenId = V7_TAB_SCREENS[tab] },
                onSend = { text -> actions.onAction("【语音】$text") },
            )
        }
        return
    }

    val ctx = LocalContext.current
    val app = ctx.applicationContext as? MinisApp
    val chatRepo = app?.chatRepositoryOrNull
    val providerRepo = app?.providerRepositoryOrNull

    // [destiny/v7] 01/02 屏的真实数据源：会话与消息来自 ChatRepository（Room）。
    val sessions by remember(chatRepo) {
        chatRepo?.observeSessions()
            ?: kotlinx.coroutines.flow.flowOf<List<ChatSessionEntity>>(emptyList())
    }.collectAsState(initial = emptyList())
    val latestSessionId = sessions.firstOrNull { !it.isChild }?.id
    val messages by remember(chatRepo, latestSessionId) {
        if (chatRepo == null || latestSessionId == null) {
            kotlinx.coroutines.flow.flowOf<List<MessageEntity>>(emptyList())
        } else {
            chatRepo.observeMessages(latestSessionId)
        }
    }.collectAsState(initial = emptyList())
    // [destiny/v7] 16 屏的真实设置源：当前模型（ProviderRepository）、账号台账（VaultStore）。
    val config by remember(providerRepo) {
        providerRepo?.config ?: kotlinx.coroutines.flow.flowOf(ProviderConfig())
    }.collectAsState(initial = ProviderConfig())
    val accounts by remember(ctx) { VaultStore.entries(ctx) }.collectAsState()
    val themeMode = remember(ctx) { getThemeMode(ctx) }

    val elems = buildV7Elems(screen, sessions, messages, config, accounts.size, themeMode)
    val header = screen.elems.firstOrNull { it.kind == "H" }?.a ?: screen.title
    val draft = { s: String -> actions.onAction("【${screen.title}】$s\n按这一条执行；需要先确认的先列给我。") }

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

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Screens without a big header already show their title in the
            // top bar — do not repeat it in the content area.
            items(elems.size) { i ->
                V7Element(elems[i], screen, draft, actions)
            }
            item {
                Spacer(Modifier.height(20.dp))
                V7FeatureLinks(screen, actions)
                Spacer(Modifier.height(10.dp))
                Text(
                    "第 ${screen.group} 组 · ${screen.id} 屏 · ${elems.size} 个元素 · 点任一行交给 Agent",
                    fontSize = 10.sp,
                    color = V7.Tx3,
                )
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
    // "insp" marks the 45c screen's metric rows + 「让 Agent 巡检」 button: the
    // real inspection path lives in ServerVaultPage, so route there / draft it.
    val insp = e.d == "insp"
    // 真实入口标记（放在 d 的尾部，不破坏 V7Row 的 avatar/tone 解析）：
    // "session|<id>" → 真实聊天屏；"route|<route>" → 真实设置入口；"voice" → 语音屏。
    val open = v7OpenAction(e, actions)
    val ctx = LocalContext.current
    val runInspect: () -> Unit = {
        val servers = ServerVaultStore.load(ctx)
        if (servers.isEmpty()) actions.onServers()
        else actions.onAction(
            if (e.kind == "B") inspectPrompt(servers) else metricsPrompt(servers),
        )
    }
    when (e.kind) {
        "H", "I" -> Unit // header and its icon actions are drawn in the top bar
        "D" -> Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln).padding(vertical = 6.dp))
        "Q" -> V7Search(e.a) { draft("搜索：「${e.a}」") }
        "S" -> V7Section(e.a, e.b)
        "R" -> V7Row(e) { open?.invoke() ?: draft(listOf(e.a, e.b).filter { it.isNotBlank() }.joinToString(" · ")) }
        "P" -> V7Chip(e.a, e.c)
        "B" -> V7Button(e.a) { open?.invoke() ?: if (insp) runInspect() else draft(e.a) }
        "G" -> V7Toggle(e.a, e.b == "1") { draft("切换：「${e.a}」") }
        "N" -> Text(
            e.a, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = V7.Tx,
            modifier = Modifier.padding(vertical = 5.dp).clickable { open?.invoke() ?: draft(e.a) },
        )
        else -> V7Text(e.a, e.c) { open?.invoke() ?: if (insp) runInspect() else draft(e.a) }
    }
}

/** 真实入口标记 → 点击动作；无标记返回 null（回退到「交给 Agent」草稿）。 */
private fun v7OpenAction(e: V7Elem, actions: V7Actions): (() -> Unit)? {
    val bits = e.d.split("|")
    for (i in bits.indices) {
        when (bits[i]) {
            "session" -> {
                val id = bits.getOrNull(i + 1).orEmpty()
                if (id.isNotEmpty()) return { actions.onOpenSession(id) }
            }
            "route" -> {
                val route = bits.getOrNull(i + 1).orEmpty()
                if (route.isNotEmpty()) return { actions.onOpenRoute(route) }
            }
            "voice" -> return { actions.onVoice() }
        }
    }
    return null
}

/**
 * [destiny/v7] 01/02/16 三屏的真实数据源：会话/消息来自 ChatRepository，
 * 当前模型来自 ProviderRepository，账号数来自 VaultStore。其余屏原样渲染。
 */
private fun buildV7Elems(
    screen: V7Screen,
    sessions: List<ChatSessionEntity>,
    messages: List<MessageEntity>,
    config: ProviderConfig,
    accountCount: Int,
    themeMode: Int,
): List<V7Elem> = when (screen.id) {
    "01" -> buildSessionListElems(sessions)
    "02" -> buildConversationElems(config, sessions, messages)
    "16" -> buildSettingsElems(screen, config, accountCount, themeMode)
    else -> screen.elems
}

/** 01 会话列表：真实会话（置顶在前，最近在后），点行开真实聊天屏。 */
private fun buildSessionListElems(sessions: List<ChatSessionEntity>): List<V7Elem> {
    val visible = sessions.filter { !it.isChild }
    if (visible.isEmpty()) {
        return listOf(
            V7Elem("H", "会话"),
            V7Elem("Q", "搜索会话、文件、记忆"),
            V7Elem("T", "还没有会话，说一句开始"),
        )
    }
    val pinned = visible.filter { it.pinnedAt != null }.sortedByDescending { it.pinnedAt }
    val recent = visible.filter { it.pinnedAt == null }.sortedByDescending { it.updatedAt }
    return buildList {
        add(V7Elem("H", "会话"))
        add(V7Elem("Q", "搜索会话、文件、记忆"))
        add(V7Elem("S", "置顶", "${pinned.size} / ${visible.size}"))
        add(V7Elem("D"))
        pinned.forEach { add(sessionRow(it)) }
        add(V7Elem("S", "最近", "今天"))
        add(V7Elem("D"))
        add(V7Elem("T", "今天"))
        recent.take(8).forEach { add(sessionRow(it)) }
        add(V7Elem("T", "归档 ${visible.size} 个"))
    }
}

private fun sessionRow(s: ChatSessionEntity): V7Elem {
    val title = s.title ?: "新对话"
    val avatar = title.trim().take(1).ifEmpty { "新" }
    val tone = "c" + ((s.id.hashCode().mod(8)) + 1)
    return V7Elem(
        "R",
        title,
        s.lastMessage ?: "暂无消息",
        relativeTime(s.updatedAt),
        "$avatar|$tone|session|${s.id}",
    )
}

/** 02 对话：最近一个真实会话的消息流，点行开真实聊天屏。 */
private fun buildConversationElems(
    config: ProviderConfig,
    sessions: List<ChatSessionEntity>,
    messages: List<MessageEntity>,
): List<V7Elem> {
    val s = sessions.firstOrNull { !it.isChild } ?: return listOf(
        V7Elem("T", "Destiny"),
        V7Elem("T", "还没有对话"),
        V7Elem("Q", "发消息给 Destiny（@ 提及文件）"),
    )
    val snapshot = messages.lastOrNull { it.modelDisplayName != null }
    val entry = config.modelEntries.firstOrNull { it.baseModel.id == s.modelId || it.model.id == s.modelId }
    val inst = entry?.let { e -> config.instances.find { it.id == e.providerInstanceId } }
        ?: snapshot?.let { m -> config.instances.find { it.id == m.providerInstanceId } }
    val modelName = snapshot?.modelDisplayName ?: entry?.model?.displayName ?: s.modelId
    val provider = inst?.label ?: (snapshot?.providerType).orEmpty()
    return buildList {
        add(V7Elem("T", s.title ?: "新对话"))
        add(V7Elem("T", modelName))
        add(V7Elem("T", if (provider.isBlank()) "当前会话" else "通过 $provider"))
        val body = messages.takeLast(24)
        if (body.isEmpty()) add(V7Elem("T", "还没有消息"))
        body.forEach { m ->
            val text = messageText(m.partsJson)
            if (text.isNotBlank()) add(V7Elem("T", text, "", "", "|session|${s.id}"))
        }
        add(V7Elem("Q", "发消息给 Destiny（@ 提及文件）"))
    }
}

/** 16 设置：当前模型取真实默认模型组；分组行点击进真实设置入口。 */
private fun buildSettingsElems(
    screen: V7Screen,
    config: ProviderConfig,
    accountCount: Int,
    themeMode: Int,
): List<V7Elem> {
    val group = config.modelGroups.find { it.id == config.defaultPrimaryGroupId }
        ?: config.modelGroups.firstOrNull()
    val entry = group?.memberEntryIds?.firstNotNullOfOrNull { id -> config.modelEntries.find { it.id == id } }
    val inst = entry?.let { e -> config.instances.find { it.id == e.providerInstanceId } }
    val modelLine = if ((entry == null || inst == null) && config.instances.isNotEmpty()) {
        "${config.instances.size} 个服务商 · 未设默认模型组"
    } else if (entry == null || inst == null) {
        "未配置 · 点此添加服务商"
    } else {
        "${inst.label} · ${entry.model.displayName}" + (group?.name?.let { " · $it" } ?: "")
    }
    return screen.elems.map { e ->
        when (e.a) {
            "当前模型" -> e.copy(b = modelLine, d = "|c0|route|${com.openminis.app.ui.navigation.Routes.MODEL_GROUPS}")
            "技能" -> e.copy(d = "|c0|route|${com.openminis.app.ui.navigation.Routes.SKILLS}")
            "记忆" -> e.copy(d = "|c0|route|${com.openminis.app.ui.navigation.Routes.MEMORY}")
            "人格" -> e.copy(d = "|c0|route|${com.openminis.app.ui.navigation.Routes.SOUL}")
            "自动化" -> e.copy(d = "|c0|route|${com.openminis.app.ui.navigation.Routes.SCHEDULED_TASKS}")
            "账号台账" -> e.copy(b = "$accountCount 个账号", d = "|c0|route|mibot_vault")
            "本地网关" -> e.copy(d = "|c0|route|mibot_gateway")
            "沙箱" -> e.copy(d = "|c0|route|mibot_sandbox")
            "AI 邮箱" -> e.copy(d = "|c0|route|mibot_mail")
            "跟随系统" -> e.copy(
                b = "当前：" + themeLabel(themeMode),
                d = "|c0|route|${com.openminis.app.ui.navigation.Routes.APPEARANCE}",
            )
            "工具调用详情" -> e.copy(d = "|c0|route|${com.openminis.app.ui.navigation.Routes.AGENT_TOOLS}")
            else -> e
        }
    }
}

private fun themeLabel(mode: Int): String = when (mode) {
    1 -> "浅色"
    2 -> "深色"
    else -> "跟随系统"
}

/** 取 parts_json 里全部 text 片段（02 屏渲染真实消息用）。 */
private fun messageText(partsJson: String): String = try {
    val arr = org.json.JSONArray(partsJson)
    val sb = StringBuilder()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        if (o.optString("type") == "text") {
            val v = o.optString("value", "")
            if (v.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(v)
            }
        }
    }
    sb.toString().trim()
} catch (_: Exception) {
    ""
}

private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 60 * 24 -> "${TimeUnit.MILLISECONDS.toHours(diff)} 小时前"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
        }
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
