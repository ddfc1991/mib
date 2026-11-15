package com.mibot.life.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.life.settings.LifeSettings

/**
 * 首次安装引导页 —— 豆包式：
 *   ① 认识它（虚拟形象 + 自我介绍）
 *   ② 给它起个名
 *   ③ 它该怎么称呼你
 * 完成后 onboarded=true，进主界面。
 */
@Composable
fun OnboardingScreen(
    initial: LifeSettings,
    onDone: (mibotName: String, userName: String) -> Unit,
) {
    var mibotName by remember { mutableStateOf(initial.mibotName) }
    var userName by remember { mutableStateOf(initial.userName) }
    var step by remember { mutableIntStateOf(0) }

    val canNext = when (step) {
        1 -> mibotName.isNotBlank()
        2 -> userName.isNotBlank()
        else -> true
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        when (step) {
            0 -> {
                // 认识它
                MibotAvatar(size = 140.dp, mood = 0.7f, talking = false)
                Spacer(Modifier.height(28.dp))
                Text("你好，我是 ${mibotName.ifBlank { "Mibot" }}", fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(10.dp))
                Text("我在你的手机上「过自己的生活」。\n我会自己安排每天做什么、去学什么、赚点小钱，\n也会惦记你、陪你说话。", fontSize = 14.sp, color = InkSoft, lineHeight = 24.sp)
            }
            1 -> {
                MibotAvatar(size = 120.dp, mood = 0.85f, talking = false)
                Spacer(Modifier.height(24.dp))
                Text("给它起个名字", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(20.dp))
                NameField(mibotName) { mibotName = it }
            }
            2 -> {
                MibotAvatar(size = 120.dp, mood = 0.5f, talking = true)
                Spacer(Modifier.height(24.dp))
                Text("它该叫你什么？", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(20.dp))
                NameField(userName) { userName = it }
            }
        }

        Spacer(Modifier.weight(1f))

        // 步骤点
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.size(if (i == step) 18.dp else 6.dp, 6.dp)
                        .background(if (i == step) Coral else InkSoft.copy(alpha = 0.25f), RoundedCornerShape(50))
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // 按钮
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50))
                .background(if (canNext) Coral else InkSoft.copy(alpha = 0.25f))
                .clickable(enabled = canNext) {
                    when (step) {
                        0 -> step = 1
                        1 -> step = 2
                        else -> onDone(mibotName.trim(), userName.trim())
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (step) {
                    0 -> "开始"
                    1 -> "下一步"
                    else -> "开始生活"
                },
                fontSize = 16.sp, fontWeight = FontWeight.Medium,
                color = if (canNext) Color(0xFFFAF5EF) else InkSoft,
            )
        }
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(Color(0xCCFFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) Text("输入名字…", fontSize = 15.sp, color = InkSoft.copy(alpha = 0.5f))
        BasicTextField(
            value = value, onValueChange = onChange,
            textStyle = TextStyle(fontSize = 15.sp, color = Ink),
            modifier = Modifier.fillMaxWidth(), maxLines = 1,
        )
    }
}
