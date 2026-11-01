package com.openminis.app.mibot.vault

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotFilterChips
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotSpacer
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.design.tc
import kotlinx.coroutines.launch

object VaultRoutes {
    const val LIST = "mibot_vault"
    const val EDIT = "mibot_vault_edit/{id}"
    const val AUDIT = "mibot_vault_audit"
    const val DISCOVER = "mibot_vault_discover"
    const val SERVICE = "mibot_vault_service/{sid}"
    fun edit(id: String) = "mibot_vault_edit/$id"
    fun service(sid: String) = "mibot_vault_service/" + android.net.Uri.encode(sid)
}

internal fun categoryIcon(cat: String): ImageVector = when (cat) {
    "AI 服务" -> Icons.Outlined.SmartToy
    "邮箱" -> Icons.Outlined.Email
    "云服务器" -> Icons.Outlined.Cloud
    "社交" -> Icons.Outlined.People
    else -> Icons.Outlined.Key
}

internal fun categoryColor(cat: String) = when (cat) {
    "AI 服务" -> MibotTokens.Brick
    "邮箱" -> MibotTokens.Red
    "云服务器" -> MibotTokens.Green
    "社交" -> MibotTokens.Purple
    else -> MibotTokens.Steel
}

@Composable
internal fun permLabel(p: VaultPermission): Pair<String, ChipKind> = when (p) {
    VaultPermission.AUTO -> t("自动使用", "Auto") to ChipKind.OK
    VaultPermission.ASK -> t("每次询问", "Ask") to ChipKind.WARN
    VaultPermission.DISABLED -> t("禁用", "Off") to ChipKind.NEUTRAL
}

@Composable
internal fun statusLabels(): Map<String, String> = mapOf(
    "balance" to t("余额", "Balance"),
    "points" to t("积分", "Points"),
    "credits" to t("额度", "Credits"),
    "quota" to t("剩余", "Quota"),
    "expiry" to t("到期", "Expires"),
)

internal fun ago(ts: Long): String =
    if (ts <= 0) "" else DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
fun VaultEditScreen(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(id) { if (id == "new") null else VaultStore.get(ctx, id) }
    var site by remember { mutableStateOf(existing?.site ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "AI 服务") }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var permission by remember { mutableStateOf(existing?.permission ?: VaultPermission.ASK) }
    var manifestId by remember { mutableStateOf(existing?.manifestId) }
    var confirmDelete by remember { mutableStateOf(false) }
    val manifests = remember { VaultSites.all(ctx) }
    val live by remember { VaultStore.entries(ctx) }.collectAsState()
    val current = live.find { it.id == existing?.id }

    fun save(): VaultEntry? {
        if (site.isBlank()) {
            Toast.makeText(ctx, tc(ctx, "请填写网站或服务", "Site is required"), Toast.LENGTH_SHORT).show()
            return null
        }
        val base = existing ?: VaultEntry(id = VaultStore.newId(), site = "", username = "")
        return VaultStore.upsert(
            ctx,
            base.copy(site = site.trim(), username = username.trim(), category = category.trim().ifEmpty { "其他" },
                notes = notes, permission = permission, manifestId = manifestId),
            newSecret = secret.ifEmpty { null },
        ).also { VaultStore.log(ctx, it, "user", if (existing == null) "create" else "edit", "ok") }
    }

    MibotScaffold(title = if (existing == null) t("添加账号", "Add account") else t("编辑账号", "Edit account"), onBack = onBack) {
        MibotSectionHeader(t("账号", "Account"))
        MibotCard(padding = PaddingValues(14.dp)) {
            com.openminis.app.mibot.design.GlassField(site, { site = it }, t("网站 / 服务", "Site / service"), placeholder = "openrouter.ai", keyboardType = KeyboardType.Uri)
            Spacer(Modifier.size(10.dp))
            com.openminis.app.mibot.design.GlassField(username, { username = it }, t("用户名 / 邮箱 / 手机号", "Username / email / phone"), autofill = "username", keyboardType = KeyboardType.Email)
            Spacer(Modifier.size(10.dp))
            com.openminis.app.mibot.design.GlassField(
                secret, { secret = it }, t("密码 / Token / Cookie", "Password / token / cookie"),
                placeholder = if (existing?.hasSecret == true) t("已保存，留空则不修改", "Saved — leave blank to keep") else null,
                secret = true, autofill = "password",
            )
            Spacer(Modifier.size(10.dp))
            com.openminis.app.mibot.design.GlassField(notes, { notes = it }, t("备注", "Notes"), singleLine = false, minLines = 2)
        }
        MibotFootnote(t("可用系统自动填充（密码管理器）直接填入账号和密码。", "Your Autofill / password manager can fill the username and password."))

        MibotSectionHeader(t("分类", "Category"))
        MibotFilterChips(VaultCategories.defaults, category) { category = it }
        Spacer(Modifier.size(6.dp))
        Column(Modifier.padding(horizontal = 14.dp)) {
            com.openminis.app.mibot.design.GlassField(category, { category = it }, t("自定义分类", "Custom category"))
        }

        MibotSectionHeader(t("AI 使用权限", "AI permission"))
        MibotCard {
            VaultPermission.entries.forEachIndexed { i, p ->
                val (title, desc) = when (p) {
                    VaultPermission.AUTO -> t("自动使用", "Use automatically") to t("AI 可直接使用（高风险操作仍需确认）", "AI may use it directly (high-risk actions still ask)")
                    VaultPermission.ASK -> t("每次询问", "Ask every time") to t("每次使用前弹窗确认", "Confirm before each use")
                    VaultPermission.DISABLED -> t("禁用", "Disabled") to t("AI 不能使用此账号", "The AI cannot use this account")
                }
                com.openminis.app.mibot.design.MibotRadioRow(title, desc, selected = permission == p, onClick = { permission = p }, showDivider = i < 2)
            }
        }

        MibotSectionHeader(t("状态与签到", "Status & check-in"))
        MibotFilterChips(
            listOf(t("自动匹配", "Auto-match")) + manifests.map { it.id },
            manifestId ?: t("自动匹配", "Auto-match"),
        ) { manifestId = if (manifests.any { m -> m.id == it }) it else null }
        val labels = statusLabels()
        current?.status?.let { st ->
            MibotCard(padding = PaddingValues(16.dp)) {
                st.summary(labels).forEach { (k, v) -> Text("$k  $v", fontSize = com.openminis.app.mibot.design.MibotType.Body, color = MaterialTheme.colorScheme.onSurface) }
                if (st.updatedAt > 0) Text(t("更新于 ", "Updated ") + ago(st.updatedAt), fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                st.error?.let { Text(it, fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.error) }
                st.lastCheckinMessage?.let { Text(t("签到：", "Check-in: ") + it + " · " + ago(st.lastCheckinAt), fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            MibotSpacer(8.dp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MibotButton(t("刷新状态", "Refresh"), tonal = true, modifier = Modifier.weight(1f), onClick = {
                val e = save() ?: return@MibotButton
                scope.launch {
                    val r = VaultSites.refreshStatus(ctx, e, "user")
                    Toast.makeText(ctx, r, Toast.LENGTH_SHORT).show()
                }
            })
            MibotButton(t("签到", "Check in"), tonal = true, modifier = Modifier.weight(1f), onClick = {
                val e = save() ?: return@MibotButton
                scope.launch {
                    val r = VaultSites.checkin(ctx, e, "user")
                    Toast.makeText(ctx, r.message, Toast.LENGTH_SHORT).show()
                    if (r.state == "manual" && r.url != null) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            })
        }
        MibotFootnote(t(
            "状态/签到由站点清单定义（HTTP 接口 + JSON 路径，或网页手动签到）。AI 可用 vault site add 添加新站点。遇到验证码会标记为需要手动，绝不自动破解。",
            "Status/check-in come from site manifests (HTTP endpoint + JSON path, or manual web check-in). The AI can add sites with `vault site add`. Captchas are marked manual and never solved automatically.",
        ))

        MibotSpacer(20.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing != null) {
                MibotButton(if (confirmDelete) t("确认删除", "Confirm delete") else t("删除", "Delete"), tonal = true, modifier = Modifier.weight(1f), onClick = {
                    if (!confirmDelete) { confirmDelete = true; return@MibotButton }
                    VaultStore.log(ctx, existing, "user", "delete", "ok")
                    VaultStore.delete(ctx, existing.id)
                    onBack()
                })
            }
            MibotButton(t("保存", "Save"), modifier = Modifier.weight(1f), onClick = { if (save() != null) onBack() })
        }
    }
}

@Composable
fun VaultAuditScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val events by remember { VaultStore.audit(ctx) }.collectAsState()
    MibotScaffold(title = t("使用记录", "Audit log"), onBack = onBack) {
        if (events.isEmpty()) {
            MibotSpacer(20.dp)
            MibotFootnote(t("还没有使用记录。", "Nothing logged yet."))
        } else {
            MibotSpacer(8.dp)
            MibotCard {
                events.take(200).forEachIndexed { i, ev ->
                    val kind = when (ev.result) {
                        "allowed", "ok" -> ChipKind.OK
                        "denied", "disabled" -> ChipKind.WARN
                        "error" -> ChipKind.ERR
                        else -> ChipKind.NEUTRAL
                    }
                    MibotRow(
                        title = ev.label,
                        subtitle = "${ev.actor} · ${ev.action}\n${ago(ev.at)}",
                        showDivider = i < events.size - 1,
                        chevron = false,
                        trailing = { StatusChip(ev.result, kind) },
                    )
                }
            }
        }
    }
}

