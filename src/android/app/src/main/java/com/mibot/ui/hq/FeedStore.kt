package com.mibot.ui.hq

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Muse 动态的一个帖子条目：emoji + 粗标题 + 正文 + 来源链接。 */
@Serializable
data class FeedItem(
    val e: String = "",
    val t: String = "",
    val b: String = "",
    val s: String? = null,
)

/** 分组：悬浮小标签 + 条目列表。 */
@Serializable
data class FeedGroup(val h: String = "", val items: List<FeedItem> = emptyList())

/** 一批帖子。source: dynamic | butler。 */
@Serializable
data class FeedPost(val source: String = "dynamic", val groups: List<FeedGroup> = emptyList())

@Serializable
private data class FeedFile(val posts: List<FeedPost> = emptyList())

/**
 * Muse 动态数据源：读 `files/feed/posts.json`（agent 每 6h 生成一批），
 * 无则读 assets/feed/posts.json 初始数据。tab 打开时刷新。
 */
class FeedStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "feed/posts.json")

    private val _posts = MutableStateFlow<List<FeedPost>>(emptyList())
    val posts: StateFlow<List<FeedPost>> = _posts.asStateFlow()

    /** 读盘（IO 线程调用）。文件不存在用 assets 初始数据。 */
    fun load(): List<FeedPost> {
        val text = if (file.isFile) runCatching { file.readText() }.getOrNull() else null
            ?: runCatching {
                context.assets.open("feed/posts.json").bufferedReader().use { it.readText() }
            }.getOrNull()
        if (text == null) return emptyList()
        return runCatching { json.decodeFromString<FeedFile>(text).posts }.getOrElse {
            runCatching { json.decodeFromString<List<FeedPost>>(text) }.getOrDefault(emptyList())
        }
    }

    fun refresh() {
        _posts.value = load()
    }
}
