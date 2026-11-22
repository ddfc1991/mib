package com.mibot.chat

/** 分支来源:从 [parentId] 的第 [parentTurn] 轮(0 起)分出来的;[afterTurn] 为 false 时是复制到这一轮之前(第 2 阶段从用户消息重新问)。 */
data class BranchOrigin(val parentId: String, val parentTurn: Int, val afterTurn: Boolean, val at: Long)

/** 对话从哪一页开始:网址、主机名、页面标题(没有为空串)。 */
data class PageOrigin(val url: String, val host: String, val title: String = "")

data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val pinnedAt: Long? = null,
    val page: PageOrigin? = null,
    val branchOf: BranchOrigin? = null,
)
