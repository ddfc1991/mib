package com.mibot.core.uistate.tools

import com.mibot.core.gate.IrreversibleDetector
import com.mibot.core.prompt.Feedback
import com.mibot.core.uistate.ScreenFamily
import com.mibot.core.tool.Args
import com.mibot.core.tool.HINT_PROTECTED
import com.mibot.core.tool.HINT_DISPLAY_CAPTURE
import com.mibot.core.uistate.ScreenDiagnosis
import com.mibot.core.visual.Recall
import com.mibot.core.tool.Risk
import com.mibot.core.tool.ToolContext
import com.mibot.core.tool.ToolDef
import com.mibot.core.tool.ToolExecution
import com.mibot.core.tool.Verb
import com.mibot.core.tool.fail
import com.mibot.core.tool.okObj
import com.mibot.core.uistate.screenText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.mibot.core.uistate.lastSnapshot
import com.mibot.core.uistate.screen
import com.mibot.core.uistate.session

/** 读这块屏:元素表(observe 按配置补截图)、单独截图。 */
internal class Observe(f: ScreenFamily) : ScreenTool(f, "observe") {
    override val verb = Verb.Observe
    override val label = "看屏幕"
    override val risk = Risk.Read
    override val def by lazy { ToolDef(name, describe()) }

    override suspend fun execute(a: Args, ctx: ToolContext) = ctx.session.observe(
        withImage = ctx.config.visionEnabled && !ctx.config.adaptiveVision,
        adaptiveImage = ctx.config.visionEnabled && ctx.config.adaptiveVision,
    )
}

internal class ListElements(f: ScreenFamily) : ScreenTool(f, "list_elements") {
    override val verb = Verb.ListElements
    override val label = "读取界面元素"
    override val risk = Risk.Read
    override val def by lazy { ToolDef(name, describe()) }

    override suspend fun execute(a: Args, ctx: ToolContext) = ctx.session.observe(withImage = false, recordFrame = false)
}

/** 主模型能看图才提供。 */
internal class Screenshot(f: ScreenFamily) : ScreenTool(f, "screenshot") {
    override val verb = Verb.Screenshot
    override val label = "截图"
    override val risk = Risk.Read
    override val def by lazy { ToolDef(name, describe()) }

    override fun usable(ctx: ToolContext) = ctx.config.visionEnabled

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val screen = ctx.screen
        if (screen.isProtected()) return ToolExecution(buildJsonObject {
            put("ok", false); put("error", "protected_content"); put("protected", true)
            put("message", HINT_PROTECTED); put("recoverable", true)
        })
        val png = screen.screenshotPng()
        if (png == null) {
            val captureError = screen.peek().captureError
            if (captureError == ScreenDiagnosis.DISPLAY_CAPTURE_UNAVAILABLE) {
                return fail(captureError, HINT_DISPLAY_CAPTURE, recoverable = false)
            }
            return fail("screenshot_unavailable", Feedback["screenshot_unavailable"])
        }
        val s = ctx.lastSnapshot
        val shots = ctx.shots
        // 和读屏一样登记进回看,旧图被裁掉后还能按 screenshotId 找回;付款页的图不留
        val shotId = shots.next()
        shots.remember(Recall(shotId, ctx.currentStep, s?.packageName, png, s?.let(::screenText).orEmpty()))
        return ToolExecution(okObj { s?.let { put("width", it.width); put("height", it.height) }; put("screenshotId", shotId) }, image = png, imageIds = listOf(shotId))
    }
}

