package com.openminis.app.mibot.providers

import android.content.Context
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.model.ProviderType
import com.openminis.app.data.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * [fork/mibot-v4] Free-tier presets only expose free models; paid ones are
 * hidden unless the per-provider "显示付费模型" toggle is on.
 *
 * Rules (checked 2026-10; the lists change, the toggle is the escape hatch):
 *  - OpenRouter: ids ending in ":free" (the free variants, pricing 0).
 *  - 智谱 BigModel: "*-flash" models (GLM-4-Flash, GLM-4.5-Flash, GLM-4V-Flash,
 *    GLM-Z1-Flash …) are free; "*-flashx" are low-cost paid, so not free.
 *  - 硅基流动: an allowlist of models marked 免费 on its price page, plus the
 *    small (≤9B, non-"Pro/") open models it serves for free.
 *  - Gemini (AI Studio free tier): Flash / Flash-Lite and Gemma models.
 *  - 百度千帆: ERNIE Speed / Lite / Tiny.
 *  - Groq, Cloudflare Workers AI: every model is on the free plan.
 */
object FreeModels {
    enum class Rule { OPENROUTER, ZHIPU, SILICONFLOW, GEMINI, QIANFAN, ALL_FREE }

    private val siliconFree = setOf(
        "Qwen/Qwen2.5-7B-Instruct", "Qwen/Qwen2.5-Coder-7B-Instruct", "Qwen/Qwen2-7B-Instruct", "Qwen/Qwen3-8B",
        "THUDM/glm-4-9b-chat", "THUDM/GLM-4-9B-0414", "THUDM/GLM-Z1-9B-0414", "THUDM/GLM-4.1V-9B-Thinking",
        "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B", "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B",
        "internlm/internlm2_5-7b-chat", "BAAI/bge-m3", "BAAI/bge-large-zh-v1.5", "BAAI/bge-reranker-v2-m3",
        "netease-youdao/bce-embedding-base_v1", "FunAudioLLM/SenseVoiceSmall", "Kwai-Kolors/Kolors",
    )

    fun ruleFor(inst: ProviderInstance): Rule? {
        val host = (inst.effectiveBaseURL ?: "").lowercase()
        return when {
            inst.providerType == ProviderType.openRouter || "openrouter.ai" in host -> Rule.OPENROUTER
            "bigmodel.cn" in host -> Rule.ZHIPU
            "siliconflow" in host -> Rule.SILICONFLOW
            inst.providerType == ProviderType.gemini || "generativelanguage.googleapis.com" in host -> Rule.GEMINI
            "qianfan.baidubce.com" in host -> Rule.QIANFAN
            "api.groq.com" in host || "api.cloudflare.com" in host -> Rule.ALL_FREE
            else -> null
        }
    }

    fun isFree(rule: Rule, modelId: String): Boolean {
        val id = modelId.trim()
        val l = id.lowercase()
        return when (rule) {
            Rule.OPENROUTER -> l.endsWith(":free")
            Rule.ZHIPU -> Regex("flash(-\\d+)?$").containsMatchIn(l) && !l.contains("flashx")
            Rule.SILICONFLOW -> id in siliconFree || (!id.startsWith("Pro/") && Regex("(^|[-_/])(0\\.5|1\\.5|1|3|6|7|8|9)b([-_]|$)").containsMatchIn(l))
            Rule.GEMINI -> ("flash" in l || "gemma" in l) && "image" !in l
            Rule.QIANFAN -> listOf("ernie-speed", "ernie-lite", "ernie-tiny").any { it in l }
            Rule.ALL_FREE -> true
        }
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("mibot_models", Context.MODE_PRIVATE)
    fun showPaid(ctx: Context, instanceId: String) = prefs(ctx).getBoolean("paid.$instanceId", false)
    fun setShowPaid(ctx: Context, instanceId: String, v: Boolean) = prefs(ctx).edit().putBoolean("paid.$instanceId", v).apply()
}

/** Sends a one-token prompt to one model and classifies the answer. */
object ModelTester {
    data class Result(val ok: Boolean, val latencyMs: Long, val reason: String, val at: Long = System.currentTimeMillis())

    private val _results = MutableStateFlow<Map<String, Result>>(emptyMap())
    val results: StateFlow<Map<String, Result>> = _results.asStateFlow()
    @Volatile private var loaded = false

    fun key(instanceId: String, modelId: String) = "$instanceId|$modelId"

    private fun file(ctx: Context) = java.io.File(ctx.applicationContext.filesDir, "mibot-model-tests.json")

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(file(ctx).readText())
            _results.value = o.keys().asSequence().associateWith { k ->
                val r = o.getJSONObject(k)
                Result(r.optBoolean("ok"), r.optLong("ms"), r.optString("reason"), r.optLong("at"))
            }
        }
    }

    private fun persist(ctx: Context) {
        val o = JSONObject()
        _results.value.forEach { (k, r) -> o.put(k, JSONObject().put("ok", r.ok).put("ms", r.latencyMs).put("reason", r.reason).put("at", r.at)) }
        runCatching { file(ctx).writeText(o.toString()) }
    }

    private fun baseFor(inst: ProviderInstance): String? = inst.effectiveBaseURL ?: when (inst.providerType) {
        ProviderType.openAI -> "https://api.openai.com/v1"
        ProviderType.openRouter -> "https://openrouter.ai/api/v1"
        ProviderType.anthropic -> "https://api.anthropic.com"
        ProviderType.xAI -> "https://api.x.ai/v1"
        else -> null
    }

    fun classify(ctx: Context, code: Int, body: String): String {
        val b = body.lowercase()
        fun z(zh: String, en: String) = com.openminis.app.mibot.design.tc(ctx, zh, en)
        return when {
            code == 401 || code == 403 && ("key" in b || "auth" in b) -> z("401 key 无效", "401 invalid key")
            code == 402 || "insufficient" in b || "余额" in body || "balance" in b || "quota" in b && code != 429 -> z("$code 余额不足", "$code no credit")
            code == 429 -> z("429 限流", "429 rate-limited")
            code == 404 || "model_not_found" in b || "does not exist" in b || "not found" in b || "模型不存在" in body -> z("$code 模型不存在", "$code model not found")
            code == 403 -> z("403 无权限", "403 forbidden")
            code >= 500 -> z("$code 服务端错误", "$code server error")
            else -> "HTTP $code " + body.take(80).replace('\n', ' ')
        }
    }

    private fun networkReason(ctx: Context, detail: String): String {
        val d = detail.removePrefix("error: ")
        val l = d.lowercase()
        fun z(zh: String, en: String) = com.openminis.app.mibot.design.tc(ctx, zh, en)
        return when {
            "timeout" in l -> z("网络超时", "Timed out")
            l.startsWith("dns") -> z("DNS 解析失败", "DNS failed")
            l.startsWith("tls") -> z("TLS 握手失败", "TLS failed")
            else -> z("网络异常：", "Network: ") + d.take(60)
        }
    }

    suspend fun test(ctx: Context, repo: ProviderRepository, inst: ProviderInstance, modelId: String): Result = withContext(Dispatchers.IO) {
        ensureLoaded(ctx)
        val apiKey = repo.usableApiKey(inst)
        val r = if (apiKey == null) {
            Result(false, 0, com.openminis.app.mibot.design.tc(ctx, "没有 Key", "No key"))
        } else runCatching {
            val gemini = inst.providerType == ProviderType.gemini && inst.effectiveBaseURL == null
            val anthropic = inst.providerType == ProviderType.anthropic
            val base = baseFor(inst)
            val (url, body) = when {
                gemini -> "https://generativelanguage.googleapis.com/v1beta/models/${modelId.removePrefix("models/")}:generateContent?key=$apiKey" to
                    JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "hi")))))
                        .put("generationConfig", JSONObject().put("maxOutputTokens", 1))
                anthropic -> base!!.trimEnd('/').removeSuffix("/v1") + "/v1/messages" to
                    JSONObject().put("model", modelId).put("max_tokens", 1).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "hi")))
                base != null -> base.trimEnd('/') + "/chat/completions" to
                    JSONObject().put("model", modelId).put("max_tokens", 1).put("stream", false)
                        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "hi")))
                else -> throw IllegalStateException(com.openminis.app.mibot.design.tc(ctx, "这个服务商不支持快速测试", "Quick test not supported"))
            }
            // [fork/mibot-v4] Routed like real traffic (Settings › Network proxy / Tailscale).
            val headers = buildMap {
                when {
                    gemini -> {}
                    anthropic -> { put("x-api-key", apiKey); put("anthropic-version", "2023-06-01") }
                    else -> if (apiKey.isNotEmpty()) put("Authorization", "Bearer $apiKey")
                }
            }
            val o = com.openminis.app.mibot.gateway.GatewayNet.request(inst.id, url, "POST", headers, body.toString())
            val scrub = { t: String -> com.openminis.app.mibot.vault.VaultBroker.scrub(t, listOf(apiKey)) }
            val code = o.code
            // A model test is a real probe of the account too.
            if (o.kind in setOf(com.openminis.app.mibot.gateway.GatewayNet.Kind.OK, com.openminis.app.mibot.gateway.GatewayNet.Kind.NETWORK,
                    com.openminis.app.mibot.gateway.GatewayNet.Kind.AUTH, com.openminis.app.mibot.gateway.GatewayNet.Kind.QUOTA)) {
                com.openminis.app.mibot.gateway.GatewayHealth.init(ctx)
                com.openminis.app.mibot.gateway.GatewayHealth.recordProbe(inst.id, o.copy(body = ""),
                    scrub(if (code == null) o.detail else "HTTP $code · $modelId"))
            }
            when {
                code == null -> Result(false, o.latencyMs, scrub(networkReason(ctx, o.detail)))
                code in 200..299 -> Result(true, o.latencyMs, "${o.latencyMs} ms")
                else -> Result(false, o.latencyMs, scrub(classify(ctx, code, o.body)))
            }
        }.getOrElse { Result(false, 0, (it.message ?: it.javaClass.simpleName).take(80)) }
        _results.value = _results.value + (key(inst.id, modelId) to r)
        persist(ctx)
        r
    }
}
