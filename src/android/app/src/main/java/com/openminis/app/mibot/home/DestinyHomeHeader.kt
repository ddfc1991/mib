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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.v7.V7
import com.openminis.app.mibot.v7.V7Button
import java.util.Calendar

/**
 * 动态页顶部的首页区。
 *
 * [destiny/real-home] 这里以前是写死的样张：「下午好，Yang」、副标题
 * 「Destiny-2.5 · 推理 · 1M · 沙箱已连接」、三条编造的最近对话、一个点了没反应
 * 的搜索框。现在全部是调用方传进来的真实数据。
 *
 * [destiny/ui-unify] 这一块原来自带一套「暖瓷」视觉：整块 #171719 大面板、
 * 圆角 20dp + 10dp 投影的卡片、蓝/绿/紫/琥珀四色圆角图标、亮橙 #C2571F 胶囊按钮。
 * 和 V7 的设置页（纯黑、平铺行、发丝线、单一铜色强调）放在同一个 App 里就是两套
 * 设计。现在按 V7 的行规格重写：
 *   - 不再有面板和卡片，直接铺在 V7.Bg 上；
 *   - 分组标题用 V7Section 的规格（10sp/加宽字距/发丝线/右侧计数）；
 *   - 行 = 14sp 主标题 + 11.5sp 说明 + 17sp 箭头 + 1dp 发丝线，与设置页一致；
 *   - 图标块统一灰底（V7.Sf2）+ 单色字，不再一屏四个颜色；
 *   - 主操作按钮改用 V7 的铜色（V7.Cu），不再用亮橙。
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
    @Suppress("UNUSED_PARAMETER") dark: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        GreetingRow(statusLine = statusLine, onNewChat = onNewChat)
        HomeSection("最近对话", "${recents.size} 条")
        if (recents.isEmpty()) {
            Text(
                "对话保存在本机数据库；新建之后会出现在这里，点一下就能接着聊。",
                fontSize = 12.sp,
                color = V7.Tx2,
                lineHeight = 19.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        } else {
            recents.forEach { item ->
                RecentRow(item) { onOpenRecent(item.id) }
            }
        }
        HomeSection("能力", "6 项")
        CapabilityRow("手机操控", "虚拟屏 · 多开", "◈", onPhoneControl)
        CapabilityRow("微信", "扫码 · 任务", "▣", onWechat)
        CapabilityRow("应用访问", "允许 · 推荐", "▤", onAppAccess)
        CapabilityRow("钱包", "支付 · 只问不付", "◉", onWallet)
        CapabilityRow("沙箱", "403 · 镜像", "▦", onSandbox)
        CapabilityRow("监控", "诊断 · 上报", "◫", onMonitor)
        Spacer(Modifier.height(10.dp))
    }
}

/** 与 V7Section 同一规格：10sp 加宽字距 + 发丝线 + 右侧计数。 */
@Composable
private fun HomeSection(label: String, meta: String) {
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
private fun GreetingRow(statusLine: String, onNewChat: () -> Unit) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..4 -> "凌晨好"
            in 5..10 -> "上午好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            else -> "晚上好"
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(greeting, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = V7.Tx)
        if (statusLine.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(statusLine, fontSize = 11.5.sp, color = V7.Tx3)
        }
        Spacer(Modifier.height(6.dp))
        // [destiny/ui-one-cta] 主操作统一成 V7Button（白底、满宽、14dp 圆角），
        // 和「对话」tab 的「＋ 新建真实对话」是同一个组件。原来这里是铜色小胶囊，
        // 群聊是灰胶囊，三个 tab 三种按钮。
        V7Button("＋ 新建对话") { onNewChat() }
    }
}

/** 一行设置式条目：灰底单色图标 + 主标题/说明 + 箭头 + 发丝线。 */
@Composable
private fun HomeRow(
    glyph: String,
    title: String,
    subtitle: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(V7.Sf2),
                contentAlignment = Alignment.Center,
            ) {
                Text(glyph, color = V7.Tx2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = V7.Tx, maxLines = 1)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontSize = 11.5.sp, color = V7.Tx3, maxLines = 2)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                Text(trailing, fontSize = 11.sp, color = V7.Tx3)
            }
            Spacer(Modifier.width(8.dp))
            Text("\u203A", fontSize = 17.sp, color = V7.Tx3)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
    }
}

@Composable
private fun RecentRow(item: HomeRecent, onClick: () -> Unit) {
    HomeRow(
        glyph = item.letter.ifBlank { "·" },
        title = item.title,
        subtitle = item.sub,
        trailing = item.time,
        onClick = onClick,
    )
}

@Composable
private fun CapabilityRow(label: String, sub: String, glyph: String, onClick: () -> Unit) {
    HomeRow(glyph = glyph, title = label, subtitle = sub, onClick = onClick)
}
