package com.mibot.settings

import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 模型组成员:一个 provider 下的一个模型。 */
@Serializable
data class ModelGroupMember(
    val providerId: String,
    val modelId: String,
)

/** 模型组:名字 + 一组模型(成员)。strategy 决定组内自动切换(Mibot 那套):fallback 按序尝试 / loadBalance 轮询。 */
@Serializable
data class ModelGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val members: List<ModelGroupMember> = emptyList(),
    /** fallback(按顺序,失败才换) | loadBalance(每次调用轮询) */
    val strategy: String = "fallback",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 模型组设置:存 `files/settings/model-groups.json`(与 MCP、Provider 同一目录)。
 * 纯数据层,UI 通过 [groups] 观察、[add]/[remove]/[rename]/[setMembers] 修改。
 */
class ModelGroupStore(private val dir: File) {
    private val file = File(dir, "model-groups.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val _groups = MutableStateFlow(load())
    val groups: StateFlow<List<ModelGroup>> = _groups.asStateFlow()

    private fun load(): List<ModelGroup> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString<List<ModelGroup>>(file.readText())
    }.getOrElse { emptyList() }

    @Synchronized private fun persist() {
        runCatching { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(_groups.value)) }
    }

    @Synchronized fun add(name: String, members: List<ModelGroupMember> = emptyList()): ModelGroup {
        val g = ModelGroup(name = name.trim(), members = members)
        _groups.value = _groups.value + g
        persist()
        return g
    }

    @Synchronized fun remove(id: String) {
        _groups.value = _groups.value.filterNot { it.id == id }
        persist()
    }

    @Synchronized fun rename(id: String, name: String) {
        _groups.value = _groups.value.map { if (it.id == id) it.copy(name = name.trim()) else it }
        persist()
    }

    @Synchronized fun setMembers(id: String, members: List<ModelGroupMember>) {
        _groups.value = _groups.value.map { if (it.id == id) it.copy(members = members) else it }
        persist()
    }

    @Synchronized fun setStrategy(id: String, strategy: String) {
        _groups.value = _groups.value.map { if (it.id == id) it.copy(strategy = strategy) else it }
        persist()
    }
}
