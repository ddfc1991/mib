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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.life.pet.PetPhase

/**
 * Mibot 生活主界面 —— Hark 审美（克制淡雅）。
 * 结构：顶部胶囊标签（生活/对话/成长/经济/设置）+ 纯对话框 + 底部悬浮输入。
 * 桌宠在悬浮窗（后台），不进主界面。调用模型接入（对话/生活都靠模型）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LifeScreen()
            }
        }
    }
}

// Hark token（克制淡雅）
private val SkyTop = Color(0xFFDDEAF5)     // 淡天空蓝
private val SkyBottom = Color(0xFFF7F0E6)  // 暖奶油
private val InkDark = Color(0xFF3E3A36)
private val MutedInk = Color(0xFF8B857C)
private val Coral = Color(0xFFE8825A)      // 珊瑚橙（强调）
private val BubbleMine = Color(0xFFCBE4F5) // 我
private val BubbleThem = Color(0xFFF0E9DF) // Mibot

private data class ChatMsg(val from: Boolean, val text: String)

private val Tabs = listOf("生活", "对话", "成长", "经济", "设置")

@Composable
private fun LifeScreen() {
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) }
    var phase by remember { mutableStateOf(PetPhase.IDLE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom))),
    ) {
        // 顶部胶囊标签（按 App 作用：生活/对话/成长/经济/设置）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Tabs.forEachIndexed { i, label ->
                val selected = i == activeTab
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(
                            if (selected) InkDark else Color.Transparent,
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color(0xFFF7F3EC) else MutedInk,
                    )
                }
            }
        }

        // 对话区
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages) { msg -> ChatBubble(msg) }
        }

        // 底部悬浮输入（Hark 悬浮胶囊）
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
                    .padding(start = 18.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                // 简化：输入框用基础 TextField 会引入依赖，第一版先用可点击输入位
                Text(
                    if (input.isBlank()) "和 Mibot 说点什么…" else input,
                    fontSize = 14.sp,
                    color = if (input.isBlank()) MutedInk else InkDark,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            // 麦克风
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(38.dp)
                    .background(InkDark, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🎙", fontSize = 15.sp)
            }
            Spacer(Modifier.width(6.dp))
            // 发送
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
private fun ChatBubble(msg: ChatMsg) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.from) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .background(
                    if (msg.from) BubbleMine else BubbleThem,
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(msg.text, fontSize = 14.5.sp, color = InkDark, lineHeight = 22.sp)
        }
    }
}