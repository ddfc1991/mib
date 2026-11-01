package com.openminis.app.mibot.providers

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.design.tc

@Composable
private fun note(p: ProviderPreset): String? = t(p.noteZh ?: p.noteEn ?: "", p.noteEn ?: p.noteZh ?: "").ifBlank { null }

/** Registry groups shown at the top of the add-provider picker. */
@Composable
fun PresetSections(onSelect: (ProviderPreset) -> Unit) {
    val ctx = LocalContext.current
    val presets = remember { ProviderPresets.all(ctx) }
    fun pick(p: ProviderPreset) {
        if (p.supported) onSelect(p)
        else Toast.makeText(ctx, tc(ctx, "该接入方式即将支持（需要适配器）", "Coming soon (needs an adapter)"), Toast.LENGTH_SHORT).show()
    }

    PresetGroup(t("免费可用", "Free to use"), presets.filter { it.group == "free" }, Icons.Outlined.Bolt, ::pick)
    PresetGroup(t("官方 API", "Official APIs"), presets.filter { it.group == "official" }, Icons.Outlined.Cloud, ::pick)

    MibotSectionHeader(t("网页登录接入（非官方）", "Web-login capture (unofficial)"))
    val web = presets.filter { it.group == "web_login" }
    MibotCard {
        web.forEachIndexed { i, p ->
            MibotRow(
                title = p.name, subtitle = note(p), icon = Icons.Outlined.Language, color = Color(p.color),
                onClick = { pick(p) }, showDivider = true,
                trailing = { StatusChip(t("非官方", "Unofficial"), ChipKind.WARN) },
            )
        }
        MibotRow(
            title = t("让 AI 帮我接入", "Ask the AI to wire one up"),
            subtitle = t(
                "在对话里给 AI 一个网址或文档，它会用 provider_manage 生成清单、测试并在你确认后保存。网页登录接入属于非官方方式，可能违反服务条款或导致封号，风险自负；不内置任何验证码绕过。",
                "Give the AI a URL or docs in chat; it drafts a manifest with provider_manage, tests it and saves after you confirm. Web-login capture is unofficial and may break a service's ToS or get the account banned — at your own risk. No captcha bypass is included.",
            ),
            icon = Icons.Outlined.Language,
            color = Color(0xFF8A8F99),
            chevron = false,
        )
    }
    val custom = presets.filter { it.group == "custom" }
    PresetGroup(t("我的自定义清单", "My custom manifests"), custom, Icons.Outlined.Tune, ::pick)
    Spacer(Modifier.size(4.dp))
}


@Composable
private fun PresetGroup(
    title: String,
    list: List<ProviderPreset>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    pick: (ProviderPreset) -> Unit,
) {
    if (list.isEmpty()) return
    MibotSectionHeader(title)
    MibotCard {
        list.forEachIndexed { i, p ->
            val chip: Pair<String, ChipKind> = when {
                !p.supported -> t("即将支持", "Soon") to ChipKind.NEUTRAL
                p.auth == "oauth_pkce" -> t("一键登录", "1-tap login") to ChipKind.ACCENT
                p.group == "free" -> t("免费", "Free") to ChipKind.OK
                p.risk -> t("非官方", "Unofficial") to ChipKind.WARN
                else -> t("官方", "Official") to ChipKind.NEUTRAL
            }
            MibotRow(
                title = p.name,
                subtitle = note(p),
                icon = icon,
                color = Color(p.color),
                onClick = { pick(p) },
                showDivider = i < list.lastIndex,
                trailing = { StatusChip(chip.first, chip.second) },
            )
        }
    }
}

/** Key link + free-tier note shown on the configure step for a preset. */
@Composable
fun PresetInfoCard(p: ProviderPreset) {
    val ctx = LocalContext.current
    Spacer(Modifier.size(8.dp))
    MibotCard(padding = PaddingValues(16.dp)) {
        Text(p.name, fontSize = com.openminis.app.mibot.design.MibotType.TitleSmall, color = MaterialTheme.colorScheme.onSurface)
        note(p)?.let {
            Spacer(Modifier.size(4.dp))
            Text(it, fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (p.baseUrl.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text(p.baseUrl, fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (p.models.isNotEmpty()) {
            Text(t("推荐模型：", "Suggested models: ") + p.models.joinToString(", "), fontSize = com.openminis.app.mibot.design.MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(10.dp))
        Row {
            p.keyUrl?.let { url ->
                MibotButton(t("获取 Key", "Get a key"), onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
                Spacer(Modifier.size(10.dp))
            }
            p.docUrl?.let { url ->
                MibotButton(t("额度说明", "Limits"), tonal = true, onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
            }
        }
    }
    if (p.baseUrl.contains("{")) {
        MibotFootnote(t("请把地址中的 {…} 占位符替换成你的值。", "Replace the {…} placeholder in the base URL with your value."))
    }
    Spacer(Modifier.size(8.dp))
}

@Suppress("unused")
@Composable
private fun keepImports() { Column(Modifier.fillMaxWidth().padding(0.dp)) {} }
