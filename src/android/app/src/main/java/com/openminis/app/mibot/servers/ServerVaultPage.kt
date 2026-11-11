package com.openminis.app.mibot.servers

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.t
import com.openminis.app.ui.glass.LocalGlassBottomInset
import org.json.JSONArray
import org.json.JSONObject

/**
 * [destiny/v7] 远程接入 · 服务器 (design screens 45b / 45c).
 *
 * The list is really persisted in SharedPreferences — add, edit and delete
 * survive a restart. Nothing here pretends to measure CPU/RAM: that is the
 * Agent's job. 「让 Agent 巡检」 hands the saved hosts to a real chat draft so
 * read-only checks run through the app's existing tool chain, and any write
 * still needs your confirmation.
 */
data class ServerEntry(
    val id: String,
    val name: String,
    val address: String,
    val note: String,
)

private const val PREF = "destiny_servers"
private const val KEY = "list"

private fun encode(list: List<ServerEntry>): String {
    val a = JSONArray()
    list.forEach {
        a.put(
            JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("address", it.address)
                .put("note", it.note),
        )
    }
    return a.toString()
}

private fun decode(raw: String): List<ServerEntry> = try {
    val a = JSONArray(raw)
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        ServerEntry(
            o.optString("id"),
            o.optString("name"),
            o.optString("address"),
            o.optString("note"),
        )
    }
} catch (e: Exception) {
    emptyList()
}

object ServerVaultStore {
    fun load(ctx: Context): List<ServerEntry> = decode(
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]",
    )

    fun save(ctx: Context, list: List<ServerEntry>) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, encode(list)).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerVaultPage(
    onBack: () -> Unit,
    onDiscuss: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val bottom = LocalGlassBottomInset.current
    var servers by remember { mutableStateOf(ServerVaultStore.load(ctx)) }
    var editing by remember { mutableStateOf<ServerEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(t("服务器", "Servers"), fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
        ) {
            item {
                Text(
                    t("远程接入", "Remote access"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    t(
                        "填 Tailscale 内网地址（100.x.x.x）就行，公网一个端口都不用开。这里只存清单，不假报在线状态；巡检交给 Agent 实跑。",
                        "Use Tailscale intranet addresses (100.x.x.x) — no public port needed. This screen stores the list only; it never fabricates a live status. The Agent does the real checks.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                )
                Spacer(Modifier.height(12.dp))
                if (servers.isNotEmpty()) {
                    Button(onClick = {
                        val hosts = servers.joinToString("\n") {
                            "- ${it.name} ${it.address}"
                        }
                        onDiscuss(
                            "巡检这些服务器，只看负载、内存、磁盘、证书到期；有异常才告诉我，没异常一句话都别说。" +
                                "读操作你自己跑，写操作逐条列给我点确认，一次点一条：\n" + hosts,
                        )
                    }) {
                        Text(t("让 Agent 巡检", "Ask Agent to inspect"))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (servers.isEmpty()) {
                item {
                    Text(
                        t("还没有服务器，点右上角 + 添加。", "No servers yet — tap + above to add one."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = dim,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(servers, key = { it.id }) { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (s.address.isBlank()) {
                                    t("未填内网地址", "No address yet")
                                } else {
                                    s.address
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = dim,
                            )
                            if (s.note.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(s.note, style = MaterialTheme.typography.bodySmall, color = dim)
                            }
                        }
                        IconButton(onClick = {
                            val next = servers.filter { it.id != s.id }
                            servers = next
                            ServerVaultStore.save(ctx, next)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = dim)
                        }
                    }
                    Text(
                        t("编辑", "Edit"),
                        fontSize = 12.sp,
                        color = accent,
                        modifier = Modifier.clickable {
                            editing = s
                            showEditor = true
                        },
                    )
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

    if (showEditor) {
        ServerEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { entry ->
                val next = servers.filter { it.id != entry.id } + entry
                servers = next
                ServerVaultStore.save(ctx, next)
                showEditor = false
            },
        )
    }
}

@Composable
private fun ServerEditorDialog(
    initial: ServerEntry?,
    onDismiss: () -> Unit,
    onSave: (ServerEntry) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) t("添加服务器", "Add server") else t("编辑服务器", "Edit server"),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("名称", "Name")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(t("Tailscale 地址", "Tailscale address")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(t("备注", "Note")) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            ServerEntry(
                                initial?.id ?: java.util.UUID.randomUUID().toString(),
                                name.trim(),
                                address.trim(),
                                note.trim(),
                            ),
                        )
                    }
                },
            ) { Text(t("保存", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("取消", "Cancel")) }
        },
    )
}
