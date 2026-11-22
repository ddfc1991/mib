package com.mibot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mibot.ui.components.GroupCard
import com.mibot.ui.components.GroupLabel
import com.mibot.ui.components.ListRow
import com.mibot.ui.components.PageScaffold
import com.mibot.ui.components.RowChevron
import com.mibot.ui.theme.MikaIcons
import com.mibot.ui.theme.MikaTheme
import com.mibot.ui.theme.MikaType

private data class BuiltinBot(val id: String, val name: String, val desc: String)

/** 内置 Bots（占位：结构 + 名单，完整 SubAgent 逻辑后续接）。 */
private val BUILTIN_BOTS = listOf(
    BuiltinBot("assistant", "全能助手", "查资料、整理文档、处理代码任务"),
    BuiltinBot("researcher", "研究员", "多轮检索、对比、汇总结论"),
    BuiltinBot("coder", "工程师", "写代码、改 bug、跑测试"),
    BuiltinBot("writer", "写手", "文案、润色、结构化表达"),
)

@Composable
fun BotsScreen(onBack: () -> Unit, onAgentSettings: () -> Unit = {}) {
    val c = MikaTheme.colors
    PageScaffold("Bots", onBack) {
        GroupLabel("角色")
        GroupCard {
            BUILTIN_BOTS.forEachIndexed { i, b ->
                ListRow(
                    MikaIcons.Spark, b.name, b.desc,
                    first = i == 0,
                    onClick = onAgentSettings,
                ) { RowChevron() }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "完整角色逻辑（SubAgent 编排/工具）后续接入；当前为结构占位。",
            style = MikaType.Caption, color = c.ink.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
