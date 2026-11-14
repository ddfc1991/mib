package com.mibot.life.pet

/**
 * 桌宠状态机（借鉴 agent-hud-open 的 BotState）。
 * 四态 = 用户图片里的：醒来 / 工作中 / 等待批准 / 完成。
 * 表情 sign 决定脸怎么画（眼睛/嘴/标记）。
 */
enum class PetPhase {
    IDLE,      // 醒来
    WORKING,   // 工作中
    ASKING,    // 等待批准
    DONE;      // 完成

    val label: String
        get() = when (this) {
            IDLE -> "醒来"
            WORKING -> "工作中"
            ASKING -> "等待批准"
            DONE -> "完成"
        }
}

/** 面部表情标记（影响眼睛/嘴/装饰） */
enum class PetSign {
    NONE,       // 普通睁眼
    THINKING,   // 思考（眼偏移+嘴微动）
    QUESTION,   // 疑问（歪头+？）
    HAPPY,      // 完成（弯眼笑）
    SLEEP,      // 睡觉（闭眼+z）
    EXCLAIM;    // 惊讶/重要（！）
}

/** 朝向（眼睛看的方向，让桌宠"有生命"） */
enum class PetFacing { LEFT, RIGHT, UP, DOWN, CENTER }

/** 一帧的完整状态（动画+表情+朝向） */
data class PetFrame(
    val phase: PetPhase,
    val sign: PetSign,
    val facing: PetFacing,
    val blink: Float,      // 0=睁眼 1=闭眼（眨眼动画）
    val bob: Float,        // 呼吸浮动 -1..1
    val tilt: Float,       // 歪头角度（度）
    val mouth: Float,      // 嘴张开 0..1
)

/**
 * 桌宠状态机：接收生活事件 → 输出 PetFrame。
 * 用单调时钟（秒）驱动动画，尊重减少动效。
 */
class PetState {
    private var phase = PetPhase.IDLE
    private var sign = PetSign.NONE
    private var facing = PetFacing.CENTER
    private var lastBlinkAt = 0.0
    private var blinkProgress = 0f
    private var lastTiltAt = 0.0
    private var tiltTarget = 0f

    fun setPhase(newPhase: PetPhase) {
        phase = newPhase
        sign = when (newPhase) {
            PetPhase.IDLE -> PetSign.NONE
            PetPhase.WORKING -> PetSign.THINKING
            PetPhase.ASKING -> PetSign.QUESTION
            PetPhase.DONE -> PetSign.HAPPY
        }
    }

    fun setSign(newSign: PetSign) {
        sign = newSign
    }

    fun setFacing(newFacing: PetFacing) {
        facing = newFacing
    }

    /** 计算当前帧（now 秒，单调时钟） */
    fun frame(now: Double): PetFrame {
        // 呼吸 bob：4.5s 周期，0.125s 步进（借鉴 bot-moves）
        val bob = if (phase != PetPhase.DONE) {
            ((now % 4.5) / 4.5).let { kotlin.math.sin(it * Math.PI * 2).toFloat() * 0.5f }
        } else 0f

        // 眨眼：随机 3-6s 一次，0.25s 闭眼
        val blink = if (now - lastBlinkAt > nextBlinkInterval(now)) {
            lastBlinkAt = now
            blinkProgress = 1f
            1f
        } else {
            blinkProgress *= 0.8f
            blinkProgress
        }

        // 歪头：疑问时歪 8°，平时偶尔
        val tilt = when (phase) {
            PetPhase.ASKING -> 8f
            PetPhase.WORKING -> if ((now % 6.0) < 1.0) 3f else 0f
            else -> 0f
        }

        // 嘴：思考时微张，完成时笑
        val mouth = when (phase) {
            PetPhase.WORKING -> 0.3f
            PetPhase.DONE -> 0.7f
            else -> 0.1f
        }

        return PetFrame(phase, sign, facing, blink, bob, tilt, mouth)
    }

    private fun nextBlinkInterval(now: Double): Double {
        // 3~6 秒眨眼一次（伪随机但确定）
        val seed = kotlin.math.floor(now).toLong() % 3
        return 3.0 + seed
    }
}
