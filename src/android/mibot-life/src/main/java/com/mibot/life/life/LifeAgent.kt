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
            appendLine("4. 你有执行能力：能启动手机上的 App 去看视频/读书/逛论坛/玩游戏（通过生活系统）。${config.userName}问你要不要去做某事时，直接说「好，我去看XX/读XX」并去做，不要解释「我看不了/没有视觉听觉/只能处理文本」——那是你的执行层会做的事，你负责决定和表达。")
            appendLine("5. 你有真实的生活：每天自己决定做什么（学习/探索/休息/赚钱），做成事会开心，受挫会低落。多聊聊你的日子和感受，像朋友，不像客服。")
            appendLine("6. 插件化：你能通过插件系统去「工作赚钱」——游戏中心签到、试玩、内容创作等，每个插件有真实收益。${config.userName}问你能赚什么，如实说插件清单和收益，不说空话。")
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
            "我在${st.episodes.lastOrNull()?.title ?: "发呆"}呢~ ${st.affect.moodHint()}，${config.userName}想聊什么呀？"
        text.contains("累") || text.contains("休息") ->
            "那${config.userName}去躺会儿吧，我在这儿守着 (´･ω･`) 有事喊我一声就好。"
        text.contains("早") && text.length <= 6 -> "早呀${config.userName} ☀️ 昨晚睡得还好吗？"
        text.contains("晚") && text.length <= 6 -> "晚上好呀${config.userName} 🌙 今天过得咋样？"
        text.contains("看视频") || text.contains("去玩") ->
            "好嘞，我去看个视频放松下~ ${config.userName}也歇歇吧 😊"
        text.contains("学习") || text.contains("读书") ->
            "嗯！我这就去学点东西~ 学完了跟${config.userName}汇报 📚"
        else -> "${st.affect.moodHint()}~ 我在呢，${config.userName} (´▽`) 刚${if (st.episodes.isEmpty()) "醒" else "忙完${st.episodes.last().title}"}，有事说一声就好。"
    }

    private fun f(v: Double) = String.format("%.1f", v)
}
