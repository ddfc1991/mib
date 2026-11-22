package com.mibot.ui.hq

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 一个关注点：agent 按它生成动态（信源/话题/关键词）。 */
@Serializable
data class FollowPoint(
    val name: String,
    /** 信源 URL 或平台名（NodeSeek / LinuxDo / V2EX…），可空=只看关键词 */
    val source: String = "",
    /** 关键词过滤，可空=只看信源 */
    val keyword: String = "",
)

@Serializable
private data class FollowFile(val points: List<FollowPoint> = emptyList())

/**
 * 关注点管理：`files/follow.json`。agent（外部）读这份决定生成哪些信源/话题的动态。
 * 列表页（HqFeedScreen）「+」打开管理，增删关注点落盘。
 */
class FollowStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val file = File(context.filesDir, "follow.json")

    private val _points = MutableStateFlow<List<FollowPoint>>(emptyList())
    val points: StateFlow<List<FollowPoint>> = _points.asStateFlow()

    init {
        _points.value = load()
    }

    private fun load(): List<FollowPoint> {
        if (!file.isFile) return emptyList()
        return runCatching { json.decodeFromString<FollowFile>(file.readText()).points }.getOrDefault(emptyList())
    }

    fun add(point: FollowPoint) {
        val next = _points.value.filterNot { it.name == point.name } + point
        _points.value = next
        persist()
    }

    fun remove(name: String) {
        _points.value = _points.value.filterNot { it.name == name }
        persist()
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(FollowFile.serializer(), FollowFile(_points.value)))
        }
    }
}
