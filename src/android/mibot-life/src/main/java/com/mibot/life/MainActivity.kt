package com.mibot.life

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.life.life.LifeAction
import com.mibot.life.life.LifeChoice
import com.mibot.life.life.LifeSystem
import com.mibot.life.life.RunStatus

/**
 * Mibot 生活 —— 主界面。
 * 顶部：作息阶段 + 生活等级/币；中部：真实生活决策流（可解释）；底部：对话框。
 * 生活系统（LifeSystem）真实在跑：决策→执行→记账→升级。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { LifeScreen() }
        }
    }
}

// Hark token
private val SkyTop = Color(0xFFDDEAF5)
private val SkyBottom = Color(0xFFF7F0E6)
private val InkDark = Color(0xFF3E3A36)
private val MutedInk = Color(0xFF8B857C)
private val Coral = Color(0xFFE8825A)
private val BubbleMine = Color(0xFFCBE4F5)
private val BubbleThem = Color(0xFFF0E9DF)

private data class ChatMsg(val from: Boolean, val text: String)

@Composable
private fun LifeScreen() {
    val ctx = LocalContext.current
    val life = remember { LifeSystem(ctx.applicationContext) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var statusLine by remember { mutableStateOf("") }

    // 启动：读盘恢复 → 做一次真实决策
    LaunchedEffect(Unit) {
        val snap = life.snapshot()
        val phase = life.phase()
        val decision = life.decide()
        val chosen = decision.chosen
        statusLine = "作息：${phase.zh} · 经验 Lv${life.levelOf("learning")} · 币 ${snap.coin}"
        messages.add(
            ChatMsg(
                from = false,
                text = if (chosen != null) {
                    "我醒来了（${phase.zh}）。刚做了个决定：${chosen.action.zh} —— ${chosen.reason}\n" +
                        "打分 ${"%.1f".format(chosen.score)}，候选里排第一。" +
                        (chosen.factors.joinToString("") { f -> "\n· ${f.reason} ${if (f.points >= 0) "+" else ""}${"%.1f".format(f.points)}" })
                } else {
                    "我醒来了（${phase.zh}）。暂时没有可做的事，先休息。"
                },
            ),
        )
        if (chosen != null && decision.excluded.isNotEmpty()) {
            messages.add(
                ChatMsg(
                    from = false,
                    text = "没选的：\n" + decision.excluded.take(5).joinToString("\n") { "· ${it.key} — ${it.reason}" },
                ),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom))),
    ) {
        // 顶部：标题 + 状态
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        ) {
            Text(
                "Mibot 生活",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = InkDark,
            )
            Text(
                if (statusLine.isBlank()) "生活系统加载中…" else statusLine,
                fontSize = 12.sp,
                color = MutedInk,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        // 对话流（含生活决策记录）
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages) { m -> ChatBubble(m) }
        }

        // 底部输入
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xD9FFFFFF), RoundedCornerShape(50))
                    .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    if (input.isBlank()) "和 Mibot 说话…（输入后点发送）" else input,
                    fontSize = 14.sp,
                    color = if (input.isBlank()) MutedInk else InkDark,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(44.dp)
                    .background(Coral, RoundedCornerShape(50)),
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