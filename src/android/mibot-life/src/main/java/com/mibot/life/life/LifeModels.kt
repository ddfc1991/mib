package com.mibot.life.life

import java.util.UUID

/**
 * 生活系统数据模型（移植自大肥鱼 LifePlanner / Projects / LifeState，Kotlin 重写）。
 * 全部可序列化（用 JSON 持久化），重启不丢。
 */

/** 生活动作：研究(读书/看视频)、写作(产出)、探索(逛论坛)、休息 */
enum class LifeAction(val zh: String) {
    RESEARCH("研究"),
    WRITING("写作"),
    EXPLORE("探索"),
    REST("休息"),
}

/** 目标状态 */
enum class GoalStatus(val zh: String) {
    ACTIVE("进行中"), READY("待开始"), PAUSED("暂停"), DONE("完成"), ABANDONED("放弃")
}

/** 跨日好奇心目标（一个未解的小疑问） */
data class CuriosityGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val updatedAt: Long = System.currentTimeMillis(),
    val unfruitfulAttempts: Int = 0,
    val originId: String? = null,
    val evidenceIds: List<String> = emptyList(),
)

/** 目标（用户交付或自主产生） */
data class LifeGoal(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val status: GoalStatus = GoalStatus.READY,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val workId: String? = null,
    val curiosity: Boolean = false,
)

/** 作品（写作产出的成品） */
data class LifeWork(
    val id: String = UUID.randomUUID().toString(),
    val goalId: String,
    val title: String,
    val phase: WorkPhase = WorkPhase.WRITE,
    val content: String = "",
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class WorkPhase(val zh: String) {
    WRITE("书写"), REVIEW("回看"), NEXT_QUESTION("下一问"), DONE("完成")
}

/** 经历（读过/看过/观察过的东西——生活证据） */
data class LifeEpisode(
    val id: String = UUID.randomUUID().toString(),
    val kind: EpisodeKind,
    val title: String,
    val goalId: String? = null,
    val detail: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val supersededBy: String? = null,
)

enum class EpisodeKind(val zh: String) {
    READ("阅读"), VIDEO("视频"), FORUM("论坛"), PHONE("手机观察"), NOTE("笔记")
}

/** 决策因子（可解释：为什么选这个） */
data class ScoreFactor(val reason: String, val points: Double)

/** 一次生活决策的结果 */
data class LifeChoice(
    val id: String = UUID.randomUUID().toString(),
    val action: LifeAction,
    val key: String,
    val reason: String,
    val score: Double,
    val factors: List<ScoreFactor> = emptyList(),
    val goalId: String? = null,
    val workId: String? = null,
    val topic: String = "",
    val isCuriosity: Boolean = false,
    val at: Long = System.currentTimeMillis(),
    val runStatus: RunStatus? = null,
)

enum class RunStatus(val zh: String) {
    NOT_STARTED("未开始"), RUNNING("进行中"), COMPLETED("完成"),
    FAILED("失败"), TIMEOUT("超时"), INTERRUPTED("中断"),
    CANCELLED("取消"), BUDGET_EXHAUSTED("预算耗尽"),
}

/** 被排除的候选（可解释：为什么没选） */
data class ExcludedCandidate(val key: String, val reason: String)

/** 决策输出 */
data class LifeDecision(
    val chosen: LifeChoice?,
    val excluded: List<ExcludedCandidate>,
    val at: Long = System.currentTimeMillis(),
)

/** 跨日项目（大肥鱼 Projects：最多 2 个、每个 3 阶段、隔 24h 回看） */
data class LifeProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val goalId: String,
    val stage: Int = 1,
    val maxStages: Int = 3,
    val lastReviewAt: Long = 0,
    val insight: String = "",
    val limitation: String = "",
    val nextQuestion: String = "",
    val status: GoalStatus = GoalStatus.ACTIVE,
)

/** 作息阶段 */
enum class RhythmPhase(val zh: String) {
    MORNING("晨间"), DAY("白日"), EVENING("睡前"), NIGHT("夜间")
}

/** 作息配置 */
data class RhythmConfig(
    val enabled: Boolean = false,
    val morningStart: Int = 8,
    val dayStart: Int = 10,
    val eveningStart: Int = 22,
    val nightStart: Int = 23,
    val writingOnlyInNight: Boolean = true,
)

/** 主动联系预算 */
data class ContactBudget(
    val dailyLimit: Int = 1,
    val minIntervalMin: Int = 120,
    val lastContactAt: Long = 0,
    val usedToday: Int = 0,
    val unanswered: Int = 0,
)

/** 记忆策略 */
data class MemoryPolicy(
    val summaryAfterDays: Int = 7,
    val fadeAfterDays: Int = 60,
    val keepPreferences: Boolean = true,
    val keepUnfinishedGoals: Boolean = true,
)

/** 生活系统完整状态（持久化单元） */
data class LifeStateData(
    val version: Int = 1,
    val goals: List<LifeGoal> = emptyList(),
    val curiosityGoals: List<CuriosityGoal> = emptyList(),
    val works: List<LifeWork> = emptyList(),
    val episodes: List<LifeEpisode> = emptyList(),
    val projects: List<LifeProject> = emptyList(),
    val history: List<LifeChoice> = emptyList(),
    val rhythm: RhythmConfig = RhythmConfig(),
    val contact: ContactBudget = ContactBudget(),
    val memory: MemoryPolicy = MemoryPolicy(),
    val coin: Int = 0,
    val levelXp: Map<String, Int> = emptyMap(),
)