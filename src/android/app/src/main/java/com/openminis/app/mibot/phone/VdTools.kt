package com.openminis.app.mibot.phone

import android.content.Context
import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import com.openminis.app.tools.ToolExecutionResult
import org.json.JSONObject
import java.io.File

/**
 * [fork/mibot-v3] Agent tools for background phone control on a hidden virtual
 * display. All coordinates are in the agent display's pixels (vd_create /
 * vd_screenshot report its size). Nothing here ever targets display 0 while a
 * hidden display is available.
 */
object VdTools {
    const val CREATE = "vd_create"
    const val LAUNCH = "vd_launch_app"
    const val SCREENSHOT = "vd_screenshot"
    const val TAP = "vd_tap"
    const val SWIPE = "vd_swipe"
    const val TYPE = "vd_type"
    const val KEY = "vd_key"
    const val UI_DUMP = "vd_ui_dump"
    const val CLOSE = "vd_close"
    val NAMES = setOf(CREATE, LAUNCH, SCREENSHOT, TAP, SWIPE, TYPE, KEY, UI_DUMP, CLOSE)

    private val title = "tool_title" to AgentToolParam("string", "A concise 5-10 word summary shown to the user. Use the user's language.")

    private fun def(name: String, desc: String, params: Map<String, AgentToolParam> = emptyMap(), required: List<String> = emptyList()) =
        AgentToolDefinition(
            name = name,
            description = desc,
            parameters = mapOf(title) + params,
            required = listOf("tool_title") + required,
            propertyOrdering = listOf("tool_title") + params.keys,
        )

    fun definitions(): List<AgentToolDefinition> = listOf(
        def(CREATE, "Background phone control: create Mibot's hidden virtual display so you can operate other apps WITHOUT taking over the user's screen. " +
            "Call once before other vd_* tools. Returns the display size (coordinates for vd_tap/vd_swipe). If the hidden display is unavailable, Mibot may ask the user to allow main-screen accessibility control instead."),
        def(LAUNCH, "Launch an installed app (by package name, e.g. com.tencent.mm) on the hidden display. Refused if the user currently has that app open on their own screen.",
            mapOf("package" to AgentToolParam("string", "Android package name.")), listOf("package")),
        def(SCREENSHOT, "Capture the hidden display as an image to see what is on it. Use after each action that changes the screen."),
        def(TAP, "Tap at (x, y) on the hidden display, in display pixels. Payment / login / password / code steps ask the user first.",
            mapOf("x" to AgentToolParam("integer", "X in display pixels."), "y" to AgentToolParam("integer", "Y in display pixels.")), listOf("x", "y")),
        def(SWIPE, "Swipe / scroll on the hidden display from (x1,y1) to (x2,y2).",
            mapOf(
                "x1" to AgentToolParam("integer", "Start X."), "y1" to AgentToolParam("integer", "Start Y."),
                "x2" to AgentToolParam("integer", "End X."), "y2" to AgentToolParam("integer", "End Y."),
                "duration_ms" to AgentToolParam("integer", "Duration, default 300."),
            ), listOf("x1", "y1", "x2", "y2")),
        def(TYPE, "Type text into the focused field on the hidden display (tap the field first). Passwords and verification codes ask the user first; never type secrets you were not given.",
            mapOf("text" to AgentToolParam("string", "Text to type."), "submit" to AgentToolParam("boolean", "Press Enter afterwards.")), listOf("text")),
        def(KEY, "Press a navigation/editing key on the hidden display: BACK, ENTER, DEL, TAB, SPACE, ESCAPE, MENU, SEARCH, PAGE_UP, PAGE_DOWN, DPAD_*. (HOME/POWER/volume are not allowed.)",
            mapOf("key" to AgentToolParam("string", "Key name, e.g. BACK.")), listOf("key")),
        def(UI_DUMP, "Read the hidden display's UI tree (text, ids, [centerX,centerY] of clickable/editable nodes). Needs Mibot's accessibility service; otherwise use vd_screenshot."),
        def(CLOSE, "Close the hidden display and stop background control. Call when the task is done."),
    )

    suspend fun execute(ctx: Context, name: String, argsJson: String): ToolExecutionResult {
        val a = runCatching { JSONObject(argsJson) }.getOrElse { JSONObject() }
        return try {
            when (name) {
                CREATE -> VdController.create(ctx).fold(
                    { s ->
                        val where = if (s.mode == VdController.Mode.A11Y) "MAIN SCREEN via accessibility (user approved; be careful, the user can see it)" else "hidden display ${s.displayId}"
                        ToolExecutionResult("ready: $where, size ${s.width}x${s.height}. Next: vd_launch_app, then vd_screenshot.", true)
                    },
                    { ToolExecutionResult(it.message ?: "vd_create failed", false) },
                )
                LAUNCH -> {
                    val pkg = a.optString("package").trim()
                    if (pkg.isEmpty()) return ToolExecutionResult("package is required", false)
                    val msg = VdController.launch(ctx, pkg)
                    ToolExecutionResult(msg, msg.startsWith("started"))
                }
                SCREENSHOT -> {
                    val bytes = VdController.screenshot(ctx) ?: return ToolExecutionResult("no frame yet (display just created, or capture unavailable)", false)
                    val s = VdController.state.value
                    val f = File(ctx.cacheDir, "vd/shot_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs(); writeBytes(bytes) }
                    // keep only the last 20 shots
                    f.parentFile?.listFiles()?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
                    ToolExecutionResult(
                        "screenshot of display ${s.displayId} (${s.width}x${s.height}; image may be downscaled — scale coordinates back to ${s.width}x${s.height}). top=${VdController.topActivity(ctx) ?: "?"}",
                        true, imageData = bytes, imageMimeType = "image/jpeg", imageFilePath = f.absolutePath,
                    )
                }
                TAP -> {
                    val msg = VdController.tap(ctx, a.optInt("x", -1), a.optInt("y", -1))
                    ToolExecutionResult(msg, msg.startsWith("tapped"))
                }
                SWIPE -> {
                    val msg = VdController.swipe(ctx, a.optInt("x1"), a.optInt("y1"), a.optInt("x2"), a.optInt("y2"), a.optInt("duration_ms", 300))
                    ToolExecutionResult(msg, msg == "swiped")
                }
                TYPE -> {
                    val msg = VdController.type(ctx, a.optString("text"), a.optBoolean("submit", false))
                    ToolExecutionResult(msg, msg.startsWith("typed"))
                }
                KEY -> {
                    val msg = VdController.key(ctx, a.optString("key", "BACK"))
                    ToolExecutionResult(msg, msg.startsWith("pressed"))
                }
                UI_DUMP -> ToolExecutionResult(VdController.uiDump(ctx), true)
                CLOSE -> {
                    VdController.close(ctx)
                    ToolExecutionResult("closed", true)
                }
                else -> ToolExecutionResult("unknown tool $name", false)
            }
        } catch (e: IllegalStateException) {
            ToolExecutionResult(e.message ?: "not ready", false)
        } catch (t: Throwable) {
            VdController.log("$name error: ${t.javaClass.simpleName}: ${t.message}")
            ToolExecutionResult("$name failed: ${t.javaClass.simpleName}: ${t.message}", false)
        }
    }
}
