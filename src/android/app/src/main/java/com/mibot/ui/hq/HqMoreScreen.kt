package com.mibot.ui.hq

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mibot.ui.theme.MikaIcons

/** More 屏菜单项：彩色图标块 + 名称。 */
data class HqMoreEntry(val id: String, val name: String, val icon: ImageVector, val tint: Color)

/** More 屏：Gateway 信息卡 + 分组菜单。 */
@Composable
fun HqMoreScreen(
    onEntry: (HqMoreEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Text("更多", style = HqType.LargeTitle, color = Hq.ink, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .clip(HqShape.Capsule)
                    .background(Hq.surface)
                    .padding(10.dp),
            ) { Icon(MikaIcons.Search, null, tint = Hq.ink, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(16.dp))

        // Gateway 卡
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HqSpace.Page)
                .clip(HqShape.Card)
                .background(Hq.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) { Icon(MikaIcons.Spark, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mac mini", style = HqType.RowTitle, color = Hq.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(Hq.ok))
                    Spacer(Modifier.width(6.dp))
                    Text("已连接 · 9 个机器人 · Hermes 0.21.5", style = HqType.Caption, color = Hq.ink2)
                }
            }
            Icon(MikaIcons.Forward, null, tint = Hq.ink2, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(24.dp))

        HqMenuGroup("系统", listOf(
            HqMoreEntry("bots", "机器人", MikaIcons.Spark, Hq.accent),
            HqMoreEntry("settings", "设置", MikaIcons.Gear, Hq.ink2),
            HqMoreEntry("models", "模型", MikaIcons.Star, Hq.accent),
            HqMoreEntry("groups", "模型组", MikaIcons.Apps, Hq.accent),
            HqMoreEntry("vault", "账户金库", MikaIcons.Shield, Color(0xFFAF52DE)),
            HqMoreEntry("preview", "UI 预览", MikaIcons.Edit, Hq.accent),
            HqMoreEntry("dynamics", "动态", MikaIcons.Spark, Color(0xFF32D74B)),
        ), onEntry)
        HqMenuGroup("消息与数据", listOf(
            HqMoreEntry("groupchats", "群聊", MikaIcons.Route, Color(0xFF40C8E0)),
            HqMoreEntry("voice", "语音模型", MikaIcons.Message, Color(0xFF32D74B)),
            HqMoreEntry("files", "文件库", MikaIcons.Folder, Hq.accent),
            HqMoreEntry("memory", "记忆", MikaIcons.Spark, Color(0xFFBF5AF2)),
            HqMoreEntry("usage", "用量", MikaIcons.Chart, Hq.ok),
        ), onEntry)
        HqMenuGroup("工作与智能", listOf(
            HqMoreEntry("calls", "通话", MikaIcons.Message, Hq.ok),
            HqMoreEntry("plugins", "插件中心", MikaIcons.Apps, Color(0xFFBF5AF2)),
        ), onEntry)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun HqMenuGroup(
    title: String,
    entries: List<HqMoreEntry>,
    onEntry: (HqMoreEntry) -> Unit,
) {
    Column(Modifier.padding(bottom = 12.dp)) {
        if (title.isNotEmpty()) {
            Text(
                title,
                style = HqType.Section,
                color = Hq.ink2,
                modifier = Modifier.padding(horizontal = HqSpace.Page + 4.dp, vertical = 6.dp),
            )
        }
        entries.forEach { e ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HqSpace.Page, vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onEntry(e) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(e.tint.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(e.icon, null, tint = e.tint, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(e.name, style = HqType.Body, color = Hq.ink, modifier = Modifier.weight(1f))
                Icon(MikaIcons.Forward, null, tint = Hq.ink2.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            }
        }
    }
}
