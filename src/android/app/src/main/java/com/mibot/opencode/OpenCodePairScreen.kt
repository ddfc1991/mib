package com.mibot.opencode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.opencode.OpenCodeStore.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * [openbots/opencode] 服务器配对页：连电脑上的 `opencode serve`，列出它的会话。
 *
 * 对齐手机 OpenCode 客户端的服务器页——但走 serve 的 HTTP API（新版协议）。
 * 沙箱连不了局域网，所以这里必须跑在 app 内（真机 Android 网络）。
 */
@Composable
fun OpenCodePairScreen(
    onBack: () -> Unit,
    store: OpenCodeStore,
) {
    var servers by remember { mutableStateOf(emptyList<Server>()) }
    var current by remember { mutableStateOf<Server?>(null) }
    var check by remember { mutableStateOf<OcCheck?>(null) }
    var sessions by remember { mutableStateOf(emptyList<OcSession>()) }
    var showAdd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { servers = store.list() }

    val scope = rememberCoroutineScope()
    val surface = Color(0xFF1C1C1E)
    val edge = Color(0xFF2C2C2E)
    val ink2 = Color(0xFF8E8E93)
    val accent = Color(0xFF0A84FF)

    /** 连接一台服务器：先探活（/session），通了再拉会话列表。 */
    fun connect(server: Server) {
        scope.launch {
            busy = true
            check = null
            sessions = emptyList()
            val c = kotlinx.coroutines.withContext(Dispatchers.IO) { OpenCodeApi.check(server) }
            check = c
            if (c.ok) {
                sessions = kotlinx.coroutines.withContext(Dispatchers.IO) { OpenCodeApi.sessions(server) }
            }
            busy = false
        }
    }

    /** 删除一台服务器。 */
    fun drop(server: Server) {
        scope.launch {
            store.remove(server.url)
            servers = store.list()
            if (current?.url == server.url) { current = null; check = null }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text("OpenCode", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                current?.let { s -> connect(s) }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("继续上次的会话，或连接另一台电脑。", fontSize = 13.sp, color = ink2)
                Spacer(Modifier.height(16.dp))

                check?.takeIf { !it.ok }?.let { c ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(0.5.dp, Color(0xFF4A3B28), RoundedCornerShape(16.dp))
                            .background(Color(0xFF1A1A1A))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠", fontSize = 15.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("无法连接", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9500))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(c.reason, fontSize = 13.5.sp, color = Color(0xFFD1D1D6), lineHeight = 21.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "在电脑上运行 opencode serve（记下端口和密码），" +
                                "然后在这里填服务器的地址。",
                            fontSize = 13.sp, color = ink2, lineHeight = 20.sp,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (servers.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(surface)
                            .padding(16.dp),
                    ) {
                        Text("还没有服务器", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("点下面的「添加服务器」，填电脑上 opencode serve 的地址。", fontSize = 13.sp, color = ink2)
                    }
                }

                servers.forEach { s ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(surface)
                            .clickable {
                                current = s
                                connect(s)
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (s.password.isNotEmpty()) accent else ink2,
                            modifier = Modifier.then(
                                if (s.url == current?.url) Modifier else Modifier.background(Color.Transparent)
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                s.url,
                                fontSize = 12.sp, color = ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { drop(s) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                modifier = Modifier.width(16.dp).height(16.dp),
                                colorFilter = ColorFilter.tint(ink2),
                            )
                        }
                    }
                }

                check?.takeIf { it.ok }?.let { c ->
                    Spacer(Modifier.height(24.dp))
                    Text("会话", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("${c.sessionCount} 个会话", fontSize = 12.sp, color = ink2)
                    Spacer(Modifier.height(10.dp))
                    if (sessions.isEmpty()) {
                        Text("这台电脑上还没有会话。", fontSize = 13.sp, color = ink2)
                    }
                    sessions.forEach { s ->
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(surface)
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.width(30.dp).height(30.dp)
                                    .clip(CircleShape).background(Color(0xFF263844)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.width(16.dp).height(16.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(s.agent, s.model, s.tokens.takeIf { it > 0 }?.let { "$it tokens" }).joinToString(" · "),
                                    fontSize = 11.5.sp, color = ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(0.5.dp, edge, RoundedCornerShape(16.dp))
                        .background(surface)
                        .clickable { showAdd = true }
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = accent, modifier = Modifier.width(16.dp).height(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加服务器", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = accent)
                }
            }
        }
    }

    if (showAdd) AddServerDialog(store, scope) { name, url, password ->
        showAdd = false
        val s = Server(name.ifBlank { url }, url, password)
        current = s
        connect(s)
    }
}

@Composable
private fun AddServerDialog(store: OpenCodeStore, scope: kotlinx.coroutines.CoroutineScope, onSaved: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { /* 取消 */ },
        title = { Text("添加 OpenCode 服务器") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("比如：我的电脑") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("地址") },
                    placeholder = { Text("http://192.168.1.10:4096") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (saving || url.isBlank()) return@TextButton
                    saving = true
                    scope.launch {
                        store.upsert(name, url, password)
                        saving = false
                        onSaved(name, url, password)
                    }
                },
                enabled = !saving && url.isNotBlank(),
            ) {
                Text(if (saving) "保存中…" else "连接")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { }) { Text("取消") }
        },
    )
}