package com.mibot.life.life

import android.content.Context
import com.mibot.life.settings.LifeSettings

/**
 * 生活系统整合层 —— 决策 + 记账 + 等级 + 项目 + 记忆衰减。
 * 状态持久化（LifeStore），重启恢复，不依赖模型上下文。
 */
class LifeSystem(context: Context, private val settings: LifeSettings) {

    private val store = LifeStore(context)
    private val planner = LifePlanner()

    @Volatile
    private var state: LifeStateData = store.load()

    /** 当前状态快照 */
    fun snapshot(): LifeStateData = state

    private val careLedger = CareLedger()
    private val affectRules = AffectState()

    /** 从用户话里提取牵挂（温度来源）：说累/忙/考试/答应… → 记下来，到期主动问 */
    fun rememberCareFrom(userText: String) {
        val item = careLedger.extractFromUser(userText) ?: return
        state = state.copy(cares = careLedger.add(state.cares, item))
        save()
    }

    /** 情感事件（成功/失败/被回应…） */
    fun feel(event: AffectEvent) {
        state = state.copy(affect = state.affect.apply(event))
        save()
    }

    /** 记一条经历（生活痕迹，也是兴趣的来源） */
    fun recordEpisode(kind: EpisodeKind, summary: String, at: Long = System.currentTimeMillis()) {
        state = state.copy(episodes = (state.episodes + LifeEpisode(kind = kind, title = summary, occurredAt = at)).takeLast(200))
        recomputeInterests()
        save()
    }

    /** 兴趣自主生成：从经历统计，不预设加权 */
    private fun recomputeInterests() {
        val now = System.currentTimeMillis()
        val halfLife = settings.interestHalfLifeDays.toDouble()
        val grouped = state.episodes.groupBy { it.kind.name.lowercase() }
        val interests = grouped.mapNotNull { (topic, eps) ->
            if (eps.size < settings.interestMinCount) return@mapNotNull null
            // 近期度：半衰期加权
            val recency = eps.sumOf { e ->
                val days = ((now - e.occurredAt) / 86_400_000.0).coerceAtLeast(0.0)
                Math.pow(0.5, days / halfLife)
            }
            val strength = (recency * 2).coerceAtMost(settings.interestMaxBonus.toDouble())
            Interest(topic = topic, domain = topic, strength = strength,
                     count = eps.size, lastAt = eps.maxOf { it.occurredAt })
        }
        state = state.copy(interests = interests)
    }

    /** 当前作息阶段 */
    fun phase(now: Long = System.currentTimeMillis()): RhythmPhase =
        planner.phase(now, state.rhythm)

    /** 做一次生活决策（可解释） */
    fun decide(deviceBusy: Boolean = false, lowEnergy: Boolean = false): LifeDecision {
        val d = planner.choose(state, deviceBusy, lowEnergy)
        d.chosen?.let { choice ->
            // 记入历史（先标记 RUNNING）
            state = state.copy(history = (state.history + choice.copy(runStatus = RunStatus.RUNNING)).takeLast(300))
            store.save(state)
        }
        return d
    }

    /** 一次生活动作结束：记结果 + 加经验/币 + 写经历 */
    fun finish(
        choiceId: String,
        status: RunStatus,
        note: String = "",
        episodeKind: EpisodeKind? = null,
    ) {
        val idx = state.history.indexOfLast { it.id == choiceId }
        if (idx < 0) return
        val done = state.history[idx].copy(runStatus = status)
        val newHistory = state.history.toMutableList().also { it[idx] = done }

        var newState = state.copy(history = newHistory)

        if (status == RunStatus.COMPLETED) {
            // 经验：动作对应领域
            val domain = when (done.action) {
                LifeAction.RESEARCH -> "learning"
                LifeAction.WRITING -> "writing"
                LifeAction.EXPLORE -> "forum"
                LifeAction.REST -> "rest"
            }
            val gain = when (done.action) {
                LifeAction.RESEARCH -> 10
                LifeAction.WRITING -> 20
                LifeAction.EXPLORE -> 8
                LifeAction.REST -> 2
            }
            newState = newState.copy(
                levelXp = newState.levelXp + (domain to ((newState.levelXp[domain] ?: 0) + gain)),
                coin = newState.coin + (gain / 4),
            )
            // 写经历（生活证据）
            val kind = episodeKind ?: when (done.action) {
                LifeAction.RESEARCH -> EpisodeKind.READ
                LifeAction.WRITING -> EpisodeKind.NOTE
                LifeAction.EXPLORE -> EpisodeKind.FORUM
                LifeAction.REST -> EpisodeKind.NOTE
            }
            val ep = LifeEpisode(
                kind = kind,
                title = done.topic.ifBlank { done.reason },
                goalId = done.goalId,
                detail = note,
            )
            newState = newState.copy(episodes = (newState.episodes + ep).takeLast(500))
        } else if (status in setOf(RunStatus.FAILED, RunStatus.TIMEOUT, RunStatus.INTERRUPTED)) {
            // 失败的跨日好奇心记一次未成果
            if (done.isCuriosity) {
                val cid = done.key.removePrefix("curiosity:")
                newState = newState.copy(
                    curiosityGoals = newState.curiosityGoals.map {
                        if (it.id == cid) it.copy(unfruitfulAttempts = it.unfruitfulAttempts + 1, updatedAt = System.currentTimeMillis()) else it
                    },
                )
            }
        }

        state = newState
        store.save(state)
    }

    /** 添加目标 */
    fun addGoal(title: String, curiosity: Boolean = false) {
        state = if (curiosity) {
            state.copy(curiosityGoals = state.curiosityGoals + CuriosityGoal(title = title))
        } else {
            state.copy(goals = state.goals + LifeGoal(title = title))
        }
        store.save(state)
    }

    /** 升级门槛：Lv2=100 3=300 4=800 5=2000 */
    fun levelOf(domain: String): Int {
        val xp = state.levelXp[domain] ?: 0
        return when {
            xp >= 2000 -> 5
            xp >= 800 -> 4
            xp >= 300 -> 3
            xp >= 100 -> 2
            else -> 1
        }
    }

    fun levelTitle(domain: String): String = when (levelOf(domain)) {
        5 -> "大师"
        4 -> "专家"
        3 -> "熟练"
        2 -> "学徒"
        else -> "新手"
    }

    fun xpOf(domain: String): Int = state.levelXp[domain] ?: 0

    /** 记忆衰减：7 天转概要、60 天淡出（保留偏好/未完成目标） */
    fun applyMemoryPolicy(now: Long = System.currentTimeMillis()) {
        val mp = state.memory
        val day = 86_400_000L
        val kept = state.episodes.filter { ep ->
            val ageDays = (now - ep.occurredAt) / day
            val keepGoal = mp.keepUnfinishedGoals && ep.goalId != null &&
                state.goals.any { it.id == ep.goalId && it.status !in setOf(GoalStatus.DONE, GoalStatus.ABANDONED) }
            when {
                ageDays < mp.summaryAfterDays -> true
                ageDays < mp.fadeAfterDays -> keepGoal
                else -> keepGoal
            }
        }
        if (kept.size != state.episodes.size) {
            state = state.copy(episodes = kept)
            store.save(state)
        }
    }

    /** 主动联系：是否该联系（额度/间隔） */
    fun shouldContact(now: Long = System.currentTimeMillis()): Boolean {
        val c = state.contact
        val sameDay = isSameDay(c.lastContactAt, now)
        val used = if (sameDay) c.usedToday else 0
        if (used >= c.dailyLimit) return false
        val elapsedMin = (now - c.lastContactAt) / 60_000
        return elapsedMin >= c.minIntervalMin
    }

    fun markContacted(now: Long = System.currentTimeMillis()) {
        val sameDay = isSameDay(state.contact.lastContactAt, now)
        state = state.copy(
            contact = state.contact.copy(
                lastContactAt = now,
                usedToday = if (sameDay) state.contact.usedToday + 1 else 1,
            ),
        )
        store.save(state)
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }
}