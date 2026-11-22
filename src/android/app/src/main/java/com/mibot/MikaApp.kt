package com.mibot

import android.app.Application
import com.mibot.cap.phone.PhoneServices
import com.mibot.capsule.AppForeground
import com.mibot.core.model.ChatModel
import com.mibot.core.model.ModelRole
import com.mibot.settings.AppAccessStore
import com.mibot.teach.TeachRuntime

/**
 * Mika 的 App 容器:挂了一整棵依赖(聊天、任务、记忆、胶囊、MCP 等)。
 * OpenBots 融合:Miba 的 Application 改为继承本类(见 openminis.app.MinisApp),
 * 这样 5 模块(calls/proactive/wechat/sms/pocket)的 CallRuntime 等能拿
 * [phone]/[foreground]/[providers]。providers.modelFor 暂给 null(AI 总结降级),
 * 后续接 mib-src 的 LLMProvider 适配。
 */
open class MikaApp : Application(), PhoneServices.Owner {
    /** 全局的询问 / 自主,由 Mibot 侧注入;默认给一个空实现。 */
    val appAccess: AppAccessStore? = null

    val teachReady: Boolean get() = false

    val teach: TeachRuntime get() = TeachRuntime()

    /** 手机能力(calls 模块用):hub/shell/pairing 等共享服务。 */
    override val phone: PhoneServices by lazy { PhoneServices(this) }

    /** 前台可见状态(calls/proactive 用):true=app 在前台。 */
    val foreground: AppForeground by lazy { AppForeground(this) }

    /** 模型提供者(5 模块用):modelFor(Main) 暂给 null,AI 总结等降级;后续接 mib-src LLMProvider。 */
    val providers: ModelProviders = ModelProviders()

    /** 5 模块需要的模型提供者接口;当前占位,返回 null。 */
    class ModelProviders {
        fun modelFor(role: ModelRole): ChatModel? = null
    }
}
