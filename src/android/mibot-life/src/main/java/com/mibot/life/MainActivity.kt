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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.life.life.LifeEngine
import com.mibot.life.pet.DesktopPet
import com.mibot.life.pet.PetPhase
import com.mibot.life.pet.PetState

/**
 * Mibot 生活主界面 —— Hark 审美（暖瓷/白玻璃/珊瑚橙/大圆角/衬线标题）。
 * 结构：桌宠(主视觉) + 生活状态 + 对话框（底部悬浮输入）。
 * 一个开关可接入 Mibot（能力共享 core）。
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

// Hark token
private val WarmPorcelain = Color(0xFFFAF5EF)
private val WhiteGlass = Color(0xF2FFFFFF)
private val CoralOrange = Color(0xFFE8825A)
private val InkDark = Color(0xFF3D2E26)
private val MutedInk = Color(0xFF9C8A7E)

private data class ChatMsg(val from: Boolean, val text: String)

@Composable
private fun LifeScreen() {
    val petState = remember { PetState() }
    val lifeEngine = remember { LifeEngine() }
    var currentPhase by remember { mutableStateOf(PetPhase.IDLE) }
    val messages = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmPorcelain),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 上部：桌宠 + 状态（滚动）
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Mibot 生活",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = InkDark,
            )
            Text("桌宠正在生活", fontSize = 13.sp, color = MutedInk)
            Spacer(Modifier.height(20.dp))

            // 桌宠卡（白玻璃，大圆角）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WhiteGlass, RoundedCornerShape(24.dp))
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DesktopPet(state = petState, sizeDp = 170)
                Spacer(Modifier.height(12.dp))
                Text(
                    lifeEngine.statusText(currentPhase),
                    fontSize = 14.sp,
                    color = InkDark,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // 消息区
            if (messages.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    messages.forEach { msg ->
                        ChatBubble(msg)
                    }
                }
            }
        }

        // 底部：对话框（Hark 悬浮输入）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WhiteGlass)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("和 Mibot 说话…", color = MutedInk, fontSize = 14.sp) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CoralOrange,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = WarmPorcelain,
                    unfocusedContainerColor = WarmPorcelain,
                ),
                maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        messages.add(ChatMsg(from = true, text = input))
                        // 模拟 Mibot 回复（等待批准等状态切换）
                        val reply = lifeEngine.reply(input, currentPhase)
                        messages.add(ChatMsg(from = false, text = reply.first))
                        currentPhase = reply.second
                        petState.setPhase(reply.second)
                        input = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoralOrange, contentColor = Color.White),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("发送", fontSize = 13.sp)
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
                    if (msg.from) CoralOrange else Color.White,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                msg.text,
                fontSize = 14.sp,
                color = if (msg.from) Color.White else InkDark,
            )
        }
    }
}
