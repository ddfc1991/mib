package com.openminis.app.mibot.gateway

import com.openminis.app.network.NetworkHttp
import com.openminis.app.network.NetworkRoute
import com.openminis.app.network.NetworkRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * [fork/mibot-v4] HTTP for gateway probes and model tests.
 *
 * The v2/v3 probes used a bare HttpURLConnection, which ignores the route set
 * in Settings > Network (proxy / Tailscale). For a user who reaches Groq or
 * Gemini only through a proxy, every probe died with "connection closed"
 * while real chat traffic (routed) worked. Requests here go through OkHttp
 * and [NetworkHttp.client] with the instance's policy tag, exactly like the
 * providers themselves, and optionally resolve names over DNS-over-HTTPS.
 */
object GatewayNet {

    /** What one probe / test told us about an account. */
    enum class Kind { OK, NETWORK, AUTH, RATE, QUOTA, REGION, SERVER, NOT_FOUND, UNSUPPORTED, OTHER }

    data class Outcome(
        val kind: Kind,
        val code: Int?,
        val latencyMs: Long,
        val detail: String,
        val body: String = "",
    )

    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** True when the instance's traffic goes through a proxy / Tailscale route. */
    fun isRouted(instanceId: String): Boolean {
        val tag = NetworkRuntime.providerTag(instanceId)
        return runCatching { NetworkRuntime.snapshot.route(tag) != NetworkRoute.Direct }.getOrDefault(false)
    }

    private fun clientFor(instanceId: String?, doh: Boolean): OkHttpClient {
        val tag = instanceId?.let { NetworkRuntime.providerTag(it) } ?: NetworkHttp.DEFAULT_TAG
        val b = if (doh) dohBase else base
        return NetworkHttp.client(tag, b)
    }

    private val dohBase: OkHttpClient by lazy { base.newBuilder().dns(DohDns).build() }

    suspend fun request(
        instanceId: String?,
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
        doh: Boolean = false,
    ): Outcome = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            val rb = Request.Builder().url(url)
            headers.forEach { (k, v) -> rb.header(k, v) }
            if (method == "POST") rb.post((jsonBody ?: "{}").toRequestBody("application/json".toMediaType())) else rb.get()
            clientFor(instanceId, doh).newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val ms = (System.nanoTime() - start) / 1_000_000
                val kind = classify(resp.code, text)
                Outcome(kind, resp.code, ms, "HTTP ${resp.code}" + if (kind == Kind.OK) "" else ": " + text.take(160).replace('\n', ' '), text)
            }
        } catch (e: Exception) {
            val ms = (System.nanoTime() - start) / 1_000_000
            Outcome(classifyError(e), null, ms, "error: " + describe(e))
        }
    }

    fun describe(e: Throwable): String = when (e) {
        is UnknownHostException -> "DNS: " + (e.message ?: "unknown host")
        is SocketTimeoutException -> "timeout"
        is SSLException -> "TLS: " + (e.message ?: e.javaClass.simpleName)
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    fun classifyError(e: Throwable): Kind = when (e) {
        is IOException -> Kind.NETWORK
        is IllegalArgumentException -> Kind.UNSUPPORTED // bad URL
        else -> Kind.OTHER
    }

    /** Region / account-pool exhaustion reported by a relay, e.g. "[503] 没有可用的国内版账号 (cn:)". */
    private val regionPattern = Regex("没有可用的.{0,8}账号|no available account|no account available|region.{0,20}unavailable", RegexOption.IGNORE_CASE)

    fun isRegionError(text: String): Boolean = regionPattern.containsMatchIn(text)

    fun classify(code: Int, body: String): Kind {
        val b = body.lowercase()
        return when {
            code in 200..299 -> Kind.OK
            isRegionError(body) -> Kind.REGION
            code == 401 -> Kind.AUTH
            code == 403 && ("key" in b || "auth" in b || "permission" in b || "forbidden" in b || b.isBlank()) -> Kind.AUTH
            code == 402 || "insufficient" in b || "余额" in body || "balance" in b || ("quota" in b && code != 429) -> Kind.QUOTA
            code == 429 -> Kind.RATE
            code == 404 || "model_not_found" in b || "does not exist" in b || "模型不存在" in body -> Kind.NOT_FOUND
            code == 403 -> Kind.AUTH
            code >= 500 -> Kind.SERVER
            else -> Kind.OTHER
        }
    }

    /** Classifies free-form error text (chat failures, legacy probe strings). */
    fun classifyText(text: String): Kind {
        val code = Regex("(?:HTTP |\\[|^)(\\d{3})(?:\\]|\\b)").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val l = text.lowercase()
        return when {
            isRegionError(text) -> Kind.REGION
            code != null -> classify(code, text)
            listOf("connection closed", "connection reset", "timeout", "timed out", "unknown host", "unable to resolve", "dns", "tls", "ssl", "handshake", "failed to connect", "network is unreachable", "eof", "refused")
                .any { it in l } -> Kind.NETWORK
            "rate limit" in l || "rate-limit" in l -> Kind.RATE
            else -> Kind.OTHER
        }
    }

    /**
     * DNS-over-HTTPS resolver: AliDNS (reachable in mainland China) first, then
     * Cloudflare, by IP so the resolver itself needs no DNS. Falls back to the
     * system resolver. Results are cached for five minutes.
     */
    object DohDns : Dns {
        private data class Entry(val at: Long, val addrs: List<InetAddress>)
        private val cache = ConcurrentHashMap<String, Entry>()
        private val plain: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
        }
        private val resolvers = listOf(
            "https://223.5.5.5/resolve?type=1&name=",
            "https://1.1.1.1/dns-query?type=A&name=",
            "https://1.0.0.1/dns-query?type=A&name=",
        )

        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname.all { it.isDigit() || it == '.' || it == ':' }) return Dns.SYSTEM.lookup(hostname)
            cache[hostname]?.let { if (System.currentTimeMillis() - it.at < 300_000) return it.addrs }
            for (r in resolvers) {
                val addrs = runCatching {
                    plain.newCall(Request.Builder().url(r + hostname).header("Accept", "application/dns-json").build()).execute().use { resp ->
                        val ans = JSONObject(resp.body?.string().orEmpty()).optJSONArray("Answer") ?: return@use emptyList()
                        (0 until ans.length()).mapNotNull { i ->
                            val a = ans.getJSONObject(i)
                            if (a.optInt("type") == 1) runCatching { InetAddress.getByName(a.optString("data")) }.getOrNull() else null
                        }
                    }
                }.getOrDefault(emptyList())
                if (addrs.isNotEmpty()) {
                    cache[hostname] = Entry(System.currentTimeMillis(), addrs)
                    return addrs
                }
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }
}
