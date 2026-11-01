package com.openminis.app.mibot.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.openminis.app.MainActivity
import com.openminis.app.R
import com.openminis.app.mibot.design.tc

/**
 * [fork/mibot-v3] Persistent notification while Mibot controls apps in the
 * background, with a one-tap Stop. Keeps the process (and the ImageReader that
 * receives the hidden display's frames) alive.
 */
class VdForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VdController.close(this)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tc(this, "后台操控", "Background control"), NotificationManager.IMPORTANCE_LOW))
        }
        val s = VdController.state.value
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setData(android.net.Uri.parse("minis://settings/virtual-display")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VdForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when (s.mode) {
            VdController.Mode.A11Y -> tc(this, "正在主屏上操作（无障碍模式）", "Operating your main screen (accessibility mode)")
            else -> tc(this, "Mibot 正在后台虚拟屏操作${s.app?.let { " $it" } ?: ""}，不占用你的屏幕", "Mibot is working on a hidden display${s.app?.let { " ($it)" } ?: ""} — your screen stays yours")
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_hourglass)
            .setContentTitle(tc(this, "后台操控进行中", "Background control active"))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, tc(this, "停止", "Stop"), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "mibot_vd_control"
        private const val ID = 4472
        private const val ACTION_STOP = "com.openminis.app.mibot.phone.STOP"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, VdForegroundService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, VdForegroundService::class.java)) }
        }
    }
}

/** Small "Mibot is operating your screen" pill (accessibility fallback only; needs overlay permission). */
object VdOverlay {
    private var view: TextView? = null

    fun show(context: Context) {
        val app = context.applicationContext
        if (view != null || !Settings.canDrawOverlays(app)) return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                val wm = app.getSystemService(WindowManager::class.java)
                val tv = TextView(app).apply {
                    text = tc(app, "● Mibot 正在操作 · 通知栏可停止", "● Mibot is operating · stop from notification")
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 12f
                    setPadding(28, 12, 28, 12)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 40f
                        setColor(0xE0B9532F.toInt())
                    }
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 96 }
                wm.addView(tv, lp)
                view = tv
            }
        }
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            view?.let { v -> runCatching { app.getSystemService(WindowManager::class.java).removeView(v) } }
            view = null
        }
    }
}
