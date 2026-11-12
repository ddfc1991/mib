package com.openminis.app.mibot.capabilities

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotPageTitle
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.queue.TaskRunQueue
import com.openminis.app.mibot.queue.TaskRunState
import com.openminis.app.mibot.queue.TaskKind

/** 微信页 */
@Composable
fun WechatScreen() {
    var autoReply by remember { mutableStateOf(true) }
    var dispatch by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        MibotPageTitle("微信", "ilinkai 绑定 · 自动收发")
        MibotCard {
            Column(Modifier.padding(14.dp)) {
                Text("已连接", fontSize = 30.sp, fontWeight = FontWeight.Light)
                Text("账号 destiny_bot · 免扫码 · 已授权", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动收发消息", fontSize = 14.sp)
                        Text("收到即回复 · 免手动", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoReply, onCheckedChange = { autoReply = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("微信派单", fontSize = 14.sp)
                        Text("私聊文本 = 任务", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dispatch, onCheckedChange = { dispatch = it })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MibotSectionHeader("最近")
        MibotCard {
            Column(Modifier.padding(6.dp)) {
                listOf("同事 小王" to "\"明天 9 点提醒我开会\" · 已自动安排",
                    "家人群" to "\"周末聚餐\" · 已回复").forEach { (n, d) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(MibotTokens.card()).padding(6.dp), tint = MibotTokens.iconTint())
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(n, fontSize = 14.sp); Text(d, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 应用访问页（Mika 的 per-app 允许/禁用） */
@Composable
fun AppAccessScreen() {
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        MibotPageTitle("应用访问", "per-app 允许/禁用 · 无感采集")
        MibotCard {
            Column(Modifier.padding(14.dp)) {
                Text("选择允许操纵的应用。Shell/沙箱/电脑接入不受限。分类会把应用名和包名发送给 AI 模型；刷新前先征求你同意。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MibotTokens.card()).padding(10.dp)) {
                    Text(if (search.isEmpty()) "搜索名称或包名" else search, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                listOf("刷新应用列表", "推荐禁用", "全部允许").forEach {
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 7.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MibotSectionHeader("未分类 · 169")
        MibotCard {
            Column(Modifier.padding(6.dp)) {
                listOf("(ZT)Termux:Boot" to "com.termux.boot" to true,
                    "123云盘" to "com.mfcloudcalculate.networkdisk" to true,
                    "AI 拍一拍" to "com.coloros.ocrscanner" to false,
                    "浏览器" to "com.heytap.browser" to false).forEach { (p, on) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(p.first, fontSize = 13.sp); Text(p.second, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        var checked by remember(p.first) { mutableStateOf(on) }
                        Switch(checked = checked, onCheckedChange = { checked = it })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 钱包页（支付你做主 · 只问不付） */
@Composable
fun WalletScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        MibotPageTitle("钱包", "支付你做主 · 只问不付")
        MibotCard {
            Column(Modifier.padding(14.dp)) {
                Text("可支配", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥ 1,284.50", fontSize = 34.sp, fontWeight = FontWeight.Light)
                Text("单笔上限 ¥50 需你确认", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        MibotSectionHeader("卡片")
        MibotCard {
            Column(Modifier.padding(6.dp)) {
                listOf("储蓄卡 · 尾号 8842" to "余额 ¥ 1,200", "支付宝 · 已绑" to "余额 ¥ 84.50", "积分 / 礼券" to "3 平台 · 自动同步").forEach { (n, d) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(MibotTokens.card()).padding(6.dp), tint = MibotTokens.iconTint())
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(n, fontSize = 14.sp); Text(d, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MibotSectionHeader("订阅")
        MibotCard {
            Column(Modifier.padding(6.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Column(Modifier.weight(1f)) { Text("Hark Pro · 每月 ¥0", fontSize = 14.sp); Text("重置 12-07 · 自动提醒", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("正常", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 语音页（sherpa-onnx 免提 · 可调声音） */
@Composable
fun VoiceScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        MibotPageTitle("语音", "开车免提 · 手不动")
        MibotCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Mic, null, Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(MibotTokens.card()).padding(18.dp), tint = MibotTokens.iconTint())
                Spacer(Modifier.height(16.dp))
                Text("说\"帮我定明天9点的闹钟\"", fontSize = 14.sp)
                Text("本地识别 · sherpa-onnx · 免费 · 可调声音", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        MibotSectionHeader("声音")
        MibotCard {
            Column(Modifier.padding(6.dp)) {
                listOf("音色" to "fanchen-C（女声）", "语速" to "1.0 正常", "音量" to "0.9").forEach { (n, d) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(n, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(d, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 任务队列页（不丢任务） */
@Composable
fun TaskQueueScreen(queue: TaskRunQueue) {
    val runs by queue.runs.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        MibotPageTitle("任务队列", "不丢任务 · 断点续接")
        MibotCard {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("执行队列", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${runs.count { it.state == TaskRunState.QUEUED }} 排队", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                runs.take(4).forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (r.state == TaskRunState.RUNNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.goal.take(20), fontSize = 13.sp)
                            Text(r.state.name.lowercase(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.state == TaskRunState.RUNNING) Text("运行中", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MibotSectionHeader("断点续接")
        MibotCard {
            Column(Modifier.padding(6.dp)) {
                listOf("新消息不取消 · 排队尾" to "FIFO", "插队 priority > 0" to "可插", "重启不丢 · 持久化" to "SharedPrefs").forEach { (n, d) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Text(n, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(d, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
