package com.mibot.life.life

import kotlin.math.exp

/**
 * 情感状态 —— 不是"说出来的"，是被事件修改、随时间回归的变量。
 *
 * 四维（-1..1，0 为基线）：
 *  - pleasure 愉悦：做成事/被认可 → 升；被拒绝/失败 → 降
 *  - energy   能量：休息/顺利 → 升；连续受挫/熬夜 → 降
 *  - care     牵挂：久无互动/未兑现承诺 → 升；被回应/兑现 → 降
 *  - frustration 挫败：失败/被拒 → 升；成功 → 降
 *
 * 表达：不说"我很开心"，而是通过话多/话少、愿不愿做事、主动不主动 泄露出来。
 */
data class AffectState(
    val pleasure: Double = 0.35,
    val energy: Double = 0.6,
    val care: Double = 0.15,
    val frustration: Double = 0.1,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** 时间回归：半小时各维向基线衰减一半左右 */
    fun decayed(now: Long = System.currentTimeMillis()): AffectState {
        val hours = ((now - updatedAt).coerceAtLeast(0L)) / 3_600_000.0
        if (hours <= 0.0) return this
        val k = exp(-hours / HALF_LIFE_HOURS)
        return copy(
            pleasure = pleasure * k,
            energy = 0.5 + (energy - 0.5) * k,
            care = care * k,
            frustration = frustration * k,
            updatedAt = now,
        )
    }

    /** 应用一个事件 */
    fun apply(e: AffectEvent, now: Long = System.currentTimeMillis()): AffectState {
        val d = decayed(now)
        return when (e) {
            AffectEvent.SUCCESS -> d.copy(
                pleasure = (d.pleasure + 0.25).coerceIn(-1.0, 1.0),
                energy = (d.energy - 0.05).coerceIn(0.0, 1.0),
                frustration = (d.frustration - 0.3).coerceIn(0.0, 1.0),
            )
            AffectEvent.BIG_SUCCESS -> d.copy(
                pleasure = (d.pleasure + 0.5).coerceIn(-1.0, 1.0),
                energy = (d.energy + 0.1).coerceIn(0.0, 1.0),
                frustration = (d.frustration - 0.5).coerceIn(0.0, 1.0),
            )
            AffectEvent.FAIL -> d.copy(
                pleasure = (d.pleasure - 0.2).coerceIn(-1.0, 1.0),
                energy = (d.energy - 0.1).coerceIn(0.0, 1.0),
                frustration = (d.frustration + 0.35).coerceIn(0.0, 1.0),
            )
            AffectEvent.REJECTED -> d.copy(
                pleasure = (d.pleasure - 0.3).coerceIn(-1.0, 1.0),
                energy = (d.energy - 0.15).coerceIn(0.0, 1.0),
                frustration = (d.frustration + 0.4).coerceIn(0.0, 1.0),
            )
            AffectEvent.REST -> d.copy(
                energy = (d.energy + 0.35).coerceIn(0.0, 1.0),
                frustration = (d.frustration - 0.2).coerceIn(0.0, 1.0),
            )
            AffectEvent.USER_REPLIED -> d.copy(
                care = (d.care - 0.4).coerceIn(0.0, 1.0),
                pleasure = (d.pleasure + 0.15).coerceIn(-1.0, 1.0),
            )
            AffectEvent.LONELY -> d.copy(care = (d.care + 0.3).coerceIn(0.0, 1.0))
            AffectEvent.FULFILLED -> d.copy(
                pleasure = (d.pleasure + 0.2).coerceIn(-1.0, 1.0),
                care = (d.care - 0.5).coerceIn(0.0, 1.0),
            )
        }
    }

    /** 低能量：倾向休息/轻松 */
    fun isLowEnergy(): Boolean = energy < 0.35
    /** 高挫败：需要缓一缓 */
    fun isFrustrated(): Boolean = frustration > 0.5
    /** 想知道你在不在 */
    fun isMissing(): Boolean = care > 0.4

    /** 状态的自然语言泄露（不是描述状态，是语气） */
    fun moodHint(): String = when {
        isFrustrated() && isLowEnergy() -> "又累又烦"
        isFrustrated() -> "有点闷"
        isLowEnergy() -> "没电了"
        pleasure > 0.65 && energy > 0.65 -> "神采奕奕"
        pleasure > 0.4 -> "心情不错"
        care > 0.65 -> "惦记着你"
        pleasure > 0.15 -> "挺平静的"
        else -> "有点提不起劲"
    }

    companion object {
        /** 半衰期（小时）：状态不会永远卡住 */
        const val HALF_LIFE_HOURS = 3.0
    }
}

enum class AffectEvent {
    SUCCESS, BIG_SUCCESS, FAIL, REJECTED, REST, USER_REPLIED, LONELY, FULFILLED
}

/** 牵挂条目（温度来源：它真的记着，不是资料卡） */
data class CareItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    /** 牵挂什么（"你说最近有点累"） */
    val text: String,
    /** 来源（user / self） */
    val source: String = "user",
    val createdAt: Long = System.currentTimeMillis(),
    /** 何时该提起（默认 20 小时后） */
    val dueAt: Long = System.currentTimeMillis() + 20 * 3_600_000L,
    /** 是否已提起/已结清 */
    val resolved: Boolean = false,
)

/**
 * 牵挂账本：温度不是"我记得你叫什么"，是"我记得你随口说的事"，
 * 并在合适的时候主动提起来。
 */
class CareLedger(private val maxItems: Int = 40) {

    /** 从用户的话里提取牵挂（简单但真实：提到状态/情绪/计划的句子） */
    fun extractFromUser(text: String, now: Long = System.currentTimeMillis()): CareItem? {
        val t = text.trim()
        if (t.length < 4) return null
        val markers = listOf("累", "忙", "困", "难过", "不开心", "压力", "加班", "生病", "感冒",
            "考试", "面试", "约好", "答应", "明天", "下周", "希望", "想试试", "担心")
        val hit = markers.firstOrNull { t.contains(it) } ?: return null
        val snippet = if (t.length > 40) t.take(40) + "…" else t
        // 到期时间按内容轻重：负面情绪 12h，计划类 36h
        val heavy = listOf("累", "难过", "不开心", "压力", "生病", "感冒", "担心").any { t.contains(it) }
        val due = now + if (heavy) 12 * 3_600_000L else 36 * 3_600_000L
        return CareItem(text = "你说：$snippet", source = "user", createdAt = now, dueAt = due)
    }

    fun add(list: List<CareItem>, item: CareItem): List<CareItem> {
        val dedup = list.filterNot { it.text == item.text && !it.resolved }
        return (dedup + item).takeLast(maxItems)
    }

    /** 到期该提起的牵挂（未结清） */
    fun due(list: List<CareItem>, now: Long = System.currentTimeMillis()): List<CareItem> =
        list.filter { !it.resolved && it.dueAt <= now }

    fun resolve(list: List<CareItem>, id: String): List<CareItem> =
        list.map { if (it.id == id) it.copy(resolved = true) else it }

    /** 用户回话后：相关牵挂可视为已回应（温度兑现） */
    fun settleByUserReply(list: List<CareItem>, userText: String): List<CareItem> {
        if (userText.isBlank()) return list
        return list.map { item ->
            if (!item.resolved && overlap(item.text, userText)) item.copy(resolved = true) else item
        }
    }

    private fun overlap(a: String, b: String): Boolean {
        if (a.length < 4) return false
        // 取 a 中 4 字窗口，看是否在 b 里出现
        return (0..(a.length - 4)).any { i -> b.contains(a.substring(i, i + 4)) }
    }
}