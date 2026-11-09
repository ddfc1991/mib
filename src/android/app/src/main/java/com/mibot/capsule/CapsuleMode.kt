package com.mibot.capsule

/** 设置里「悬浮胶囊」的三选一;存成名字,认不出(没设过、旧版本)时是默认的「一直显示」(随时能双击发这一屏)。 */
enum class CapsuleMode {
    Off, DuringTasks, Always;

    companion object {
        val DEFAULT = Always
        fun parse(name: String?): CapsuleMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** 一颗胶囊静置后的不透明度;滑块只停在这四档,设置里存百分比。 */
enum class CapsuleIdleOpacity(val percent: Int) {
    P20(20), P40(40), P70(70), P100(100);

    val alpha: Float get() = percent / 100f

    companion object {
        val DEFAULT = P40
        fun snap(percent: Int): CapsuleIdleOpacity = entries.minBy { kotlin.math.abs(it.percent - percent) }
        fun parse(percent: Int): CapsuleIdleOpacity = entries.firstOrNull { it.percent == percent } ?: DEFAULT
    }
}
