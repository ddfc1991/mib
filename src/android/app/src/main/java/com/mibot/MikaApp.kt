package com.mibot

import android.app.Application
import com.mibot.settings.AppAccessStore
import com.mibot.teach.TeachRuntime

/**
 * Mika 的 App 容器桩:Mika 的 [com.mibot.MikaApp](MikaApp.kt) 挂了一整棵依赖
 * (聊天、任务、记忆、胶囊、MCP 等),cap 代码只用了 `application as? MikaApp` 拿
 * [appAccess]/[teach] 两条,这里只留这两条桩;Mibot 的 Application 不是本类,
 * 所以 cap 里的 `as? MikaApp` 在 Mibot 进程里恒为 null,教学/授权路径自然跳过。
 * 完整 App 容器如后续需要再整类搬入。
 */
class MikaApp : Application() {
    /** 全局的询问 / 自主,由 Mibot 侧注入;默认给一个空实现。 */
    val appAccess: AppAccessStore? = null

    val teachReady: Boolean get() = false

    val teach: TeachRuntime get() = TeachRuntime()
}
