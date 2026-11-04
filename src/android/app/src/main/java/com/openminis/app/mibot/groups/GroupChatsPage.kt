package com.openminis.app.mibot.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.Glyph
import com.openminis.app.mibot.design.LineIcons
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotPageTitle
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.ui.glass.LocalGlassBottomInset

/**
 * [fork/mibot-ui-tabs] 群聊 tab. Group chat itself lands in the next step;
 * until then this page is an honest empty state — no sample rows, and the
 * create button is shown disabled with a "coming soon" label.
 */
@Composable
fun GroupChatsPage() {
    val bottom = LocalGlassBottomInset.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { MibotPageTitle(t("群聊", "Group chats"), t("让多个 Bot 在一个对话里协作", "Let several bots work together in one chat")) }
        item {
            MibotCard(Modifier.padding(top = 12.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Glyph(LineIcons.Users, size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        t("还没有群聊", "No group chats yet"),
                        fontSize = MibotType.Title,
                        fontWeight = MibotType.Semi,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("群聊功能正在开发中，上线后可以在这里把多个 Bot 拉进同一个对话。", "Group chat is in development. Once it ships you can bring several bots into one chat here."),
                        fontSize = MibotType.Secondary,
                        lineHeight = MibotType.SecondaryLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MibotButton(t("新建群聊", "New group chat"), onClick = {}, enabled = false)
                        StatusChip(t("即将上线", "Coming soon"), ChipKind.NEUTRAL)
                    }
                }
            }
        }
    }
}
