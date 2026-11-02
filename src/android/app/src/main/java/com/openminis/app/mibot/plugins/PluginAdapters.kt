package com.openminis.app.mibot.plugins

import android.content.Context
import com.openminis.app.MinisApp
import com.openminis.app.data.model.LLMModel
import com.openminis.app.data.model.ModelEntry
import com.openminis.app.data.model.ProviderCredential
import com.openminis.app.data.model.ProviderInstance
import com.openminis.app.data.model.ProviderType
import com.openminis.app.mibot.gateway.GatewayHealth
import com.openminis.app.mibot.gateway.GatewayNet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [fork/mibot-v5b2] Native adapters for "model provider" DSH plugins.
 *
 * Mibot has no DeepSeek Harness host, so plugin JavaScript never runs. For a
 * plugin whose useful part is just an OpenAI-compatible endpoint, Mibot reads
 * the provider definition (base URL, model roster) from the INSTALLED plugin
 * files at runtime, audits the source for anything it will not run, and
 * registers the endpoint in its own gateway. No plugin code is copied.
 *
 * dsh-our-free-model (MIT, © 2026 Ebony-Vinyl, github.com/Ebony-Vinyl/dsh-our-free-model):
 *  - Kilo lane (src/kilo.js): public OpenAI-compatible gateway, no key, free
 *    pool = models with isFree:true → ported (base read from kilo.js).
 *  - everything else is refused, with the reason shown in the plugin page.
 */
object PluginAdapters {
    data class Finding(val part: String, val verdict: String, val reason: String) {
        fun toJson() = JSONObject().put("part", part).put("verdict", verdict).put("reason", reason)
    }

    data class Report(
        val supported: Boolean,
        val base: String?,
        val findings: List<Finding>,
        val note: String,
    )

    private const val OFM = "ebony-vinyl/dsh-our-free-model"
    /** Hosts an adapter may register. Anything else found in a lane is refused. */
    private val allowHosts = setOf("api.kilo.ai")

    fun hasAdapter(fullName: String) = fullName.equals(OFM, true)

    private fun read(dir: File, rel: String): String? = File(dir, rel).takeIf { it.isFile }?.readText()

    /** Audits the installed plugin; never executes anything. */
    fun audit(inst: InstalledPlugin): Report {
        val dir = File(inst.dir)
        if (!hasAdapter(inst.fullName)) return Report(false, null, emptyList(), "这个插件没有 Mibot 原生适配：它依赖 DSH 宿主运行 JavaScript，Mibot 不执行插件代码。")
        val f = mutableListOf<Finding>()
        val kilo = read(dir, "src/kilo.js")
        var base: String? = null
        if (kilo != null) {
            base = Regex("""KILO_BASE\s*=\s*\(?[^'"]*\?\?\s*'(https://[^']+)'""").find(kilo)?.groupValues?.get(1)?.trimEnd('/')
            val hosts = Regex("""https?://([a-zA-Z0-9.-]+)""").findAll(kilo).map { it.groupValues[1].lowercase() }.toSet() - setOf("kilo.ai")
            val host = base?.let { runCatching { java.net.URI(it).host }.getOrNull() }
            when {
                base == null -> f += Finding("Kilo 免费车道", "未启用", "没在 src/kilo.js 里找到网关地址（插件可能已改版）")
                host !in allowHosts || hosts.any { it !in allowHosts } -> f += Finding("Kilo 免费车道", "拒绝", "kilo.js 指向未审核的主机：${(hosts + listOfNotNull(host)).joinToString()}")
                else -> f += Finding("Kilo 免费车道", "已启用", "公开 OpenAI 兼容网关 $base，无需 Key；只取 isFree 模型。上游声明免费池的提示词可能被记录，勿发敏感内容。")
            }
        } else f += Finding("Kilo 免费车道", "未启用", "插件里没有 src/kilo.js")
        if (read(dir, "src/upstream.js") != null) f += Finding("OpenCode 匿名免费车道", "拒绝",
            "要冒充官方 OpenCode 客户端（指纹头、注入官方提示词、改写工具名）并为每个请求伪造 session / project id 来绕开免费额度限制——这是规避上游限流，Mibot 不做。")
        if (read(dir, "src/vault.js") != null) f += Finding("EAC 协付渠道", "拒绝",
            "凭据与端点被刻意密封、混淆（代码里写明“任何文件都还原不出来”），只对指定桌面宿主解锁，并要求 GitHub 登录且 star 仓库；未知端点，无法审核。")
        val subs = read(dir, "src/subscriptions.js")
        if (subs != null && (".codex" in subs || ".claude" in subs || ".gemini" in subs)) f += Finding("订阅渠道", "拒绝",
            "会读取电脑上其他工具的登录凭据文件（~/.codex/auth.json、~/.claude/.credentials.json、~/.gemini/oauth_creds.json）来调用官方订阅接口；在手机上不适用，且属于复用他人客户端令牌。")
        if (File(dir, "vendor/channel-pack").isDirectory) f += Finding("十个账号渠道（TRAE / Qoder / Raccoon / ZCode …）", "拒绝",
            "逆向各家客户端登录（WASM 加密信封、机器码、验证码流程、积分领取），需要 Node 宿主运行；Mibot 不移植也不运行。")
        val upd = read(dir, "src/updater.js")
        if (upd != null) f += Finding("应用内升级", "不运行", "会从 GitHub / jsDelivr 下载新代码并热替换；Mibot 不执行插件下载的代码。")
        val probe = read(dir, "src/probe.js")
        if (probe != null && ("ipify" in probe || "ipinfo" in probe || "ipapi" in probe)) f += Finding("出口 IP 探测", "不运行",
            "向 api.ipify.org / ipinfo.io / ipapi.co 查询你的公网 IP 与国家（第三方，非必要）。")
        val ok = f.any { it.verdict == "已启用" }
        return Report(ok, if (ok) base else null, f,
            if (ok) "Mibot 只用了它的 Kilo 免费车道（公开、无需 Key），其余部分按下方原因不运行。" else "没有可安全使用的部分。")
    }

    const val ATTRIBUTION = "dsh-our-free-model · MIT License · © 2026 Ebony-Vinyl · github.com/Ebony-Vinyl/dsh-our-free-model。Mibot 只读取已安装插件里的网关地址与模型清单，不复制、不执行插件代码。"

    /** Shown before the plugin is installed: what the adapter will check once the files are here. */
    fun preview(fullName: String): Report? = if (!hasAdapter(fullName)) null else Report(false, null, listOf(
        Finding("Kilo 免费车道", "待审核", "安装后读取 src/kilo.js 的网关地址，只允许 api.kilo.ai；只取 isFree 模型，无需 Key。"),
        Finding("OpenCode 匿名免费车道", "拒绝", "冒充官方客户端并伪造会话 id 绕开免费额度限制。"),
        Finding("EAC 协付渠道", "拒绝", "密封、混淆的未知端点，无法审核。"),
        Finding("订阅渠道", "拒绝", "读取其他工具的本机 OAuth 凭据文件。"),
        Finding("账号渠道", "拒绝", "逆向各家客户端登录流程。"),
        Finding("应用内升级 / 出口 IP 探测", "不运行", "下载并热替换代码；向第三方查询公网 IP。"),
    ), "安装后 Mibot 会按实际源码复核以上结论，再决定是否启用 Kilo 车道。")

    data class EnableResult(val ok: Boolean, val models: Int, val probe: String, val instanceId: String?)

    /** Registers the audited lane's free models in the gateway and runs a real probe. */
    suspend fun enable(ctx: Context, inst: InstalledPlugin): EnableResult = withContext(Dispatchers.IO) {
        val rep = audit(inst)
        val base = rep.base ?: return@withContext EnableResult(false, 0, rep.note, null)
        val listing = GatewayNet.request(null, "$base/models", headers = mapOf("User-Agent" to "Mibot"))
        if (listing.kind != GatewayNet.Kind.OK) return@withContext EnableResult(false, 0, "模型清单拉取失败：${listing.detail}", null)
        val arr = runCatching { JSONObject(listing.body).optJSONArray("data") }.getOrNull() ?: runCatching { JSONArray(listing.body) }.getOrNull() ?: JSONArray()
        val free = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optBoolean("isFree") }
        if (free.isEmpty()) return@withContext EnableResult(false, 0, "上游没有 isFree 模型", null)
        val app = ctx.applicationContext as? MinisApp ?: return@withContext EnableResult(false, 0, "no app", null)
        val repo = app.providerRepositoryOrNull ?: return@withContext EnableResult(false, 0, "provider repo not ready", null)
        repo.ensureConfigLoaded()
        val label = "Kilo 免费池 · 来自插件"
        val existing = repo.config.value.instances.find { it.label == label }
        val instance = existing ?: ProviderInstance(
            id = java.util.UUID.randomUUID().toString(), label = label, providerType = ProviderType.openAI,
            credentialType = ProviderCredential.apiKey, customBaseURL = base, appendV1Suffix = false,
        ).also { repo.addInstance(it) }
        val have = repo.config.value.modelEntries.filter { it.providerInstanceId == instance.id }.map { it.baseModel.id }.toSet()
        for (m in free) {
            val id = m.optString("id")
            if (id.isBlank()) continue
            if (id in have) continue
            val model = LLMModel(id = id, displayName = m.optString("name", id).removeSuffix(" (free)") + "（来自插件）",
                provider = instance.providerType.displayName, contextWindow = m.optInt("context_length").takeIf { it > 0 })
            repo.addEntry(ModelEntry(providerInstanceId = instance.id, baseModel = model, isCustom = true))
        }
        // Real gateway test: one tiny chat completion on the auto router.
        val body = JSONObject().put("model", free.firstOrNull { it.optString("id") == "kilo-auto/free" }?.optString("id") ?: free.first().optString("id"))
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping"))).put("max_tokens", 8).toString()
        val probe = GatewayNet.request(instance.id, "$base/chat/completions", method = "POST", jsonBody = body)
        runCatching { GatewayHealth.init(ctx); GatewayHealth.recordProbe(instance.id, probe, label = "插件适配 · 真实对话探测") }
        val okProbe = probe.kind == GatewayNet.Kind.OK
        EnableResult(okProbe, free.size, if (okProbe) "探测通过 · ${probe.latencyMs}ms" else "探测失败：${probe.detail.take(80)}", instance.id)
    }
}
