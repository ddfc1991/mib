package com.mibot.life.life

import com.mibot.life.pet.PetPhase

/**
 * 生活引擎（第一版：最小可跑）。
 * 后续移植大肥鱼 LifePlanner（打分决策）/ Projects（成长线）/ MemoryPolicy（记忆）。
 */
class LifeEngine {
    /** 当前生活状态文字 */
    fun statusText(phase: PetPhase): String = when (phase) {
        PetPhase.IDLE -> "Mibot 醒来了，正在想今天做什么…"
        PetPhase.WORKING -> "Mibot 正在生活：读书 / 看视频 / 逛论坛 / 赚钱 / 学习"
        PetPhase.ASKING -> "Mibot 需要你批准：接下来去赚钱吗？"
        PetPhase.DONE -> "Mibot 完成了一项生活活动，满意地伸了个懒腰"
    }
}
