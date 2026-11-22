package com.mibot.ui.hq

import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mibot.ui.theme.MikaIcons
import com.mibot.chat.ConversationSummary
import com.mibot.core.record.RunSummary
import com.mibot.task.TaskRun

/** Sessions 屏：Miga 真实会话列表（长按/⋯ 删除，溶解动效）。 */
@Composable
fun HqSessionsScreen(
    sessions: List<ConversationSummary> = emptyList(),
    onOpenSession: (ConversationSummary) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onNewSession: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var deleteConfirm by remember { mutableStateOf<String?>(null) }
    var dissolving by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val q = query.trim().lowercase()
    val visible = if (q.isEmpty()) sessions else sessions.filter {
        it.title.lowercase().contains(q)
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了", color = Hq.accent) } },
            title = { Text("会话", color = Hq.ink) },
            text = { Text("点会话继续聊；行尾 ⋯ 删除（溶解动效）。「+」开新会话，「放大镜」搜索。", color = Hq.ink2) },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hq.bg)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HqSpace.Page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("会话", style = HqType.ScreenTitle, color = Hq.ink)
                    Spacer(Modifier.width(6.dp))
                    Icon(MikaIcons.Down, null, tint = Hq.ink2, modifier = Modifier.size(14.dp))
                }
                Text("Miga", style = HqType.Caption, color = Hq.ink2)
            }
            Row(
                modifier = Modifier
                    .clip(HqShape.Capsule)
                    .background(Hq.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(MikaIcons.ListPlus, "新建会话", tint = Hq.ink, modifier = Modifier.size(20.dp).clickable { onNewSession() })
                Icon(MikaIcons.Search, "搜索会话", tint = Hq.ink, modifier = Modifier.size(20.dp).clickable { showHelp = true; query = "" })
                Icon(MikaIcons.Edit, "帮助", tint = Hq.ink, modifier = Modifier.size(20.dp).clickable { showHelp = true })
            }
        }
        Spacer(Modifier.height(16.dp))

        if (visible.isEmpty()) {
            Text(
                if (q.isNotEmpty()) "没有匹配的会话" else "还没有会话",
                style = HqType.Caption, color = Hq.ink3,
                modifier = Modifier.padding(horizontal = HqSpace.Page, vertical = 24.dp),
            )
        } else {
            visible.forEach { s ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HqSessionRow(s, onClick = { onOpenSession(s) }, onDelete = { deleteConfirm = s.id })
                    if (dissolving == s.id) {
                        DissolveParticles(
                            onDone = { onDeleteSession(s.id); dissolving = null },
                            modifier = Modifier.matchParentSize(),
                            color = Hq.ink,
                        )
                    }
                }
            }
        }
    }
    deleteConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text("删除会话？") },
            text = { Text("删除后聊天记录一并清除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { deleteConfirm = null; dissolving = id }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HqSessionRow(s: ConversationSummary, onClick: () -> Unit = {}, onDelete: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = HqSpace.Page, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PebbleAvatar(s.title, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.title, style = HqType.RowTitle, color = Hq.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeLabel(s.updatedAt), style = HqType.Caption, color = Hq.ink3)
            }
            Spacer(Modifier.height(2.dp))
            Text("点击打开", style = HqType.Caption, color = Hq.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(MikaIcons.More, null, tint = Hq.ink2, modifier = Modifier.size(16.dp).clickable { onDelete() })
    }
}

private fun timeLabel(t: Long): String =
    if (t <= 0) "" else android.text.format.DateUtils.getRelativeTimeSpanString(t).toString()

/** Jobs 屏：agent 活动流 = 运行中任务 + 运行历史（Muse 对齐）。 */
@Composable
fun HqJobsScreen(
    jobs: List<TaskRun> = emptyList(),
    runs: List<RunSummary> = emptyList(),
    onOpenJob: (TaskRun) -> Unit = {},
    onOpenRun: (RunSummary) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val q = query.trim().lowercase()
    val visibleJobs = if (q.isEmpty()) jobs else jobs.filter { it.goal.lowercase().contains(q) }
    val visibleRuns = if (q.isEmpty()) runs else runs.filter { it.goal.lowercase().contains(q) }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了", color = Hq.accent) } },
            title = { Text("任务", color = Hq.ink) },
            text = { Text("这里是 agent 活动流：运行中任务置顶，下面是运行历史（目标/时间/结局）。「+」发任务，「放大镜」搜索。", color = Hq.ink2) },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hq.bg)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HqSpace.Page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("任务", style = HqType.ScreenTitle, color = Hq.ink)
                Text("agent 活动流", style = HqType.Caption, color = Hq.ink2)
            }
            Row(
                modifier = Modifier
                    .clip(HqShape.Capsule)
                    .background(Hq.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(MikaIcons.ListPlus, "发任务", tint = Hq.ink, modifier = Modifier.size(20.dp).clickable { showHelp = true })
                Icon(MikaIcons.Search, "搜索任务", tint = Hq.ink, modifier = Modifier.size(20.dp).clickable { showHelp = true; query = "" })
            }
        }
        Spacer(Modifier.height(16.dp))

        if (visibleJobs.isEmpty() && visibleRuns.isEmpty()) {
            Text(
                if (q.isNotEmpty()) "没有匹配的任务" else "还没有任务——去对话里跟 Miga 聊聊吧",
                style = HqType.Caption, color = Hq.ink3,
                modifier = Modifier.padding(horizontal = HqSpace.Page, vertical = 24.dp),
            )
        } else {
            // 运行中任务置顶
            visibleJobs.forEach { job ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenJob(job) }
                        .padding(horizontal = HqSpace.Page, vertical = 3.dp)
                        .clip(HqShape.Card)
                        .background(Hq.surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(MikaIcons.Clock, null, tint = Hq.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(job.goal, style = HqType.RowTitle, color = Hq.ink)
                        Text("运行中", style = HqType.Caption, color = Hq.ok)
                    }
                    Text("进行中", style = HqType.Caption, color = Hq.ink3)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (jobs.isNotEmpty() && runs.isNotEmpty()) {
                Text("历史", style = HqType.RowTitle, color = Hq.ink2, modifier = Modifier.padding(horizontal = HqSpace.Page, vertical = 8.dp))
            }
            // 运行历史（agent 活动流主体）
            visibleRuns.forEach { run ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRun(run) }
                        .padding(horizontal = HqSpace.Page, vertical = 3.dp)
                        .clip(HqShape.Card)
                        .background(Hq.surface)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (run.outcome == "ok") Hq.ok else Hq.warn))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(run.goal, style = HqType.RowTitle, color = Hq.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(relTime(run.startedAtMs), style = HqType.Caption, color = Hq.ink2)
                    }
                    Text(run.outcome ?: "完成", style = HqType.Caption, color = Hq.ink3)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** Kanban 屏：Miga 运行中任务卡片。 */
@Composable
fun HqKanbanScreen(tasks: List<TaskRun> = emptyList(), onOpenTask: (TaskRun) -> Unit = {}, modifier: Modifier = Modifier) {
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了", color = Hq.accent) } },
            title = { Text("看板", color = Hq.ink) },
            text = { Text("这是 Miga 运行中任务卡片（Blocked/Triage/Todo 列）。「+」发新任务：去对话页跟 Miga 说目标即可。", color = Hq.ink2) },
        )
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hq.bg)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HqSpace.Page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("看板", style = HqType.ScreenTitle, color = Hq.ink)
                Text("Miga 任务", style = HqType.Caption, color = Hq.ink)
            }
            Icon(MikaIcons.ListPlus, "发任务", tint = Hq.ink, modifier = Modifier.size(20.dp).clickable { showHelp = true })
            Spacer(Modifier.width(16.dp))
            Icon(MikaIcons.Info, null, tint = Hq.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (tasks.isEmpty()) {
            Text("没有运行中的任务", style = HqType.Caption, color = Hq.ink3, modifier = Modifier.padding(horizontal = HqSpace.Page, vertical = 24.dp))
        } else {
            tasks.forEach { task ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTask(task) }
                        .padding(horizontal = HqSpace.Page, vertical = 5.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Hq.surface)
                        .padding(16.dp),
                ) {
                    Text(task.goal, style = HqType.RowTitle, color = Hq.ink)
                    Spacer(Modifier.height(4.dp))
                    Text("运行中", style = HqType.Caption, color = Hq.ink2)
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

/** 动态屏：Muse 信息茧房——agent 按你关注的社交/信源生成的分组帖子流。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HqFeedScreen(
    posts: List<FeedPost> = emptyList(),
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val today = remember { java.text.SimpleDateFormat("M月d日", java.util.Locale.getDefault()).format(java.util.Date()) }
    val context = LocalContext.current
    val follow = remember { FollowStore(context) }
    val points by follow.points.collectAsState()
    var showFollow by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // 关注管理弹窗
    if (showFollow) {
        FollowManageDialog(points = points, onAdd = { follow.add(it) }, onRemove = { follow.remove(it) }, onDismiss = { showFollow = false })
    }
    // 搜索弹窗
    if (showSearch) {
        SearchDialog(query = query, onQuery = { query = it }, onDismiss = { showSearch = false })
    }

    val q = query.trim().lowercase()
    val visiblePosts = if (q.isEmpty()) posts else posts.mapNotNull { post ->
        val groups = post.groups.mapNotNull { g ->
            val items = g.items.filter { it.t.lowercase().contains(q) || it.b.lowercase().contains(q) || it.s?.lowercase()?.contains(q) == true }
            if (items.isEmpty()) null else g.copy(items = items)
        }
        if (groups.isEmpty()) null else post.copy(groups = groups)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hq.bg)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = HqSpace.Page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("动态", style = HqType.ScreenTitle, color = Hq.ink)
                Text("你关注的 · $today", style = HqType.Caption, color = Hq.ink2)
            }
            Row(
                modifier = Modifier
                    .clip(HqShape.Capsule)
                    .background(Hq.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(MikaIcons.ListPlus, "编辑关注", tint = Hq.ink, modifier = Modifier.size(20.dp).clickable { showFollow = true })
                Icon(MikaIcons.Search, "搜索动态", tint = Hq.ink, modifier = Modifier.size(20.dp).clickable { showSearch = true })
            }
        }
        Spacer(Modifier.height(16.dp))

        if (q.isNotEmpty()) {
            Text("搜索「$query」", style = HqType.Caption, color = Hq.ink3, modifier = Modifier.padding(horizontal = HqSpace.Page))
            Spacer(Modifier.height(8.dp))
        }

        if (visiblePosts.isEmpty()) {
            Text(
                if (q.isNotEmpty()) "没有匹配的动态" else "还没有动态——agent 按你的关注生成后会出现在这里",
                style = HqType.Caption, color = Hq.ink3,
                modifier = Modifier.padding(horizontal = HqSpace.Page, vertical = 24.dp),
            )
        } else {
            visiblePosts.forEach { post ->
                // butler（短信管家）来源 tag，默认折叠提示
                if (post.source == "butler") {
                    Row(
                        modifier = Modifier.padding(horizontal = HqSpace.Page, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0x33FF9F0A)).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Hq.warn))
                        Spacer(Modifier.width(6.dp))
                        Text("管家提醒", style = HqType.Caption, color = Hq.warn)
                    }
                }
                post.groups.forEach { group ->
                    // 分组悬浮小标签
                    Text(
                        group.h,
                        style = HqType.Caption,
                        color = Hq.ink2,
                        modifier = Modifier.padding(horizontal = HqSpace.Page, vertical = 8.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Hq.surface2).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    group.items.forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = HqSpace.Page, vertical = 4.dp)
                                .clip(HqShape.Card)
                                .background(Hq.surface)
                                .padding(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(item.e, style = HqType.RowTitle)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.t, style = HqType.RowTitle, color = Hq.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (item.b.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(item.b, style = HqType.Body, color = Hq.ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            val link = item.s?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                            if (link != null) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpenUrl(link) }
                                        .background(Hq.surface2).padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("🔗", style = HqType.Caption)
                                    Spacer(Modifier.width(6.dp))
                                    Text(link, style = HqType.Caption, color = Hq.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

/** 关注点管理弹窗：列表 + 添加（名称/信源/关键词）+ 删除。 */
@Composable
private fun FollowManageDialog(
    points: List<FollowPoint>,
    onAdd: (FollowPoint) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onAdd(FollowPoint(name.trim(), source.trim(), keyword.trim()))
                name = ""; source = ""; keyword = ""
            }) { Text("添加", color = Hq.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭", color = Hq.ink3) } },
        title = { Text("关注点", color = Hq.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).height(360.dp)) {
                // 添加表单
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称（如 赛博羊毛）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("信源（可空，如 NodeSeek / V2EX / URL）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = keyword, onValueChange = { keyword = it }, label = { Text("关键词（可空）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("已关注 ${points.size}", style = HqType.Caption, color = Hq.ink2)
                Spacer(Modifier.height(4.dp))
                points.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = HqType.Body, color = Hq.ink)
                            val sub = listOf(p.source, p.keyword).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, style = HqType.Caption, color = Hq.ink2)
                        }
                        TextButton(onClick = { onRemove(p.name) }) { Text("删除", color = Hq.warn) }
                    }
                }
                if (points.isEmpty()) Text("还没有关注点——agent 会按信源/关键词生成动态", style = HqType.Caption, color = Hq.ink3)
            }
        },
    )
}

/** 搜索弹窗：输入关键词过滤当前动态。 */
@Composable
private fun SearchDialog(query: String, onQuery: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成", color = Hq.accent) } },
        title = { Text("搜索动态", color = Hq.ink) },
        text = {
            OutlinedTextField(value = query, onValueChange = onQuery, label = { Text("关键词") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        },
    )
}

private fun relTime(ms: Long): String {
    val now = System.currentTimeMillis()
    val d = now - ms
    if (d < 60_000) return "刚刚"
    if (d < 3_600_000) return "${d / 60_000} 分钟前"
    if (d < 86_400_000) return "${d / 3_600_000} 小时前"
    return "${d / 86_400_000} 天前"
}
