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
import com.mibot.life.life.LifeAction
import com.mibot.life.life.LifeSystem

/**
 * Mibot 生活 —— 主界面（可对话 + 真实生活系统）。
 * 输入框是真 BasicTextField（弹键盘），发送后基于生活系统真实数据回复。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LifeScreen() } }
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

/** 基于生活系统真实数据的回复 */
private fun replyFor(input: String, life: LifeSystem): String {
    val q = input.trim()
    val snap = life.snapshot()
    return when {
        q.contains("在干") || q.contains("干嘛") || q.contains("做什么") -> {
            val d = life.decide()
            val c = d.chosen
            if (c == null) "现在没什么可做的，我休息一下。"
            else "我刚决定：${c.action.zh} —— ${c.reason}\n打分 ${"%.1f".format(c.score)}" +
                c.factors.joinToString("") { f -> "\n· ${f.reason} ${if (f.points >= 0) "+" else ""}${"%.1f".format(f.points)}" }
        }
        q.contains("等级") || q.contains("经验") || q.contains("Lv") ->
            "学习 Lv${life.levelOf("learning")}（${life.levelTitle("learning")}）· 经验 ${life.xpOf("learning")}\n" +
                "写作 Lv${life.levelOf("writing")}（${life.xpTitleSafe()}）\n币：${snap.coin}"
        q.contains("目标") || q.contains("计划") ->
            if (snap.goals.isEmpty() && snap.curiosityGoals.isEmpty()) "还没有目标。你可以跟我说想让我做什么。"
            else buildString {
                if (snap.goals.isNotEmpty()) appendLine("目标：" + snap.goals.take(5).joinToString("；") { "${it.title}（${it.status.zh}）" })
                if (snap.curiosityGoals.isNotEmpty()) append("小疑问：" + snap.curiosityGoals.take(3).joinToString("；") { it.title })
            }
        q.contains("币") || q.contains("钱") -> "我现在有 ${snap.coin} 个 Mibot 币。"
        q.contains("经历") || q.contains("记忆") || q.contains("做过") ->
            if (snap.episodes.isEmpty()) "还没什么经历。"
            else "最近的经历：\n" + snap.episodes.takeLast(5).joinToString("\n") { "· [${it.kind.zh}] ${it.title}" }
        q.contains("休息") -> "好，我休息一下。"
        q.contains("读") || q.contains("看") || q.contains("研究") ->
            "好，我去研究。你现在可以让我加个目标，我就按它去读。"
        q.isBlank() -> "嗯？"
        else -> "收到：「$q」。我现在的生活系统能做的：研究/写作/探索/休息。想让我做别的，先给我加个目标。"
    }
}

/** 安全拿写作称号（避免越界异常） */
private fun LifeSystem.xpTitleSafe(): String = runCatching { levelTitle("writing") }.getOrElse { "新手" }

@Composable
private fun LifeScreen() {
    val ctx = LocalContext.current
    val life = remember { LifeSystem(ctx.applicationContext) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var statusLine by remember { mutableStateOf("") }

    fun reflectStatus() {
        val snap = life.snapshot()
        statusLine = "作息：${life.phase().zh} · 经验 Lv${life.levelOf("learning")} · 币 ${snap.coin}"
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        messages.add(ChatMsg(from = true, text = text))
        input = ""
        // 加目标（用户说"目标：xxx"或"让我做xxx"）
        runCatching {
            if (text.startsWith("目标") || text.startsWith("让我")) {
                val title = text.removePrefix("目标").removePrefix("让我").trimStart('：', ':', ' ')
                if (title.isNotEmpty()) {
                    life.addGoal(title)
                    messages.add(ChatMsg(from = false, text = "记住了，目标：$title。我会按它安排生活。"))
                    reflectStatus()
                    return
                }
            }
            messages.add(ChatMsg(from = false, text = replyFor(text, life)))
            reflectStatus()
        }.onFailure {
            messages.add(ChatMsg(from = false, text = "出了点问题：${it.message}"))
        }
    }

    LaunchedEffect(Unit) {
        val phase = life.phase()
        val decision = life.decide()
        val chosen = decision.chosen
        reflectStatus()
        messages.add(
            ChatMsg(
                from = false,
                text = if (chosen != null) {
                    "我醒来了（${phase.zh}）。刚做了个决定：${chosen.action.zh} —— ${chosen.reason}\n" +
                        "打分 ${"%.1f".format(chosen.score)}，候选里排第一。" +
                        (chosen.factors.joinToString("") { f -> "\n· ${f.reason} ${if (f.points >= 0) "+" else ""}${"%.1f".format(f.points)}" })
                } else "我醒来了（${phase.zh}）。暂时没有可做的事。",
            ),
        )
        if (decision.excluded.isNotEmpty()) {
            messages.add(ChatMsg(from = false, text = "没选的：\n" + decision.excluded.take(4).joinToString("\n") { "· ${it.key} — ${it.reason}" }))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom))),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
            Text("Mibot 生活", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = InkDark)
            Text(
                if (statusLine.isBlank()) "生活系统加载中…" else statusLine,
                fontSize = 12.sp, color = MutedInk, modifier = Modifier.padding(top = 3.dp),
            )
        }

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
                modifier = Modifier
                    .weight(1f)
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
                        if (input.isEmpty()) {
                            Text("和 Mibot 说话…", fontSize = 14.sp, color = MutedInk)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(44.dp).height(44.dp)
                    .background(Coral, RoundedCornerShape(50))
                    .clickable { send() },
                contentAlignment = Alignment.Center,
            ) {
                Text("➤", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMsg) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (m.from) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .background(if (m.from) BubbleMine else BubbleThem, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(m.text, fontSize = 14.sp, color = InkDark, lineHeight = 21.sp)
        }
    }
}