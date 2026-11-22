package com.mibot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.openminis.app.R
import com.mibot.settings.ModelGroupMember
import com.mibot.settings.ModelGroupStore
import com.mibot.settings.ProviderStore
import com.mibot.ui.components.FooterNote
import com.mibot.ui.components.GroupCard
import com.mibot.ui.components.GroupLabel
import com.mibot.ui.components.ListRow
import com.mibot.ui.theme.MikaIcons
import com.mibot.ui.theme.MikaTheme
import com.mibot.ui.theme.MikaType

/** 模型组页:模型分组管理(组名 + 成员),Mibot 模型组款。点组进成员管理,可加/删模型。 */
@Composable
fun ModelGroupsScreen(
    store: ModelGroupStore,
    onBack: () -> Unit,
    providers: ProviderStore? = null,
    onOpenGroup: (String) -> Unit = {},
) {
    val groups by store.groups.collectAsState()
    val c = MikaTheme.colors
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }

    val editing = groups.firstOrNull { it.id == editingId }
    if (editing != null) {
        GroupMembersView(
            store = store,
            providers = providers,
            group = editing,
            onBack = { editingId = null },
        )
        return
    }

    Page(stringResource(R.string.models_groups_title), onBack) {
        if (groups.isEmpty() && !adding) {
            FooterNote(stringResource(R.string.models_groups_empty))
        } else {
            GroupLabel(stringResource(R.string.models_groups_label))
            GroupCard {
                groups.forEachIndexed { i, g ->
                    ListRow(
                        icon = MikaIcons.Apps,
                        title = g.name,
                        subtitle = stringResource(R.string.models_groups_member_count, g.members.size),
                        first = i == 0,
                        onClick = { providers?.let { editingId = g.id } ?: onOpenGroup(g.id) },
                        trailing = {
                            TextButton(onClick = { store.remove(g.id) }) {
                                Text(stringResource(R.string.models_groups_delete), color = c.bad)
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (adding) {
            GroupCard {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.models_groups_name_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) { store.add(name); name = ""; adding = false }
                        },
                    ) { Text(stringResource(R.string.models_groups_create)) }
                    TextButton(onClick = { adding = false; name = "" }) {
                        Text(stringResource(R.string.models_groups_cancel), color = c.ink3)
                    }
                }
            }
        } else {
            GroupCard {
                ListRow(
                    icon = MikaIcons.Plus,
                    title = stringResource(R.string.models_groups_add),
                    first = true,
                    onClick = { adding = true },
                )
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

/** 成员管理:看组里有哪些模型、加(从各服务商模型列表选)、删。 */
@Composable
private fun GroupMembersView(
    store: ModelGroupStore,
    providers: ProviderStore?,
    group: com.mibot.settings.ModelGroup,
    onBack: () -> Unit,
) {
    val c = MikaTheme.colors
    var showPicker by remember { mutableStateOf(false) }

    Page("成员管理 · ${group.name}", onBack) {
        GroupLabel("组内模型")
        GroupCard {
            // 组内自动切换策略（Mibot 那套：fallback 按序 / loadBalance 轮询）
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动切换", style = MikaType.Body)
                    Text(if (group.strategy == "loadBalance") "轮询：每次调用轮流用组内模型" else "按序：失败才换下一个",
                        style = MikaType.Caption, color = c.ink2)
                }
                TextButton(onClick = { store.setStrategy(group.id, "fallback") }) {
                    Text("按序", color = if (group.strategy == "fallback") c.accent else c.ink3)
                }
                TextButton(onClick = { store.setStrategy(group.id, "loadBalance") }) {
                    Text("轮询", color = if (group.strategy == "loadBalance") c.accent else c.ink3)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GroupLabel("组内模型")
        GroupCard {
            if (group.members.isEmpty()) {
                FooterNote("还没有成员。点下面添加,从已连接的服务商模型里选。")
            } else {
                group.members.forEachIndexed { i, m ->
                    val pName = providers?.provider(m.providerId)?.name ?: m.providerId
                    val mName = providers?.provider(m.providerId)
                        ?.models?.firstOrNull { it.id == m.modelId }?.displayName?.ifBlank { null } ?: m.modelId
                    ListRow(
                        icon = MikaIcons.ListChecks,
                        title = mName,
                        subtitle = pName,
                        first = i == 0,
                        trailing = {
                            TextButton(onClick = {
                                store.setMembers(group.id, group.members.filterNot { it == m })
                            }) { Text("移除", color = c.bad) }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GroupCard {
            ListRow(
                icon = MikaIcons.Plus,
                title = "添加模型",
                first = true,
                onClick = { showPicker = true },
            )
        }
        Spacer(Modifier.height(80.dp))
    }

    if (showPicker) {
        val allModels = providers?.providers?.collectAsState()?.value.orEmpty().flatMap { p ->
            (p.models ?: emptyList()).map { m -> Triple(p, m.id, m.displayName.ifBlank { m.id }) }
        }
        val available = allModels.filter { (p, mid, _) ->
            group.members.none { it.providerId == p.id && it.modelId == mid }
        }
        var picked by remember { mutableStateOf(setOf<Pair<String, String>>()) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    if (picked.isNotEmpty()) {
                        val add = picked.map { (pid, mid) -> ModelGroupMember(pid, mid) }
                        store.setMembers(group.id, group.members + add)
                    }
                    showPicker = false
                }) {
                    Text(if (picked.isEmpty()) stringResource(R.string.models_groups_cancel)
                         else stringResource(R.string.models_groups_add_selected, picked.size), color = c.accent)
                }
            },
            title = { Text(stringResource(R.string.models_groups_add)) },
            text = {
                if (available.isEmpty()) {
                    Text(stringResource(R.string.models_groups_no_models))
                } else {
                    Column(Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
                        available.forEach { (p, mid, mName) ->
                            val key = p.id to mid
                            val on = picked.contains(key)
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { picked = if (on) picked - key else picked + key }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { picked = if (on) picked - key else picked + key },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(mName, style = MikaType.Body)
                                    Text(p.name, style = MikaType.Caption, color = c.ink2)
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}
