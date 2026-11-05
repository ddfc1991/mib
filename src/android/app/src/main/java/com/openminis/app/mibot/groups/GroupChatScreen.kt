package com.openminis.app.mibot.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.openminis.app.mibot.design.Glyph
import com.openminis.app.mibot.design.LineIcons
import com.openminis.app.mibot.design.MibotIcon
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotTopBar
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.t

/** Route for one group chat: `group_chat/{groupId}`. */
object GroupRoutes {
    const val CHAT = "group_chat/{groupId}"
    fun chat(id: String) = "group_chat/$id"
}

/**
 * [fork/mibot-groups] One group chat: transcript with speaker names, an input
 * row (send / stop), member chips that insert `@Name`, and the last turn's
 * stop note (round cap / budget / stopped).
 */
@Composable
fun GroupChatScreen(groupId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { GroupChats.store(ctx) }
    val groups by store.groups.collectAsState()
    // Every append bumps the group's updatedAt, which re-emits [groups]; reload then.
    val stamp = groups.firstOrNull { it.id == groupId }?.updatedAt
    val file = remember(groupId, stamp) { store.load(groupId) }
    val running by GroupChats.running.collectAsState()
    val notes by GroupChats.notes.collectAsState()
    val busy = groupId in running
    val roster = remember(groupId) { GroupChats.roster(ctx) }
    val members = remember(file?.group?.memberBotIds, roster) {
        val byId = roster.associateBy { it.id }
        file?.group?.memberBotIds.orEmpty().mapNotNull { byId[it] }.map { GroupChats.asGroupBot(ctx, it) }
    }
    var input by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val messages = file?.messages.orEmpty()
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        MibotTopBar(file?.group?.title ?: t("群聊", "Group chat"), onBack = onBack) {
            if (file != null) {
                Box(
                    Modifier.size(MibotIcon.TopButton).clip(CircleShape).clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) { Glyph(LineIcons.Trash2, size = MibotIcon.Dense, tint = MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = t("删除群聊", "Delete group")) }
            }
        }
        if (file == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(t("这个群聊不存在或已被删除", "This group chat no longer exists"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        // Member chips: tap to address a bot.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            members.forEach { m ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(MibotTokens.card()).border(1.dp, MibotTokens.hairline(), RoundedCornerShape(50))
                        .clickable { input = (input.trimEnd() + " @" + m.name + " ").trimStart() }
                        .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InitialAvatar(m.name, m.id, 20.dp)
                    Text("@" + m.name, fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) item {
                Text(
                    t("发一条消息开始吧。用 @名字 点名某个 Bot，不点名时会自动挑选最相关的 Bot 回答。", "Send a message to start. Use @Name to address a bot; otherwise the most relevant bots answer."),
                    fontSize = MibotType.Secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(messages, key = { it.id }) { m -> MessageBubble(m, members) }
            item {
                val note = notes[groupId]
                when {
                    busy -> Text(t("Bot 正在回复…", "Bots are replying…"), fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                    note != null -> Text(note, fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            }
        }
        // Input row.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(MibotTokens.card())
                    .border(1.dp, MibotTokens.hairline(), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (input.isEmpty()) Text(t("发消息，@ 点名 Bot", "Message — @ to address a bot"), fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MibotType.Body),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val canSend = !busy && input.isNotBlank() && members.isNotEmpty()
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (busy || canSend) MaterialTheme.colorScheme.primary else MibotTokens.card())
                    .clickable(enabled = busy || canSend) {
                        if (busy) GroupChats.stop(groupId) else { GroupChats.send(ctx, groupId, input); input = "" }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) Icon(Icons.Outlined.StopCircle, contentDescription = t("停止", "Stop"), tint = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = t("发送", "Send"), tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("删除群聊？", "Delete group chat?")) },
            text = { Text(t("聊天记录会一起删除，Bot 本身不受影响。", "Its messages are deleted too; the bots themselves are kept.")) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; GroupChats.delete(ctx, groupId); onBack() }) {
                    Text(t("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("取消", "Cancel")) } },
        )
    }
}

@Composable
private fun MessageBubble(m: GroupMessage, members: List<GroupBot>) {
    val mine = m.role == GroupMessage.ROLE_USER
    val name = if (mine) "" else members.firstOrNull { it.id == m.botId }?.name ?: m.botName ?: "Bot"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            InitialAvatar(name, m.botId ?: name, 30.dp)
            Spacer(Modifier.size(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (!mine) Text(name, fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            val bg = when {
                mine -> MaterialTheme.colorScheme.primary
                m.error -> MaterialTheme.colorScheme.errorContainer
                else -> MibotTokens.card()
            }
            val fg = when {
                mine -> MaterialTheme.colorScheme.onPrimary
                m.error -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
            Box(
                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                    .then(if (!mine && !m.error) Modifier.border(1.dp, MibotTokens.hairline(), RoundedCornerShape(16.dp)) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    if (m.error) t("回复失败：", "Reply failed: ") + m.text else m.text,
                    fontSize = MibotType.Body,
                    lineHeight = MibotType.BodyLine,
                    color = fg,
                )
            }
        }
    }
}
