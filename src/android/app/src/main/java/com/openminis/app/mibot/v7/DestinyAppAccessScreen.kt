package com.openminis.app.mibot.v7

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── [destiny/app-access-real] 手机控制：读真机的无障碍状态，不假装有名单 ────
//
// 旧版这一页是 HTML 复刻带过来的：一行「未分类 · 169」，配 4 个写死的 App 名和
// 4 个只改本地变量的 Switch。关掉它并不会让 Agent 少碰任何 App，因为：
//   1. 那些 Switch 的状态没有落盘，退出页面就还原；
//   2. 真正会拦人的是 core 里的 AppAccessPolicy，而它默认 ALLOW；
//   3. 这个包本身没有 PhoneServices.Owner，手机控制运行时压根没接进来
//      （PhoneServices.of() 会抛），所以「开/关某个 App」在这里没有任何对象可作用。
//
// 所以这一页不再画假的名单，只做三件真的事：
//   · 真读系统设置里已开启的无障碍服务（Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES）
//   · 真读本 App 是否在无障碍列表里
//   · 如实说明这个包的手机控制运行时没接入，以及缺的是什么

private fun a11yServices(ctx: Context): List<String> =
    Settings.Secure.getString(
        ctx.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )?.split(':')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

/** 系统里开着几个无障碍服务、本 App 在不在里面——两个都是真机上的真值。 */
private data class A11ySnapshot(
    val services: List<String>,
    val oursEnabled: Boolean,
)

@Composable
fun DestinyV7AppAccessScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val snap: A11ySnapshot = remember(tick) {
        val list = a11yServices(ctx)
        A11ySnapshot(list, list.any { it.startsWith(ctx.packageName) })
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(V7.Bg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = V7.Tx)
            }
            Text(
                "手机控制",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = V7.Tx,
                modifier = Modifier.weight(1f),
            )
            V7Tag(
                if (snap.oursEnabled) "无障碍已开" else "无障碍未开",
                if (snap.oursEnabled) V7.Ok else V7.Wa,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // ── 真机状态 ──────────────────────────────────────────────
            Text("本机状态", fontSize = 12.sp, color = V7.Tx2)
            Spacer(Modifier.height(8.dp))
            v7StatusRow(
                "无障碍服务",
                if (snap.services.isEmpty()) "系统里没开任何服务"
                else "已开 ${snap.services.size} 个",
                snap.services.isNotEmpty(),
            )
            v7StatusRow(
                "本 App 的无障碍",
                if (snap.oursEnabled) "在列表里" else "不在列表里",
                snap.oursEnabled,
            )
            if (snap.services.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                snap.services.take(4).forEach { s ->
                    Text("· $s", fontSize = 11.sp, color = V7.Tx3, lineHeight = 17.sp)
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── 如实说明运行时没接 ────────────────────────────────────
            Text("这一版的运行时", fontSize = 12.sp, color = V7.Tx2)
            Spacer(Modifier.height(8.dp))
            Text(
                "这个包里没有接入手机控制运行时：没有 PhoneServices 的持有者，" +
                    "也没有可用的读屏/点击通道。所以这里不给你一个「关掉某个 App」的开关 —— " +
                    "那种开关关不掉任何东西，是假的。",
                fontSize = 12.5.sp,
                color = V7.Tx,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text("要真正能用手机控制，还差", fontSize = 12.sp, color = V7.Tx2)
            Spacer(Modifier.height(6.dp))
            listOf(
                "把手机控制运行时接进本包（PhoneServices 的持有者）",
                "在系统设置里本 App 的无障碍服务打开",
                "把「哪些 App 可操作」的名单接到 AppAccessPolicy 上，让它真的拦得住",
            ).forEach { s ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(V7.Wa))
                    Spacer(Modifier.width(9.dp))
                    Text(s, fontSize = 12.sp, color = V7.Tx2, lineHeight = 18.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── 真跳系统页 ───────────────────────────────────────────
            V7Button("打开系统无障碍设置", onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            })
            Spacer(Modifier.height(10.dp))
            V7Button("打开本应用详情", onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", ctx.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            })
            Spacer(Modifier.height(10.dp))
            Text(
                "重新读取状态",
                fontSize = 12.sp,
                color = V7.Cu,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { tick += 1 }
                    .padding(vertical = 10.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun v7StatusRow(label: String, value: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (ok) V7.Ok else V7.Tx3),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = V7.Tx, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = if (ok) V7.Ok else V7.Tx3)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(V7.Ln))
}
