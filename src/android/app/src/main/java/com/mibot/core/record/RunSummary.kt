package com.mibot.core.record

import kotlinx.serialization.Serializable

/** 一次运行的概要（最小集：HqJobsScreen 活动流显示用）。
 * 完整版在 Miga core.record.RunEvent.RunSummary（含事件累积 with()），
 * 本机只搬显示所需字段，事件累积由 mib-src TaskCenterState 承担。 */
@Serializable
data class RunSummary(
    val id: String,
    val goal: String,
    val model: String = "",
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val outcome: String? = null,
    val answerOrReason: String? = null,
    val steps: Int = 0,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
)
