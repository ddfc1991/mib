package com.openminis.app.opencode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.ui.theme.MikaIcons
import kotlinx.coroutines.launch

/**
 * OpenCode 聊天页：显示会话消息、发消息、SSE 实时流式接收回复。
 */
@Composable
fun OpenCodeChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val baseUrl = OpenCodeStore.baseUrl(context)
    val token = OpenCodeStore.token(context)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<OcMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var liveText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // 加载历史消息
    LaunchedEffect(sessionId) {
        runCatching { OpenCodeApi.listMessages(baseUrl, token, sessionId) }
            .onSuccess { messages = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    // 订阅 SSE 事件流
    DisposableEffect(sessionId) {
        val source = OpenCodeApi.subscribeEvents(
            baseUrl = baseUrl,
            token = token,
            onEvent = { ev ->
                if (ev.sessionId != sessionId) return@subscribeEvents
                when (ev.type) {
                    "session.text.started" -> {
                        streaming = true; liveText = ""
                    }
                    "session.text.delta" -> {
                        streaming = true
                        liveText += ev.delta.orEmpty()
                    }
                    "session.text.ended", "session.idle", "session.execution.succeeded", "session.execution.failed" -> {
                        streaming = false
                        // 刷新完整消息列表，拿到官方落库的最终文本
                        scope.launch {
                            runCatching { OpenCodeApi.listMessages(baseUrl, token, sessionId) }
                                .onSuccess { messages = it }
                            liveText = ""
                        }
                    }
                }
            },
        )
        onDispose { source.cancel() }
    }

    // 新消息自动滚到底
    LaunchedEffect(messages.size, liveText) {
        if (messages.isNotEmpty() || liveText.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(maxOf(0, messages.size + if (liveText.isNotEmpty()) 1 else 0)) }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isBlank() || busy) return
        // 乐观插入用户消息
        val optimistic = OcMessage(
            id = "local-${System.currentTimeMillis()}",
            role = "user",
            parts = listOf(OcPart("text", text, null, null, null)),
            modelId = null,
        )
        messages = messages + optimistic
        draft = ""
        busy = true
        scope.launch {
            runCatching { OpenCodeApi.sendPrompt(baseUrl, token, sessionId, text) }
                .onFailure { error = it.message ?: "发送失败" }
            busy = false
        }
    }

    Column(modifier = modifier.fillMaxSize().background(OcTheme.bg)) {
        // 全 App 统一顶栏
        com.openminis.app.ui.theme.AppTopBar(
            title = "会话",
            subtitle = sessionId,
            onBack = onBack,
            actions = {
                if (streaming) {
                    Text("运行中", color = OcTheme.accent, fontSize = 12.sp)
                    IconButton(onClick = {
                        scope.launch { runCatching { OpenCodeApi.interrupt(baseUrl, token, sessionId) } }
                    }) {
                        Icon(MikaIcons.Stop, "停止", tint = OcTheme.danger, modifier = Modifier.size(18.dp))
                    }
                }
            },
        )

        if (error != null) {
            Text(error!!, color = OcTheme.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }

        // 消息列表
        Box(Modifier.weight(1f)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = OcTheme.accent) }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
                    if (liveText.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth()) {
                                Box(
                                    Modifier
                                        .background(OcTheme.surface, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                        .fillMaxWidth(0.9f)
                                ) {
                                    Text(liveText, color = OcTheme.ink, fontSize = 13.sp, lineHeight = 19.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 输入栏
        Row(
            Modifier
                .fillMaxWidth()
                .background(OcTheme.surface)
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("发送消息…", color = OcTheme.ink3) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = OcTheme.ink,
                    unfocusedTextColor = OcTheme.ink,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = OcTheme.accent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = 4,
            )
            IconButton(onClick = { send() }, enabled = draft.isNotBlank() && !busy) {
                Icon(
                    MikaIcons.Up,
                    "发送",
                    tint = if (draft.isNotBlank() && !busy) OcTheme.accent else OcTheme.ink3,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: OcMessage) {
    val text = msg.parts.filter { it.type == "text" }.joinToString("\n") { it.text }
    val tools = msg.parts.filter { it.type == "tool" }
    if (text.isBlank() && tools.isEmpty()) return

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .background(
                    if (msg.isUser) OcTheme.surface2 else OcTheme.surface,
                    RoundedCornerShape(12.dp),
                )
                .padding(12.dp)
                .fillMaxWidth(0.9f),
        ) {
            Text(
                if (msg.isUser) "你" else (msg.modelId ?: "OpenCode"),
                color = if (msg.isUser) OcTheme.accent else OcTheme.ink2,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            if (text.isNotBlank()) {
                Text(text, color = OcTheme.ink, fontSize = 13.sp, lineHeight = 19.sp)
            }
            tools.forEach { t ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚙ ${t.toolName ?: "tool"}${t.toolStatus?.let { " · $it" } ?: ""}",
                    color = OcTheme.ink3,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                if (!t.toolInput.isNullOrBlank()) {
                    Text(t.toolInput, color = OcTheme.ink2, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
