package com.mibot.life.ui

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.life.life.LifeSystem
import com.mibot.life.settings.Domains
import com.mibot.life.settings.LifeSettings

private val InkDark = Color(0xFF3E3A36)
private val MutedInk = Color(0xFF8B857C)
private val Coral = Color(0xFFE8825A)
private val CardBg = Color(0xCCFFFFFF)

// ---------------- 成长页 ----------------

@Composable
fun GrowthScreen(life: LifeSystem) {
    val snap = life.snapshot()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("成长 · 一步一步提升", fontSize = 13.sp, color = MutedInk, modifier = Modifier.padding(vertical = 8.dp))
        }
        items(Domains.ALL) { d ->
            val lv = life.levelOf(d.key)
            val xp = life.xpOf(d.key)
            GlassRow {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.zh, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
                        Spacer(Modifier.width(8.dp))
                        Badge("Lv$lv", if (lv >= 5) Coral else Color(0x33E8825A), if (lv >= 5) Color.White else Coral)
                        Spacer(Modifier.width(6.dp))
                        Text(Domains.ladderTitle(lv, d), fontSize = 12.sp, color = MutedInk)
                    }
                    Spacer(Modifier.height(6.dp))
                    XPBar(xp = xp, next = nextThreshold(lv))
                    Spacer(Modifier.height(3.dp))
                    Text("$xp / ${nextThreshold(lv)} XP", fontSize = 11.sp, color = MutedInk)
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun nextThreshold(lv: Int): Int = when (lv) {
    1 -> 100; 2 -> 300; 3 -> 800; 4 -> 2000; else -> 2000
}

@Composable
private fun XPBar(xp: Int, next: Int) {
    val frac = (xp.toFloat() / next.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(6.dp).background(Color(0x1A000000), RoundedCornerShape(3.dp)),
    ) {
        Box(Modifier.fillMaxWidth(frac).height(6.dp).background(Coral, RoundedCornerShape(3.dp)))
    }
}

// ---------------- 经济页 ----------------

@Composable
fun EconomyScreen(life: LifeSystem, settings: LifeSettings) {
    val snap = life.snapshot()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            GlassRow {
                Column {
                    Text("💰 Mibot 币", fontSize = 12.sp, color = MutedInk)
                    Text("${snap.coin}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = InkDark)
                    Text("做完生活动作赚的虚拟资产（纯本地）", fontSize = 11.sp, color = MutedInk)
                }
            }
        }
        item { Text("赚钱档位", fontSize = 13.sp, color = MutedInk, modifier = Modifier.padding(top = 8.dp)) }
        item { EconRow("档1 虚拟资产", "玩游戏/做任务/产出赚币", if (settings.econTier1Enabled) "开启 · 自主" else "关闭", settings.econTier1Enabled) }
        item { EconRow("档2a 试玩任务", "试玩平台挂机拿奖励（合规）", if (settings.econTier2aAuthed) "已授权" else "需授权", settings.econTier2aAuthed) }
        item { EconRow("档2b 挂机激励", "游戏中心签到/补给站（已实证：OPPO 游戏中心）", if (settings.econTier2bAuthed) "已授权" else "需授权", settings.econTier2bAuthed) }
        item { EconRow("档2c 打金卖玩家", "封号/合规风险，绝不自动", if (settings.econTier2cEnabled) "已开启（风险自担）" else "关闭", settings.econTier2cEnabled) }
        item { EconRow("档3 真实赚钱", "接单/薅羊毛，逐项批", if (settings.econTier3Enabled) "已开启" else "关闭", settings.econTier3Enabled) }
        item {
            Text(
                "原则：只有档1 完全自主；档2a/2b 需你授权；档2c/3 绝不自动。",
                fontSize = 11.sp, color = MutedInk, modifier = Modifier.padding(top = 6.dp),
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun EconRow(title: String, desc: String, status: String, on: Boolean) {
    GlassRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = InkDark)
            Text(desc, fontSize = 11.sp, color = MutedInk)
        }
        Badge(status, if (on) Color(0x2210B981) else Color(0x14000000), if (on) Color(0xFF059669) else MutedInk)
    }
}

// ---------------- 设置页 ----------------

@Composable
fun SettingsScreen(
    settings: LifeSettings,
    onChange: (LifeSettings) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { SectionTitle("自主生活") }
        item {
            ToggleRow("自主模式", "它自己决定去做什么", settings.autonomousEnabled) {
                onChange(settings.copy(autonomousEnabled = it))
            }
        }
        item {
            ToggleRow("作息门控", "按晨间/白日/睡前/夜间限制活动", settings.rhythmEnabled) {
                onChange(settings.copy(rhythmEnabled = it))
            }
        }
        item {
            ToggleRow("你在前台时暂停", "不打扰你玩手机", settings.pauseWhenForeground) {
                onChange(settings.copy(pauseWhenForeground = it))
            }
        }
        item { StepperRow("多开上限", "${settings.maxConcurrentScreens} 屏", 1, 3, settings.maxConcurrentScreens) {
            onChange(settings.copy(maxConcurrentScreens = it))
        } }

        item { SectionTitle("游戏") }
        item {
            ToggleRow("自主打游戏", "游戏有账号/风控风险，默认关", settings.gamingAuto) {
                onChange(settings.copy(gamingAuto = it))
            }
        }
        item {
            ToggleRow("只做日常/挂机", "不推主线，稳妥", settings.gamingDailyOnly) {
                onChange(settings.copy(gamingDailyOnly = it))
            }
        }
        item { StepperRow("单次最长", "${settings.gamingMaxMinutes} 分钟", 10, 120, settings.gamingMaxMinutes) {
            onChange(settings.copy(gamingMaxMinutes = it))
        } }

        item { SectionTitle("互动授权") }
        item { ToggleRow("允许点赞", "逛论坛/视频时点赞", settings.allowLike) { onChange(settings.copy(allowLike = it)) } }
        item { ToggleRow("允许评论", "有风险，默认关", settings.allowComment) { onChange(settings.copy(allowComment = it)) } }
        item { ToggleRow("允许关注", "默认关", settings.allowFollow) { onChange(settings.copy(allowFollow = it)) } }

        item { SectionTitle("赚钱档位") }
        item { ToggleRow("档1 虚拟资产（自主）", "默认开", settings.econTier1Enabled) { onChange(settings.copy(econTier1Enabled = it)) } }
        item { ToggleRow("档2a 试玩任务（需授权）", "平台试玩奖励", settings.econTier2aAuthed) { onChange(settings.copy(econTier2aAuthed = it)) } }
        item { ToggleRow("档2b 挂机激励（需授权）", "游戏中心签到/补给站", settings.econTier2bAuthed) { onChange(settings.copy(econTier2bAuthed = it)) } }
        item { ToggleRow("档2c 打金（风险）", "封号风险，不推荐", settings.econTier2cEnabled) { onChange(settings.copy(econTier2cEnabled = it)) } }
        item { ToggleRow("档3 真实赚钱", "逐项批", settings.econTier3Enabled) { onChange(settings.copy(econTier3Enabled = it)) } }

        item { SectionTitle("模型（分层：日常便宜/产出强）") }
        item { TextRow("网关", settings.gatewayUrl) }
        item { TextRow("日常模型", settings.dailyModel) }
        item { TextRow("产出模型", settings.deepModel) }
        item {
            Text(
                "模型调用下一步接入（本机网关 :8788）。当前对话用生活系统本地回复。",
                fontSize = 11.sp, color = MutedInk,
            )
        }

        item { SectionTitle("记忆") }
        item { StepperRow("概要阈值", "${settings.summaryAfterDays} 天", 1, 30, settings.summaryAfterDays) {
            onChange(settings.copy(summaryAfterDays = it))
        } }
        item { StepperRow("淡出阈值", "${settings.fadeAfterDays} 天", 30, 365, settings.fadeAfterDays) {
            onChange(settings.copy(fadeAfterDays = it))
        } }
        item { ToggleRow("保留偏好", "偏好不被淡出", settings.keepPreferences) { onChange(settings.copy(keepPreferences = it)) } }
        item { ToggleRow("保留未完成目标", "未完成目标不被淡出", settings.keepUnfinishedGoals) { onChange(settings.copy(keepUnfinishedGoals = it)) } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------------- 通用组件 ----------------

@Composable
private fun GlassRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontSize = 12.sp, color = MutedInk, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun ToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    GlassRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = InkDark)
            Text(desc, fontSize = 11.sp, color = MutedInk)
        }
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Coral,
            ),
        )
    }
}

@Composable
private fun StepperRow(title: String, value: String, min: Int, max: Int, current: Int, onChange: (Int) -> Unit) {
    GlassRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = InkDark)
            Text(value, fontSize = 12.sp, color = Coral, fontWeight = FontWeight.SemiBold)
        }
        Row {
            StepBtn("−") { if (current > min) onChange(current - 1) }
            Spacer(Modifier.width(8.dp))
            StepBtn("+") { if (current < max) onChange(current + 1) }
        }
    }
}

@Composable
private fun StepBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(32.dp).height(32.dp)
            .background(Color(0x14E8825A), RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 16.sp, color = Coral, fontWeight = FontWeight.Bold) }
}

@Composable
private fun TextRow(title: String, value: String) {
    GlassRow {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = InkDark)
            Text(value.ifBlank { "（未设置）" }, fontSize = 11.sp, color = MutedInk)
        }
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(text, fontSize = 10.sp, color = fg, fontWeight = FontWeight.SemiBold) }
}