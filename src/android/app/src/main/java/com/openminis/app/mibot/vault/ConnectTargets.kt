package com.openminis.app.mibot.vault

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * [fork/mibot-v5b2] Pure (no Android) checks of the connect targets in
 * assets/mibot/services.json and assets/mibot/key_recipes.json, shared by the
 * app (owner lookup) and the JVM unit test (ConnectTargetsTest).
 *
 * The v5-B1 bug: 「连接 腾讯混元·元宝」 showed platform.openai.com/api-keys. The
 * sheet kept the previous provider's URL; here every connect URL must belong
 * to the provider it is listed under, never to another one.
 *
 * A domain entry may carry a path ("console.cloud.tencent.com/hunyuan") so two
 * services can share one console host; the most specific match owns a URL.
 */
object ConnectTargets {
    data class Svc(val id: String, val domains: List<String>, val urls: Map<String, String>, val packages: List<String>)

    fun parseServices(json: String): List<Svc> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it).lowercase() } } ?: emptyList()
            val urls = LinkedHashMap<String, String>()
            for (k in listOf("url", "keys", "login")) o.optString(k).takeIf { it.isNotBlank() }?.let { urls[k] = it }
            Svc(o.getString("id"), strs("domains"), urls, o.optJSONArray("packages")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList())
        }
    }

    /** "https://Console.Cloud.Tencent.com/hunyuan/api-key?x" -> "console.cloud.tencent.com/hunyuan/api-key". */
    fun hostPath(url: String): String? = runCatching {
        val u = URI(url.trim())
        val host = u.host?.lowercase()?.removePrefix("www.") ?: return null
        host + (u.rawPath ?: "")
    }.getOrNull()

    /** How specifically [domain] matches [hp] (host+path), or -1. */
    fun matchScore(domain: String, hp: String): Int {
        val d = domain.lowercase().removePrefix("www.")
        val host = hp.substringBefore('/')
        return if ('/' in d) {
            if (hp == d || hp.startsWith("$d/") || hp.startsWith("$d?")) d.length else -1
        } else {
            if (host == d || host.endsWith(".$d")) d.length else -1
        }
    }

    /** The service that owns [url] (most specific domain match), or null. Ties -> null. */
    fun owner(url: String, services: List<Svc>): String? {
        val hp = hostPath(url) ?: return null
        var best = -1; var bestId: String? = null; var tie = false
        for (s in services) for (d in s.domains) {
            val sc = matchScore(d, hp)
            if (sc > best) { best = sc; bestId = s.id; tie = false } else if (sc == best && sc >= 0 && bestId != s.id) tie = true
        }
        return if (best < 0 || tie) null else bestId
    }

    /** Every problem found (empty = good). */
    fun problems(servicesJson: String, recipesJson: String?): List<String> {
        val svcs = parseServices(servicesJson)
        val out = mutableListOf<String>()
        val ids = svcs.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { out += "duplicate service id $it" }
        for (s in svcs) for ((k, u) in s.urls) {
            if (!u.startsWith("https://")) { out += "${s.id}.$k is not https: $u"; continue }
            val own = owner(u, svcs)
            if (own != s.id) out += "${s.id}.$k ($u) belongs to ${own ?: "nobody"}"
        }
        // A package name must not be claimed by two services (app-login routing).
        svcs.flatMap { s -> s.packages.map { it to s.id } }.groupBy { it.first }
            .filter { e -> e.value.map { it.second }.distinct().size > 1 }
            .forEach { (pkg, l) -> out += "package $pkg claimed by ${l.map { it.second }.distinct()}" }
        if (recipesJson != null) {
            val root = JSONObject(recipesJson)
            if (root.optInt("version", 0) < 1) out += "key_recipes.json needs version >= 1"
            val recipes = root.optJSONObject("recipes") ?: JSONObject()
            // [fork/mibot-autokey-v2] A recipe's own `hosts` (a console that moved, e.g. platform.claude.com)
            // count as that service's domains for the recipe URLs only; services.json URLs keep the strict check.
            val withHosts = svcs.map { sv ->
                val a = recipes.optJSONObject(sv.id)?.optJSONArray("hosts") ?: return@map sv
                sv.copy(domains = (sv.domains + (0 until a.length()).map { a.optString(it).lowercase() }.filter { it.isNotBlank() }).distinct())
            }
            for (id in recipes.keys()) {
                val r = recipes.getJSONObject(id)
                val svc = svcs.find { it.id == id }
                if (svc == null) { out += "recipe $id has no service"; continue }
                if (r.optInt("v", 0) < 1) out += "recipe $id needs v >= 1"
                for (k in listOf("start", "login")) {
                    val u = r.optString(k).takeIf { it.isNotBlank() } ?: continue
                    val own = owner(u, withHosts)
                    if (own != id) out += "recipe $id.$k ($u) belongs to ${own ?: "nobody"}"
                }
                r.optString("keyRegex").takeIf { it.isNotBlank() }?.let { rx ->
                    runCatching { Regex(rx) }.onFailure { out += "recipe $id keyRegex invalid" }
                }
            }
        }
        return out
    }
}
