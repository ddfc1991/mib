package com.mibot.ui.hq

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mibot.chat.ConversationSummary
import com.mibot.core.record.RunSummary
import com.mibot.settings.Persona

/** Hermes HQ 截图 1:1 空白 UI 预览版：视觉完全照截图，数据=截图示例占位（无对话/开发记录）。 */
@Composable
fun HqUiPreviewScreen(
    onOpenSession: (String) -> Unit = {},
    onMoreEntry: (HqMoreEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    HqShell(
        botsContent = { HqBotsScreen(personas = previewPersonas, onOpenBot = {}) },
        sessions = previewSessions,
        jobs = previewJobs,
        runs = previewRuns,
        posts = emptyList(),
        onOpenSession = onOpenSession,
        onDeleteSession = {},
        onOpenUrl = {},
        onMoreEntry = onMoreEntry,
        modifier = modifier,
    )
}

// ---- 截图示例占位（视觉 1:1；不含真实对话/开发记录） ----
private val nowMs = System.currentTimeMillis()
private val previewPersonas = listOf(
    Persona(name = "Hermes", description = "主智能体 · 处理会话与任务", prompt = ""),
    Persona(name = "Deploys", description = "部署 bot · 后台自动化", prompt = ""),
    Persona(name = "Research", description = "研究 bot · 资料收集分析", prompt = ""),
)
private val previewSessions = listOf(
    ConversationSummary(id = "s1", title = "Release notes", updatedAt = nowMs - 30 * 60_000L),
    ConversationSummary(id = "s2", title = "Comparing vector", updatedAt = nowMs - 2 * 3_600_000L),
    ConversationSummary(id = "s3", title = "Reading", updatedAt = nowMs - 6 * 3_600_000L),
    ConversationSummary(id = "s4", title = "Finance", updatedAt = nowMs - 24 * 3_600_000L),
)
private val previewJobs = listOf(
    HqJob(id = "j1", goal = "构建 release 包"),
    HqJob(id = "j2", goal = "迁移仓库到新组织"),
)
private val previewRuns = listOf(
    RunSummary(id = "r1", goal = "整理 UI 组件清单", startedAtMs = nowMs - 3_600_000L, outcome = "ok"),
    RunSummary(id = "r2", goal = "修复登录超时", startedAtMs = nowMs - 5 * 3_600_000L, outcome = "fail"),
)
