package com.openminis.app.mibot.vault

import android.content.Context
import org.json.JSONArray

/**
 * [fork/mibot-v4] Known-services catalog (assets/mibot/services.json): the
 * provider registry the 账号 page groups by, auto-discovery matches against
 * (domains / Android package names), and auto-connect picks a method from.
 *
 * Logos are rendered PNGs of Simple Icons (CC0-1.0) and LobeHub Icons (MIT);
 * see THIRD_PARTY_LICENSES.md.
 */
data class VaultService(
    val id: String,
    val name: String,
    val sub: String,
    val cat: String,
    val domains: List<String>,
    val packages: List<String>,
    /** oauth_openrouter | api_key | web */
    val auth: String,
    val url: String,
    val manifest: String?,
    val free: String?,
    val keyRegex: String?,
    val logo: String?,
    val color: String?,
    /** [fork/mibot-v4] Free sign-up AND free models (not just a trial credit). */
    val freeModels: Boolean = false,
) {
    fun matchesSite(site: String): Boolean {
        val s = site.lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
        return s.isNotEmpty() && domains.any { d -> s == d || s.endsWith(".$d") }
    }
}

object VaultServices {
    /** Display order + Chinese / English labels of the categories. */
    val categories: List<Triple<String, String, String>> = listOf(
        Triple("ai", "AI 服务商", "AI providers"),
        Triple("mail", "邮箱", "Email"),
        Triple("dev", "开发与服务器", "Dev & servers"),
        Triple("social", "社交与资讯", "Social & news"),
        Triple("finance", "金融与加密", "Finance & crypto"),
        Triple("office", "效率与办公", "Productivity"),
        Triple("other", "其他", "Other"),
    )

    /** The section a not-yet-connected category is listed under. */
    val addCategories: Map<String, Pair<String, String>> = mapOf(
        "ai" to ("AI 模型" to "AI models"),
        "mail" to ("邮箱" to "Email"),
        "dev" to ("开发与服务器" to "Dev & servers"),
        "office" to ("效率与办公" to "Productivity"),
        "finance" to ("金融与加密" to "Finance & crypto"),
        "social" to ("社交与消息" to "Social & messaging"),
        "other" to ("其他" to "Other"),
    )

    /** Old free-text vault categories -> catalog category. */
    fun catOfLegacy(category: String): String = when (category) {
        "AI 服务", "AI services" -> "ai"
        "邮箱", "Email" -> "mail"
        "云服务器", "Cloud servers" -> "dev"
        "社交", "Social" -> "social"
        else -> "other"
    }

    fun legacyCategory(cat: String): String = when (cat) {
        "ai" -> "AI 服务"
        "mail" -> "邮箱"
        "dev" -> "云服务器"
        "social" -> "社交"
        else -> "其他"
    }

    @Volatile private var cache: List<VaultService>? = null

    fun all(ctx: Context): List<VaultService> {
        cache?.let { return it }
        val list = runCatching {
            val arr = JSONArray(ctx.applicationContext.assets.open("mibot/services.json").bufferedReader().use { it.readText() })
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                VaultService(
                    id = o.getString("id"), name = o.optString("name"), sub = o.optString("sub"), cat = o.optString("cat", "other"),
                    domains = strs("domains"), packages = strs("packages"), auth = o.optString("auth", "web"), url = o.optString("url"),
                    manifest = o.optStringOrNull("manifest"), free = o.optStringOrNull("free"), keyRegex = o.optStringOrNull("keyRegex"),
                    logo = o.optStringOrNull("logo"), color = o.optStringOrNull("color"),
                    freeModels = o.optBoolean("freeModels", false),
                )
            }
        }.getOrDefault(emptyList())
        cache = list
        return list
    }

    fun get(ctx: Context, id: String?): VaultService? = id?.let { i -> all(ctx).find { it.id == i } }

    /** Which catalog service an entry belongs to (explicit id, else by domain). */
    fun serviceOf(ctx: Context, e: VaultEntry): VaultService? =
        get(ctx, e.serviceId) ?: all(ctx).firstOrNull { it.matchesSite(e.site) }

    // ----------------------------------------------------------------- health

    enum class Health { OK, WARN, INVALID }

    fun health(e: VaultEntry): Health {
        val st = e.status
        val err = st?.error.orEmpty()
        return when {
            !e.hasSecret -> Health.WARN
            err.contains("HTTP 401") || err.contains("HTTP 403") -> Health.INVALID
            err.isNotEmpty() && err != "no status manifest" -> Health.WARN
            st?.lastCheckinState == "error" || st?.lastCheckinState == "manual" -> Health.WARN
            else -> Health.OK
        }
    }

    /** One service row on the 账号 page: its accounts and the combined state. */
    data class Group(val service: VaultService?, val key: String, val name: String, val cat: String, val entries: List<VaultEntry>) {
        val warn get() = entries.count { health(it) == Health.WARN }
        val invalid get() = entries.count { health(it) == Health.INVALID }
        val hasWebLogin get() = entries.any { it.kind == "cookie" }
        val worst: Health get() = when {
            invalid > 0 -> Health.INVALID
            warn > 0 -> Health.WARN
            else -> Health.OK
        }
    }

    fun groups(ctx: Context, entries: List<VaultEntry>): List<Group> {
        val byKey = LinkedHashMap<String, MutableList<VaultEntry>>()
        val svc = HashMap<String, VaultService?>()
        for (e in entries) {
            val s = serviceOf(ctx, e)
            val key = s?.id ?: ("site:" + e.site.lowercase())
            svc[key] = s
            byKey.getOrPut(key) { mutableListOf() }.add(e)
        }
        return byKey.map { (k, list) ->
            val s = svc[k]
            Group(s, k, s?.name ?: list.first().site, s?.cat ?: catOfLegacy(list.first().category), list)
        }
    }

    // --------------------------------------------------------------- ignored

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("mibot_vault_services", Context.MODE_PRIVATE)

    fun ignored(ctx: Context): Set<String> = prefs(ctx).getStringSet("ignored", emptySet()) ?: emptySet()

    fun setIgnored(ctx: Context, id: String, ignore: Boolean) {
        val cur = ignored(ctx).toMutableSet()
        if (ignore) cur.add(id) else cur.remove(id)
        prefs(ctx).edit().putStringSet("ignored", cur).apply()
    }

    fun masked(ctx: Context): Boolean = prefs(ctx).getBoolean("masked", true)
    fun setMasked(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("masked", v).apply()

    /** "me@example.com" -> "me***@ex***.com", phone -> "138****0000". */
    fun mask(s: String): String {
        if (s.isBlank()) return s
        if ('@' in s) {
            val (u, d) = s.split('@', limit = 2)
            val dn = d.substringBeforeLast('.'); val tld = d.substringAfterLast('.', "")
            return u.take(2) + "***@" + dn.take(2) + "***" + if (tld.isNotEmpty()) ".$tld" else ""
        }
        if (s.length >= 7 && s.all { it.isDigit() }) return s.take(3) + "****" + s.takeLast(4)
        return if (s.length <= 3) s.take(1) + "**" else s.take(2) + "***" + s.takeLast(1)
    }
}
