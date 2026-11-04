package com.openminis.app.mibot.ideas

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openminis.app.mibot.design.GlassField
import com.openminis.app.mibot.design.Glyph
import com.openminis.app.mibot.design.LineIcons
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotPageTitle
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotSwitch
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.design.tc
import com.openminis.app.scheduled.ScheduledTaskManager
import com.openminis.app.ui.glass.LocalGlassBottomInset
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** [fork/mibot-ideas] Process-wide store at `filesDir/ideas/`. */
object Ideas {
    @Volatile private var store: IdeaStore? = null
    fun store(context: Context): IdeaStore =
        store ?: synchronized(this) {
            store ?: IdeaStore(File(context.applicationContext.filesDir, "ideas")).also { store = it }
        }
}

/** Six sticky-note colors (light paper; text is always dark). */
internal val IdeaColors = listOf(
    Color(0xFFFFF4C2), // yellow
    Color(0xFFE3ECFF), // blue
    Color(0xFFDDF5EA), // green
    Color(0xFFFFE3EC), // pink
    Color(0xFFEDE4FF), // purple
    Color(0xFFFFE9D6), // peach
)
private val NoteInk = Color(0xFF22252B)

private fun ideaColor(i: Int) = IdeaColors[IdeaStore.clampColor(i)]

/**
 * [fork/mibot-ideas] 点子 tab: sticky notes (pinned first), add / edit /
 * delete, 6 colors, pin; 「变成任务」 makes a one-shot scheduled task and
 * 「去对话」 opens a new chat prefilled with the note.
 */
@Composable
fun IdeasPage(onChat: (String) -> Unit) {
    val ctx = LocalContext.current
    val bottom = LocalGlassBottomInset.current
    val store = remember { Ideas.store(ctx) }
    val ideas by store.ideas.collectAsState()
    // null = closed; "" = new note; else the id being edited.
    var editing by remember { mutableStateOf<String?>(null) }
    var taskFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { store.refresh() }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                MibotPageTitle(t("点子", "Ideas"), t("随手记下，之后变成任务或拿去对话", "Jot it down; turn it into a task or a chat later"))
                MibotButton(t("新点子", "New idea"), onClick = { editing = "" }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        }
        if (ideas.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Glyph(LineIcons.Lightbulb, size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(t("还没有点子", "No ideas yet"), fontSize = MibotType.Title, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("想到什么就记下来。重要的可以置顶，想做的可以一键变成定时任务。", "Write down whatever comes to mind. Pin the important ones; turn any into a scheduled task."),
                        fontSize = MibotType.Secondary,
                        lineHeight = MibotType.SecondaryLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(ideas, key = { it.id }) { idea -> IdeaNote(idea) { editing = idea.id } }
        }
    }

    editing?.let { id ->
        val idea = if (id.isEmpty()) null else ideas.firstOrNull { it.id == id }
        IdeaSheet(
            idea = idea,
            onDismiss = { editing = null },
            onSave = { text, color, pinned ->
                if (idea == null) {
                    store.add(text, color, pinned)
                } else {
                    if (text.trim() != idea.text || color != idea.color) store.update(idea.id, text, color)
                    if (pinned != idea.pinned) store.setPinned(idea.id, pinned)
                }
                editing = null
            },
            onDelete = { idea?.let { store.delete(it.id) }; editing = null },
            onTask = { text, color, pinned ->
                val saved = if (idea == null) store.add(text, color, pinned) else {
                    if (pinned != idea.pinned) store.setPinned(idea.id, pinned)
                    if (text.trim() != idea.text || color != idea.color) store.update(idea.id, text, color) else store.get(idea.id)
                }
                editing = null
                taskFor = saved?.id
            },
            onChat = { text ->
                editing = null
                onChat(text)
            },
        )
    }

    taskFor?.let { id ->
        val idea = ideas.firstOrNull { it.id == id }
        if (idea != null) {
            IdeaTaskDialog(idea.text, onDismiss = { taskFor = null }) { label, dayStart, h, m ->
                val ok = runCatching { ScheduledTaskManager(ctx.applicationContext).create(IdeaTasks.oneShot(idea.text, dayStart, h, m, label)) }.isSuccess
                val whenText = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(IdeaTasks.fireAt(dayStart, h, m)))
                Toast.makeText(
                    ctx,
                    if (ok) tc(ctx, "已创建定时任务 · $whenText", "Scheduled task created · $whenText")
                    else tc(ctx, "创建任务失败", "Could not create the task"),
                    Toast.LENGTH_SHORT,
                ).show()
                taskFor = null
            }
        }
    }
}

@Composable
private fun IdeaNote(idea: Idea, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ideaColor(idea.color).copy(alpha = 0.94f))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        if (idea.pinned) {
            Text(t("置顶", "Pinned"), fontSize = MibotType.Secondary, fontWeight = MibotType.Semi, color = NoteInk.copy(alpha = 0.55f))
            Spacer(Modifier.height(4.dp))
        }
        Text(idea.text, fontSize = MibotType.Body, lineHeight = MibotType.BodyLine, color = NoteInk, maxLines = 12, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(idea.updatedAt)),
            fontSize = MibotType.Secondary,
            color = NoteInk.copy(alpha = 0.5f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeaSheet(
    idea: Idea?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Boolean) -> Unit,
    onDelete: () -> Unit,
    onTask: (String, Int, Boolean) -> Unit,
    onChat: (String) -> Unit,
) {
    var text by remember(idea?.id) { mutableStateOf(idea?.text.orEmpty()) }
    var color by remember(idea?.id) { mutableIntStateOf(idea?.color ?: 0) }
    var pinned by remember(idea?.id) { mutableStateOf(idea?.pinned ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasText = text.isNotBlank()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                if (idea == null) t("新点子", "New idea") else t("编辑点子", "Edit idea"),
                fontSize = MibotType.Title,
                fontWeight = MibotType.Semi,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Box(Modifier.padding(horizontal = 16.dp)) {
                GlassField(
                    value = text,
                    onValueChange = { text = it.take(IdeaStore.MAX_TEXT) },
                    label = t("内容", "Note"),
                    placeholder = t("写下你的点子…", "Write your idea…"),
                    singleLine = false,
                    minLines = 4,
                )
            }
            MibotSectionHeader(t("颜色", "Color"))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IdeaColors.forEachIndexed { i, c ->
                    val on = i == color
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (on) 2.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else NoteInk.copy(alpha = 0.15f), CircleShape)
                            .clickable { color = i },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            MibotCard {
                Row(Modifier.fillMaxWidth().clickable { pinned = !pinned }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("置顶", "Pin to top"), fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    MibotSwitch(checked = pinned, onCheckedChange = { pinned = it })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MibotButton(t("变成任务", "Make a task"), onClick = { onTask(text, color, pinned) }, enabled = hasText, tonal = true, modifier = Modifier.weight(1f))
                MibotButton(t("去对话", "Chat about it"), onClick = { onChat(text) }, enabled = hasText, tonal = true, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (idea != null) {
                    MibotButton(t("删除", "Delete"), onClick = { confirmDelete = true }, tonal = true, modifier = Modifier.weight(1f))
                } else {
                    MibotButton(t("取消", "Cancel"), onClick = onDismiss, tonal = true, modifier = Modifier.weight(1f))
                }
                MibotButton(t("保存", "Save"), onClick = { onSave(text, color, pinned) }, enabled = hasText, modifier = Modifier.weight(1f))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("删除这个点子？", "Delete this idea?")) },
            text = { Text(text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(t("删除", "Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("取消", "Cancel")) } },
        )
    }
}

/** One-shot schedule: label, day (today / tomorrow / pick), time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeaTaskDialog(text: String, onDismiss: () -> Unit, onCreate: (String, Long, Int, Int) -> Unit) {
    val initial = remember { IdeaTasks.defaultFire(System.currentTimeMillis()) }
    var label by remember { mutableStateOf(IdeaTasks.label(text)) }
    var day by remember { mutableLongStateOf(IdeaTasks.startOfDay(initial)) }
    var showDate by remember { mutableStateOf(false) }
    val cal = remember { Calendar.getInstance().apply { timeInMillis = initial } }
    val time = rememberTimePickerState(initialHour = cal.get(Calendar.HOUR_OF_DAY), initialMinute = cal.get(Calendar.MINUTE), is24Hour = true)
    val today = IdeaTasks.startOfDay(System.currentTimeMillis())
    val tomorrow = IdeaTasks.startOfDay(today + 36 * 3_600_000L)
    val fire = IdeaTasks.fireAt(day, time.hour, time.minute)
    val inFuture = fire > System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("变成定时任务", "Make a scheduled task")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GlassField(value = label, onValueChange = { label = it.take(60) }, label = t("任务名", "Task name"))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MibotButton(t("今天", "Today"), onClick = { day = today }, tonal = day != today, modifier = Modifier.weight(1f))
                    MibotButton(t("明天", "Tomorrow"), onClick = { day = tomorrow }, tonal = day != tomorrow, modifier = Modifier.weight(1f))
                    val other = day != today && day != tomorrow
                    MibotButton(
                        if (other) SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(day)) else t("选日期", "Pick"),
                        onClick = { showDate = true },
                        tonal = !other,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                TimeInput(state = time)
                if (!inFuture) {
                    Text(t("这个时间已经过去了", "That time has already passed"), color = MaterialTheme.colorScheme.error, fontSize = MibotType.Secondary)
                }
                MibotFootnote(t("到点后会在新对话里把这个点子交给 AI 执行，只运行一次。", "At that time the idea runs once in a new chat."))
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(label, day, time.hour, time.minute) }, enabled = inFuture) { Text(t("创建", "Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("取消", "Cancel")) } },
    )

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMidnightOf(day))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = localDayFromUtcMidnight(it) }
                    showDate = false
                }) { Text(t("确定", "OK")) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(t("取消", "Cancel")) } },
        ) { DatePicker(state = state) }
    }
}

/** DatePicker works in UTC-midnight ms; map our local day to/from it. */
private fun utcMidnightOf(localDayStart: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = localDayStart }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

private fun localDayFromUtcMidnight(utcMs: Long): Long {
    val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}
