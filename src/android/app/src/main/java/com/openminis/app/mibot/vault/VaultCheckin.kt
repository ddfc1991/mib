package com.openminis.app.mibot.vault

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.openminis.app.mibot.design.tc
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * [fork/mibot-v4] Daily auto check-in. A WorkManager periodic job (24 h,
 * network required, first run around 09:00 local) refreshes status and runs
 * check-in for every account whose AI permission is not 禁用, then posts one
 * summary notification. Captcha sites come back as 需要手动 and are listed —
 * never solved.
 */
object VaultCheckin {
    private const val WORK = "mibot_daily_checkin"
    private const val CHANNEL = "mibot_checkin"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("mibot_vault_services", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("daily_checkin", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("daily_checkin", on).apply()
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!on) { wm.cancelUniqueWork(WORK); return }
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val req = PeriodicWorkRequestBuilder<Worker>(24, TimeUnit.HOURS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    data class Summary(val ok: Int, val manual: Int, val failed: Int, val lines: List<String>)

    suspend fun runAll(ctx: Context): Summary {
        VaultStore.ensureLoaded(ctx)
        var ok = 0; var manual = 0; var failed = 0
        val lines = mutableListOf<String>()
        for (e in VaultStore.entries(ctx).value.filter { it.permission != VaultPermission.DISABLED }) {
            runCatching { VaultSites.refreshStatus(ctx, e, "checkin") }
            val r = runCatching { VaultSites.checkin(ctx, e, "checkin") }.getOrNull() ?: continue
            when (r.state) {
                "ok" -> { ok++; lines.add("✓ ${e.site} ${r.message}") }
                "manual" -> { manual++; lines.add("✋ ${e.site} " + tc(ctx, "需要手动", "manual")) }
                "error" -> { failed++; lines.add("✗ ${e.site} ${r.message}") }
            }
        }
        return Summary(ok, manual, failed, lines)
    }

    fun notify(ctx: Context, s: Summary) {
        if (s.ok + s.manual + s.failed == 0) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, tc(ctx, "每日签到", "Daily check-in"), NotificationManager.IMPORTANCE_LOW))
        }
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("minis://settings/vault")
        }
        val pi = open?.let { PendingIntent.getActivity(ctx, 0x5167, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val title = tc(ctx, "签到：成功 ${s.ok} · 需手动 ${s.manual} · 失败 ${s.failed}", "Check-in: ${s.ok} ok · ${s.manual} manual · ${s.failed} failed")
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(s.lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(s.lines.take(12).joinToString("\n")))
            .setAutoCancel(true)
            .apply { pi?.let { setContentIntent(it) } }
            .build()
        runCatching { nm.notify(0x5167, n) }
    }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val s = runAll(applicationContext)
            notify(applicationContext, s)
            return Result.success()
        }
    }
}
