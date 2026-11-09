package com.mibot.task

import android.content.Context

/**
 * 任务服务的桩:Mika 的 TaskService.kt(1208 行)挂了一整棵依赖(聊天/确认/记忆/接管/agent),
 * cap 只用它的四条静态命令(交回/自己完成/取消/暂离)。这里留同名签名,动作交给 Mibot 侧实现;
 * 完整 TaskService 如后续需要再整类搬入(见 MIKA_PORT_REPORT 的剩余编译点)。
 */
object TaskService {
    fun cancel(context: Context, runKey: String? = null) {
        // 桩
    }

    fun handBack(context: Context, waitKey: Int? = null, runKey: String? = null) {
        // 桩
    }

    fun finishedMyself(context: Context, waitKey: Int? = null, runKey: String? = null) {
        // 桩
    }

    fun putAside(context: Context, waitKey: Int? = null, runKey: String? = null) {
        // 桩
    }
}
