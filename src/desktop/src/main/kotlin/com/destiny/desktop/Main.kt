package com.destiny.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

private val Bg = Color(0xFF060607)
private val Card = Color(0xFF101013)
private val Border = Color(0xFF1D1D20)
private val Tx = Color(0xFFECECEA)
private val Tx2 = Color(0xFF8A8A90)
private val Accent = Color(0xFF8A7BFF)
private val Teal = Color(0xFF4EC9B0)
private val Amber = Color(0xFFFFC24B)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Destiny 桌面端",
        state = rememberWindowState(width = 1080.dp, height = 700.dp),
    ) {
        MaterialTheme(colors = darkColors(background = Bg, surface = Bg)) {
            DesktopShell()
        }
    }
}

@Composable
fun DesktopShell() {
    Row(Modifier.fillMaxSize().background(Bg)) {
        // 侧栏功能块
        Column(
            Modifier.width(200.dp).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Destiny", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tx)
            Text("桌面端 · core 引擎", fontSize = 11.sp, color = Tx2)
            Spacer(Modifier.height(12.dp))
            listOf("对话", "手机操控", "微信", "沙箱", "应用访问", "钱包", "任务队列", "技能", "设置").forEach {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (it == "对话") Accent.copy(alpha = .18f) else Card)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(it, fontSize = 13.sp, color = if (it == "对话") Accent else Tx)
                }
            }
        }
        // 对话主区
        Column(Modifier.weight(1f).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("反编译 App 功能分析", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tx)
                    Text("运行中 · 64% · 断点续接", fontSize = 11.sp, color = Accent)
                }
                Text("沙箱运行 · 隧道 yn 12ms · 网关正常", fontSize = 11.sp, color = Tx2)
            }
            // 消息区
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Card).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Message("思考 · Java is installed at /usr/lib/jvm/java-21-openjdk", Tx2)
                Message("运行 · ./dl_jadx.py", Tx)
                Message("写入 · dl_jadx.py 新建 · +26", Tx)
                Message("已停止 · 2 分 43 秒 · 5 步 · 692K tokens", Tx2)
            }
            // 输入框
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Card).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("生成中 · 可直接插话 · 语音", fontSize = 13.sp, color = Tx2, modifier = Modifier.weight(1f))
                Text("●", fontSize = 16.sp, color = Accent)
            }
        }
    }
}

@Composable
fun Message(text: String, color: Color) {
    Text(text, fontSize = 12.sp, color = color)
}
