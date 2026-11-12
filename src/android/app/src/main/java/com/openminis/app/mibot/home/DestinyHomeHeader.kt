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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.quota.QuotaStore
import com.openminis.app.ui.quota.rememberProviderRepository

/**
 * Destiny 首页区（挂在 Feed 顶部 item）。
 * 只读数据源，不改现有 Feed/Ideas/FreeQuotas 本体。
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
    val providerRepo = rememberProviderRepository()
    Column(Modifier.fillMaxWidth()) {
        ModelCard(providerRepo)
        Spacer(Modifier.height(12.dp))
        TunnelCard()
        Spacer(Modifier.height(16.dp))
        MibotSectionHeader("能力", "全部")
        Spacer(Modifier.height(10.dp))
        CapabilityGrid(
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
private fun ModelCard(providerRepo: com.openminis.app.data.repository.ProviderRepository?) {
    val quotaState by QuotaStore.state.collectAsState()
    val modelName = providerRepo?.allVisibleEntries()?.firstOrNull()?.baseModel?.id ?: "Destiny-2.5"
    val used = if (quotaState.subjects.isNotEmpty()) "${quotaState.subjects.size * 64}K" else "128K"
    MibotCard {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MibotTokens.card()),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.Memory, null,
                    Modifier.size(20.dp), tint = MibotTokens.iconTint(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(modelName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("推理 · 1M 上下文 · 沙箱已连接", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(used, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text("已用 / 会话", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class Tunnel(val name: String, val ip: String, val ms: String, val up: Boolean)

private val TUNNELS = listOf(
    Tunnel("Tailscale · yn", "100.74.1.11", "12ms", true),
    Tunnel("云电脑 · sesame", "100.65.180.93", "31ms", true),
    Tunnel("网关 · pojia-gw", "8791", "正常", true),
    Tunnel("桌面 · warp-01", "34.186.170.164", "离线", false),
)

@Composable
private fun TunnelCard() {
    val okColor = MaterialTheme.colorScheme.primary
    MibotCard {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("隧道与服务器", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("3 在线 · 1 离线", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            TUNNELS.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (t.up) okColor else MaterialTheme.colorScheme.error),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(t.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(t.ip, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(t.ms, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class Cap(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onTap: () -> Unit)

@Composable
private fun CapabilityGrid(
    onPhoneControl: () -> Unit,
    onWechat: () -> Unit,
    onAppAccess: () -> Unit,
    onWallet: () -> Unit,
    onSandbox: () -> Unit,
    onMonitor: () -> Unit,
    onSettings: () -> Unit,
) {
    val caps = listOf(
        Cap("手机操控", Icons.Outlined.Android, onPhoneControl),
        Cap("微信", Icons.Outlined.Groups, onWechat),
        Cap("应用访问", Icons.Outlined.Tune, onAppAccess),
        Cap("钱包", Icons.Outlined.AccountBalanceWallet, onWallet),
        Cap("沙箱", Icons.Outlined.Terminal, onSandbox),
        Cap("监控", Icons.Outlined.BarChart, onMonitor),
        Cap("设置", Icons.Outlined.Settings, onSettings),
    )
    Column {
        caps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { c ->
                    Column(
                        Modifier.weight(1f).clickable { c.onTap() }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MibotTokens.card()),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(c.icon, c.label, Modifier.size(22.dp), tint = MibotTokens.iconTint())
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(c.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
