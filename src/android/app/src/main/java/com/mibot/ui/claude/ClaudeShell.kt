package com.mibot.ui.claude

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.chat.ConversationSummary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Claude 主壳 —— 复刻 com.anthropic.claude 的界面骨架（真机截图 + 反编译源码）。
 *
 * 结构：顶栏（`≡` + 标题 + Claude 图标）+ 会话侧栏 + 主内容 + 输入栏。
 * 参考 app/main/MainAppScreens + app/main/loggedin/LoggedInScreens（LoggedIn → ClaudeApp）。
 */
@Composable
fun ClaudeShell(
    chatContent: @Composable () -> Unit,
    sessions: List<ConversationSummary> = emptyList(),
    onOpenSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    showSidebar: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Claude.bg),
    ) {
        if (showSidebar) {
            ClaudeSidebar(
                sessions = sessions,
                onOpenSession = onOpenSession,
                onDeleteSession = onDeleteSession,
                modifier = Modifier.width(300.dp),
            )
            DividerVLine(modifier = Modifier.fillMaxHeight())
        }
        Column(modifier = Modifier.weight(1f).fillMaxSize()) {
            ClaudeTopBar(modifier = Modifier.fillMaxWidth())
            Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Claude.inputRadius))) {
                chatContent()
            }
            ClaudeInputBar(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 会话侧栏（Claude 侧栏：hamburger 触发 + 会话列表）。 */
@Composable
fun ClaudeSidebar(
    sessions: List<ConversationSummary>,
    onOpenSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Claude.bgAlt),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClaudeHamburger(modifier = Modifier.size(20.dp), color = Claude.ink2)
            Spacer(Modifier.width(12.dp))
            Text("Claude", style = ClaudeType.RowTitle)
        }
        HorizontalDivider(thickness = 1.dp, color = Claude.edge)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(sessions, key = { it.id }) { s ->
                ClaudeSessionRow(s = s, onClick = { onOpenSession(s.id) }, onDelete = { onDeleteSession(s.id) })
            }
        }
    }
}

/** 顶栏：左上 `≡` 两横线 + 右上 Claude 图标。 */
@Composable
fun ClaudeTopBar(
    modifier: Modifier = Modifier,
    onMenu: () -> Unit = {},
    title: String? = null,
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .background(Claude.bg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ClaudeHamburger(modifier = Modifier.size(20.dp), color = Claude.ink2, onClick = onMenu)
        if (title != null) Text(title, style = ClaudeType.RowTitle)
        ClaudeSparkIcon(size = 22.dp, color = Claude.ink2)
    }
}

/** 竖分隔线（侧栏和主区之间）。 */
@Composable
private fun DividerVLine(modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(1.dp).background(Claude.edge))
}

/** 会话行（Claude 侧栏项）。 */
@Composable
fun ClaudeSessionRow(
    s: ConversationSummary,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Claude.cardRadius))
            .background(if (selected) Claude.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = s.title.ifBlank { "新对话" },
                style = ClaudeType.RowTitle,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = claudeWhen(s.updatedAt),
                style = ClaudeType.Caption,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        ClaudeSparkIcon(size = 14.dp, color = Claude.ink3)
    }
}

/** 空态：居中橙色 asterisk + 建议提问（对应 Claude 的"月光下的对话？"）。 */
@Composable
fun ClaudeEmptyState(
    prompt: String,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ClaudeSparkIcon(size = 64.dp, color = Claude.accent)
        Spacer(Modifier.height(28.dp))
        Text(
            text = prompt,
            style = ClaudeType.ScreenTitle.copy(fontWeight = FontWeight.Normal, textAlign = TextAlign.Center),
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(36.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                suggestions.forEach { s ->
                    ClaudeSuggestionPill(text = s, onClick = { onSuggestion(s) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** 建议提问胶囊。 */
@Composable
fun ClaudeSuggestionPill(text: String, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Claude.buttonRadius))
            .background(Claude.suggestion)
            .border(1.dp, Claude.edge, RoundedCornerShape(Claude.buttonRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = ClaudeType.Body.copy(fontSize = 14.sp))
    }
}

/** Claude asterisk ✳（品牌标志，8 尖放射）。 */
@Composable
fun ClaudeSparkIcon(
    size: androidx.compose.ui.unit.Dp = 24.dp,
    color: Color = Claude.accent,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val cx = size.value / 2f
        val cy = size.value / 2f
        val len = cx * 0.92f
        val armWidth = size.value * 0.16f
        repeat(8) { i ->
            val a = (i * 45.0) * (Math.PI / 180.0)
            val dx = (len * cos(a)).toFloat()
            val dy = (len * sin(a)).toFloat()
            drawLine(
                color = color,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = armWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 顶栏菜单图标 `≡`（Claude 用两横线）。 */
@Composable
fun ClaudeHamburger(
    size: androidx.compose.ui.unit.Dp = 20.dp,
    color: Color = Claude.ink2,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Claude.buttonRadius))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        val w = size.value - 4f
        val bar = size.value * 0.09f
        drawLine(color, Offset(0f, w * 0.32f), Offset(w, w * 0.32f), strokeWidth = bar, cap = StrokeCap.Round)
        drawLine(color, Offset(0f, w * 0.72f), Offset(w, w * 0.72f), strokeWidth = bar, cap = StrokeCap.Round)
    }
}

/** 把会话时间格式成相对文案。 */
private fun claudeWhen(ts: Long): String {
    if (ts <= 0L) return "刚刚"
    val sec = ((System.currentTimeMillis() - ts) / 1000L).coerceAtLeast(0)
    return when {
        sec < 60 -> "刚刚"
        sec < 3600 -> "${sec / 60} 分钟前"
        sec < 86400 -> "${sec / 3600} 小时前"
        sec < 604800 -> "${sec / 86400} 天前"
        else -> "${ts / 1000 / 86400 / 365} 年前"
    }
}
