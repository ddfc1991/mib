package com.mibot.ui.claude

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Claude 输入栏 —— 底部毛玻璃输入框 + 发送按钮（复刻 app 的输入胶囊）。
 * [onSend] 回调把用户输入交给外层（真机界面里对应 ConversationService/StreamTimeline）。
 */
@Composable
fun ClaudeInputBar(
    onSend: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    placeholder: String = "Ask Claude anything",
    enabled: Boolean = true,
) {
    var text by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .background(Claude.bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Claude.inputRadius))
                .background(Claude.surface)
                .border(1.dp, Claude.edge, RoundedCornerShape(Claude.inputRadius))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(text = placeholder, style = ClaudeType.Body.copy(color = Claude.ink3))
                } else {
                    Text(text = text, style = ClaudeType.Body)
                }
            }
            Spacer(Modifier.width(12.dp))
            ClaudeSendButton(
                enabled = enabled && text.isNotBlank(),
                onClick = {
                    onSend(text)
                    text = ""
                },
            )
        }
    }
}

/** 发送按钮（橙色 asterisk）。 */
@Composable
fun ClaudeSendButton(
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(Claude.buttonRadius))
            .background(if (enabled) Claude.accent else Claude.surface2)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        ClaudeSparkIcon(
            size = 20.dp,
            color = if (enabled) Claude.onAccent else Claude.ink3,
        )
    }
}

/**
 * Claude 消息气泡。
 * [isUser] true = 用户消息（深棕灰底），false = Claude 回答（更深灰底）。
 * 对应真机界面：用户气泡在右，Claude 气泡在左并带 asterisk 前缀。
 */
@Composable
fun ClaudeBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Claude.bubbleRadius))
                    .background(if (isUser) Claude.bubbleUser else Claude.bubbleBot)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(text = text, style = ClaudeType.Body)
            }
        }
    }
}

/**
 * Claude 聊天主屏（消息列表 + 自动滚动）。
 * [emptyPrompt] 为空时显示 asterisk 空态（真机的"月光下的对话？"）。
 */
@Composable
fun ClaudeChatScreen(
    messages: List<ClaudeMessage>,
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit = {},
    emptyPrompt: String = "What would you like to work on?",
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    if (messages.isEmpty()) {
        ClaudeEmptyState(
            prompt = emptyPrompt,
            suggestions = suggestions,
            onSuggestion = onSuggestion,
            modifier = modifier,
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Claude.bg),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    ClaudeBubble(text = m.text, isUser = m.isUser)
                }
            }
        }
    }
}

/** 一条聊天消息。 */
data class ClaudeMessage(val id: String, val text: String, val isUser: Boolean)

/**
 * 提示卡（对应真机的"Claude 在您所在的地区不可用"一类横条卡）。
 * [actionLabel] 非空时右侧显示可点链接。
 */
@Composable
fun ClaudeNoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Claude.cardRadius))
            .background(Claude.surface)
            .border(1.dp, Claude.edge, RoundedCornerShape(Claude.cardRadius))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ClaudeSparkIcon(size = 18.dp, color = Claude.warn)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, style = ClaudeType.RowTitle)
            Spacer(Modifier.height(4.dp))
            Text(text = body, style = ClaudeType.Caption)
            if (actionLabel != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = actionLabel,
                    style = ClaudeType.Caption.copy(color = Claude.accent, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                    modifier = Modifier.clickable(onClick = onAction),
                )
            }
        }
        if (onDismiss != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "×",
                color = Claude.ink2,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}

/**
 * 错误卡（对应真机的"组织已停用"卡）：暗红底 + 标题 + 说明 + 主按钮。
 */
@Composable
fun ClaudeErrorCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String = "申请复审",
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Claude.cardRadius))
            .background(Claude.errorBg)
            .border(1.dp, Claude.errorEdge, RoundedCornerShape(Claude.cardRadius))
            .padding(16.dp),
    ) {
        Text(text = title, style = ClaudeType.RowTitle.copy(color = Claude.errorTitle))
        Spacer(Modifier.height(6.dp))
        Text(text = body, style = ClaudeType.Caption.copy(color = Claude.errorInk))
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Claude.buttonRadius))
                .background(Color.White)
                .clickable(onClick = onAction)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = actionLabel, style = ClaudeType.RowTitle.copy(color = Color(0xFF1C1C1E)))
        }
    }
}

/** 分隔线。 */
@Composable
fun ClaudeDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Claude.edge)
}
