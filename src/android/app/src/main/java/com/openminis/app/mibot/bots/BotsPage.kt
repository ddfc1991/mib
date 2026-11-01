package com.openminis.app.mibot.bots

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.openminis.app.MinisApp
import com.openminis.app.data.model.SubAgentDefinition
import com.openminis.app.data.model.SubAgentLimits
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotPageTitle
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.mibot.design.t
import com.openminis.app.mibot.design.tc
import com.openminis.app.ui.glass.LocalGlassBottomInset
import org.json.JSONObject
import java.io.File

private val avatarColors = listOf(MibotTokens.Brick, MibotTokens.Sky, MibotTokens.Green, MibotTokens.Purple, MibotTokens.Amber, MibotTokens.Teal)

private fun avatarFile(ctx: Context, id: String) = File(ctx.filesDir, "mibot/avatars/$id.png")

@Composable
private fun BotAvatar(ctx: Context, def: SubAgentDefinition, index: Int) {
    val file = avatarFile(ctx, def.id)
    val bmp = remember(def.id, file.lastModified()) {
        if (file.exists()) runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
        }.getOrNull() else null
    }
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(avatarColors[index % avatarColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(def.displayName(ctx).take(1).uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = com.openminis.app.mibot.design.MibotType.TitleSmall)
        }
    }
}

/**
 * [fork/mibot-v2] Bots tab. A Bot is a saved, named agent profile — the same
 * thing the main agent delegates to as a sub-agent (subagent_task `agent`),
 * so the old Settings › Sub agents page and this tab are one list now.
 * Temporary sub-agent runs show inside the chat that started them.
 */
@Composable
fun BotsPage(onOpen: (String) -> Unit, onNew: () -> Unit, onAgentSettings: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { (ctx.applicationContext as MinisApp).providerRepository }
    var gen by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { gen++ } }
    val roster = remember(gen) { repo.subAgents }
    val bottom = LocalGlassBottomInset.current

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val msg = PersonaCards.import(ctx, uri)
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            gen++
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
        item { MibotPageTitle("Bots", t("保存的 AI 角色，主 AI 可以把任务交给它们", "Saved AI profiles the main AI can hand tasks to")) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MibotButton(t("新建 Bot", "New Bot"), enabled = roster.size < SubAgentLimits.MAX_COUNT, modifier = Modifier.weight(1f), onClick = onNew)
                MibotButton(t("导入人设卡", "Import card"), tonal = true, enabled = roster.size < SubAgentLimits.MAX_COUNT, modifier = Modifier.weight(1f), onClick = {
                    importer.launch(arrayOf("application/json", "image/png", "text/plain", "application/octet-stream"))
                })
            }
        }
        item {
            val builtInLabel = t("内置", "Built-in")
            val builtInChip: @Composable () -> Unit = { StatusChip(builtInLabel, ChipKind.NEUTRAL) }
            MibotSectionHeader(t("我的 Bots", "My Bots") + " · ${roster.size}/${SubAgentLimits.MAX_COUNT}")
            MibotCard {
                roster.forEachIndexed { i, def ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(16.dp))
                        BotAvatar(ctx, def, i)
                        Box(Modifier.weight(1f)) {
                            MibotRow(
                                title = def.displayName(ctx),
                                subtitle = def.displayDescription(ctx),
                                onClick = { onOpen(def.id) },
                                showDivider = false,
                                trailing = if (def.isBuiltIn) builtInChip else null,
                            )
                        }
                    }
                }
            }
        }
        item {
            MibotSectionHeader(t("子代理", "Sub-agents"))
            MibotCard {
                MibotRow(
                    t("委派设置", "Delegation settings"),
                    t("允许主 AI 委派、排序与并发", "Allow delegation, order and concurrency"),
                    Icons.Outlined.Tune, MibotTokens.Steel, onClick = onAgentSettings,
                )
            }
            MibotFootnote(t(
                "主 AI 可以临时开一个子代理，也可以直接调用这里保存的 Bot；临时子代理的运行过程显示在对应对话里。人设卡支持 Character Card V2/V3（JSON 或 PNG 内嵌），请只导入你有权使用的角色与头像。",
                "The main AI can spawn a temporary sub-agent or call a saved Bot; temporary runs show inside their chat. Persona cards: Character Card V2/V3 (JSON or PNG-embedded) — import only characters and art you have the rights to.",
            ))
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * [fork/mibot-v2] Character Card V2/V3 import (JSON, or PNG with a `chara` /
 * `ccv3` tEXt chunk). The card becomes a Bot: persona text goes into the
 * Bot's instructions, the PNG becomes its avatar, and the original card is
 * kept next to it (filesDir/mibot/personas) for later export.
 */
object PersonaCards {
    fun import(ctx: Context, uri: Uri): String = runCatching {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("cannot read file")
        val isPng = bytes.size > 8 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
        val jsonText = if (isPng) pngCard(bytes) ?: error(tc(ctx, "PNG 里没有人设卡数据", "No card data in this PNG")) else String(bytes, Charsets.UTF_8)
        val root = JSONObject(jsonText)
        val d = root.optJSONObject("data") ?: root
        val name = d.optString("name").ifBlank { error("card has no name") }
        val repo = (ctx.applicationContext as MinisApp).providerRepository
        if (repo.subAgents.size >= SubAgentLimits.MAX_COUNT) error(tc(ctx, "Bot 数量已达上限", "Bot limit reached"))
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "persona_" + (System.currentTimeMillis() % 100000) }
        val wire = generateSequence(slug) { it + "_" }.first { cand -> repo.subAgents.none { it.name.equals(cand, true) } }.take(SubAgentLimits.NAME_MAX_LENGTH)
        val extras = d.optJSONObject("extensions")?.optJSONObject("mibot")
        val instructions = buildString {
            append("You are role-playing the persona \"").append(name).append("\". Stay in character.\n")
            fun sec(label: String, key: String) { d.optString(key).takeIf { it.isNotBlank() }?.let { append("\n## ").append(label).append("\n").append(it).append("\n") } }
            sec("System", "system_prompt")
            sec("Description", "description")
            sec("Personality", "personality")
            sec("Scenario", "scenario")
            sec("Example dialogue", "mes_example")
            sec("Opening line", "first_mes")
            extras?.optString("speech_habits")?.takeIf { it.isNotBlank() }?.let { append("\n## Speech habits\n").append(it).append("\n") }
        }
        val desc = (d.optString("creator_notes").ifBlank { d.optString("description") }).replace('\n', ' ').take(SubAgentLimits.DESCRIPTION_MAX_LENGTH)
            .ifBlank { "Persona: $name" }
        val def = SubAgentDefinition(
            name = wire,
            description = "[$name] $desc".take(SubAgentLimits.DESCRIPTION_MAX_LENGTH),
            instructions = instructions.take(SubAgentLimits.INSTRUCTIONS_MAX_LENGTH),
            sortOrder = repo.subAgents.size,
        )
        repo.upsertSubAgent(def)
        File(ctx.filesDir, "mibot/personas").apply { mkdirs() }.resolve("${def.id}.json").writeText(jsonText)
        if (isPng) avatarFile(ctx, def.id).apply { parentFile?.mkdirs() }.writeBytes(bytes)
        tc(ctx, "已导入「$name」", "Imported \"$name\"")
    }.getOrElse { tc(ctx, "导入失败：", "Import failed: ") + (it.message ?: it.javaClass.simpleName) }

    /** Reads a `ccv3` (preferred) or `chara` tEXt chunk; values are base64 JSON. */
    private fun pngCard(b: ByteArray): String? {
        var pos = 8
        var chara: String? = null
        var ccv3: String? = null
        while (pos + 12 <= b.size) {
            val len = ((b[pos].toInt() and 0xFF) shl 24) or ((b[pos + 1].toInt() and 0xFF) shl 16) or
                ((b[pos + 2].toInt() and 0xFF) shl 8) or (b[pos + 3].toInt() and 0xFF)
            val type = String(b, pos + 4, 4, Charsets.ISO_8859_1)
            val start = pos + 8
            if (len < 0 || start + len > b.size) break
            if (type == "tEXt") {
                val chunk = b.copyOfRange(start, start + len)
                val nul = chunk.indexOf(0.toByte())
                if (nul > 0) {
                    val key = String(chunk, 0, nul, Charsets.ISO_8859_1)
                    val value = String(chunk, nul + 1, chunk.size - nul - 1, Charsets.ISO_8859_1)
                    val decoded = runCatching { String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                    if (key.equals("ccv3", true)) ccv3 = decoded
                    if (key.equals("chara", true)) chara = decoded
                }
            }
            if (type == "IEND") break
            pos = start + len + 4
        }
        return ccv3 ?: chara
    }
}
