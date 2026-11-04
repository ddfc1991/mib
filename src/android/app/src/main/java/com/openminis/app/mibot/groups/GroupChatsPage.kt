package com.openminis.app.mibot.groups

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.data.model.SubAgentDefinition
import com.openminis.app.mibot.design.CheckMark
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.GlassField
import com.openminis.app.mibot.design.Glyph
import com.openminis.app.mibot.design.LineIcons
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotPageTitle
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.ui.glass.LocalGlassBottomInset

internal val GroupAvatarColors = listOf(MibotTokens.Brick, MibotTokens.Sky, MibotTokens.Green, MibotTokens.Purple, MibotTokens.Amber, MibotTokens.Teal)

internal fun avatarColorFor(id: String) = GroupAvatarColors[(id.hashCode() and 0x7fffffff) % GroupAvatarColors.size]

@Composable
internal fun InitialAvatar(name: String, colorKey: String, size: Dp = 32.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(avatarColorFor(colorKey)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp)
    }
}

/**
 * [fork/mibot-groups] 群聊 tab: the user's bot group chats (newest first) and
 * the new-group sheet. Groups live in filesDir/groups (see [GroupChatStore]).
 */
@Composable
fun GroupChatsPage(onOpen: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val bottom = LocalGlassBottomInset.current
    val store = remember { GroupChats.store(ctx) }
    val groups by store.groups.collectAsState()
    val running by GroupChats.running.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var rosterGen by remember { mutableIntStateOf(0) }
    val roster = remember(rosterGen, showNew) { GroupChats.roster(ctx) }
    val nameById = remember(roster) { roster.associate { it.id to it.displayName(ctx) } }
    LaunchedEffect(Unit) { store.refresh() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { MibotPageTitle(t("群聊", "Group chats"), t("让多个 Bot 在一个对话里协作", "Let several bots work together in one chat")) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                MibotButton(
                    t("新建群聊", "New group chat"),
                    onClick = { rosterGen++; showNew = true },
                    enabled = roster.size >= 2,
                    modifier = Modifier.weight(1f),
                )
            }
            if (roster.size < 2) MibotFootnote(t("至少需要 2 个 Bot 才能建群，可以在 设置 › Bots 里新建。", "You need at least 2 bots for a group — create them in Settings › Bots."))
        }
        if (groups.isEmpty()) {
            item {
                MibotCard(Modifier.padding(top = 12.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Glyph(LineIcons.Users, size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        Text(t("还没有群聊", "No group chats yet"), fontSize = MibotType.Title, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t("把几个 Bot 拉进同一个对话，用 @名字 点名谁来回答。", "Bring a few bots into one chat; use @Name to pick who answers."),
                            fontSize = MibotType.Secondary,
                            lineHeight = MibotType.SecondaryLine,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            item {
                MibotSectionHeader(t("我的群聊", "My group chats") + " · ${groups.size}")
                val replyingLabel = t("回复中", "Replying")
                val replyingChip: @Composable () -> Unit = { StatusChip(replyingLabel, ChipKind.ACCENT) }
                MibotCard {
                    groups.forEachIndexed { i, g ->
                        val names = g.memberBotIds.map { nameById[it] ?: "?" }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(16.dp))
                            InitialAvatar(g.title, g.id, 36.dp)
                            Box(Modifier.weight(1f)) {
                                MibotRow(
                                    title = g.title,
                                    subtitle = names.joinToString(" · "),
                                    onClick = { onOpen(g.id) },
                                    showDivider = i < groups.lastIndex,
                                    trailing = if (g.id in running) replyingChip else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewGroupSheet(
            roster = roster,
            onDismiss = { showNew = false },
            onCreate = { title, ids, rounds ->
                val g = store.create(title, ids)
                if (rounds != g.maxRounds) store.updateGroup(g.copy(maxRounds = rounds))
                showNew = false
                onOpen(g.id)
            },
        )
    }
}

/** Pick 2+ saved bots and a title. Reads the roster only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupSheet(roster: List<SubAgentDefinition>, onDismiss: () -> Unit, onCreate: (String, List<String>, Int) -> Unit) {
    val ctx = LocalContext.current
    val picked = remember { mutableStateListOf<String>() }
    var title by remember { mutableStateOf("") }
    var rounds by remember { mutableIntStateOf(GroupChat.DEFAULT_MAX_ROUNDS) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                t("新建群聊", "New group chat"),
                fontSize = MibotType.Title,
                fontWeight = MibotType.Semi,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Box(Modifier.padding(horizontal = 16.dp)) {
                GlassField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = t("群名", "Title"),
                    placeholder = picked.mapNotNull { id -> roster.firstOrNull { it.id == id }?.displayName(ctx) }
                        .joinToString("、").ifBlank { t("例如：产品评审小组", "e.g. Product review") },
                )
            }
            MibotSectionHeader(t("选择 Bot（至少 2 个）", "Pick bots (2 or more)") + " · ${picked.size}")
            MibotCard {
                roster.forEachIndexed { i, def ->
                    val on = def.id in picked
                    Row(
                        Modifier.fillMaxWidth().clickable { if (on) picked.remove(def.id) else picked.add(def.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InitialAvatar(def.displayName(ctx), def.id, 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(def.displayName(ctx), fontSize = MibotType.Body, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Text(def.displayDescription(ctx), fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        CheckMark(on, ringWhenOff = true)
                    }
                    if (i < roster.lastIndex) Spacer(Modifier.height(1.dp))
                }
            }
            MibotSectionHeader(t("每轮最多回复", "Max replies per turn"))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..5).forEach { n ->
                    MibotButton("$n", onClick = { rounds = n }, tonal = rounds != n, modifier = Modifier.weight(1f))
                }
            }
            MibotFootnote(t(
                "你发一条消息后，被 @ 的 Bot 先回答；没人被 @ 时按相关度挑选发言者。每轮有回复条数和 token 预算上限，随时可以停止。",
                "After you send, @-mentioned bots answer first; otherwise speakers are picked by relevance. Each turn is capped by reply count and a token budget, and you can stop any time.",
            ))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MibotButton(t("取消", "Cancel"), onClick = onDismiss, tonal = true, modifier = Modifier.weight(1f))
                MibotButton(
                    t("创建", "Create"),
                    enabled = picked.size >= 2,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val auto = picked.mapNotNull { id -> roster.firstOrNull { it.id == id }?.displayName(ctx) }.joinToString("、")
                        onCreate(title.ifBlank { auto }, picked.toList(), rounds)
                    },
                )
            }
        }
    }
}
