package com.mibot.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mibot.ui.components.ListRow
import com.mibot.ui.components.RowChevron
import com.mibot.ui.theme.MikaIcons
import com.mibot.ui.theme.MikaTheme
import com.mibot.ui.theme.MikaType
import com.mibot.vault.VaultEntry
import com.mibot.vault.VaultPermission
import com.mibot.vault.VaultStore

/** 账户金库：账号台账，密钥只存加密（Android Keystore AES-GCM）。 */
@Composable
fun VaultScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { VaultStore.seedAccounts(ctx) }
    val entries by VaultStore.entries(ctx).collectAsState()
    val c = MikaTheme.colors

    /** null = 未在编辑；VaultEntry(空 id)=新增。 */
    var editing by remember { mutableStateOf<VaultEntry?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // 标题栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MikaIcons.Back, contentDescription = "返回", tint = c.ink)
            }
            Spacer(Modifier.width(8.dp))
            Text("账户金库", style = MikaType.Title, color = c.ink)
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有账号", style = MikaType.Body, color = c.ink.copy(alpha = 0.6f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点下面「添加账号」存第一个；密钥只存设备加密区",
                        style = MikaType.Caption, color = c.ink.copy(alpha = 0.45f),
                    )
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.id }) { e ->
                    VaultRow(e) { editing = e }
                }
            }
        }

        // 底部添加
        Row(
            Modifier.fillMaxWidth().padding(16.dp).clickable {
                editing = VaultEntry(id = VaultStore.newId(), site = "", username = "")
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("＋ 添加账号", color = c.ink, style = MikaType.Body)
        }
    }

    editing?.let { entry ->
        VaultEditorDialog(
            entry = entry,
            isNew = entries.none { it.id == entry.id },
            onDismiss = { editing = null },
            onSave = { updated, secret ->
                VaultStore.upsert(ctx, updated, secret)
                editing = null
            },
            onDelete = if (entries.any { it.id == entry.id }) {
                { VaultStore.delete(ctx, entry.id); editing = null }
            } else null,
        )
    }
}

@Composable
private fun VaultRow(e: VaultEntry, onEdit: () -> Unit) {
    val c = MikaTheme.colors
    val perm = when (e.permission) {
        VaultPermission.AUTO -> "自动"
        VaultPermission.ASK -> "询问"
        VaultPermission.DISABLED -> "禁用"
    }
    val statusText = e.status?.let { s ->
        s.credits?.let { " · $it" } ?: s.balance?.let { " · $it" } ?: s.quota?.let { " · $it" } ?: ""
    } ?: ""
    ListRow(
        icon = MikaIcons.Key,
        title = e.site,
        subtitle = "${e.username} · $perm$statusText",
        onClick = onEdit,
    ) { RowChevron() }
}

/** 新增/编辑账号表单。secret 留空=不改动已有密钥。 */
@Composable
private fun VaultEditorDialog(
    entry: VaultEntry,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (VaultEntry, String?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var site by remember { mutableStateOf(entry.site) }
    var username by remember { mutableStateOf(entry.username) }
    var secret by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(entry.category) }
    var perm by remember { mutableStateOf(entry.permission) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加账号" else "编辑账号") },
        text = {
            Column {
                OutlinedTextField(
                    value = site, onValueChange = { site = it },
                    label = { Text("站点/服务名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("用户名/账号") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret, onValueChange = { secret = it },
                    label = { Text(if (isNew) "密码/API Key" else "密码/API Key（留空不改）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    label = { Text("分类") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("权限", style = MikaType.Caption, color = MikaTheme.colors.ink.copy(alpha = 0.6f))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VaultPermission.entries.forEach { p ->
                        val sel = p == perm
                        Text(
                            when (p) {
                                VaultPermission.AUTO -> "自动"
                                VaultPermission.ASK -> "询问"
                                VaultPermission.DISABLED -> "禁用"
                            },
                            style = MikaType.Caption,
                            color = if (sel) MikaTheme.colors.ink else MikaTheme.colors.ink.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clickable { perm = p }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        entry.copy(
                            site = site.trim(),
                            username = username.trim(),
                            category = category.ifBlank { "其他" },
                            permission = perm,
                        ),
                        secret.ifBlank { null },
                    )
                },
                enabled = site.isNotBlank() && username.isNotBlank(),
            ) { Text(if (isNew) "保存" else "保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
