package com.openminis.app.mibot.phone

import android.content.Context
import com.openminis.app.sandbox.NativeOffloadHandler
import com.openminis.app.sandbox.NativeOffloadRequest
import com.openminis.app.sandbox.NativeOffloadResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * [fork/mibot-v3] `mibot-vd` — background phone control from the sandbox, so
 * scripts can drive the hidden display with the same rules as the vd_* tools
 * (own display only, ask-mode for payments/logins/codes).
 */
class VdOffloadHandler(private val context: Context) : NativeOffloadHandler {
    override fun handle(request: NativeOffloadRequest): NativeOffloadResult {
        val argv = request.argv.drop(1)
        if (argv.isEmpty() || argv[0] in setOf("-h", "--help", "help")) return NativeOffloadResult(0, HELP)
        val args = JSONObject().put("tool_title", "mibot-vd")
        val tool = when (argv[0]) {
            "create" -> VdTools.CREATE
            "launch" -> { args.put("package", argv.getOrNull(1) ?: ""); VdTools.LAUNCH }
            "tap" -> { args.put("x", argv.getOrNull(1)?.toIntOrNull() ?: -1).put("y", argv.getOrNull(2)?.toIntOrNull() ?: -1); VdTools.TAP }
            "swipe" -> {
                listOf("x1", "y1", "x2", "y2", "duration_ms").forEachIndexed { i, k -> argv.getOrNull(i + 1)?.toIntOrNull()?.let { args.put(k, it) } }
                VdTools.SWIPE
            }
            "type" -> { args.put("text", argv.drop(1).joinToString(" ")); VdTools.TYPE }
            "key" -> { args.put("key", argv.getOrNull(1) ?: "BACK"); VdTools.KEY }
            "dump" -> VdTools.UI_DUMP
            "shot" -> VdTools.SCREENSHOT
            "close" -> VdTools.CLOSE
            "status" -> return NativeOffloadResult(0, VdController.state.value.toString() + "\n")
            else -> return NativeOffloadResult(2, "mibot-vd: unknown command '${argv[0]}'\n$HELP")
        }
        val r = runBlocking { VdTools.execute(context, tool, args.toString()) }
        val out = if (tool == VdTools.SCREENSHOT && r.imageFilePath != null) r.output + "\nsaved: " + r.imageFilePath else r.output
        return NativeOffloadResult(if (r.success) 0 else 1, out + "\n")
    }

    private companion object {
        const val HELP = """mibot-vd — operate apps on Mibot's hidden virtual display
  mibot-vd create                 create the hidden display
  mibot-vd launch <package>       start an app on it
  mibot-vd tap <x> <y>            tap (display pixels)
  mibot-vd swipe x1 y1 x2 y2 [ms] swipe / scroll
  mibot-vd type <text>            type into the focused field
  mibot-vd key <BACK|ENTER|...>   press an allowed key
  mibot-vd dump                   UI tree (needs accessibility)
  mibot-vd shot                   screenshot (JPEG path on the app side)
  mibot-vd status | close
"""
    }
}
