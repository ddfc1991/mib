package com.mibot.life

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.life.life.LifeSystem
import com.mibot.life.settings.LifeSettings
import com.mibot.life.settings.SettingsStore
import com.mibot.life.ui.EconomyScreen
import com.mibot.life.ui.GrowthScreen
import com.mibot.life.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LifeAppShell() } }
    }
}

private val SkyTop = Color(0xFFDDEAF5)
private val SkyBottom = Color(0xFFF7F0E6)
private val InkDark = Color(0xFF3E3A36)
private val MutedInk = Color(0xFF8B857C)
private val Coral = Color(0xFFE8825A)
private val BubbleMine = Color(0xFFCBE4F5)
private val BubbleThem = Color(0xFFF0E9DF)

private data class ChatMsg(val from: Boolean, val text: String)

private val Tabs = listOf("生活", "对话", "成长", "经济", "设置")

@Composable
private fun LifeAppShell() {
    val ctx = LocalContext.current
    val life = remember { LifeSystem(ctx.applicationContext) }
    val settingsStore = remember { SettingsStore(ctx.applicationContext) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom))),
    ) {
        // 顶部：标题 + 5 胶囊标签
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
            Text(
                "Mibot 生活",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = InkDark,
                modifier = Modifier.padding(start = 20.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Tabs.forEachIndexed { i, label ->
                    val on = i == tab
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .background(if (on) InkDark else Color.Transparent, RoundedCornerShape(50))
                            .clickable { tab = i }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text(
                            label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) Color(0xFFF7F3EC) else MutedInk,
                        )
                    }
                }
            }
        }

        // 内容
        when (tab) {
            0 -> LifeTab(life)
            1 -> ChatTab(life)
            2 -> GrowthScreen(life)
            3 -> EconomyScreen(life, settings)
            else -> SettingsScreen(settings) { newSettings ->
                settings = newSettings
                settingsStore.save(newSettings)
            }
        }
    }
}

// ---------------- 生活 tab（状态 + 决策流）----------------

@Composable
private fun LifeTab(life: LifeSystem) {
    var loaded by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<String>() }
    var header by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        loaded = true
        val phase = life.phase()
        val snap = life.snapshot()
        header = "作息：${phase.zh} · 经验 Lv${life.levelOf("learning")} · 币 ${snap.coin} · 目标 ${snap.goals.size} · 经历 ${snap.episodes.size}"
        val d = life.decide()
        d.chosen?.let { c ->
            lines.add("我刚决定：${c.action.zh} —— ${c.reason}\n打分 ${"%.1f".format(c.score)}")
            if (c.factors.isNotEmpty()) {
                lines.add("打分依据：\n" + c.factors.joinToString("\n") { "· ${it.reason} ${if (it.points >= 0) "+" else ""}${"%.1f".format(it.points)}" })
            }
        } ?: lines.add("暂时没有可做的事。")
        if (d.excluded.isNotEmpty()) {
            lines.add("没选（及原因）：\n" + d.excluded.take(6).joinToString("\n") { "· ${it.key} — ${it.reason}" })
        }
        if (snap.goals.isNotEmpty()) {
            lines.add("我的目标：\n" + snap.goals.take(5).joinToString("\n") { "· ${it.title}（${it.status.zh}）" })
        }
        if (snap.episodes.isNotEmpty()) {
            lines.add("最近经历：\n" + snap.episodes.takeLast(4).joinToString("\n") { "· [${it.kind.zh}] ${it.title}" })
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(header.ifBlank { "加载中…" }, fontSize = 11.5.sp, color = MutedInk,
            modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lines) { t ->
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(BubbleThem, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) { Text(t, fontSize = 13.5.sp, color = InkDark, lineHeight = 20.sp) }
            }
        }
    }
}

// ---------------- 对话 tab ----------------

@Composable
private fun ChatTab(life: LifeSystem) {
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var inited by remember { mutableStateOf(false) }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        messages.add(ChatMsg(true, text))
        input = ""
        runCatching {
            if (text.startsWith("目标") || text.startsWith("让我")) {
                val title = text.removePrefix("目标").removePrefix("让我").trimStart('：', ':', ' ')
                if (title.isNotEmpty()) {
                    life.addGoal(title)
                    messages.add(ChatMsg(false, "记住了，目标：$title。我会按它安排生活。"))
                    return
                }
            }
            messages.add(ChatMsg(false, replyFor(text, life)))
        }.onFailure { messages.add(ChatMsg(false, "出了点问题：${it.message}")) }
    }

    LaunchedEffect(Unit) {
        if (inited) return@LaunchedEffect
        inited = true
        messages.add(ChatMsg(false, "我在。想聊什么，或者告诉我「目标：xxx」。\n也可以问：在干嘛 / 等级 / 目标 / 经历 / 币。"))
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages) { m -> ChatBubble(m) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f)
                    .background(Color(0xD9FFFFFF), RoundedCornerShape(50))
                    .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = InkDark),
                    cursorBrush = SolidColor(Coral),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (input.isEmpty()) Text("和 Mibot 说话…", fontSize = 14.sp, color = MutedInk)
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.width(44.dp).height(44.dp)
                    .background(Coral, RoundedCornerShape(50))
                    .clickable { send() },
                contentAlignment = Alignment.Center,
            ) { Text("➤", fontSize = 16.sp, color = Color.White) }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMsg) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.from) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .background(if (m.from) BubbleMine else BubbleThem, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) { Text(m.text, fontSize = 14.sp, color = InkDark, lineHeight = 21.sp) }
    }
}

/** 基于生活系统的本地回复（模型接入前） */
private fun replyFor(input: String, life: LifeSystem): String {
    val q = input.trim()
    val snap = life.snapshot()
    return when {
        q.contains("在干") || q.contains("干嘛") || q.contains("做什么") -> {
            val c = life.decide().chosen
            if (c == null) "现在没什么可做的，我休息一下。"
            else "我刚决定：${c.action.zh} —— ${c.reason}\n打分 ${"%.1f".format(c.score)}"
        }
        q.contains("等级") || q.contains("经验") ->
            "学习 Lv${life.levelOf("learning")} · 写作 Lv${life.levelOf("writing")} · 币 ${snap.coin}"
        q.contains("目标") ->
            if (snap.goals.isEmpty()) "还没有目标。说「目标：xxx」我就记下。"
            else snap.goals.take(6).joinToString("\n") { "· ${it.title}（${it.status.zh}）" }
        q.contains("币") || q.contains("钱") -> "我现在有 ${snap.coin} 个 Mibot 币。"
        q.contains("经历") || q.contains("记忆") ->
            if (snap.episodes.isEmpty()) "还没什么经历。"
            else snap.episodes.takeLast(5).joinToString("\n") { "· [${it.kind.zh}] ${it.title}" }
        else -> "收到：「$q」。生活系统现在能做：研究/写作/探索/休息。加个目标我就按它去做。"
    }
}