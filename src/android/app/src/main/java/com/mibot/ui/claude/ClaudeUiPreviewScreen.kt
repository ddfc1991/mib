package com.mibot.ui.claude

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mibot.chat.ConversationSummary
import com.mibot.ui.hq.HqMoreEntry

/**
 * Claude 壳预览屏 —— 用逆向 Claude 的整套壳承载 Omega 的真实数据。
 * 结构对应 com.anthropic.claude 的 LoggedIn → ClaudeApp（会话侧栏 + 聊天主区 + 输入栏）。
 */
@Composable
fun ClaudeUiPreviewScreen(
    onOpenSession: (String) -> Unit = {},
    onMoreEntry: (HqMoreEntry) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    ClaudeShell(
        chatContent = {
            ClaudeChatScreen(
                messages = previewMessages,
                emptyPrompt = "What would you like to work on?",
                suggestions = listOf("帮我看看这段日志", "重构这个函数"),
                onSuggestion = {},
                onSend = {},
                modifier = Modifier,
            )
        },
        sessions = previewSessions,
        onOpenSession = onOpenSession,
        onDeleteSession = {},
        showSidebar = true,
        modifier = modifier,
    )
}

// ---- 示例占位（视觉对齐 Claude 界面;真实会话由外层注入） ----
private val nowMs = System.currentTimeMillis()
private val previewSessions = listOf(
    ConversationSummary(id = "c1", title = "Claude app 逆向", updatedAt = nowMs - 30 * 60_000L),
    ConversationSummary(id = "c2", title = "设计系统", updatedAt = nowMs - 2 * 3_600_000L),
    ConversationSummary(id = "c3", title = "登录流程", updatedAt = nowMs - 6 * 3_600_000L),
    ConversationSummary(id = "c4", title = "供应商配置", updatedAt = nowMs - 24 * 3_600_000L),
)
private val previewMessages = listOf(
    ClaudeMessage(id = "m1", text = "帮我看下这个界面是什么风格", isUser = true),
    ClaudeMessage(id = "m2", text = "这是 Claude 的深色主界面:纯黑底、橙色 asterisk 强调、Claude Sans 字体。", isUser = false),
)
