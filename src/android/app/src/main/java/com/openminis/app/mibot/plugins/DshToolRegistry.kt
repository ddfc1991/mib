package com.openminis.app.mibot.plugins

import com.openminis.app.data.model.AgentSchemaNode
import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * [fork/mibot-dsh-tools] D2 (notes/vd-mika-port.md §5): the Kotlin mirror of the
 * tools DSH plugins register in their host process (host.mjs `tools` service).
 *
 *  - Every plugin tool becomes an agent tool `plugin__<slug>__<tool>`, and every
 *    plugin with commands gets one more, `plugin__<slug>__command` (list / run).
 *    Names are sanitised to `[a-zA-Z0-9_-]`, ≤ 64 chars, with a collision suffix.
 *  - The plugin's JSON Schema becomes [AgentToolParam]s (nested schemas through
 *    [AgentSchemaNode]); a property the converter cannot express travels as a
 *    JSON string (as browser_use `cookies` does) and is decoded again in [prepareArgs].
 *  - Mibot's `tool_title` is prepended, like VdTools.def.
 *  - The declared risk (Mika's Read / Write / External) is carried through;
 *    commands are always [DshToolRisk.WRITE].
 *  - [snapshot] freezes the whole set: call it once at the start of an agent turn
 *    (Mika's freeze). A plugin that changes its tools mid-turn shows up next turn.
 *
 * Not wired into AgentTools / ChatViewModel yet (D3). The host side feeds it from
 * DshHostManager (tools/change, tools.list, commands.list) and clears it on stop.
 * Everything below the state section is pure and JVM-unit-tested (DshToolRegistryTest).
 */
enum class DshToolRisk(val wire: String) {
    READ("read"), WRITE("write"), EXTERNAL("external");

    companion object {
        /** Unknown / missing → WRITE (Mika's default for undeclared tools). */
        fun parse(s: String?): DshToolRisk = values().firstOrNull { it.wire.equals(s?.trim(), ignoreCase = true) } ?: WRITE
    }
}

/** One tool as the host reported it (host.mjs `tools.list` / `tools/change`). */
data class DshToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema of the arguments (type: object), as JSON text. */
    val parametersJson: String,
    val risk: DshToolRisk,
    val timeoutMs: Long? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): DshToolSpec? {
            val name = o.optString("name").trim().ifEmpty { return null }
            val params = o.optJSONObject("parameters") ?: JSONObject().put("type", "object").put("properties", JSONObject())
            return DshToolSpec(name, o.optString("description"), params.toString(), DshToolRisk.parse(o.optString("risk")),
                o.optLong("timeoutMs").takeIf { it > 0 })
        }

        fun listFrom(a: JSONArray?): List<DshToolSpec> =
            if (a == null) emptyList() else (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::fromJson) }
    }
}

/** One plugin command (host.mjs `commands.list`). */
data class DshCommandSpec(val name: String, val description: String = "") {
    companion object {
        fun listFrom(a: JSONArray?): List<DshCommandSpec> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
            when (val v = a.opt(i)) {
                is JSONObject -> v.optString("name").trim().removePrefix("/").ifEmpty { null }?.let { DshCommandSpec(it, v.optString("description")) }
                is String -> v.trim().removePrefix("/").ifEmpty { null }?.let { DshCommandSpec(it) }
                else -> null
            }
        }
    }
}

/** What one plugin's host currently offers (input to [DshToolRegistry.build]). */
data class DshPluginTools(
    val fullName: String,
    val label: String = fullName,
    val tools: List<DshToolSpec> = emptyList(),
    val commands: List<DshCommandSpec> = emptyList(),
)

/** One mirrored agent tool. [hostTool] is null for the per-plugin command tool. */
data class DshAgentTool(
    val agentName: String,
    val pluginFullName: String,
    val slug: String,
    val hostTool: String?,
    val risk: DshToolRisk,
    val definition: AgentToolDefinition,
    /** Top-level properties that travel as JSON strings, with the JSON type they decode to ("object"/"array"/"any"). */
    val jsonStringParams: Map<String, String> = emptyMap(),
    /** The plugin's own schema (null for the command tool). */
    val schemaJson: String? = null,
    val commands: List<String> = emptyList(),
    val timeoutMs: Long? = null,
) {
    val isCommand: Boolean get() = hostTool == null
}

/** A frozen view of every plugin tool for one agent turn. */
class DshToolSnapshot internal constructor(val version: Long, val tools: List<DshAgentTool>) {
    val byName: Map<String, DshAgentTool> = tools.associateBy { it.agentName }
    val definitions: List<AgentToolDefinition> get() = tools.map { it.definition }
    val names: Set<String> get() = byName.keys
    operator fun get(agentName: String): DshAgentTool? = byName[agentName]
    fun isEmpty() = tools.isEmpty()

    companion object { val EMPTY = DshToolSnapshot(0, emptyList()) }
}

object DshToolRegistry {
    const val PREFIX = "plugin__"
    const val SEP = "__"
    const val MAX_NAME = 64
    const val COMMAND_TOOL = "command"
    const val TITLE = "tool_title"

    // ------------------------------------------------------------------ state

    private val lock = Any()
    private val plugins = LinkedHashMap<String, DshPluginTools>()
    private val version = AtomicLong(0)
    @Volatile private var cached: DshToolSnapshot = DshToolSnapshot.EMPTY

    /** Replace a plugin's tools (tools/change notification or tools.list result). */
    fun updateTools(fullName: String, tools: List<DshToolSpec>, label: String? = null) = mutate(fullName, label) { it.copy(tools = tools) }

    /** Replace a plugin's commands (commands.list result, or names from a status notification). */
    fun updateCommands(fullName: String, commands: List<DshCommandSpec>, label: String? = null) = mutate(fullName, label) { it.copy(commands = commands) }

    /** Plugin stopped / host exited / uninstalled. */
    fun clear(fullName: String) = synchronized(lock) { if (plugins.remove(fullName) != null) bump() }

    fun clearAll() = synchronized(lock) { if (plugins.isNotEmpty()) { plugins.clear(); bump() } }

    /** Tools currently mirrored for one plugin (UI, D4). */
    fun toolsOf(fullName: String): List<DshToolSpec> = synchronized(lock) { plugins[fullName]?.tools.orEmpty() }

    fun commandsOf(fullName: String): List<DshCommandSpec> = synchronized(lock) { plugins[fullName]?.commands.orEmpty() }

    /**
     * The frozen tool set. Call once at the start of an agent turn and resolve every
     * call of that turn against the same snapshot; cheap when nothing changed.
     */
    fun snapshot(): DshToolSnapshot = synchronized(lock) {
        val v = version.get()
        cached.takeIf { it.version == v } ?: DshToolSnapshot(v, build(plugins.values.toList()))
            .also { cached = it }
    }

    private inline fun mutate(fullName: String, label: String?, f: (DshPluginTools) -> DshPluginTools) = synchronized(lock) {
        val cur = plugins[fullName] ?: DshPluginTools(fullName, label ?: fullName)
        val next = f(cur).let { if (label != null) it.copy(label = label) else it }
        if (next != cur) { plugins[fullName] = next; bump() }
    }

    private fun bump() { version.incrementAndGet() }

    // ------------------------------------------------------------------ pure: build

    /**
     * Build the agent tools for [entries]. Plugins are processed in fullName order
     * (tools in name order) so names are stable across turns.
     */
    fun build(entries: List<DshPluginTools>): List<DshAgentTool> {
        val taken = HashSet<String>()
        val slugs = HashSet<String>()
        val out = ArrayList<DshAgentTool>()
        for ((fullName, label, tools, commands) in entries.sortedBy { it.fullName.lowercase() }) {
            if (tools.isEmpty() && commands.isEmpty()) continue
            val slug = uniqueSlug(slugOf(fullName), slugs)
            // The command tool claims plugin__<slug>__command first; a plugin tool that is
            // itself called "command" then gets the collision suffix.
            val commandName = if (commands.isNotEmpty()) mangle(slug, COMMAND_TOOL, taken) else null
            val hostNames = HashSet<String>()
            for (t in tools.sortedBy { it.name }) {
                if (!hostNames.add(t.name)) continue
                val name = mangle(slug, t.name, taken)
                out += toolFor(name, fullName, label, slug, t)
            }
            if (commandName != null) out += commandToolFor(commandName, fullName, label, slug, commands.distinctBy { it.name })
        }
        return out
    }

    private fun toolFor(agentName: String, fullName: String, label: String, slug: String, t: DshToolSpec): DshAgentTool {
        val schema = runCatching { JSONObject(t.parametersJson) }.getOrElse { JSONObject().put("type", "object") }
        val conv = convertSchema(schema)
        val ownTitle = conv.params.containsKey(TITLE)
        val params = if (ownTitle) conv.params else linkedMapOf(TITLE to titleParam) + conv.params
        val required = if (ownTitle) conv.required else listOf(TITLE) + conv.required
        val def = AgentToolDefinition(
            name = agentName,
            description = describe(label, t.description, t.risk),
            parameters = params,
            required = required,
            propertyOrdering = params.keys.toList(),
        )
        return DshAgentTool(agentName, fullName, slug, t.name, t.risk, def, conv.jsonStrings, t.parametersJson, timeoutMs = t.timeoutMs)
    }

    private fun commandToolFor(agentName: String, fullName: String, label: String, slug: String, commands: List<DshCommandSpec>): DshAgentTool {
        val names = commands.map { it.name }
        val list = commands.joinToString("; ") { c -> if (c.description.isBlank()) c.name else "${c.name}: ${c.description.take(120)}" }
        val params = linkedMapOf(
            TITLE to titleParam,
            "action" to AgentToolParam("string", "list = show this plugin's commands; run = run one.", listOf("list", "run")),
            "command" to AgentToolParam("string", "Command to run (required for action=run).", names),
            "args" to AgentToolParam("string", "Optional JSON object of command arguments, e.g. {\"who\":\"me\"}."),
        )
        val def = AgentToolDefinition(
            name = agentName,
            description = clipDesc("[Plugin $label] Run one of this plugin's commands. Commands: $list"),
            parameters = params,
            required = listOf(TITLE, "action"),
            propertyOrdering = params.keys.toList(),
        )
        return DshAgentTool(agentName, fullName, slug, null, DshToolRisk.WRITE, def, mapOf("args" to "object"), commands = names)
    }

    private val titleParam = AgentToolParam("string", "A concise 5-10 word summary shown to the user. Use the user's language.")

    private fun describe(label: String, desc: String, risk: DshToolRisk): String {
        val tag = when (risk) { DshToolRisk.READ -> "read-only"; DshToolRisk.WRITE -> "may change things"; DshToolRisk.EXTERNAL -> "acts outside the phone" }
        return clipDesc("[Plugin $label · $tag] ${desc.trim()}".trim())
    }

    private fun clipDesc(s: String) = if (s.length <= 2000) s else s.take(1999) + "…"

    // ------------------------------------------------------------------ pure: names

    /** "owner/repo-name" → "repo-name" sanitised (lowercase, [a-z0-9-], ≤ 24, never empty). */
    fun slugOf(fullName: String): String {
        val base = fullName.substringAfterLast('/').lowercase()
        val s = sanitize(base).replace('_', '-').replace(Regex("-+"), "-").trim('-')
        return s.take(24).trim('-').ifEmpty { "plugin" }
    }

    private fun uniqueSlug(slug: String, used: MutableSet<String>): String {
        if (used.add(slug)) return slug
        var i = 2
        while (true) { val c = "${slug.take(21)}-$i"; if (used.add(c)) return c; i++ }
    }

    /** Anything outside [a-zA-Z0-9_-] → '_', runs of '_' collapsed (so "__" stays a pure separator). */
    fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9_-]"), "_").replace(Regex("_{2,}"), "_").trim('_')

    /**
     * `plugin__<slug>__<tool>`, ≤ [MAX_NAME] chars, unique within [taken] (added to it).
     * Long tool names are cut and get a short stable hash so two long names that share
     * a prefix stay distinct; a remaining clash gets `_2`, `_3`, ….
     */
    fun mangle(slug: String, tool: String, taken: MutableSet<String>): String {
        val s = sanitize(slug).ifEmpty { "plugin" }
        val t = sanitize(tool).ifEmpty { "tool" }
        var name = "$PREFIX$s$SEP$t"
        if (name.length > MAX_NAME) {
            val hash = stableHash(tool)
            val head = "$PREFIX${s.take(20).trimEnd('_')}$SEP"
            val room = MAX_NAME - head.length - hash.length - 1
            name = head + t.take(room).trimEnd('_') + "_" + hash
        }
        if (taken.add(name)) return name
        var i = 2
        while (true) {
            val suffix = "_$i"
            val c = if (name.length + suffix.length <= MAX_NAME) name + suffix else name.take(MAX_NAME - suffix.length) + suffix
            if (taken.add(c)) return c
            i++
        }
    }

    private fun stableHash(s: String): String {
        var h = 0x811C9DC5.toInt()
        for (b in s.toByteArray(Charsets.UTF_8)) { h = h xor (b.toInt() and 0xff); h *= 0x01000193 }
        return Integer.toHexString(h).padStart(8, '0').take(6)
    }

    // ------------------------------------------------------------------ pure: schema

    data class Converted(
        val params: LinkedHashMap<String, AgentToolParam>,
        val required: List<String>,
        /** property → JSON type it decodes to when it travels as a string. */
        val jsonStrings: Map<String, String>,
    )

    /** Plugin JSON Schema (type: object) → Mibot parameters. Never throws. */
    fun convertSchema(schema: JSONObject): Converted {
        val props = schema.optJSONObject("properties") ?: JSONObject()
        val params = LinkedHashMap<String, AgentToolParam>()
        val jsonStrings = LinkedHashMap<String, String>()
        for (key in props.keys().asSequence().toList()) {
            val p = props.optJSONObject(key) ?: JSONObject()
            val desc = p.optString("description").ifBlank { p.optString("title") }
            val type = typeOf(p)
            val enum = stringEnum(p)
            when {
                type == "string" || (type == null && enum != null) -> params[key] = AgentToolParam("string", desc, enum)
                type == "integer" || type == "number" || type == "boolean" -> params[key] = AgentToolParam(type, desc, null)
                type == "array" || type == "object" -> {
                    val node = toNode(p, 0)
                    if (node != null) params[key] = AgentToolParam(type, desc, null, node)
                    else {
                        jsonStrings[key] = type
                        params[key] = AgentToolParam("string", jsonStringDesc(desc, type, p))
                    }
                }
                else -> {
                    jsonStrings[key] = "any"
                    params[key] = AgentToolParam("string", jsonStringDesc(desc, "any", p))
                }
            }
        }
        val req = schema.optJSONArray("required")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty().filter { it in params }
        return Converted(params, req, jsonStrings)
    }

    private fun jsonStringDesc(desc: String, type: String, p: JSONObject): String {
        val shape = JSONObject(p.toString()).apply { remove("description") }.toString().let { if (it.length > 300) it.take(299) + "…" else it }
        val what = when (type) { "object" -> "a JSON object"; "array" -> "a JSON array"; else -> "a JSON value" }
        return "${desc.trim()} (Pass $what encoded as a string; schema: $shape)".trim()
    }

    /** The single non-null JSON type of a property, or null for unions / missing. */
    fun typeOf(p: JSONObject): String? = when (val t = p.opt("type")) {
        is String -> t
        is JSONArray -> (0 until t.length()).map { t.optString(it) }.filter { it != "null" }.singleOrNull()
        else -> null
    }

    private fun stringEnum(p: JSONObject): List<String>? {
        val a = p.optJSONArray("enum") ?: return null
        val vals = (0 until a.length()).map { a.opt(it) }
        return if (vals.isNotEmpty() && vals.all { it is String }) vals.map { it as String } else null
    }

    /** JSON Schema → [AgentSchemaNode] where it can be expressed exactly enough, else null. */
    fun toNode(p: JSONObject, depth: Int): AgentSchemaNode? {
        if (depth > 8) return null
        if (p.has("anyOf") || p.has("oneOf") || p.has("allOf") || p.has("\$ref")) return null
        val desc = p.optString("description").ifBlank { null }
        return when (typeOf(p) ?: if (stringEnum(p) != null) "string" else null) {
            "string" -> AgentSchemaNode.Str(desc, stringEnum(p))
            "integer" -> AgentSchemaNode.Integer(desc)
            "boolean" -> AgentSchemaNode.Bool(desc)
            "array" -> {
                val items = p.optJSONObject("items") ?: return null
                val node = toNode(items, depth + 1) ?: return null
                AgentSchemaNode.Arr(desc, node, p.optInt("minItems", -1).takeIf { it >= 0 }, p.optInt("maxItems", -1).takeIf { it >= 0 })
            }
            "object" -> {
                val props = p.optJSONObject("properties") ?: return null
                if (p.opt("additionalProperties") is JSONObject) return null // a dict: not expressible
                val list = props.keys().asSequence().toList().map { k -> k to (toNode(props.optJSONObject(k) ?: return null, depth + 1) ?: return null) }
                val req = p.optJSONArray("required")?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty().filter { r -> list.any { it.first == r } }
                AgentSchemaNode.Obj(desc, list, req)
            }
            else -> null // "number" has no node type; unions; untyped
        }
    }

    // ------------------------------------------------------------------ pure: arguments

    /**
     * Problems with the model's [args] for [tool]: missing required properties, a wrong
     * top-level type, a value outside a string enum, a JSON-string property that does
     * not parse to its type. Empty = fine. (Nested bounds are the plugin's job.)
     */
    fun validateArgs(tool: DshAgentTool, args: JSONObject): List<String> {
        val errors = ArrayList<String>()
        val def = tool.definition
        for (r in def.required) if (r != TITLE && (!args.has(r) || args.isNull(r))) errors += "missing required argument \"$r\""
        for ((key, p) in def.parameters) {
            if (key == TITLE && !tool.ownsTitle()) continue
            if (!args.has(key) || args.isNull(key)) continue
            val v = args.opt(key)
            val js = tool.jsonStringParams[key]
            if (js != null) {
                if (v is String) {
                    val parsed = parseJson(v)
                    if (parsed == null) { errors += "\"$key\" must be ${article(js)} JSON-encoded as a string (did not parse)"; continue }
                    if (!jsonTypeOk(js, parsed)) errors += "\"$key\" must decode to ${article(js)}"
                } else if (!jsonTypeOk(js, v)) errors += "\"$key\" must be ${article(js)}"
                continue
            }
            when (p.type) {
                "string" -> if (v !is String) errors += "\"$key\" must be a string"
                    else if (p.enumValues != null && v !in p.enumValues!!) errors += "\"$key\" must be one of ${p.enumValues!!.joinToString()}"
                "integer" -> if (!isInteger(v)) errors += "\"$key\" must be an integer"
                "number" -> if (!isNumber(v)) errors += "\"$key\" must be a number"
                "boolean" -> if (v !is Boolean && v != "true" && v != "false") errors += "\"$key\" must be a boolean"
                "array" -> if (v !is JSONArray && !(v is String && parseJson(v) is JSONArray)) errors += "\"$key\" must be an array"
                "object" -> if (v !is JSONObject && !(v is String && parseJson(v) is JSONObject)) errors += "\"$key\" must be an object"
            }
        }
        if (tool.isCommand) {
            val action = args.optString("action")
            if (action == "run") {
                val c = args.optString("command")
                if (c.isBlank()) errors += "\"command\" is required for action=run"
            }
        }
        return errors
    }

    /**
     * The arguments to send to the host: Mibot's `tool_title` removed (unless the plugin
     * declares it), JSON-string properties decoded, nested JSON-in-a-string coerced,
     * "true"/"false" and numeric strings for scalar properties fixed up.
     */
    fun prepareArgs(tool: DshAgentTool, args: JSONObject): JSONObject {
        val out = JSONObject(tool.definition.coerceNestedJsonStrings(args).toString())
        if (!tool.ownsTitle()) out.remove(TITLE)
        for ((key, _) in tool.jsonStringParams) {
            val v = out.opt(key)
            if (v is String) parseJson(v)?.let { out.put(key, it) }
        }
        for ((key, p) in tool.definition.parameters) {
            val v = out.opt(key) as? String ?: continue
            when (p.type) {
                "boolean" -> if (v == "true" || v == "false") out.put(key, v == "true")
                "integer" -> v.trim().toLongOrNull()?.let { out.put(key, it) }
                "number" -> v.trim().toDoubleOrNull()?.let { out.put(key, it) }
            }
        }
        return out
    }

    private fun DshAgentTool.ownsTitle(): Boolean =
        hostTool != null && schemaJson != null && runCatching { JSONObject(schemaJson).optJSONObject("properties")?.has(TITLE) == true }.getOrDefault(false)

    private fun article(t: String) = when (t) { "object" -> "a JSON object"; "array" -> "a JSON array"; else -> "a JSON value" }
    private fun jsonTypeOk(t: String, v: Any?) = when (t) { "object" -> v is JSONObject; "array" -> v is JSONArray; else -> true }
    private fun isInteger(v: Any?) = when (v) {
        is Int, is Long, is Short, is Byte -> true
        is Double -> v % 1.0 == 0.0
        is Float -> v % 1f == 0f
        is java.math.BigInteger -> true
        is java.math.BigDecimal -> v.stripTrailingZeros().scale() <= 0
        is String -> v.trim().toLongOrNull() != null
        else -> false
    }
    private fun isNumber(v: Any?) = v is Number || (v is String && v.trim().toDoubleOrNull() != null)

    private fun parseJson(s: String): Any? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return runCatching {
            when {
                t.startsWith("{") -> JSONObject(t)
                t.startsWith("[") -> JSONArray(t)
                else -> org.json.JSONTokener(t).nextValue().takeIf { it !is String || t.startsWith("\"") }
            }
        }.getOrNull()
    }
}
