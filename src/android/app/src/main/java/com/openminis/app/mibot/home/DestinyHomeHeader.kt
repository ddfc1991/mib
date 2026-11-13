package com.openminis.app.mibot.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.HarkTokens
import java.util.Calendar

/**
 * 动态页顶部的首页区。
 *
 * [destiny/real-home] 这里以前是写死的样张：「下午好，Yang」、副标题
 * 「Destiny-2.5 · 推理 · 1M · 沙箱已连接」、三条编造的最近对话（Mines 设计稿
 * v36 / 梦洁 SSH 修好了 / Hermes 交易机器人），还有一个点了没任何反应的搜索框。
 * 现在全部换成调用方传进来的真实数据：
 *   - 问候语按当前小时算，不编名字；
 *   - 副标题由调用方给出真实状态，给空就不显示；
 *   - 「最近对话」是真实会话表的前几条，点了真能进那个会话；
 *   - 真没有会话就如实写空态，不摆假条目；
 *   - 搜索框删掉——没有搜索后端的输入框只是骗人。
 *
 * [destiny/home-dark] HarkTokens 是浅色「暖瓷」一套令牌，直接贴进 V7 的深色壳里
 * 会是一块白板。dark=true 时整体换深色等价色，深色页里不再有白卡。
 */
data class HomeRecent(
    val id: String,
    val title: String,
    val sub: String,
    val time: String,
    val color: Color,
    val letter: String,
)

@Composable
fun DestinyHomeHeader(
    onPhoneControl: () -> Unit = {},
    onWechat: () -> Unit = {},
    onAppAccess: () -> Unit = {},
    onWallet: () -> Unit = {},
    onSandbox: () -> Unit = {},
    onMonitor: () -> Unit = {},
    onSettings: () -> Unit = {},
    recents: List<HomeRecent> = emptyList(),
    onOpenRecent: (String) -> Unit = {},
    statusLine: String = "",
    onNewChat: () -> Unit = {},
    dark: Boolean = false,
) {
    val p = homePalette(dark)
    Column(Modifier.fillMaxWidth().background(p.card)) {
        GreetingHeader(p, statusLine = statusLine, onNewChat = onNewChat)
        Spacer(Modifier.height(20.dp))
        RecentSection(p, recents = recents, onOpenRecent = onOpenRecent)
        Spacer(Modifier.height(16.dp))
        CapabilityGroup(
            p = p,
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

/** 首页区配色：默认浅色暖瓷；V7 深色壳里传 dark=true。 */
private data class HomePalette(
    val card: Color,
    val ink: Color,
    val inkSub: Color,
    val line: Color,
)

private fun homePalette(dark: Boolean) = if (dark) {
    HomePalette(
        card = Color(0xFF171719),
        ink = Color(0xFFF2F0EB),
        inkSub = Color(0xFFA3A099),
        line = Color(0x1FFFFFFF),
    )
} else {
    HomePalette(
        card = HarkTokens.glass,
        ink = HarkTokens.ink,
        inkSub = HarkTokens.inkSub,
        line = HarkTokens.hairline,
    )
}

@Composable
private fun GreetingHeader(p: HomePalette, statusLine: String, onNewChat: () -> Unit) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..4 -> "凌晨好"
            in 5..10 -> "上午好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            else -> "晚上好"
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(HarkTokens.terracotta),
            contentAlignment = Alignment.Center,
        ) {
            Text("D", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(greeting, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink)
            if (statusLine.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(statusLine, fontSize = 12.5.sp, color = p.inkSub)
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(99.dp)).background(HarkTokens.terracotta)
                .clickable { onNewChat() }.padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text("+ 新对话", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RecentSection(p: HomePalette, recents: List<HomeRecent>, onOpenRecent: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("最近对话", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Spacer(Modifier.width(8.dp))
            Text("${recents.size}", fontSize = 12.sp, color = p.inkSub)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .background(p.card),
        ) {
            if (recents.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Text("还没有对话", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "对话保存在本机数据库；新建之后会出现在这里，点一下就能接着聊。",
                        fontSize = 12.sp,
                        color = p.inkSub,
                    )
                }
            } else {
                recents.forEachIndexed { i, item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenRecent(item.id) }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(item.color),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item.letter, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                            if (item.sub.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(item.sub, fontSize = 12.sp, color = p.inkSub, maxLines = 2)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(item.time, fontSize = 12.sp, color = p.inkSub)
                    }
                    if (i < recents.size - 1) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                    }
                }
            }
        }
    }
}

private data class Cap(val label: String, val sub: String, val color: Color, val letter: String, val onTap: () -> Unit)

@Composable
private fun CapabilityGroup(
    p: HomePalette,
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
        Text("能力", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .background(p.card),
        ) {
            caps.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth().clickable { c.onTap() }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(c.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c.letter, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.label, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                        Text(c.sub, fontSize = 12.sp, color = p.inkSub)
                    }
                    Text("›", fontSize = 18.sp, color = p.inkSub)
                }
                if (i < caps.size - 1) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                }
            }
        }
    }
}
