package com.openminis.app.mibot.spec

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.glass.LocalGlassBottomInset

/**
 * [destiny/v7] All 56 design screens rendered as NATIVE Compose pages.
 *
 * This is not a WebView and not a screenshot: every row is a real, clickable
 * Compose element that hands the line to the Agent as a chat draft. The spec
 * text is data (DestinySpecScreens.kt); the whole thing is rendered here with
 * the app's own V7 material theme.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinySpecIndexPage(
    onBack: () -> Unit,
    onOpenScreen: (String) -> Unit,
    onOpenVoice: () -> Unit,
) {
    val bottom = LocalGlassBottomInset.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val groups = DESTINY_SPEC_SCREENS.groupBy { it.group }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全部页面 · 56 屏", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottom + 24.dp),
        ) {
            item {
                Text(
                    "设计规格已全部落进原生页面",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "56 屏都能点开；每一行的点按都会把这件事交给 Agent 去办，不是一张静态图。" +
                        "语音是独立真功能，走系统识别与朗读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onOpenVoice, modifier = Modifier.fillMaxWidth()) {
                    Text("打开语音 · 按住说")
                }
                Spacer(Modifier.height(16.dp))
            }
            groups.forEach { (group, screens) ->
                item {
                    Text(
                        "第 $group 组 · ${screens.size} 屏",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                }
                items(screens, key = { it.id }) { s ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenScreen(s.id) }
                            .padding(vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.id,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(s.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("${s.lines.size} 行", fontSize = 11.sp, color = dim)
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinySpecPage(
    specId: String,
    onBack: () -> Unit,
    onAction: (String) -> Unit,
) {
    val bottom = LocalGlassBottomInset.current
    val spec = specScreen(specId)
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(spec?.title ?: specId, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        if (spec == null) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("没找到这一屏：$specId", color = dim)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottom + 32.dp),
        ) {
            item {
                Text(
                    spec.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "第 ${spec.group} 组 · ${spec.lines.size} 条规格 · 点任一行让 Agent 去办",
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(28.dp).height(2.dp).background(accent))
                Spacer(Modifier.height(16.dp))
            }
            // Index-based items: spec lines may repeat ("今天", "最近"), and
            // a duplicated LazyColumn key throws at runtime.
            items(spec.lines.size) { i ->
                val line = spec.lines[i]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAction("【${spec.title}】$line\n按这一条执行；需要先确认的先列给我。")
                        }
                        .padding(vertical = 13.dp),
                ) {
                    Text(line, style = MaterialTheme.typography.bodyLarge)
                    Text("交给 Agent →", fontSize = 11.sp, color = accent)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                )
            }
        }
    }
}
