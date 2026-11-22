package com.mibot.task

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mibot.core.run.ChannelPeer
import com.openminis.app.MainActivity

/**
 * 任务服务的桩:Mika 的 TaskService.kt(1208 行)挂了一整棵依赖(聊天/确认/记忆/接管/agent),
 * cap 只用它的四条静态命令(交回/自己完成/取消/暂离)。这里留同名签名,动作交给 Mibot 侧实现;
 * 完整 TaskService 如后续需要再整类搬入(见 MIKA_PORT_REPORT 的剩余编译点)。
 */
object TaskService {
    const val CHANNEL_PROGRESS = "task_progress"

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

    /** 渠道交来的任务保留回复地址(wechat 模块用);当前降级:不接 Mibot AgentLoop,no-op。 */
    fun startChannel(context: Context, peer: ChannelPeer, messageId: String, goal: String) {
        // 桩:wechat 新消息暂不触发任务,后续接 AgentLoop 通道。
    }

    /** 通知渠道(wechat/calls 模块用):注册进度渠道,幂等。 */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existing = manager.getNotificationChannel(CHANNEL_PROGRESS)
        if (existing == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Task progress", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    /** 打开 app(wechat 通知用):跳主 Activity。 */
    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
