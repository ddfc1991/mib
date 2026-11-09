package com.openminis.app.mibot.module

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openminis.app.mibot.design.MibotPageTitle
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotTokens

/**
 * [fork/mibot-modules] 模块化接入框架 —— Mibot 是统一宿主，被分解的外部 app
 * 以 [ModuleManifest] 形式接入。设计意图：
 *
 *   1. 每个被分解的 app 产出一个 manifest（id 唯一、能力清单、入口 Composable、
 *      权限要求、来源标记），经 [ModuleRegistry.register] 注册。
 *   2. 注册后自动出现在设置页「应用」网格（[ModuleRegistry.gridItems]），
 *      点击进入 [ModuleHostScreen] 渲染该模块入口。
 *   3. 模块间能力复用走 [CapabilityBus]（例如网关模块调用沙箱模块的能力），
 *      不强耦合编译依赖。
 *   4. 第一个接入的模块 = Mika（三能力：phone_control / sandbox / browser）；
 *      其后 Agent2API、网关管家(pojia2api)、BrowserAct 等按同一规范拆解接入。
 *
 * 接入规范（给未来分解 app 用）：
 *   - id        : 全局唯一，如 "mika"、"agent2api"、"pojia2api"
 *   - provider  : 分解自哪个 app（溯源）
 *   - capabilities: 能力 token，如 "phone_control","sandbox","browser","gateway"
 *   - entry     : 模块主页（Composable），宿主用 ModuleHostScreen 包装后渲染
 *   - permissions: 需要的 Android 权限（接入时检查，缺失则提示去授权）
 */

/** 模块能力 token 常量（跨模块约定）。 */
object ModuleCapability {
    const val PHONE_CONTROL = "phone_control"
    const val SANDBOX = "sandbox"
    const val BROWSER = "browser"
    const val GATEWAY = "gateway"
    const val MAIL = "mail"
    const val VAULT = "vault"
    const val REGISTER = "register"
}

/**
 * 一个被分解 app 的模块描述符。
 * @param entry 模块主页 Composable（可为 null 表示仍在移植中，宿主显示占位说明页）。
 * @param showInGrid 是否在设置页「应用」网格显示独立入口。**默认 false**：模块只作为
 *   能力提供者经 [CapabilityBus] 注入宿主功能（如 Mika 操控手机能力并入「后台操控」），
 *   不单独出现卡片；只有确实需要独立入口的模块才置 true。
 */
data class ModuleManifest(
    val id: String,
    val name: String,
    val summary: String,
    val icon: ImageVector,
    val color: Color,
    val capabilities: Set<String> = emptySet(),
    val provider: String = "external",
    val version: String = "1.0",
    val permissions: List<String> = emptyList(),
    val entry: (@Composable () -> Unit)? = null,
    val showInGrid: Boolean = false,
)

/**
 * 模块注册表。静态注册 + 运行时查询；用户可启停（内存态，后续接 DataStore 持久化）。
 */
object ModuleRegistry {
    private val _modules = mutableStateListOf<ModuleManifest>()
    private val _enabled = mutableStateListOf<String>()

    val modules: List<ModuleManifest> get() = _modules

    fun register(m: ModuleManifest) {
        if (_modules.none { it.id == m.id }) _modules.add(m)
    }

    fun all(): List<ModuleManifest> = _modules

    fun byId(id: String): ModuleManifest? = _modules.find { it.id == id }

    fun byCapability(cap: String): List<ModuleManifest> =
        _modules.filter { cap in it.capabilities }

    fun isEnabled(id: String): Boolean =
        _enabled.contains(id) || _modules.find { it.id == id }?.let { it.id in _enabled } == true

    fun setEnabled(id: String, on: Boolean) {
        if (on) { if (id !in _enabled) _enabled.add(id) }
        else _enabled.remove(id)
    }
}

/**
 * 能力总线：模块间按 token 调用能力，解耦编译依赖。
 * 例如沙箱模块注册 "sandbox.exec"，网关模块经 [call] 调用，无需直接引用沙箱类。
 */
object CapabilityBus {
    private val handlers = mutableMapOf<String, (Any?) -> Any?>()

    fun register(capability: String, handler: (Any?) -> Any?) {
        handlers[capability] = handler
    }

    fun call(capability: String, arg: Any? = null): Any? = handlers[capability]?.invoke(arg)

    fun has(capability: String): Boolean = handlers.containsKey(capability)
}

/**
 * 模块宿主页：统一页头 + 模块入口。模块 entry 为 null 时显示「移植中」占位说明。
 */
@Composable
fun ModuleHostScreen(m: ModuleManifest, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        MibotPageTitle(m.name)
        if (m.entry != null) {
            m.entry()
        } else {
            MibotCard {
                Text("模块 ${m.id}（来源 ${m.provider}）正在接入中。", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "能力：${m.capabilities.joinToString(" / ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MibotTokens.Slate,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "权限：${if (m.permissions.isEmpty()) "无" else m.permissions.joinToString(" / ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MibotTokens.Slate,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("返回") }
    }
}
