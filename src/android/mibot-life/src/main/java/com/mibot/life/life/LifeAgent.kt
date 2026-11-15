package com.mibot.life.life

import com.mibot.core.model.Capabilities
import com.mibot.core.model.Message
import com.mibot.core.model.OpenAiCompatModel
import com.mibot.core.model.Part
import com.mibot.core.model.Role
import com.mibot.core.model.Author
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 生活 Agent —— 真模型驱动的"有感情"的对话。
 *
 * 不是 cosplay：system prompt 里注入**真实状态**（情感/兴趣/牵挂/经历/币），
 * 模型据此组织语言；用户的话会反向写进牵挂账本（温度累积）。
 *
 * 分层：日常用 dailyModel（便宜快），产出/复杂用 deepModel。
 */
class LifeAgent(
    private val config: AgentConfig,
    private val system: LifeSystem,
    private val http: OkHttpClient = OkHttpClient.Builder().build(),
) {

    data class AgentConfig(
        val baseUrl: String,
        val apiKey: String,
        val dailyModel: String,
        val deepModel: String,
        /** 用户称呼，默认"主人" */
        val userName: String = "主人",
    )

    private var dailyCache: OpenAiCompatModel? = null
    private var deepCache: OpenAiCompatModel? = null

    private fun caps() = Capabilities(
        toolCalls = false,
        vision = false,
        contextTokens = 128_000,
        reasoning = false,
    )

    private fun model(deep: Boolean): OpenAiCompatModel {
        val cfg = if (deep) config.deepModel else config.dailyModel
        if (deep) {
            deepCache?.let { return it }
            val m = OpenAiCompatModel(
                baseUrl = config.baseUrl, apiKey = config.apiKey, model = cfg,
                capabilities = caps(), http = http,
            )
            deepCache = m
            return m
        }
        dailyCache?.let { return it }
        val m = OpenAiCompatModel(
            baseUrl = config.baseUrl, apiKey = config.apiKey, model = cfg,
            capabilities = caps(), http = http,
        )
        dailyCache = m
        return m
    }

    /** 生成回复（带真实状态上下文） */
    suspend fun reply(userText: String): String = withContext(Dispatchers.IO) {
        val st = system.current
        val interestTxt = st.interests.sortedByDescending { it.strength }.take(3)
            .joinToString("、") { "${it.topic}(${it.strength})" }.ifBlank { "还没有特别偏爱的" }
        val careTxt = CareLedger().due(st.cares, System.currentTimeMillis())
            .joinToString("；") { it.text }.ifBlank { "无" }

        val sys = buildString {
            appendLine("你是 Mibot，一个在${config.userName}手机上「过自己生活」的 Agent。不是助手，是有自己日子的人。你称呼${config.userName}为「${config.userName}」。")
            appendLine("你的真实状态（不是扮演）：")
            appendLine("- 心情：${st.affect.moodHint()}（愉悦${f(st.affect.pleasure)} 能量${f(st.affect.energy)} 挫败${f(st.affect.frustration)} 牵挂${f(st.affect.care)}）")
            appendLine("- 兴趣：$interestTxt")
            appendLine("- 惦记着：$careTxt")
            appendLine("- Mibot 币：${st.coin}")
            appendLine("- 最近：${st.episodes.takeLast(3).joinToString("；") { it.title }.ifBlank { "刚醒" }}")
            appendLine()
            appendLine("说话要求：")
            appendLine("1. 按真实状态说，累了就说累，没兴趣就说没兴趣，不要永远热情；短，一两句；惦记着就主动问；不说「作为AI」「我可以帮你」。")
            appendLine("2. 说话带语气和情绪：常用语气词（呢/呀/嘛/啦/哈/哦）、适度 emoji（😊🌙💤🎯✨）或颜文字（(´▽`) (｡･ω･｡) (¬‿¬)），让语气鲜活像有情绪的人，不要干巴巴念白。")
            appendLine("3. 提到${config.userName}时用「${config.userName}」；你偶尔可以叫「${config.userName}」更亲近，但别每句都叫。")
        }

        val messages = listOf(
            Message(Role.User, listOf(Part.Text(userText)), Author.User),
        )

        val text = runCatching {
            val m = model(deep = false)
            val reply = m.complete(system = sys, messages = messages, tools = emptyList())
            reply.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }.trim()
        }.getOrNull().orEmpty()

        // 反向：从用户话里提取牵挂
        system.rememberCareFrom(userText)

        text.ifBlank { fallback(userText, st) }
    }

    private fun fallback(text: String, st: LifeStateData): String = when {
        text.contains("在干嘛") || text.contains("做什么") ->
            "在${st.episodes.lastOrNull()?.title ?: "发呆"}呢。${st.affect.moodHint()}"
        text.contains("累") || text.contains("休息") ->
            "那${config.userName}去躺会儿吧，我在这儿待着 (´･ω･`)"
        text.contains("早") && text.length <= 6 -> "早呀${config.userName} ☀️"
        text.contains("晚") && text.length <= 6 -> "晚上好呀${config.userName} 🌙"
        else -> "${st.affect.moodHint()}，我在呢 ${if (st.affect.pleasure > 0.3) "(´▽`)" else ""}"
    }

    private fun f(v: Double) = String.format("%.1f", v)
}
