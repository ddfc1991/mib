package com.openminis.app.opencode

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.theme.AppTokens
import kotlinx.coroutines.launch

/**
 * OpenCode 页面配色 —— 直接读取全 App 唯一视觉真源。
 * 旧常量名保留，页面代码不用改；明暗与皮肤强调色自动跟随全局设置。
 */
object OcTheme {
    private val p: com.openminis.app.ui.theme.OmegaPalette
        @Composable
        @ReadOnlyComposable
        get() = AppTokens.current

    val bg: Color
        @Composable @ReadOnlyComposable get() = p.bg
    val surface: Color
        @Composable @ReadOnlyComposable get() = p.surface
    val surface2: Color
        @Composable @ReadOnlyComposable get() = p.surface2
    val ink: Color
        @Composable @ReadOnlyComposable get() = p.ink
    val ink2: Color
        @Composable @ReadOnlyComposable get() = p.ink2
    val ink3: Color
        @Composable @ReadOnlyComposable get() = p.ink3
    val accent: Color
        @Composable @ReadOnlyComposable get() = p.accent
    val danger: Color
        @Composable @ReadOnlyComposable get() = p.danger
    val border: Color
        @Composable @ReadOnlyComposable get() = p.border
}

/**
 * 配对页：粘贴 pair 链接（或手动填地址+token），连接电脑上的 OpenCode。
 */
@Composable
fun OpenCodePairScreen(
    onConnected: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().background(OcTheme.bg)) {
        com.openminis.app.ui.theme.AppTopBar(title = "连接 OpenCode", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
        Spacer(Modifier.height(24.dp))

        Text("在电脑上运行：", color = OcTheme.ink2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().background(OcTheme.surface2, RoundedCornerShape(10.dp)).padding(16.dp)
        ) {
            Text(
                "opencode pair --remote",
                color = OcTheme.accent,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "把打印出来的一次性链接粘贴到下面（5 分钟内有效）。手机和电脑在同一 Wi-Fi 时，也可用本地链接。",
            color = OcTheme.ink3,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            label = { Text("配对链接", color = OcTheme.ink2) },
            placeholder = { Text("https://xxx.opentunnel.xyz/auth/connect/...", color = OcTheme.ink3) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OcTheme.ink,
                unfocusedTextColor = OcTheme.ink,
                focusedBorderColor = OcTheme.accent,
                unfocusedBorderColor = OcTheme.border,
                cursorColor = OcTheme.accent,
            ),
            minLines = 3,
        )
        Spacer(Modifier.height(8.dp))
        if (error != null) {
            Text(error!!, color = OcTheme.danger, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                val link = OpenCodeApi.parsePairLink(input)
                if (link == null) {
                    error = "链接格式不对，应包含 /auth/connect/..."
                } else {
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            val token = OpenCodeApi.pair(link.baseUrl, link.code)
                            OpenCodeStore.save(context, link.baseUrl, token)
                        }.onSuccess { onConnected() }
                            .onFailure { error = it.message ?: "连接失败" }
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !busy && input.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = OcTheme.accent, contentColor = OcTheme.bg),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = OcTheme.bg, strokeWidth = 2.dp)
            else Text("连接", fontWeight = FontWeight.SemiBold)
        }
        }
    }
}

/**
 * 会话列表页：显示电脑上的 OpenCode 会话，可新建、进入聊天。
 */
@Composable
fun OpenCodeSessionListScreen(
    onOpenSession: (String) -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sessions by remember { mutableStateOf<List<OcSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val baseUrl = OpenCodeStore.baseUrl(context)
    val token = OpenCodeStore.token(context)

    fun refresh() {
        loading = true; error = null
        scope.launch {
            runCatching { OpenCodeApi.listSessions(baseUrl, token) }
                .onSuccess { sessions = it.sortedByDescending { s -> s.updatedAt } }
                .onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = modifier.fillMaxSize().background(OcTheme.bg)) {
        com.openminis.app.ui.theme.AppTopBar(
            title = "OpenCode",
            subtitle = baseUrl,
            onBack = onBack,
            actions = {
                Text(
                    "断开",
                    color = OcTheme.ink2,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onDisconnect() }.padding(8.dp),
                )
            },
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = OcTheme.accent)
            }
            error != null -> Column(
                Modifier.fillMaxSize(),
                Arrangement.Center,
                Alignment.CenterHorizontally,
            ) {
                Text(error!!, color = OcTheme.danger, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { refresh() }, colors = ButtonDefaults.buttonColors(containerColor = OcTheme.surface2)) {
                    Text("重试", color = OcTheme.ink)
                }
            }
            sessions.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("电脑上没有会话", color = OcTheme.ink2, fontSize = 13.sp)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(sessions, key = { it.id }) { s ->
                    SessionRow(s, onClick = { onOpenSession(s.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: OcSession, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(OcTheme.surface, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp).background(
                when (s.outcome) {
                    "failed" -> OcTheme.danger
                    null -> OcTheme.accent
                    else -> OcTheme.ink3
                },
                RoundedCornerShape(4.dp),
            )
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = OcTheme.ink, fontSize = 14.sp, maxLines = 1)
            Text(
                listOfNotNull(s.agent, s.modelId, s.directory?.substringAfterLast('\\')).joinToString(" · "),
                color = OcTheme.ink3,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}
