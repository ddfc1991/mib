package com.openminis.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.R

/*
 * [fork/glass-ui] First-round pages for the Feed / Ideas / Bots tabs. The
 * layouts are the intended ones; the content is clearly-labelled sample data
 * until feat/feed and feat/bots land. All draw beneath the glass tab bar.
 */

@Composable
private fun PageHeader(title: String, horizontal: Int = 20) {
    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = horizontal.dp, end = horizontal.dp, top = 20.dp, bottom = 8.dp)) {
        Text(title, fontSize = com.openminis.app.mibot.design.MibotType.PageHeader, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.glass_sample_notice),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun cardColors() = CardDefaults.cardColors(
    containerColor = FrostTokens.flat(frostIsDark()), // [fork/frost] was surface@0.78 flat
    // Translucent containers defeat contentColorFor(); set it explicitly.
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun Initial(text: String, color: Color, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.take(1), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size * 0.42f).sp)
    }
}

// [fork/mibot-feed] GlassFeedPage / GlassIdeasPage (sample mocks) were
// replaced by the real mibot.feed.FeedPage and mibot.ideas.IdeasPage.

/** Bots: roster list with a create entry. */
@Composable
fun GlassBotsPage() {
    val bottom = LocalGlassBottomInset.current
    val bots = listOf(
        Triple("Planner", R.string.glass_bot1_desc, Color(0xFF7C8CF8)),
        Triple("Critic", R.string.glass_bot2_desc, Color(0xFFF59E7A)),
        Triple("Writer", R.string.glass_bot3_desc, Color(0xFF5CC8A8)),
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeader(stringResource(R.string.glass_tab_bots)) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    stringResource(R.string.glass_bots_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = {}, enabled = false) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(com.openminis.app.mibot.design.MibotIcon.Dense))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.glass_create_bot))
                }
            }
        }
        items(bots) { (name, desc, color) ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = cardColors(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Initial(name, color, 44)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold, fontSize = com.openminis.app.mibot.design.MibotType.TitleSmall)
                        Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.glass_coming_soon)) })
                }
            }
        }
    }
}
