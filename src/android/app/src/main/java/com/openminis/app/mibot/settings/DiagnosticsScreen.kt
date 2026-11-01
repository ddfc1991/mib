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
    MibotScaffold(title = t("诊断", "Diagnostics"), onBack = onBack) {
        MibotSectionHeader(t("设备", "Device"))
        MibotCard(padding = PaddingValues(16.dp)) {
            Text("Mibot ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = com.openminis.app.mibot.design.MibotType.Body, color = MaterialTheme.colorScheme.onSurface)
            Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MibotSectionHeader(t("测试与记录", "Tests & records"))
        MibotCard {
            MibotRow(t("AI 网关体检", "Gateway health check"), t("逐个测试服务商账号", "Test every provider account"), Icons.Outlined.Hub, MibotTokens.Slate, onClick = onGateway, showDivider = true)
            MibotRow(t("日志", "Logs"), t("应用与代理日志", "App and agent logs"), Icons.Outlined.Description, MibotTokens.Steel, onClick = onLogs, showDivider = true)
            MibotRow(t("Token 用量", "Token usage"), null, Icons.Outlined.BarChart, MibotTokens.Green, onClick = onUsage, showDivider = true)
            MibotRow(t("存储占用", "Storage usage"), null, Icons.Outlined.Storage, MibotTokens.Amber, onClick = onStorage)
        }
        MibotSectionHeader(t("上报", "Report"))
        MibotCard {
            MibotRow(t("反馈问题", "Report a problem"), t("在 GitHub 打开已填好信息的问题单", "Opens a pre-filled GitHub issue"), Icons.Outlined.BugReport, MibotTokens.Red, onClick = onReportBug)
        }
    }
}
