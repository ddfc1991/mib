package com.mibot.life.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * 桌宠前台服务：让桌宠（悬浮窗）在后台持续显示。
 * 用 mediaPlayback 类型避免 Android 14+ 的 6h/24h 前台服务上限。
 */
class PetForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "mibot_life_pet"
        const val NOTIF_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Mibot 生活桌宠",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "桌宠状态显示" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Mibot 生活")
            .setContentText("桌宠正在生活")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }
}
