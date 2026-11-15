package com.mibot.life.life

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.mibot.life.MainActivity
import com.mibot.life.plugin.OppoGameCenterPlugin
import com.mibot.life.plugin.PluginContext
import com.mibot.life.plugin.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 生活自主引擎：真正的「自己过日子」。
 * 前台服务 + 后台协程循环，每 TICK 间隔做一次自主决策并执行：
 *  - 看作息（晨间/白日/睡前/夜间）决定该干嘛
 *  - decide() 得到可解释决策
 *  - 执行：记录 episode + 情感演化（做成事开心/受挫低落）
 *  - 持久化到 LifeStore
 *  - 偶尔主动联系用户（发通知）
 * 无论 App 在前台还是后台都在跑，不依赖用户点按钮。
 */
class LifeEngine : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var system: LifeSystem
    private lateinit var store: LifeStore
    private var tickCount = 0

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsStore(this).load()
        system = LifeSystem(this, settings)
        store = LifeStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("我在过日子…"))
        scope.launch {
            while (isActive) {
                runTick()
                delay(TICK_MS)
            }
        }
        return START_STICKY
    }

    private fun runTick() {
        try {
            tickCount++
            val st = system.snapshot()
            val ph = system.phase()
            // 夜间安静：只做轻量记账，不打扰
            val isNight = ph == RhythmPhase.NIGHT
            val decision = system.decide(
                deviceBusy = false,
                lowEnergy = st.affect.isLowEnergy(),
            )
            // 执行决策：记录 + 情感
            val choice = decision.chosen
            if (choice != null) {
                when (choice.action) {
                    LifeAction.RESEARCH -> {
                        // 学什么：挑兴趣最强的领域（20 分钟一个单元）
                        val topic = st.interests.maxByOrNull { it.strength }?.domain ?: "新东西"
                        system.recordEpisode(EpisodeKind.READ, "学$topic：${choice.reason.take(50)}")
                        system.feel(AffectEvent.SUCCESS)
                    }
                    LifeAction.WRITING -> {
                        system.recordEpisode(EpisodeKind.NOTE, choice.reason.take(60))
                        system.feel(AffectEvent.SUCCESS)
                    }
                    LifeAction.EXPLORE -> {
                        system.recordEpisode(EpisodeKind.PHONE, "探索：${choice.reason.take(50)}")
                        system.feel(AffectEvent.SUCCESS)
                    }
                    LifeAction.REST -> {
                        system.recordEpisode(EpisodeKind.READ, "休息一下")
                        system.feel(AffectEvent.REST)
                    }
                }
            }
            // 主动联系用户（白天偶尔；夜间不打扰）
            if (!isNight && system.shouldContact() && tickCount % 3 == 0) {
                system.markContacted()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID_CONTACT, buildNotification(
                    "「${st.affect.moodHint()}」${st.episodes.lastOrNull()?.let { "刚${it.title}" } ?: "有点想你"}",
                    content = "点进来看我",
                ))
            }
            // 持久化
            store.save(system.snapshot())
            // 通知更新
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, buildNotification(
                "${ph.zh} · 现在想做：${decision.chosen?.action?.zh ?: "休息"}",
                content = decision.chosen?.reason?.take(80) ?: "安静待着",
            ))
        } catch (t: Throwable) {
            android.util.Log.w("MibotLifeEngine", "tick 失败: ${t.message}")
        }
    }

    private fun buildNotification(text: String, content: String = ""): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "生活自主", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(text)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "life_engine"
        private const val NOTIF_ID = 8101
        private const val NOTIF_ID_CONTACT = 8102
        private const val TICK_MS = 20 * 60 * 1000L   // 每 20 分钟自主决策一次
        fun start(context: Context) {
            context.startForegroundService(Intent(context, LifeEngine::class.java))
        }
    }
}
