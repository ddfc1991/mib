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
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
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

/** Feed: one clean single-column card stream (Threads / Jike style), no category filters. */
@Composable
fun GlassFeedPage() {
    val bottom = LocalGlassBottomInset.current
    // One unified stream, curated and ordered by Minis: no categories/filters.
    val posts = listOf(R.string.glass_feed_post1, R.string.glass_feed_post2, R.string.glass_feed_post3)
    val appName = stringResource(R.string.app_name)
    val accent = MaterialTheme.colorScheme.primary
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeader(stringResource(R.string.glass_tab_feed)) }
        item {
            Text(
                stringResource(R.string.glass_feed_desc),
                fontSize = com.openminis.app.mibot.design.MibotType.Secondary,
                lineHeight = com.openminis.app.mibot.design.MibotType.SecondaryLine,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        items(posts) { body ->
            // [fork/mibot-v4] Same frosted card + compact type as every Mibot page.
            com.openminis.app.mibot.design.MibotCard(padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Initial(appName, accent, 30)
                    Spacer(Modifier.width(10.dp))
                    Text(appName, fontWeight = FontWeight.SemiBold, fontSize = com.openminis.app.mibot.design.MibotType.Body, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(body),
                    fontSize = com.openminis.app.mibot.design.MibotType.Body,
                    lineHeight = com.openminis.app.mibot.design.MibotType.BodyLine,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    val tint = com.openminis.app.mibot.design.MibotTokens.iconTint()
                    com.openminis.app.mibot.design.Glyph(com.openminis.app.mibot.design.LineIcons.Heart, size = com.openminis.app.mibot.design.MibotIcon.Dense, tint = tint)
                    com.openminis.app.mibot.design.Glyph(com.openminis.app.mibot.design.LineIcons.MessageCircle, size = com.openminis.app.mibot.design.MibotIcon.Dense, tint = tint)
                    com.openminis.app.mibot.design.Glyph(com.openminis.app.mibot.design.LineIcons.Trash2, size = com.openminis.app.mibot.design.MibotIcon.Dense, tint = tint)
                }
            }
        }
    }
}

/** Ideas: sticky-note style staggered grid; the canvas comes later. */
@Composable
fun GlassIdeasPage() {
    val bottom = LocalGlassBottomInset.current
    val notes = listOf(
        R.string.glass_idea1 to Color(0xFFFFF4C2),
        R.string.glass_idea2 to Color(0xFFE3ECFF),
        R.string.glass_idea3 to Color(0xFFDDF5EA),
        R.string.glass_idea4 to Color(0xFFFFE3EC),
    )
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column {
                PageHeader(stringResource(R.string.glass_tab_ideas), horizontal = 4)
                Text(
                    stringResource(R.string.glass_ideas_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }
        }
        items(notes) { (text, color) ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.92f)),
            ) {
                Text(
                    stringResource(text),
                    color = Color(0xFF22252B),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

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
