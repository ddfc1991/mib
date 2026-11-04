package com.openminis.app.mibot.plugins

import org.json.JSONArray
import org.json.JSONObject

/**
 * [fork/mibot-dsh-tools] D3 (notes/vd-mika-port.md §5): per-tool permission for DSH
 * plugin tools, plus the argument preview and output clip / scrub used around a call.
 * Pure (no Android): JVM-unit-tested in DshToolPolicyTest.
 *
 *  - A setting per host tool lives in the plugin's installed.json record
 *    ([InstalledPlugin.toolPolicy]); missing = the default for the tool's risk.
 *  - Defaults: READ → allow (runs directly); WRITE / EXTERNAL → ask.
 *  - EXTERNAL asks even when set to allow, unless the user picked 「始终允许」 in the
 *    confirmation dialog ([Setting.ALWAYS]; Mika's ConfirmChoice.Always).
 *  - A denial within one run is remembered (Mika `declined`) so the model is not asked again.
 */
object DshToolPolicy {
    /** Policy key of the per-plugin command tool (host tool names never start with "/"). */
    const val COMMAND_KEY = "/command"
    const val OUTPUT_CAP = 64 * 1024
    const val PREVIEW_CHARS = 200
    const val MASK = "••••••"

    enum class Setting(val wire: String) {
        ALLOW("allow"), ASK("ask"), DENY("deny"),
        /** Allow without asking, external tools included (only set from the dialog). */
        ALWAYS("always");

        companion object {
            fun parse(s: String?): Setting? = values().firstOrNull { it.wire.equals(s?.trim(), ignoreCase = true) }
        }
    }

    enum class Decision { RUN, ASK, DENY, DECLINED }

    fun keyOf(tool: DshAgentTool): String = tool.hostTool ?: COMMAND_KEY

    fun defaultFor(risk: DshToolRisk): Setting = if (risk == DshToolRisk.READ) Setting.ALLOW else Setting.ASK

    /** The setting in force for a tool: the stored one, else the default for its risk. */
    fun settingOf(stored: Map<String, String>, key: String, risk: DshToolRisk): Setting =
        Setting.parse(stored[key]) ?: defaultFor(risk)

    /** What the three-way switch shows (ALWAYS reads as 允许). */
    fun switchOf(setting: Setting): Setting = if (setting == Setting.ALWAYS) Setting.ALLOW else setting

    fun decide(setting: Setting, risk: DshToolRisk, declinedThisRun: Boolean): Decision = when {
        setting == Setting.DENY -> Decision.DENY
        declinedThisRun -> Decision.DECLINED
        setting == Setting.ALWAYS -> Decision.RUN
        setting == Setting.ALLOW -> if (risk == DshToolRisk.EXTERNAL) Decision.ASK else Decision.RUN
        else -> Decision.ASK
    }

    /** The setting to store after 「始终允许」 in the dialog. */
    fun afterAlways(risk: DshToolRisk): Setting = if (risk == DshToolRisk.EXTERNAL) Setting.ALWAYS else Setting.ALLOW

    /**
     * New policy map with [key] set to [setting]; setting it to the risk default
     * removes the entry (so "reset" is just an empty map).
     */
    fun withSetting(stored: Map<String, String>, key: String, risk: DshToolRisk, setting: Setting): Map<String, String> =
        if (setting == defaultFor(risk)) stored - key else stored + (key to setting.wire)

    // ------------------------------------------------------------------ run memory

    /** Denials per run (keyed by the assistant message of the run), bounded. */
    class RunMemory(private val maxRuns: Int = 32) {
        private val runs = object : LinkedHashMap<String, MutableSet<String>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableSet<String>>?) = size > maxRuns
        }
        @Synchronized fun declined(run: String, tool: String): Boolean = runs[run]?.contains(tool) == true
        @Synchronized fun decline(run: String, tool: String) { runs.getOrPut(run) { HashSet() }.add(tool) }
        @Synchronized fun forget(tool: String) { runs.values.forEach { it.remove(tool) } }
    }

    // ------------------------------------------------------------------ preview

    private val SENSITIVE_KEY = Regex("(?i)(pass(word|wd)?|secret|token|api[_-]?key|apikey|cookie|auth|credential|private[_-]?key|otp|pin)")

    /**
     * ≤ [PREVIEW_CHARS]-char one-line preview of the arguments for the confirmation
     * dialog (Mika ConfirmationCopy): `tool_title` dropped, values of secret-looking
     * keys masked, control characters / newlines flattened.
     */
    fun preview(args: JSONObject, max: Int = PREVIEW_CHARS): String {
        val copy = mask(JSONObject(args.toString()).apply { remove(DshToolRegistry.TITLE) }, 0)
        val sb = StringBuilder()
        render(copy, sb, 0, max + 1)
        val flat = sb.toString().replace(Regex("[\\p{Cntrl}\\u2028\\u2029]"), " ").replace(Regex("\\s{2,}"), " ")
        return if (flat.length <= max) flat else flat.take(max - 1) + "…"
    }

    /** Compact human JSON-ish rendering: no unicode escapes (Chinese stays readable), stops past [limit]. */
    private fun render(v: Any?, sb: StringBuilder, depth: Int, limit: Int) {
        if (sb.length > limit) return
        when (v) {
            is JSONObject -> {
                sb.append('{')
                v.keys().asSequence().toList().forEachIndexed { i, k ->
                    if (sb.length > limit) return
                    if (i > 0) sb.append(", ")
                    sb.append(k).append(": ")
                    render(v.opt(k), sb, depth + 1, limit)
                }
                sb.append('}')
            }
            is JSONArray -> {
                sb.append('[')
                for (i in 0 until v.length()) {
                    if (sb.length > limit) return
                    if (i > 0) sb.append(", ")
                    render(v.opt(i), sb, depth + 1, limit)
                }
                sb.append(']')
            }
            is String -> sb.append('"').append(if (v.length > limit) v.take(limit) else v).append('"')
            null, JSONObject.NULL -> sb.append("null")
            else -> sb.append(v.toString())
        }
    }

    private fun mask(v: Any?, depth: Int): Any? = when {
        depth > 8 -> "…"
        v is JSONObject -> JSONObject().also { o ->
            for (k in v.keys().asSequence().toList()) o.put(k, if (SENSITIVE_KEY.containsMatchIn(k) && v.opt(k) !is JSONObject && v.opt(k) !is JSONArray) MASK else mask(v.opt(k), depth + 1))
        }
        v is JSONArray -> JSONArray().also { a -> for (i in 0 until v.length()) a.put(mask(v.opt(i), depth + 1)) }
        v is String && v.contains("{{vault:") -> v // a placeholder is not the secret
        else -> v
    }

    // ------------------------------------------------------------------ output

    /**
     * Replace every [secrets] value (≥ 4 chars, longest first) and its JSON-escaped
     * form with [MASK]. Applied to everything a plugin tool returns.
     */
    fun scrub(output: String, secrets: Collection<String>): String {
        val forms = secrets.asSequence().filter { it.length >= 4 }
            .flatMap { s -> sequenceOf(s, JSONObject.quote(s).let { q -> q.substring(1, q.length - 1) }) }
            .filter { it.length >= 4 }.distinct().sortedByDescending { it.length }.toList()
        return forms.fold(output) { acc, s -> acc.replace(s, MASK) }
    }

    /** UTF-8 byte clip to [cap] with a visible marker (Mika OutputClip); never splits a code point. */
    fun clip(s: String, cap: Int = OUTPUT_CAP): String {
        val bytes = s.toByteArray(Charsets.UTF_8)
        if (bytes.size <= cap) return s
        val marker = "\n…[output truncated: ${bytes.size} bytes, limit $cap]"
        var cut = cap - marker.toByteArray(Charsets.UTF_8).size
        while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut--
        return String(bytes, 0, cut.coerceAtLeast(0), Charsets.UTF_8) + marker
    }

    /** Scrub, then clip: what the model sees. */
    fun finish(output: String, secrets: Collection<String>, cap: Int = OUTPUT_CAP): String = clip(scrub(output, secrets), cap)
}
