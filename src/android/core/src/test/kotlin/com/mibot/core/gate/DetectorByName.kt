package com.mibot.core.gate

import com.mibot.core.risk.GateTarget
import com.mibot.core.uistate.ScreenSnapshot
import com.mibot.core.tool.FakeScreen
import kotlinx.serialization.json.JsonObject
import com.mibot.core.assembly.toolDispatcher

/** 测试里按工具名判:目标取工具声明的那个(和执行入口传给判定的一样)。 */
private val declarations by lazy { toolDispatcher(FakeScreen()) }

internal fun gateTargetOf(tool: String): GateTarget? = declarations.gateTarget(tool)

internal fun IrreversibleDetector.needsCheck(tool: String, args: JsonObject) = needsCheck(gateTargetOf(tool), args)

internal fun IrreversibleDetector.classify(tool: String, args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot? = null) =
    classify(gateTargetOf(tool), args, snapshot, fresh)

internal fun IrreversibleDetector.unresolved(tool: String, args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot? = null) =
    unresolved(gateTargetOf(tool), args, snapshot, fresh)
