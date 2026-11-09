package com.mibot.core.general

import com.mibot.core.prompt.Prompts
import com.mibot.core.tool.AgentTool
import com.mibot.core.tool.ArgError
import com.mibot.core.tool.Args
import com.mibot.core.tool.Risk
import com.mibot.core.tool.ToolContext
import com.mibot.core.tool.ToolDef
import com.mibot.core.tool.ToolExecution
import com.mibot.core.tool.Verb
import com.mibot.core.tool.ok
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object Wait : AgentTool {
    override val verb = Verb.Wait
    override val batchable = true
    override val label = "等待"
    override val risk = Risk.Read
    override val logged = true
    override val yieldsToSteer = true
    override val def = ToolDef("wait", Prompts.tool("wait"),
        mapOf("seconds" to buildJsonObject { put("type", "number"); put("description", "0.2-10 seconds."); put("minimum", 0.2); put("maximum", 10) }),
        listOf("seconds"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val s = a.number("seconds")!!
        if (s < 0.2 || s > 10) throw ArgError("seconds", "must be between 0.2 and 10")
        delay((s * 1000).toLong())
        return ok { put("waitedSeconds", s) }
    }
}
