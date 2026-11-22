package com.mibot.ui.hq

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mibot.ui.theme.MikaIcons

/** Bots 屏的一个 bot。 */
data class HqBot(val name: String, val group: String, val summary: String, val time: String)

/** 顶排 3 个置顶大 blob（pebble 55dp + 名称）。 */
@Composable
fun HqTopPebbles(
    bots: List<HqBot>,
    onOpen: (HqBot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = HqSpace.Page),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        bots.forEach { bot ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(55.dp)
                        .clip(HqShape.Capsule)
                        .background(Hq.pebble(bot.name))
                        .clickable { onOpen(bot) },
                ) { PebbleAvatar(bot.name, Modifier.fillMaxSize()) }
                Spacer(Modifier.height(8.dp))
                Text(bot.name, style = HqType.Caption, color = Hq.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Bots 列表屏：大标题 + 图标胶囊 + 顶排大 blob + 分组列表。 */
@Composable
fun HqBotsScreen(
    personas: List<com.mibot.settings.Persona> = emptyList(),
    onOpenBot: (HqBot) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 主 agent(本能全能) + 分出的 bot(personas 真实数据)
    val mainBot = HqBot("Miga", "主智能体", "本能全能 · 随时可分 bot 干活", "在线")
    val botList = personas.map { HqBot(it.name, "分出的 bot", it.description.ifBlank { "自定义人格" }, "就绪") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Hq.bg)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        // 标题行：Bots ⌄ + Mac mini ｜ 漏斗/搜索/编辑 胶囊
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HqSpace.Page),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("机器人", style = HqType.LargeTitle, color = Hq.ink)
                    Spacer(Modifier.width(6.dp))
                    Icon(MikaIcons.Down, null, tint = Hq.ink2, modifier = Modifier.size(14.dp))
                }
                Text("Mac mini", style = HqType.Caption, color = Hq.ink2)
            }
            // 3 图标胶囊
            Row(
                modifier = Modifier
                    .clip(HqShape.Capsule)
                    .background(Hq.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(MikaIcons.ListPlus, null, tint = Hq.ink, modifier = Modifier.size(20.dp))
                Icon(MikaIcons.Search, null, tint = Hq.ink, modifier = Modifier.size(20.dp))
                Icon(MikaIcons.Edit, null, tint = Hq.ink, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        HqTopPebbles(listOf(mainBot), onOpenBot)
        Spacer(Modifier.height(24.dp))

        // 分组列表:主智能体 + 分出的 bot
        listOf("主智能体" to listOf(mainBot), "分出的 bot" to botList).forEach { (group, items) ->
            if (items.isEmpty()) return@forEach
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HqSpace.Page, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$group · ${items.size}", style = HqType.Section, color = Hq.ink2, modifier = Modifier.weight(1f))
                Icon(MikaIcons.Down, null, tint = Hq.ink2, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(14.dp))
                Icon(MikaIcons.More, null, tint = Hq.ink2, modifier = Modifier.size(16.dp))
            }
            items.forEach { bot ->
                HqBotRow(bot, onOpenBot)
                Box(
                    Modifier
                        .padding(start = 44.dp, end = HqSpace.Page)
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(Hq.edge.copy(alpha = 0.5f)),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 列表项：blob 28dp + 标题 + 右上时间 + 摘要 2 行 + 行尾 ⋯。 */
@Composable
fun HqBotRow(bot: HqBot, onOpen: (HqBot) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(bot) }
            .padding(horizontal = HqSpace.Page, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PebbleAvatar(bot.name, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bot.name, style = HqType.RowTitle, color = Hq.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(bot.time, style = HqType.Caption, color = Hq.ink2)
            }
            Spacer(Modifier.height(2.dp))
            Text(bot.summary, style = HqType.Body, color = Hq.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(MikaIcons.More, null, tint = Hq.ink2, modifier = Modifier.size(16.dp))
    }
}
