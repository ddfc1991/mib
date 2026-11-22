package com.mibot

import android.app.Application
import com.mibot.cap.phone.PhoneServices
import com.mibot.capsule.AppForeground
import com.mibot.core.model.ChatModel
import com.mibot.core.model.ModelRole
import com.mibot.core.run.Delivery
import com.mibot.core.run.Outbox
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

    /** 通话功能(calls 模块用):CallRuntime 是本地状态与实时语音的宿主。 */
    val calls: com.mibot.calls.CallRuntime by lazy { com.mibot.calls.CallRuntime(this) }

    /** 前台可见状态(calls/proactive 用):true=app 在前台。 */
    val foreground: AppForeground = AppForeground()

    /** 模型提供者(5 模块用):modelFor(Main) 暂给 null,AI 总结等降级;后续接 mib-src LLMProvider。 */
    val providers: ModelProviders = ModelProviders()

    /** 任务状态(wechat 模块用):占位,run() 恒 null;wechat 的询问/回答暂降级,后续接 Mibot 任务体系。 */
    val taskState: TaskStateRef = TaskStateRef()

    /** 投递通道(wechat 模块用):占位 Outbox;后续接 mib-src 的消息投递。 */
    val outbox: Outbox = Outbox()

    /** 微信(wechat 模块):扫码绑定 + 私聊消息监听,消息转任务降级(见 [TaskService.startChannel] 桩)。 */
    val wechat: com.mibot.wechat.WechatRuntime by lazy {
        com.mibot.wechat.WechatRuntime(
            this,
            com.mibot.wechat.WechatStore(
                java.io.File(filesDir, "wechat"),
                com.mibot.settings.KeystoreKeyCipher("mika-wechat-account"),
            ),
        )
    }

    /** 主动能力(proactive 模块):通知监听/归纳/提醒;判定模型与用量占位(null/0),功能降级。 */
    val proactive: com.mibot.proactive.ProactiveRuntime by lazy {
        com.mibot.proactive.ProactiveRuntime.create(
            this,
            { com.mibot.proactive.AppGate(true, emptySet()) },
            kotlinx.coroutines.flow.flowOf(com.mibot.proactive.AppGate(true, emptySet())),
            com.mibot.proactive.TriageHooks(
                target = { null },
                model = { _ -> null },
                tokensToday = { 0L },
                callsToday = { 0L },
                usageVersion = { kotlinx.coroutines.flow.MutableStateFlow(0L) },
                busy = kotlinx.coroutines.flow.MutableStateFlow(false),
            ),
            { phone.shellManager.commandRunner },
        )
    }

    /** 5 模块需要的模型提供者接口;当前占位,返回 null。 */
    class ModelProviders {
        fun modelFor(role: ModelRole): ChatModel? = null
    }

    /** wechat 需要的任务状态引用;占位,run() 恒 null(wechat 不接 AgentLoop 任务/询问)。 */
    class TaskStateRef {
        fun run(threadId: String): TaskRunRef? = null
    }

    /** 单个运行的任务引用;占位,无 activeAsk/回答能力。 */
    class TaskRunRef {
        val activeAsk: Delivery.Ask? = null
        suspend fun answerFromChannel(text: String, askId: String): Boolean = false
        fun cancelAsk(askId: String) {}
    }
}
