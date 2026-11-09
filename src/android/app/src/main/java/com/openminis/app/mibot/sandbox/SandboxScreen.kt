package com.openminis.app.mibot.sandbox

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.IconChip
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotGrid
import com.openminis.app.mibot.design.MibotGridItem
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.sandbox.PRootKernel
import com.openminis.app.sandbox.RootfsInstallState
import com.openminis.app.sandbox.RootfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SandboxRoutes { const val SANDBOX = "mibot_sandbox" }

/**
 * [fork/mibot-v2] 沙箱 / 运行环境: one visible home for the Linux sandbox that
 * boots automatically (Alpine rootfs under PRoot). Status, size, and every
 * related tool (terminal, files, network/DNS, env vars, mounts, reset).
 */
@Composable
fun SandboxScreen(
    onBack: () -> Unit,
    onTerminal: () -> Unit,
    onFiles: () -> Unit,
    onDns: () -> Unit,
    onNetwork: () -> Unit,
    onEnvVars: () -> Unit,
    onMounts: () -> Unit,
    onShared: () -> Unit,
    onStorage: () -> Unit,
    onManage: () -> Unit,
    onMirrors: () -> Unit = onManage,
    @Suppress("UNUSED_PARAMETER") onOpenTerminalCommand: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val mgr = remember { RootfsManager.getInstance(ctx.applicationContext) }
    val state by mgr.installState.collectAsState()
    var size by remember { mutableLongStateOf(-1L) }
    var version by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        size = runCatching { mgr.getRootfsSize() }.getOrDefault(-1L)
        version = withContext(Dispatchers.IO) {
            runCatching { File(mgr.rootfsDir, "etc/alpine-release").readText().trim() }.getOrNull()
        }
    }
    val (statusText, kind) = when (val s = state) {
        is RootfsInstallState.Installed -> (if (PRootKernel.isBooted) t("运行中", "Running") else t("已安装", "Installed")) to ChipKind.OK
        is RootfsInstallState.Extracting -> (t("安装中 ", "Installing ") + "${(s.progress * 100).toInt()}%") to ChipKind.WARN
        is RootfsInstallState.Preparing, is RootfsInstallState.Finalizing -> t("启动中", "Booting") to ChipKind.WARN
        is RootfsInstallState.Failed -> t("出错", "Error") to ChipKind.ERR
        else -> (if (mgr.isInstalled) t("已安装", "Installed") else t("未安装", "Not installed")) to ChipKind.NEUTRAL
    }

    MibotScaffold(title = t("沙箱 / 运行环境", "Sandbox / runtime"), onBack = onBack) {
        Spacer(Modifier.size(8.dp))
        MibotCard(padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconChip(Icons.Outlined.ViewInAr, MibotTokens.Teal, size = 40.dp, iconSize = com.openminis.app.mibot.design.MibotIcon.Standard)
                Spacer(Modifier.width(14.dp))
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text("Alpine Linux" + (version?.let { " $it" } ?: ""), fontSize = com.openminis.app.mibot.design.MibotType.TitleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        t("PRoot 容器 · 开机自动安装", "PRoot container · installed automatically at boot"),
                        fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(statusText, kind)
            }
            Spacer(Modifier.size(12.dp))
            Text(
                t("占用空间：", "Size on disk: ") + if (size >= 0) formatSize(size) else "…",
                fontSize = com.openminis.app.mibot.design.MibotType.Body, color = MaterialTheme.colorScheme.onSurface,
            )
            Text(mgr.rootfsDir.absolutePath, fontSize = com.openminis.app.mibot.design.MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (state as? RootfsInstallState.Failed)?.let {
                Spacer(Modifier.size(6.dp))
                Text(it.error, fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.error)
            }
        }

        MibotSectionHeader(t("常用", "Tools"))
        MibotGrid(
            listOf(
                MibotGridItem(t("终端", "Terminal"), Icons.Outlined.Terminal, MibotTokens.Navy, onTerminal),
                MibotGridItem(t("文件", "Files"), Icons.Outlined.Folder, MibotTokens.Amber, onFiles),
                MibotGridItem(t("网络", "Network"), Icons.Outlined.Lan, MibotTokens.Sky, onNetwork),
                MibotGridItem(t("重置", "Reset"), Icons.Outlined.RestartAlt, MibotTokens.Rose, onManage),
            ),
        )

        MibotSectionHeader(t("设置", "Settings"))
        MibotCard {
            MibotRow(t("容器 DNS", "Container DNS"), t("resolv.conf 与解析服务器", "resolv.conf and resolvers"), Icons.Outlined.Dns, MibotTokens.Sky, onClick = onDns, showDivider = true)
            MibotRow(t("环境变量", "Environment variables"), t("注入到每条命令", "Injected into every command"), Icons.Outlined.Tune, MibotTokens.Purple, onClick = onEnvVars, showDivider = true)
            MibotRow(t("挂载外部文件夹", "Mounted folders"), t("把手机文件夹挂进沙箱", "Mount phone folders into the sandbox"), Icons.Outlined.Folder, MibotTokens.Amber, onClick = onMounts, showDivider = true)
            MibotRow(t("共享文件夹", "Shared folders"), t("共享 / 技能 / 记忆", "Shared / skills / memory"), Icons.Outlined.FolderShared, MibotTokens.Green, onClick = onShared, showDivider = true)
            MibotRow(t("镜像源与重装", "Mirrors & reinstall"), t("软件源、重置（可保留用户数据）", "Package mirrors, reset (optionally keep data)"), Icons.Outlined.RestartAlt, MibotTokens.Rose, onClick = onMirrors, showDivider = true)
            MibotRow(t("存储与备份", "Storage & backup"), t("空间占用与会话文件", "Usage and session files"), Icons.Outlined.Storage, MibotTokens.Steel, onClick = onStorage)
        }

        MibotSectionHeader(t("发行版", "Distribution"))
        MibotCard {
            val distroStatus = remember { mutableStateMapOf<String, String>() }
            val tint = mapOf("debian" to MibotTokens.Rose, "ubuntu" to MibotTokens.Brick, "alpine" to MibotTokens.Teal)
            val instT = t("已安装", "Installed"); val notT = t("未安装", "Not installed"); val failT = t("失败", "Failed")
            LinuxDistros.ALL.forEachIndexed { i, d ->
                val live = distroStatus[d.id]
                val installingT = t("安装中…", "Installing…")
                val status = if (live == null) (if (LinuxDistros.installed(ctx, d.id)) instT else notT)
                else if (live.isBlank()) instT else live
                val kind = when {
                    live != null && live.isNotBlank() && status != instT && !status.startsWith(failT) -> ChipKind.WARN
                    status == instT -> ChipKind.OK
                    status.startsWith(failT) -> ChipKind.ERR
                    else -> ChipKind.NEUTRAL
                }
                MibotRow(
                    d.name, "${d.desc} · ${d.size}", Icons.Outlined.ViewInAr, tint[d.id] ?: MibotTokens.Steel,
                    onClick = {
                        if (!LinuxDistros.installed(ctx, d.id)) {
                            distroStatus[d.id] = installingT
                            LinuxDistros.installWithMessage(ctx, d.id) { msg ->
                                distroStatus[d.id] = if (msg.isBlank()) "" else msg
                            }
                        }
                    },
                    chevron = false, showDivider = i < LinuxDistros.ALL.size - 1,
                    trailing = { StatusChip(status, kind) },
                )
            }
        }
        MibotFootnote(t(
            "AI 的 shell 命令都在这个沙箱里运行。点击发行版即可一键安装（Debian 约 90 MB、Ubuntu 约 30 MB、Alpine 约 4 MB，无需 root）。",
            "The AI's shell commands run in this sandbox. Tap a distro to install it with one tap (Debian ~90 MB, Ubuntu ~30 MB, Alpine ~4 MB, no root).",
        ))
    }
}

private fun formatSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format("%.1f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format("%.0f KB", b / 1024.0)
    else -> "$b B"
}
