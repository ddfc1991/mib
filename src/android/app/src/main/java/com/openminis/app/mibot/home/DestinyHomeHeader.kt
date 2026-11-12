package com.openminis.app.mibot.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.HarkTokens

/**
 * Destiny 首页（Hark 蒸馏 · 暖瓷毛玻璃 × Muse 结构）。
 * 大标题"下午好 Yang" + 搜索框 + 最近对话分组卡。
 * 质感：渐变底 / 毛玻璃卡 / 多层阴影 / 陶土 accent / 绿点——真机截图逐项核验。
 */
@Composable
fun DestinyHomeHeader(
    onPhoneControl: () -> Unit = {},
    onWechat: () -> Unit = {},
    onAppAccess: () -> Unit = {},
    onWallet: () -> Unit = {},
    onSandbox: () -> Unit = {},
    onMonitor: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().background(HarkTokens.bgBrush)) {
        GreetingHeader()
        Spacer(Modifier.height(20.dp))
        SearchBar()
        Spacer(Modifier.height(20.dp))
        RecentSection()
        Spacer(Modifier.height(16.dp))
        CapabilityGroup(
            onPhoneControl = onPhoneControl,
            onWechat = onWechat,
            onAppAccess = onAppAccess,
            onWallet = onWallet,
            onSandbox = onSandbox,
            onMonitor = onMonitor,
            onSettings = onSettings,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GreetingHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(HarkTokens.terracotta),
            contentAlignment = Alignment.Center,
        ) {
            Text("Y", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("下午好，Yang", style = HarkTokens.museTitle)
            Spacer(Modifier.height(4.dp))
            Text("Destiny-2.5 · 推理 · 1M · 沙箱已连接", fontSize = 13.sp, color = HarkTokens.inkSub)
        }
        Box(
            Modifier.clip(RoundedCornerShape(99.dp)).background(HarkTokens.terracotta)
                .clickable { onSettings() }.padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text("+ 新对话", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SearchBar() {
    Row(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
            .background(HarkTokens.glass).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = HarkTokens.inkSub)
        Spacer(Modifier.width(10.dp))
        Text("搜索功能，比如 账号连接", fontSize = 14.sp, color = HarkTokens.inkSub)
    }
}

private data class RecentItem(val title: String, val sub: String, val time: String, val color: Color, val letter: String)

private val RECENT = listOf(
    RecentItem("Mines 设计稿 v36", "首页只读优化，整理需要重构信息架构", "13:01", HarkTokens.blue, "◆"),
    RecentItem("梦洁 SSH 修好了", "现在可以连服务器，但连接管理页面信息", "09:58", HarkTokens.terracotta, "▣"),
    RecentItem("Hermes 交易机器人", "只看消息了账号，配置一处没有", "昨天", HarkTokens.green, "▦"),
)

@Composable
private fun RecentSection() {
    Column(Modifier.fillMaxWidth()) {
        Text("最近对话", style = HarkTokens.museSection)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().shadow(16.dp, HarkTokens.groupRadius).clip(HarkTokens.groupRadius)
                .background(HarkTokens.glass),
        ) {
            RECENT.forEachIndexed { i, item ->
                Row(
                    Modifier.fillMaxWidth().clickable { }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(item.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.letter, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HarkTokens.ink)
                        Spacer(Modifier.height(3.dp))
                        Text(item.sub, fontSize = 12.sp, color = HarkTokens.inkSub, maxLines = 2)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(item.time, fontSize = 12.sp, color = HarkTokens.inkSub)
                }
                if (i < RECENT.size - 1) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HarkTokens.hairline))
                }
            }
        }
    }
}

private data class Cap(val label: String, val sub: String, val color: Color, val letter: String, val onTap: () -> Unit)

@Composable
private fun CapabilityGroup(
    onPhoneControl: () -> Unit,
    onWechat: () -> Unit,
    onAppAccess: () -> Unit,
    onWallet: () -> Unit,
    onSandbox: () -> Unit,
    onMonitor: () -> Unit,
    onSettings: () -> Unit,
) {
    val caps = listOf(
        Cap("手机操控", "虚拟屏 · 多开", HarkTokens.blue, "◈", onPhoneControl),
        Cap("微信", "扫码 · 任务", HarkTokens.green, "▣", onWechat),
        Cap("应用访问", "允许 · 推荐", HarkTokens.purple, "▤", onAppAccess),
        Cap("钱包", "支付 · 只问不付", HarkTokens.amber, "◉", onWallet),
        Cap("沙箱", "403 · 镜像", HarkTokens.terracotta, "▦", onSandbox),
        Cap("监控", "诊断 · 上报", HarkTokens.blue, "◫", onMonitor),
    )
    Column(Modifier.fillMaxWidth()) {
        Text("能力", style = HarkTokens.museSection)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().shadow(16.dp, HarkTokens.groupRadius).clip(HarkTokens.groupRadius)
                .background(HarkTokens.glass),
        ) {
            caps.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth().clickable { c.onTap() }.padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c.letter, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = HarkTokens.ink)
                        Text(c.sub, fontSize = 12.sp, color = HarkTokens.inkSub)
                    }
                    Text("›", fontSize = 18.sp, color = HarkTokens.inkSub)
                }
                if (i < caps.size - 1) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HarkTokens.hairline))
                }
            }
        }
    }
}
