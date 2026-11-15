package com.mibot.life.plugin

/**
 * Mibot 生活插件框架 —— 对齐大肥鱼(AstrBot)插件模型。
 * 每个插件 = 一个可执行的能力模块（工作/赚钱/生活），生活引擎自主调度。
 */
interface LifePlugin {
    /** 唯一 id（如 "oppo-game-center"） */
    val id: String
    /** 展示名（如 "游戏中心签到"） */
    val name: String
    /** 插件类型：工作赚钱 / 生活 / 学习 / 互动 */
    val kind: PluginKind
    /** 是否已授权可执行 */
    val authorized: Boolean
    /** 真实收益描述（如 "+2 游币/天"） */
    val reward: String

    /** 执行一次（真实工作），返回真实结果 */
    suspend fun execute(ctx: PluginContext): PluginResult
}

enum class PluginKind { WORK_EARN, LIFE, LEARN, SOCIAL }

/** 插件执行上下文（可启动 App、记录、上报收益） */
class PluginContext(
    val launchApp: (String) -> Boolean,
    val reportEarn: (Int, String) -> Unit,
    val log: (String) -> Unit,
)

/** 插件执行结果 */
data class PluginResult(
    val ok: Boolean,
    val message: String,
    val earned: Int = 0,
    val detail: String = "",
)
