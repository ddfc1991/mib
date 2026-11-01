package com.openminis.app.mibot.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openminis.app.MinisApp
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotGrid
import com.openminis.app.mibot.design.MibotGridItem
import com.openminis.app.mibot.design.MibotPageTitle
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.vault.VaultStore
import com.openminis.app.ui.glass.LocalGlassBottomInset

/** Everything the settings home can open. */
class MibotSettingsActions(
    val vault: () -> Unit,
    val providers: () -> Unit,
    val gateway: () -> Unit,
    val modelGroups: () -> Unit,
    val backdrop: () -> Unit,
    val appearance: () -> Unit,
    val bots: () -> Unit,
    val soul: () -> Unit,
    val skills: () -> Unit,
    val memory: () -> Unit,
    val mcp: () -> Unit,
    val tools: () -> Unit,
    val scheduled: () -> Unit,
    val sandbox: () -> Unit,
    val terminal: () -> Unit,
    val files: () -> Unit,
    val network: () -> Unit,
    val tailscale: () -> Unit,
    val proxies: () -> Unit,
    val permissions: () -> Unit,
    val background: () -> Unit,
    val backup: () -> Unit,
    val diagnostics: () -> Unit,
    val reportBug: () -> Unit,
    val about: () -> Unit,
    val virtualDisplay: () -> Unit = {},
)

/**
 * [fork/mibot-v2] Settings home, regrouped in the reference's language:
 * 模型 card → apps grid → 账号与服务 → 外观与背景 → Bots → 运行时/沙箱 →
 * 诊断与反馈 → 关于. Each feature has exactly one entry here.
 */
@Composable
fun MibotSettingsScreen(a: MibotSettingsActions) {
    val ctx = LocalContext.current
    val repo = remember { (ctx.applicationContext as MinisApp).providerRepository }
    val config by repo.config.collectAsState()
    val accounts by remember { VaultStore.entries(ctx) }.collectAsState()
    val bottom = LocalGlassBottomInset.current

    val group = config.modelGroups.find { it.id == config.defaultPrimaryGroupId } ?: config.modelGroups.firstOrNull()
    val entry = group?.memberEntryIds?.firstNotNullOfOrNull { id -> config.modelEntries.find { it.id == id } }
    val inst = entry?.let { e -> config.instances.find { it.id == e.providerInstanceId } }
    val sameProvider = inst?.let { i -> config.instances.count { it.providerType == i.providerType } } ?: 0
    val modelLine = if (entry == null || inst == null) {
        t("未配置 · 点此添加服务商", "Not set up · tap to add a provider")
    } else {
        "${inst.label}（${sameProvider}${t(" 账号", " acct")}）· ${entry.model.displayName}" + (group?.name?.let { " · $it" } ?: "")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { MibotPageTitle(t("设置", "Settings")) }
        item {
            Spacer(Modifier.height(4.dp))
            MibotCard {
                MibotRow(
                    title = t("模型", "Model"),
                    subtitle = modelLine,
                    icon = Icons.Outlined.Layers,
                    color = MibotTokens.Brick,
                    onClick = if (entry == null) a.providers else a.gateway,
                )
            }
        }
        item {
            MibotSectionHeader(t("应用", "Apps"))
            MibotGrid(
                listOf(
                    MibotGridItem(t("账号台账", "Vault"), Icons.Outlined.Key, MibotTokens.Brick, a.vault),
                    MibotGridItem(t("AI 网关", "Gateway"), Icons.Outlined.Hub, MibotTokens.Slate, a.gateway),
                    MibotGridItem(t("沙箱", "Sandbox"), Icons.Outlined.ViewInAr, MibotTokens.Teal, a.sandbox),
                    MibotGridItem(t("终端", "Terminal"), Icons.Outlined.Terminal, MibotTokens.Navy, a.terminal),
                    MibotGridItem(t("文件", "Files"), Icons.Outlined.Folder, MibotTokens.Amber, a.files),
                    MibotGridItem(t("技能", "Skills"), Icons.Outlined.AutoAwesome, MibotTokens.Purple, a.skills),
                    MibotGridItem(t("记忆", "Memory"), Icons.Outlined.Psychology, MibotTokens.Rose, a.memory),
                    MibotGridItem("MCP", Icons.Outlined.Extension, MibotTokens.Green, a.mcp),
                    MibotGridItem(t("定时任务", "Scheduled"), Icons.Outlined.Schedule, MibotTokens.Sky, a.scheduled),
                    MibotGridItem("Bots", Icons.Outlined.SmartToy, MibotTokens.Taupe, a.bots),
                    MibotGridItem(t("人格", "Soul"), Icons.Outlined.Face, MibotTokens.Red, a.soul),
                    MibotGridItem(t("后台操控", "BG control"), Icons.Outlined.PhonelinkSetup, MibotTokens.Brick, a.virtualDisplay),
                    MibotGridItem(t("诊断", "Diagnose"), Icons.Outlined.MonitorHeart, MibotTokens.Steel, a.diagnostics),
                ),
            )
        }
        item {
            MibotSectionHeader(t("账号与服务", "Accounts & services"))
            MibotCard {
                MibotRow(t("账号台账", "Account Vault"),
                    t("AI 服务、登录与密钥 · ${accounts.size} 个账号", "AI services, logins & keys · ${accounts.size} accounts"),
                    Icons.Outlined.Key, MibotTokens.Brick, onClick = a.vault, showDivider = true)
                MibotRow(t("服务商", "Providers"), t("${config.instances.size} 个 · API Key、一键登录、免费可用", "${config.instances.size} · API keys, 1-tap login, free tiers"),
                    Icons.Outlined.VpnKey, MibotTokens.Sky, onClick = a.providers, showDivider = true)
                MibotRow(t("AI 网关", "AI gateway"), t("账号池、故障切换、冷却与健康检查", "Account pools, failover, cooldown, health checks"),
                    Icons.Outlined.Hub, MibotTokens.Slate, onClick = a.gateway, showDivider = true)
                MibotRow(t("模型组", "Model groups"), t("模型别名与回退/负载均衡", "Model aliases, fallback / load-balance"),
                    Icons.Outlined.AccountTree, MibotTokens.Green, onClick = a.modelGroups)
            }
        }
        item {
            MibotSectionHeader(t("外观与背景", "Appearance & background"))
            MibotCard {
                MibotRow(t("背景与主题", "Background & theme"), t("云天 / 夜空、浅色深色、照片或纯色", "Cloud sky / night, light-dark, photo or solid"),
                    Icons.Outlined.Wallpaper, MibotTokens.Teal, onClick = a.backdrop, showDivider = true)
                MibotRow(t("字体、语言与显示", "Fonts, language & display"), null,
                    Icons.Outlined.TextFields, MibotTokens.Purple, onClick = a.appearance)
            }
        }
        item {
            MibotSectionHeader("Bots")
            MibotCard {
                MibotRow(t("Bots 与子代理", "Bots & sub-agents"), t("保存的 Bot 可被主 AI 当作子代理调用", "Saved Bots can be called by the main AI as sub-agents"),
                    Icons.Outlined.SmartToy, MibotTokens.Taupe, onClick = a.bots, showDivider = true)
                MibotRow(t("人格", "Soul"), t("主 AI 的性格与说话方式", "Main AI personality and voice"), Icons.Outlined.Face, MibotTokens.Red, onClick = a.soul, showDivider = true)
                MibotRow(t("技能", "Skills"), null, Icons.Outlined.AutoAwesome, MibotTokens.Purple, onClick = a.skills, showDivider = true)
                MibotRow(t("记忆", "Memory"), null, Icons.Outlined.Psychology, MibotTokens.Rose, onClick = a.memory, showDivider = true)
                MibotRow("MCP", null, Icons.Outlined.Extension, MibotTokens.Green, onClick = a.mcp, showDivider = true)
                MibotRow(t("工具开关", "Tool switches"), t("浏览器、提问卡片等", "Browser, ask cards and more"), Icons.Outlined.Build, MibotTokens.Steel, onClick = a.tools)
            }
        }
        item {
            MibotSectionHeader(t("运行时 / 沙箱", "Runtime / sandbox"))
            MibotCard {
                MibotRow(t("后台操控 / 虚拟屏", "Background control"), t("在隐藏虚拟屏操作 App，不占你的屏幕 · 无线调试 / Shizuku", "Operate apps on a hidden display · Wireless Debugging / Shizuku"),
                    Icons.Outlined.PhonelinkSetup, MibotTokens.Brick, onClick = a.virtualDisplay, showDivider = true)
                MibotRow(t("沙箱 / 运行环境", "Sandbox / runtime"), t("Alpine Linux · 终端、文件、DNS、重置", "Alpine Linux · terminal, files, DNS, reset"),
                    Icons.Outlined.ViewInAr, MibotTokens.Teal, onClick = a.sandbox, showDivider = true)
                MibotRow(t("网络路由", "Network routing"), null, Icons.Outlined.Lan, MibotTokens.Sky, onClick = a.network, showDivider = true)
                MibotRow("Tailscale", null, Icons.Outlined.Lan, MibotTokens.Navy, onClick = a.tailscale, showDivider = true)
                MibotRow(t("代理", "Proxies"), null, Icons.Outlined.Security, MibotTokens.Slate, onClick = a.proxies, showDivider = true)
                MibotRow(t("权限", "Permissions"), null, Icons.Outlined.Lock, MibotTokens.Amber, onClick = a.permissions, showDivider = true)
                MibotRow(t("后台运行", "Background running"), t("电池优化与自启动", "Battery optimisation & autostart"), Icons.Outlined.BatteryChargingFull, MibotTokens.Green, onClick = a.background, showDivider = true)
                MibotRow(t("存储与备份", "Storage & backup"), null, Icons.Outlined.Storage, MibotTokens.Steel, onClick = a.backup)
            }
        }
        item {
            MibotSectionHeader(t("诊断与反馈", "Diagnostics & feedback"))
            MibotCard {
                MibotRow(t("诊断", "Diagnostics"), t("日志、测试与上报", "Logs, tests and reports"), Icons.Outlined.MonitorHeart, MibotTokens.Rose, onClick = a.diagnostics, showDivider = true)
                MibotRow(t("反馈问题", "Report a problem"), t("打开已填好设备信息的问题单", "Opens an issue pre-filled with device info"), Icons.Outlined.BugReport, MibotTokens.Red, onClick = a.reportBug)
            }
        }
        item {
            MibotSectionHeader(t("关于", "About"))
            MibotCard {
                MibotRow(t("关于 Mibot", "About Mibot"), t("版本、更新与开源许可（GPL-3.0）", "Version, updates and licenses (GPL-3.0)"), Icons.Outlined.Info, MibotTokens.Steel, onClick = a.about)
            }
        }
        item { Box(Modifier.height(8.dp)) }
    }
}
