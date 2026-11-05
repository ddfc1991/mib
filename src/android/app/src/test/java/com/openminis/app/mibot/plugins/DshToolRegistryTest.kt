package com.openminis.app.mibot.plugins

import com.openminis.app.data.model.AgentSchemaNode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork/mibot-dsh-tools] D2: name mangling, schema conversion, argument validation, snapshots. */
class DshToolRegistryTest {

    @After fun reset() = DshToolRegistry.clearAll()

    private val nameRe = Regex("^[A-Za-z0-9_-]{1,64}$")

    private fun spec(name: String, schema: String = """{"type":"object","properties":{}}""", risk: String? = null) =
        DshToolSpec.fromJson(JSONObject().put("name", name).put("description", "d").put("parameters", JSONObject(schema)).apply { risk?.let { put("risk", it) } })!!

    // ------------------------------------------------------------ names

    @Test fun `slug comes from the repo name, sanitised`() {
        assertEquals("modlens", DshToolRegistry.slugOf("liustack/modlens"))
        assertEquals("dsh-our-free-model", DshToolRegistry.slugOf("Ebony-Vinyl/dsh-our-free-model"))
        assertEquals("deepseek-balance-whale-w", DshToolRegistry.slugOf("MeteorNOX/DeepSeek-Balance-Whale-Widget"))
        assertEquals("plugin", DshToolRegistry.slugOf("x/中文"))
        assertEquals("a-b-c", DshToolRegistry.slugOf("o/a__b..c"))
    }

    @Test fun `mangle builds plugin__slug__tool`() {
        val taken = HashSet<String>()
        assertEquals("plugin__modlens__modlens_read_image", DshToolRegistry.mangle("modlens", "modlens_read_image", taken))
        assertEquals("plugin__x__a_b_c", DshToolRegistry.mangle("x", "a.b:c", taken))
        // "__" inside a tool name never reads as a separator
        assertEquals("plugin__x__a_b", DshToolRegistry.mangle("x", "a__b", taken))
        assertEquals("plugin__x__tool", DshToolRegistry.mangle("x", "中文", taken))
    }

    @Test fun `mangle caps at 64 chars and keeps long names distinct`() {
        val taken = HashSet<String>()
        val a = DshToolRegistry.mangle("some-very-long-plugin-slug", "x".repeat(80) + "_alpha", taken)
        val b = DshToolRegistry.mangle("some-very-long-plugin-slug", "x".repeat(80) + "_beta", taken)
        assertTrue(a, a.length <= 64 && nameRe.matches(a))
        assertTrue(b, b.length <= 64 && nameRe.matches(b))
        assertTrue(a != b)
        assertTrue(a.startsWith("plugin__some-very-long-plugi"))
    }

    @Test fun `mangle adds a collision suffix`() {
        val taken = HashSet<String>()
        assertEquals("plugin__x__a_b", DshToolRegistry.mangle("x", "a.b", taken))
        assertEquals("plugin__x__a_b_2", DshToolRegistry.mangle("x", "a-b".replace('-', ':'), taken))
        assertEquals("plugin__x__a_b_3", DshToolRegistry.mangle("x", "a b", taken))
        val long = HashSet<String>()
        val n1 = DshToolRegistry.mangle("s", "t".repeat(54), long) // exactly 64
        assertEquals(64, n1.length)
        val n2 = DshToolRegistry.mangle("s", "t".repeat(54), long)
        assertEquals(64, n2.length); assertTrue(n2.endsWith("_2")); assertTrue(n1 != n2)
    }

    @Test fun `build mirrors tools plus one command tool, reserving the command name`() {
        val tools = DshToolRegistry.build(listOf(
            DshPluginTools("liustack/modlens", tools = listOf(spec("modlens_read_image", risk = "read"), spec("command"))),
            DshPluginTools("a/fixture", tools = listOf(spec("fixture_echo")), commands = listOf(DshCommandSpec("hello", "say hello"), DshCommandSpec("bye"))),
            DshPluginTools("b/empty"),
        ))
        val names = tools.map { it.agentName }
        assertEquals(listOf("plugin__fixture__fixture_echo", "plugin__fixture__command", "plugin__modlens__command", "plugin__modlens__modlens_read_image"), names)
        names.forEach { assertTrue(it, nameRe.matches(it)) }
        val cmd = tools.first { it.agentName == "plugin__fixture__command" }
        assertTrue(cmd.isCommand); assertNull(cmd.hostTool); assertEquals(DshToolRisk.WRITE, cmd.risk)
        assertEquals(listOf("hello", "bye"), cmd.definition.parameters["command"]!!.enumValues)
        assertTrue(cmd.definition.description.contains("hello: say hello"))
        // a plugin tool literally called "command" is a plain tool, not the command tool
        val plain = tools.first { it.agentName == "plugin__modlens__command" }
        assertEquals("command", plain.hostTool)
        assertEquals(DshToolRisk.READ, tools.first { it.hostTool == "modlens_read_image" }.risk)
        // with commands too, the command tool keeps the plain name
        val both = DshToolRegistry.build(listOf(DshPluginTools("o/p", tools = listOf(spec("command")), commands = listOf(DshCommandSpec("c")))))
        assertEquals(mapOf("plugin__p__command_2" to "command", "plugin__p__command" to null), both.associate { it.agentName to it.hostTool })
    }

    @Test fun `same slug from two plugins gets distinct names`() {
        val tools = DshToolRegistry.build(listOf(
            DshPluginTools("a/modlens", tools = listOf(spec("read"))),
            DshPluginTools("b/modlens", tools = listOf(spec("read"))),
        ))
        assertEquals(listOf("plugin__modlens__read", "plugin__modlens-2__read"), tools.map { it.agentName })
    }

    // ------------------------------------------------------------ risk

    @Test fun `risk parsing defaults to write`() {
        assertEquals(DshToolRisk.READ, DshToolRisk.parse("read"))
        assertEquals(DshToolRisk.EXTERNAL, DshToolRisk.parse("External"))
        assertEquals(DshToolRisk.WRITE, DshToolRisk.parse(null))
        assertEquals(DshToolRisk.WRITE, DshToolRisk.parse("destructive"))
        assertEquals(DshToolRisk.WRITE, spec("t").risk)
    }

    // ------------------------------------------------------------ schema conversion

    private val modlensSchema = """{"type":"object","properties":{
        "path":{"type":"string","description":"Absolute local file path"},
        "prompt":{"type":"string","description":"Optional extra focus"}},"required":["path"]}"""

    @Test fun `scalar schema converts with tool_title first`() {
        val t = DshToolRegistry.build(listOf(DshPluginTools("liustack/modlens", tools = listOf(spec("modlens_read_image", modlensSchema, "read"))))).single()
        val d = t.definition
        assertEquals(listOf("tool_title", "path", "prompt"), d.parameters.keys.toList())
        assertEquals(listOf("tool_title", "path"), d.required)
        assertEquals(listOf("tool_title", "path", "prompt"), d.propertyOrdering)
        assertTrue(d.description.startsWith("[Plugin liustack/modlens · read-only]"))
        val oa = d.toOpenAIJson().getJSONObject("function").getJSONObject("parameters")
        assertEquals("string", oa.getJSONObject("properties").getJSONObject("path").getString("type"))
    }

    @Test fun `enums, numbers, nullable types and nested schemas`() {
        val c = DshToolRegistry.convertSchema(JSONObject("""{"type":"object","properties":{
            "mode":{"enum":["fast","slow"]},
            "n":{"type":["integer","null"]},
            "ratio":{"type":"number"},
            "tags":{"type":"array","items":{"type":"string"},"maxItems":3},
            "opts":{"type":"object","properties":{"deep":{"type":"boolean"},"level":{"type":"integer"}},"required":["deep"]}
        },"required":["mode","missing"]}"""))
        assertEquals(listOf("fast", "slow"), c.params["mode"]!!.enumValues)
        assertEquals("integer", c.params["n"]!!.type)
        assertEquals("number", c.params["ratio"]!!.type)
        val tags = c.params["tags"]!!.schema as AgentSchemaNode.Arr
        assertEquals(3, tags.maxItems)
        val opts = c.params["opts"]!!.schema as AgentSchemaNode.Obj
        assertEquals(listOf("deep"), opts.required)
        assertEquals(listOf("mode"), c.required) // unknown required names dropped
        assertTrue(c.jsonStrings.isEmpty())
    }

    @Test fun `inexpressible properties travel as JSON strings`() {
        val c = DshToolRegistry.convertSchema(JSONObject("""{"type":"object","properties":{
            "headers":{"type":"object","additionalProperties":{"type":"string"}},
            "points":{"type":"array","items":{"type":"number"}},
            "value":{"anyOf":[{"type":"string"},{"type":"integer"}]},
            "free":{}
        }}"""))
        assertEquals(mapOf("headers" to "object", "points" to "array", "value" to "any", "free" to "any"), c.jsonStrings)
        c.params.values.forEach { assertEquals("string", it.type); assertNull(it.schema) }
        assertTrue(c.params["headers"]!!.description.contains("JSON object encoded as a string"))
    }

    @Test fun `plugin that declares its own tool_title keeps it`() {
        val t = DshToolRegistry.build(listOf(DshPluginTools("o/p", tools = listOf(spec("t",
            """{"type":"object","properties":{"tool_title":{"type":"string","description":"mine"}},"required":["tool_title"]}"""))))).single()
        assertEquals("mine", t.definition.parameters["tool_title"]!!.description)
        assertEquals(listOf("tool_title"), t.definition.required)
        assertEquals("x", DshToolRegistry.prepareArgs(t, JSONObject().put("tool_title", "x")).getString("tool_title"))
    }

    @Test fun `spec parsing tolerates junk`() {
        assertNull(DshToolSpec.fromJson(JSONObject().put("name", " ")))
        val list = DshToolSpec.listFrom(JSONArray("""[{"name":"a"},7,{"x":1},{"name":"b","risk":"external","timeoutMs":5000}]"""))
        assertEquals(listOf("a", "b"), list.map { it.name })
        assertEquals(DshToolRisk.EXTERNAL, list[1].risk); assertEquals(5000L, list[1].timeoutMs)
        assertEquals(listOf("hello", "x"), DshCommandSpec.listFrom(JSONArray("""[{"name":"/hello"},"x",{"name":""}]""")).map { it.name })
    }

    // ------------------------------------------------------------ argument validation

    private fun tool(schema: String) = DshToolRegistry.build(listOf(DshPluginTools("o/p", tools = listOf(spec("t", schema))))).single()

    @Test fun `validateArgs reports missing, wrong types and enums`() {
        val t = tool("""{"type":"object","properties":{
            "path":{"type":"string"},"mode":{"type":"string","enum":["a","b"]},"n":{"type":"integer"},"on":{"type":"boolean"},
            "tags":{"type":"array","items":{"type":"string"}},"headers":{"type":"object","additionalProperties":{"type":"string"}}},"required":["path"]}""")
        assertEquals(emptyList<String>(), DshToolRegistry.validateArgs(t, JSONObject("""{"tool_title":"x","path":"/a","mode":"a","n":3,"on":true,"tags":["q"],"headers":"{\"k\":\"v\"}"}""")))
        // tool_title is ours: never required from the plugin's point of view
        assertEquals(emptyList<String>(), DshToolRegistry.validateArgs(t, JSONObject("""{"path":"/a"}""")))
        val errs = DshToolRegistry.validateArgs(t, JSONObject("""{"mode":"c","n":1.5,"on":"yes","tags":{},"headers":"not json"}"""))
        assertTrue(errs.toString(), errs.any { "missing required argument \"path\"" in it })
        assertTrue(errs.toString(), errs.any { "\"mode\" must be one of a, b" in it })
        assertTrue(errs.toString(), errs.any { "\"n\" must be an integer" in it })
        assertTrue(errs.toString(), errs.any { "\"on\" must be a boolean" in it })
        assertTrue(errs.toString(), errs.any { "\"tags\" must be an array" in it })
        assertTrue(errs.toString(), errs.any { "\"headers\"" in it && "did not parse" in it })
        assertEquals(6, errs.size)
        assertTrue(DshToolRegistry.validateArgs(t, JSONObject("""{"path":"/a","headers":"[1]"}""")).single().contains("must decode to a JSON object"))
    }

    @Test fun `prepareArgs strips tool_title and decodes strings`() {
        val t = tool("""{"type":"object","properties":{
            "n":{"type":"integer"},"on":{"type":"boolean"},"tags":{"type":"array","items":{"type":"string"}},
            "headers":{"type":"object","additionalProperties":{"type":"string"}}}}""")
        val out = DshToolRegistry.prepareArgs(t, JSONObject("""{"tool_title":"x","n":"4","on":"true","tags":"[\"a\"]","headers":"{\"k\":\"v\"}"}"""))
        assertFalse(out.has("tool_title"))
        assertEquals(4L, out.getLong("n")); assertEquals(true, out.getBoolean("on"))
        assertEquals("a", out.getJSONArray("tags").getString(0))
        assertEquals("v", out.getJSONObject("headers").getString("k"))
    }

    @Test fun `command tool validation`() {
        val cmd = DshToolRegistry.build(listOf(DshPluginTools("o/p", commands = listOf(DshCommandSpec("hello"))))).single()
        assertEquals(emptyList<String>(), DshToolRegistry.validateArgs(cmd, JSONObject("""{"tool_title":"x","action":"list"}""")))
        assertTrue(DshToolRegistry.validateArgs(cmd, JSONObject("""{"action":"run"}""")).any { "action=run" in it })
        assertTrue(DshToolRegistry.validateArgs(cmd, JSONObject("""{"action":"run","command":"nope"}""")).any { "must be one of hello" in it })
        assertTrue(DshToolRegistry.validateArgs(cmd, JSONObject("""{}""")).any { "\"action\"" in it })
        val a = DshToolRegistry.prepareArgs(cmd, JSONObject("""{"tool_title":"x","action":"run","command":"hello","args":"{\"who\":\"me\"}"}"""))
        assertEquals("me", a.getJSONObject("args").getString("who"))
    }

    // ------------------------------------------------------------ state + snapshot

    @Test fun `snapshot is frozen per turn and cached until something changes`() {
        DshToolRegistry.updateTools("liustack/modlens", listOf(spec("read_image", risk = "read")))
        val s1 = DshToolRegistry.snapshot()
        assertSame(s1, DshToolRegistry.snapshot())
        assertNotNull(s1["plugin__modlens__read_image"])
        // identical update: no new version
        DshToolRegistry.updateTools("liustack/modlens", listOf(spec("read_image", risk = "read")))
        assertSame(s1, DshToolRegistry.snapshot())
        // mid-turn change: the old snapshot is untouched, the next turn sees it
        DshToolRegistry.updateCommands("liustack/modlens", listOf(DshCommandSpec("doctor")))
        val s2 = DshToolRegistry.snapshot()
        assertEquals(setOf("plugin__modlens__read_image"), s1.names)
        assertEquals(setOf("plugin__modlens__read_image", "plugin__modlens__command"), s2.names)
        assertTrue(s2.version > s1.version)
        DshToolRegistry.clear("liustack/modlens")
        assertTrue(DshToolRegistry.snapshot().isEmpty())
        assertEquals(2, s2.definitions.size)
    }
}
