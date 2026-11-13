package com.openminis.app.mibot.feed

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.ideas.IdeasPage
import com.openminis.app.ui.glass.LocalGlassBottomInset

/**
 * [destiny/v7] 动态 owns three states: 在看 (real feed), 想到的 (ideas),
 * 能领的 (free quotas). 点子 is no longer a bottom tab — the V7 IA folds it
 * in here together with the free-token list from aihot.news/free-tokens.
 *
 * All three pages are real, persisted screens; nothing here is a mock.
 */
private val FEED_MODES = listOf("在看", "想到的", "能领的")

@Composable
fun DestinyFeedHost(
    onDiscuss: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onChat: (String) -> Unit,
    // [destiny/real-home] 首页区的真实数据：最近会话 + 状态行 + 新建对话。
    recents: List<com.openminis.app.mibot.home.HomeRecent> = emptyList(),
    statusLine: String = "",
    onNewChat: () -> Unit = {},
    dark: Boolean = false,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        FeedModeTabs(mode) { mode = it }
        Box(Modifier.weight(1f)) {
            when (mode) {
                0 -> FeedPage(
                    onDiscuss = onDiscuss,
                    onOpenSession = onOpenSession,
                    onOpenGroup = onOpenGroup,
                    recents = recents,
                    statusLine = statusLine,
                    onNewChat = onNewChat,
                    dark = dark,
                )
                1 -> IdeasPage(onChat = onChat)
                else -> FreeQuotasPage(onDiscuss = onDiscuss)
            }
        }
    }
}

@Composable
private fun FeedModeTabs(selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FEED_MODES.forEachIndexed { i, label ->
                Column(
                    Modifier
                        .clickable { onSelect(i) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == selected) accent else dim,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .size(width = 24.dp, height = 2.dp)
                            .background(if (i == selected) accent else Color.Transparent),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
    }
}

/** A starter list. Nothing here claims to be live — tapping a row asks the Agent to verify. */
private data class Quota(
    val name: String,
    val detail: String,
    val kind: String,
    val url: String,
)

private val QUOTAS = listOf(
    Quota(
        "Cloudflare Workers AI",
        "每天 1 万 Neurons · 无需实名 · 超额即停",
        "能进网关",
        "https://developers.cloudflare.com/workers-ai/",
    ),
    Quota(
        "Rime 语音额度",
        "每月约 50 美元额度 · 无需绑卡 · 限评估与开发",
        "能进网关",
        "https://rime.ai/",
    ),
    Quota(
        "硅基流动实名券",
        "¥16 代金券 · 含 Pro 与高速通道",
        "只能产品内用",
        "https://cloud.siliconflow.cn/",
    ),
    Quota(
        "阿里云百炼新人额度",
        "每模型 100 万 Token · 仅北京地域",
        "只能产品内用",
        "https://help.aliyun.com/zh/model-studio/",
    ),
    Quota(
        "豆包电脑版签到",
        "每天额度 · 仅电脑端产品内使用",
        "只能产品内用",
        "https://www.doubao.com/",
    ),
    Quota(
        "aihot.news 免费 Tokens",
        "免费 Token / API 额度清单汇总，按可用性排序",
        "清单入口",
        "https://aihot.news/free-tokens",
    ),
)

@Composable
private fun FreeQuotasPage(onDiscuss: (String) -> Unit) {
    val bottom = LocalGlassBottomInset.current
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 10.dp,
            bottom = bottom + 24.dp,
        ),
    ) {
        item {
            Text(
                "能领的钱 · ${QUOTAS.size} 条内置清单",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "按「能不能挂进你的网关」排，不按钱多少。额度随时会变，点任一条让 Agent 去核实最新条件，别按旧数字做决定。",
                style = MaterialTheme.typography.bodySmall,
                color = dim,
            )
            Spacer(Modifier.height(14.dp))
        }
        items(QUOTAS, key = { it.name }) { q ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDiscuss(
                            "帮我核实这条免费额度现在还能不能领、能不能挂进我的本地网关：" +
                                "${q.name}（${q.detail}）。来源：${q.url}。" +
                                "先查实时条件，再告诉我该不该领，不要直接写进收入。",
                        )
                    }
                    .padding(vertical = 14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        q.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        q.kind,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(q.detail, style = MaterialTheme.typography.bodySmall, color = dim)
                Spacer(Modifier.height(4.dp))
                Text(
                    "让 Agent 核实 →",
                    fontSize = 11.sp,
                    color = accent,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "领到的挂进本地网关，先烧免费再轮付费。没领到的不算收入。",
                style = MaterialTheme.typography.bodySmall,
                color = dim,
            )
        }
    }
}
