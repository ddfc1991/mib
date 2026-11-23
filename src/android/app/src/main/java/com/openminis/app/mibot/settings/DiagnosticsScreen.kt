package com.openminis.app.mibot.settings

import android.os.Build
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.openminis.app.BuildConfig
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.t

object DiagnosticsRoutes { const val DIAGNOSTICS = "mibot_diagnostics" }

/** [fork/mibot-v2] One home for logs, usage, health checks and bug reports. */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    onLogs: () -> Unit,
    onUsage: () -> Unit,
    onStorage: () -> Unit,
    onGateway: () -> Unit,
    onReportBug: () -> Unit,
) {
    OmegaScaffold(title = t("诊断", "Diagnostics"), onBack = onBack) {
        OmegaSectionHeader(t("设备", "Device"))
        OmegaCard(padding = PaddingValues(16.dp)) {
            Text("Mibot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = com.openminis.app.mibot.design.OmegaType.Body, color = MaterialTheme.colorScheme.onSurface)
            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", fontSize = com.openminis.app.mibot.design.OmegaType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OmegaSectionHeader(t("测试与记录", "Tests & records"))
        OmegaCard {
            OmegaRow(t("AI 网关体检", "Gateway health check"), t("逐个测试服务商账号", "Test every provider account"), Icons.Outlined.Hub, OmegaTokens.Slate, onClick = onGateway, showDivider = true)
            OmegaRow(t("日志", "Logs"), t("应用与代理日志", "App and agent logs"), Icons.Outlined.Description, OmegaTokens.Steel, onClick = onLogs, showDivider = true)
            OmegaRow(t("Token 用量", "Token usage"), null, Icons.Outlined.BarChart, OmegaTokens.Green, onClick = onUsage, showDivider = true)
            OmegaRow(t("存储占用", "Storage usage"), null, Icons.Outlined.Storage, OmegaTokens.Amber, onClick = onStorage)
        }
        OmegaSectionHeader(t("上报", "Report"))
        OmegaCard {
            OmegaRow(t("反馈问题", "Report a problem"), t("在 GitHub 打开已填好信息的问题单", "Opens a pre-filled GitHub issue"), Icons.Outlined.BugReport, MibotTokens.Red, onClick = onReportBug)
        }
    }
}
