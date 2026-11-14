package com.mibot.life.life

import kotlin.math.max
import kotlin.math.pow

/**
 * 生活决策引擎 —— 移植大肥鱼 LifePlanner.choose()（Python → Kotlin）。
 *
 * 核心：可解释打分。每个候选动作按「目标价值 + 兴趣加成 + 新奇度 - 最近重复 - 冷却」
 * 打分，另有一批硬门控（作息/设备忙/项目门控/尝试失败）直接排除。
 * 决策结果带 factors（为什么选）和 excluded（为什么没选），可审计。
 */
class LifePlanner(
    private val random: kotlin.random.Random = kotlin.random.Random.Default,
) {
    companion object {
        /** 最近重复扣分 */
        const val REPEAT_PENALTY = 12.0
        /** 晨间续做加分 */
        const val MORNING_CONTINUE_BONUS = 6.0
        /** 好奇心冷却（小时） */
        const val CURIOSITY_COOLDOWN_H = 6.0
        /** 好奇心每日配额 */
        const val CURIOSITY_DAILY_QUOTA = 1
        /** 未成果尝试上限 */
        const val MAX_UNFRUITFUL = 2
        /** 低能量偏好轻松/休息的加减分 */
        const val PREFER_LIGHT_BONUS = 4.0
        const val LOW_ENERGY_REDUCTION = -4.0
    }

    /** 当前作息阶段 */
    fun phase(now: Long, cfg: RhythmConfig): RhythmPhase {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            h in cfg.morningStart until cfg.dayStart -> RhythmPhase.MORNING
            h in cfg.dayStart until cfg.eveningStart -> RhythmPhase.DAY
            h in cfg.eveningStart until cfg.nightStart -> RhythmPhase.EVENING
            else -> RhythmPhase.NIGHT
        }
    }

    /**
     * 打分决策。
     * @param state 当前生活状态
     * @param deviceBusy 设备是否被占用（虚拟屏/用户在用）
     * @param lowEnergy 心境低能量（偏好休息）
     * @param now 当前时间
     */
    fun choose(
        state: LifeStateData,
        deviceBusy: Boolean = false,
        lowEnergy: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): LifeDecision {
        val excluded = mutableListOf<ExcludedCandidate>()
        val candidates = mutableListOf<LifeChoice>()
        val rhythm = state.rhythm
        val currentPhase = phase(now, rhythm)
        val recent = state.history.takeLast(8)
        // 情感影响：低能量/高挫败 → 倾向休息（不是硬规则，是打分偏移）
        val affect = state.affect.decayed(now)
        val low = lowEnergy || affect.isLowEnergy() || affect.isFrustrated()
        // 兴趣表：topic/domain → 加成
        val interestBonus: (String, String) -> Double = { key, domain ->
            val byTopic = state.interests.filter { it.topic.isNotEmpty() && (key.contains(it.topic) || it.topic.contains(key.take(20))) }
            val byDomain = state.interests.filter { it.domain == domain }
            (byTopic + byDomain).sumOf { it.strength }.coerceAtMost(MAX_INTEREST_BONUS)
        }

        fun add(
            action: LifeAction,
            key: String,
            signature: String,
            reason: String,
            base: Double,
            goalId: String? = null,
            workId: String? = null,
            topic: String = "",
            isCuriosity: Boolean = false,
        ) {
            val factors = mutableListOf<ScoreFactor>()
            var score = base

            // 作息门控
            if (rhythm.enabled) {
                val nightWritingOnly = currentPhase == RhythmPhase.NIGHT && rhythm.writingOnlyInNight
                if (nightWritingOnly && action !in setOf(LifeAction.WRITING)) {
                    excluded += ExcludedCandidate(key, "rhythm_${currentPhase.name.lowercase()}")
                    return
                }
                if (currentPhase == RhythmPhase.MORNING && action in setOf(LifeAction.RESEARCH, LifeAction.WRITING)) {
                    factors += ScoreFactor("rhythm_continue_in_morning", MORNING_CONTINUE_BONUS)
                    score += MORNING_CONTINUE_BONUS
                }
            }

            // 最近同 key+signature 失败/进行中 → 排除
            val attempts = state.history.filter { it.key == key && signatureOf(it) == signature }
            val bad = setOf(
                RunStatus.FAILED, RunStatus.TIMEOUT, RunStatus.INTERRUPTED,
                RunStatus.CANCELLED, RunStatus.BUDGET_EXHAUSTED,
                RunStatus.RUNNING, RunStatus.NOT_STARTED,
            )
            if (attempts.isNotEmpty() && (attempts.first().runStatus in bad || attempts.size >= 2)) {
                excluded += ExcludedCandidate(key, "recent_attempt_without_new_progress")
                return
            }

            // 最近重复扣分
            val repeated = recent.count { it.key == key }
            if (repeated > 0) {
                factors += ScoreFactor("recent_repetition", -REPEAT_PENALTY * repeated)
                score -= REPEAT_PENALTY * repeated
            }

            // 低能量偏好
            if (lowEnergy) {
                if (action in setOf(LifeAction.EXPLORE, LifeAction.REST)) {
                    factors += ScoreFactor("affective_prefer_light_or_rest", PREFER_LIGHT_BONUS)
                    score += PREFER_LIGHT_BONUS
                } else if (action in setOf(LifeAction.RESEARCH, LifeAction.WRITING)) {
                    factors += ScoreFactor("affective_low_energy_reduction", LOW_ENERGY_REDUCTION)
                    score += LOW_ENERGY_REDUCTION
                }
            }

            candidates += LifeChoice(
                action = action, key = key, reason = reason, score = score,
                factors = factors, goalId = goalId, workId = workId,
                topic = topic, isCuriosity = isCuriosity, at = now,
            )
        }

        // 1) 好奇心目标（跨日未解疑问）
        val curiosityRunsToday = state.history.count {
            it.isCuriosity && sameDay(it.at, now)
        }
        state.curiosityGoals.forEach { g ->
            if (curiosityRunsToday >= CURIOSITY_DAILY_QUOTA) {
                excluded += ExcludedCandidate("curiosity:${g.id}", "curiosity_daily_quota_exhausted")
                return@forEach
            }
            if (g.status != GoalStatus.ACTIVE) {
                excluded += ExcludedCandidate("curiosity:${g.id}", "curiosity_${g.status.name.lowercase()}")
                return@forEach
            }
            val deltaH = max(0.0, (now - g.updatedAt) / 3_600_000.0)
            if (deltaH < CURIOSITY_COOLDOWN_H) {
                excluded += ExcludedCandidate("curiosity:${g.id}", "curiosity_cooldown")
                return@forEach
            }
            if (g.unfruitfulAttempts >= MAX_UNFRUITFUL) {
                excluded += ExcludedCandidate("curiosity:${g.id}", "curiosity_max_unfruitful_attempts")
                return@forEach
            }
            if (deviceBusy) {
                excluded += ExcludedCandidate("curiosity:${g.id}", "device_busy")
                return@forEach
            }
            // 好奇心衰减模型：越久未解越想知道，但尝试过多次则衰减
            val decay = curiosityScore(deltaH, g.unfruitfulAttempts)
            add(
                LifeAction.RESEARCH, "curiosity:${g.id}",
                signature = "cur:${g.unfruitfulAttempts}:${g.updatedAt}",
                reason = "探索跨日未完小疑问：${g.title.take(60)}",
                base = decay, topic = g.title.take(60), isCuriosity = true,
            )
        }

        // 2) 普通目标
        state.goals.forEach { goal ->
            if (goal.status !in setOf(GoalStatus.READY, GoalStatus.PAUSED)) {
                excluded += ExcludedCandidate("goal:${goal.id}", "goal_${goal.status.name.lowercase()}")
                return@forEach
            }
            val work = state.works.firstOrNull { it.goalId == goal.id }
            val action = if (work != null && work.phase == WorkPhase.WRITE) LifeAction.WRITING else LifeAction.RESEARCH
            if (deviceBusy && action != LifeAction.WRITING) {
                excluded += ExcludedCandidate("goal:${goal.id}", "device_busy")
                return@forEach
            }
            // 目标基础分：有作品在做 > 全新目标
            val base = when {
                work != null && work.phase == WorkPhase.WRITE -> 30.0
                work != null && work.phase == WorkPhase.REVIEW -> 22.0
                else -> 20.0
            }
            add(
                action, "goal:${goal.id}",
                signature = "goal:${goal.id}:${goal.updatedAt}:${work?.version ?: 0}",
                reason = if (action == LifeAction.WRITING) "继续写：${goal.title.take(40)}"
                else "研究：${goal.title.take(40)}",
                base = base, goalId = goal.id, workId = work?.id, topic = goal.title.take(60),
            )
        }

        // 3) 基础生活动作（探索/休息）——无目标可做时兜底
        add(
            LifeAction.EXPLORE, "explore:forum",
            signature = "explore:${dayKey(now)}:${state.history.count { it.action == LifeAction.EXPLORE && sameDay(it.at, now) }}",
            reason = "去逛论坛收集灵感", base = 15.0,
        )
        add(
            LifeAction.REST, "rest:idle",
            signature = "rest:${dayKey(now)}",
            reason = "休息一下，发会儿呆", base = 10.0,
        )

        // 4) 选最高分
        val chosen = candidates.maxByOrNull { it.score }
        return LifeDecision(chosen = chosen, excluded = excluded, at = now)
    }

    /** 跨日好奇心衰减：越久越强，尝试越多越弱 */
    private fun curiosityScore(deltaHours: Double, unfruitful: Int): Double {
        val freshness = deltaHours.coerceIn(0.0, 72.0) / 24.0   // 0..3 天
        val effort = 0.7.pow(unfruitful.toDouble())              // 每次未成果打 7 折
        return (10.0 + freshness * 8.0) * effort
    }

    private fun signatureOf(c: LifeChoice): String =
        "${c.key}:${c.goalId ?: ""}:${c.workId ?: ""}"

    private fun dayKey(now: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }
}