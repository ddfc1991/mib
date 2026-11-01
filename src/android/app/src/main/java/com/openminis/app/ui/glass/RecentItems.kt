package com.openminis.app.ui.glass

import com.openminis.app.data.db.ChatSessionEntity

/** One row of the sidebar's single mixed "Recent" list. */
sealed class RecentItem {
    abstract val id: String
    abstract val title: String?
    abstract val updatedAt: Long

    data class Chat(
        override val id: String,
        override val title: String?,
        override val updatedAt: Long,
        val pinned: Boolean = false,
    ) : RecentItem()

    /**
     * A bot group chat. No persistence exists for these yet (feat/bots); the
     * type is here so the sidebar already renders them inline with chats.
     */
    data class Group(
        override val id: String,
        override val title: String?,
        override val updatedAt: Long,
        /** Display names of member bots; first letters become stacked avatars. */
        val members: List<String>,
    ) : RecentItem()
}

object RecentItems {
    fun fromSessions(sessions: List<ChatSessionEntity>): List<RecentItem> =
        sessions.asSequence()
            .filter { !it.isChild }
            .map { RecentItem.Chat(it.id, it.title?.takeIf { t -> t.isNotBlank() }, it.updatedAt, it.pinnedAt != null) }
            .toList()

    /** Pinned first, then newest first; [query] matches titles case-insensitively. */
    fun merge(chats: List<RecentItem>, groups: List<RecentItem>, query: String): List<RecentItem> {
        val q = query.trim()
        return (chats + groups)
            .filter { q.isEmpty() || (it.title ?: "").contains(q, ignoreCase = true) }
            .sortedWith(compareByDescending<RecentItem> { (it as? RecentItem.Chat)?.pinned == true }.thenByDescending { it.updatedAt })
    }
}

/**
 * Sample rows for screenshot runs. Only ever enabled on debug builds, via the
 * `glass_demo` launch extra the CI smoke test passes (see MainActivity).
 */
object GlassDemo {
    @Volatile var enabled: Boolean = false

    fun items(now: Long = System.currentTimeMillis()): List<RecentItem> = listOf(
        RecentItem.Chat("demo-1", "周末杭州两日行程", now - 5 * 60_000L),
        RecentItem.Group("demo-g1", "产品评审小组", now - 22 * 60_000L, listOf("Planner", "Critic", "Writer")),
        RecentItem.Chat("demo-2", "Kotlin 协程取消的坑", now - 2 * 3_600_000L),
        RecentItem.Group("demo-g2", "小说写作工作室", now - 5 * 3_600_000L, listOf("Muse", "Editor")),
        RecentItem.Chat("demo-3", "读书笔记：《置身事内》", now - 26 * 3_600_000L),
        RecentItem.Chat("demo-4", "家里 NAS 备份方案", now - 50 * 3_600_000L),
    )
}
