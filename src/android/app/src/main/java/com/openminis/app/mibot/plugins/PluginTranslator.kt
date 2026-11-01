package com.openminis.app.mibot.plugins

import android.content.Context
import android.util.Log
import com.openminis.app.MinisApp
import com.openminis.app.data.model.LLMMessage
import com.openminis.app.data.model.ThinkingLevel
import com.openminis.app.ui.chat.TitleCandidates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * [fork/mibot-v5] Chinese for every plugin text. Descriptions that already
 * carry Chinese are shown as their Chinese part; the rest are translated by
 * the user's own configured models (free / flash / lite models tried first)
 * and cached on disk forever (keyed by a hash of the source text), so each
 * text is translated once. With no usable model the original is shown and
 * the UI says so.
 */
object PluginTranslator {
    private const val TAG = "MibotPluginTr"
    private val cache = HashMap<String, String>()
    private var loaded = false
    private val mutex = Mutex()
    /** Bumps when new translations land (UI recomposes). */
    val version = MutableStateFlow(0)
    /** Last failure reason (e.g. no model), for the UI hint. */
    val lastError = MutableStateFlow<String?>(null)

    private fun file(ctx: Context) = File(PluginStore.storeDir(ctx), "translations.json")

    private fun key(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun ensure(ctx: Context) {
        if (loaded) return
        synchronized(cache) {
            if (loaded) return
            runCatching {
                val o = JSONObject(file(ctx).readText())
                o.keys().forEach { cache[it] = o.getString(it) }
            }
            loaded = true
        }
    }

    private fun persist(ctx: Context) {
        val o = JSONObject()
        synchronized(cache) { cache.forEach { (k, v) -> o.put(k, v) } }
        runCatching { file(ctx).writeText(o.toString()) }
    }

    private fun cjkRatio(s: String): Float {
        val letters = s.count { it.isLetter() }
        if (letters == 0) return 0f
        return s.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }.toFloat() / letters
    }

    /**
     * The Chinese part of a (often bilingual) description: segments split on
     * common separators that are mostly Chinese. Null when there is none.
     */
    fun chinesePart(s: String): String? {
        if (s.isBlank()) return null
        if (cjkRatio(s) > 0.6f) return s.trim()
        val segs = s.split(Regex("""\s[|｜·—–/]\s|｜|\n|(?<=[。！？])\s+|(?<=\.)\s+(?=[\u4e00-\u9fff])"""))
            .map { it.trim() }.filter { it.isNotEmpty() && cjkRatio(it) > 0.35f }
        if (segs.isEmpty()) return null
        return segs.joinToString(" ").trim().takeIf { cjkRatio(it) > 0.35f }
    }

    /** Chinese text to show right now, or null if it still needs translating. */
    fun zh(ctx: Context, s: String): String? {
        if (s.isBlank()) return s
        chinesePart(s)?.let { return it }
        ensure(ctx)
        return synchronized(cache) { cache[key(s)] }
    }

    private fun freeScore(id: String, label: String, base: String?): Int {
        val h = (id + " " + label + " " + (base ?: "")).lowercase()
        return when {
            ":free" in h || "免费" in h || "free" in h -> 0
            listOf("flash", "lite", "mini", "glm-4-flash", "groq", "siliconflow", "speed").any { it in h } -> 1
            else -> 2
        }
    }

    private suspend fun ask(ctx: Context, system: String, user: String, maxTokens: Int): String? {
        val repo = (ctx.applicationContext as? MinisApp)?.providerRepositoryOrNull ?: return null
        val cands = TitleCandidates.forSession(repo, "mibot-plugin-translate", null, null)
            .sortedBy { e ->
                val inst = repo.instance(e.providerInstanceId)
                freeScore(e.model.id, inst?.label ?: "", inst?.effectiveBaseURL)
            }
        if (cands.isEmpty()) {
            lastError.value = "未配置可用模型，显示原文"
            return null
        }
        val r = TitleCandidates.walk(cands, origin = "plugin-translate", maxTries = 3) { entry ->
            val provider = TitleCandidates.providerFor(repo, ctx, entry, "mibot-plugin-translate") ?: return@walk null
            val resp = provider.sendMessage(
                messages = listOf(LLMMessage(role = LLMMessage.Role.USER, content = user)),
                systemPrompt = system,
                maxTokens = if (provider.model.supportsReasoning == true) maxTokens + 2048 else maxTokens,
                temperature = null,
                thinkingLevel = ThinkingLevel.OFF,
            )
            resp.text.trim().ifEmpty { null }
        }
        lastError.value = if (r == null) "翻译失败（模型不可用），显示原文" else null
        return r
    }

    /** Translate the descriptions that still need it (one batched request per call). */
    suspend fun translateDescriptions(ctx: Context, texts: List<String>) = withContext(Dispatchers.IO) {
        ensure(ctx)
        val todo = texts.filter { it.isNotBlank() && zh(ctx, it) == null }.distinct().take(24)
        if (todo.isEmpty()) return@withContext
        mutex.withLock {
            val still = todo.filter { zh(ctx, it) == null }
            if (still.isEmpty()) return@withLock
            val user = still.mapIndexed { i, s -> "${i + 1}\t" + s.replace('\n', ' ').replace('\t', ' ').take(500) }.joinToString("\n")
            val out = runCatching {
                ask(ctx,
                    "你是软件插件简介的翻译。把每行译成简洁自然的简体中文；产品名、模型名、命令、代码保持原样。严格按行输出“编号<TAB>译文”，不要输出其它内容。",
                    user, 1800)
            }.onFailure { Log.w(TAG, "translate: ${it.message}") }.getOrNull() ?: return@withLock
            var added = 0
            out.lines().forEach { line ->
                val m = Regex("""^\s*(\d+)\s*[\t:：.、)]\s*(.+)$""").find(line) ?: return@forEach
                val i = m.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
                val zhText = m.groupValues[2].trim()
                if (i in still.indices && zhText.isNotEmpty()) {
                    synchronized(cache) { cache[key(still[i])] = zhText }; added++
                }
            }
            if (added > 0) { persist(ctx); version.value = version.value + 1 }
        }
    }

    /** README in Chinese (first ~10k chars, chunked, Markdown kept). Null when no model. */
    suspend fun translateReadme(ctx: Context, md: String): String? = withContext(Dispatchers.IO) {
        ensure(ctx)
        if (md.isBlank()) return@withContext md
        if (cjkRatio(md) > 0.5f) return@withContext md
        val src = md.take(10_000)
        val chunks = mutableListOf<String>()
        var cur = StringBuilder()
        src.split("\n\n").forEach { para ->
            if (cur.length + para.length > 2600 && cur.isNotEmpty()) { chunks += cur.toString(); cur = StringBuilder() }
            cur.append(para).append("\n\n")
        }
        if (cur.isNotBlank()) chunks += cur.toString()
        val out = StringBuilder()
        for (c in chunks) {
            val k = key("readme:" + c)
            val hit = synchronized(cache) { cache[k] }
            val t = hit ?: mutex.withLock {
                ask(ctx,
                    "把下面的 Markdown 译成简体中文。保留 Markdown 结构、链接与代码块（代码块内容不翻译）；产品名、命令保持原样。只输出译文。",
                    c, 3500)
            }?.also { tr -> synchronized(cache) { cache[k] = tr }; persist(ctx) }
            if (t == null) return@withContext if (out.isEmpty()) null else out.toString()
            out.append(t.trim()).append("\n\n")
        }
        if (md.length > src.length) out.append("\n\n> （以下内容较长，未翻译部分请查看原文）")
        version.value = version.value + 1
        out.toString()
    }
}
