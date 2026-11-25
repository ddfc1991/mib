package com.mibot.ui.hq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.mibot.chat.ConversationSummary
import com.mibot.core.record.RunSummary
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** 任务占位(UI 用):goal 显示;数据由 MainActivity 映射。 */
data class HqJob(val id: String = "", val goal: String)

/**
 * HQ 5-tab 主框架：Bots（聊天）/ Sessions / Jobs / Kanban / More。
 * [botsContent] 是聊天本体（Miga 现有 ChatScreen），其余 tab 是 HQ 复刻屏。
 * 点 bot 打开聊天时由外层控制。
 */
@Composable
fun HqShell(
    botsContent: @Composable () -> Unit,
    sessions: List<ConversationSummary> = emptyList(),
    jobs: List<HqJob> = emptyList(),
    runs: List<RunSummary> = emptyList(),
    posts: List<FeedPost> = emptyList(),
    onOpenSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onMoreEntry: (HqMoreEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(HqTab.Bots) }
    // [fork/glass-depth] The floating tab bar blurs the page it covers, not
    // the app-wide backdrop. A local Haze source on the content area gives the
    // bar real list content (rows, cards, header) to refract as it scrolls
    // underneath, which is what makes a docked bar read as glass rather than a
    // flat tinted pill.
    val pageHaze = rememberHazeState()
    Column(
        modifier = modifier
            .fillMaxSize()
            // A light veil, not an opaque slab: the shared atmosphere stays
            // faintly visible and the glass cards have something to sit on.
            .background(Hq.bg.copy(alpha = 0.88f))
            .statusBarsPadding(),
    ) {
        Box(Modifier.weight(1f).hazeSource(pageHaze)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }).togetherWith(
                        fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 10 }
                    )
                },
                label = "tabSwitch",
            ) { t ->
                when (t) {
                    HqTab.Bots -> botsContent()
                    HqTab.Sessions -> HqSessionsScreen(sessions, onOpenSession = { onOpenSession(it.id) }, onDeleteSession = onDeleteSession)
                    HqTab.Jobs -> HqJobsScreen(jobs = jobs, runs = runs, onOpenJob = {}, onOpenRun = {})
                    HqTab.Kanban -> HqFeedScreen(posts = posts, onOpenUrl = onOpenUrl)
                    HqTab.More -> HqMoreScreen(onMoreEntry)
                }
            }
        }
        Box(
            Modifier
                .navigationBarsPadding()
                .padding(top = 8.dp),
        ) {
            HqTabBar(current = tab, onSelect = { tab = it }, haze = pageHaze)
        }
    }
}
