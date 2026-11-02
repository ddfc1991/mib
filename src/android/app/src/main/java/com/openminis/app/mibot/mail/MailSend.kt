package com.openminis.app.mibot.mail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * [fork/mibot-v5b2] Outgoing mail: autonomy gate (全自动 / 服务邮件自动·真人确认 /
 * 每封确认), per-mailbox rate limit + daily cap, a 10 s undo window shown as a
 * notification, and the full outgoing log kept in the ledger.
 */
object MailOutbox {
    const val UNDO_SECONDS = 10
    private const val MIN_INTERVAL_MS = 20_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<String, Job>()

    fun dailyCap(ctx: Context): Int = ctx.getSharedPreferences("mibot_mail", Context.MODE_PRIVATE).getInt("daily_cap", 30)
    fun setDailyCap(ctx: Context, n: Int) = ctx.getSharedPreferences("mibot_mail", Context.MODE_PRIVATE).edit().putInt("daily_cap", n.coerceIn(1, 500)).apply()

    private val serviceHints = Regex("(?i)no-?reply|noreply|support|verify|notification|notify|service|help|account|security|bounce|mailer|system|admin|team@|hello@")

    /** True when [to] looks like a service / robot address rather than a person. */
    fun isService(ctx: Context, to: String): Boolean {
        if (serviceHints.containsMatchIn(to)) return true
        val dom = to.substringAfter('@').lowercase()
        return com.openminis.app.mibot.vault.VaultServices.all(ctx).any { s -> s.domains.any { d -> dom == d || dom.endsWith(".$d") } }
    }

    data class Result(val record: SendRecord, val message: String) {
        fun toJson() = record.toJson().put("message", message)
    }

    /**
     * Queues one outgoing mail. [kind] send / reply / forward ([refId] = message id).
     * Returns the record: needs_confirm (wait for the user in 邮箱台账), queued
     * (sends after the undo window), blocked (rate limit / cap / receive-only).
     */
    fun submit(ctx: Context, m: Mailbox, to: String, subject: String, text: String, kind: String = "send", refId: String? = null, confirmed: Boolean = false): Result {
        val app = ctx.applicationContext
        val p = MailProviders.get(app, m.provider)
        val now = System.currentTimeMillis()
        val id = "out" + java.lang.Long.toHexString(now).takeLast(7)
        fun record(state: String, detail: String) = SendRecord(id, now, to, subject, text, state, detail, kind).also { r ->
            MailCore.update(app, m.id) { it.copy(outbox = it.outbox + r) }
        }
        if (p == null || !p.canSend) return Result(record("blocked", "仅收件平台，不能发信"), "${p?.name ?: m.provider} 是仅收件的临时邮箱，不能发信")
        val today = m.outbox.count { it.state == "sent" && now - it.at < 86_400_000L }
        if (today >= dailyCap(app)) return Result(record("blocked", "达到每日上限 ${dailyCap(app)}"), "今天已发 $today 封，达到每日上限（防止被判为垃圾邮件）")
        val last = m.outbox.filter { it.state == "sent" || it.state == "queued" }.maxOfOrNull { it.at } ?: 0L
        if (now - last < MIN_INTERVAL_MS) return Result(record("blocked", "发送过快"), "两封之间至少间隔 ${MIN_INTERVAL_MS / 1000} 秒")
        val needConfirm = !confirmed && when (m.autonomy) {
            MailAutonomy.AUTO -> false
            MailAutonomy.SERVICE -> !isService(app, to)
            MailAutonomy.CONFIRM -> true
        }
        if (needConfirm) {
            MailNotify.post(app, "待确认发信 · ${m.address}", "给 $to：$subject（在邮箱台账里确认）")
            return Result(record("needs_confirm", refId.orEmpty()), "发给真人前需要你确认：已放进待确认列表")
        }
        val r = record("queued", refId.orEmpty())
        schedule(app, m.id, r)
        return Result(r, "${UNDO_SECONDS} 秒后发送，可在通知里撤销")
    }

    fun confirm(ctx: Context, mailboxId: String, recId: String) {
        val m = MailCore.get(ctx, mailboxId) ?: return
        val r = m.outbox.find { it.id == recId && it.state == "needs_confirm" } ?: return
        MailCore.update(ctx, m.id) { b -> b.copy(outbox = b.outbox.map { if (it.id == recId) it.copy(state = "queued") else it }) }
        schedule(ctx.applicationContext, m.id, r.copy(state = "queued"))
    }

    fun undo(ctx: Context, mailboxId: String, recId: String) {
        pending.remove(recId)?.cancel()
        MailCore.update(ctx, mailboxId) { b -> b.copy(outbox = b.outbox.map { if (it.id == recId && it.state in setOf("queued", "needs_confirm")) it.copy(state = "undone") else it }) }
        MailNotify.cancel(ctx, recId.hashCode())
    }

    private fun schedule(app: Context, mailboxId: String, r: SendRecord) {
        MailNotify.undo(app, mailboxId, r)
        pending[r.id] = scope.launch {
            delay(UNDO_SECONDS * 1000L)
            pending.remove(r.id)
            val m = MailCore.get(app, mailboxId) ?: return@launch
            val res = deliver(app, m, r)
            MailCore.update(app, mailboxId) { b -> b.copy(outbox = b.outbox.map { if (it.id == r.id) it.copy(state = if (res == null) "sent" else "failed", detail = res ?: "") else it }) }
            MailNotify.cancel(app, r.id.hashCode())
            if (res != null) MailNotify.post(app, "发信失败 · ${m.address}", res)
        }
    }

    /** Returns an error, or null when the platform accepted the mail. */
    private suspend fun deliver(ctx: Context, m: Mailbox, r: SendRecord): String? {
        if (m.provider == MailDemo.ID) { MailDemo.deliver(ctx, m, r); return null }
        val p = MailProviders.get(ctx, m.provider) ?: return "平台缺失"
        val spec = p.step(r.kind) ?: p.step("send") ?: return "平台不支持 ${r.kind}"
        val vars = HashMap<String, String>()
        vars["address"] = m.address; vars["to"] = r.to; vars["subject"] = r.subject; vars["text"] = r.text; vars["id"] = r.detail
        MailCore.reveal(m.tokenEnc)?.let { vars["token"] = it }
        vars.putAll(MailProviders.configVars(ctx, p))
        val resp = MailHttp.call(spec, vars)
        return if (resp.ok) null else "HTTP ${if (resp.code < 0) resp.error else resp.code} ${resp.body.take(160)}"
    }
}

/** Undo button on the "sending in 10 s" notification. */
class MailUndoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val box = intent.getStringExtra("box") ?: return
        val rec = intent.getStringExtra("rec") ?: return
        MailOutbox.undo(context, box, rec)
    }
}

object MailNotify {
    private const val CH = "mibot_mail"

    private fun ensure(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "Mibot 邮箱", NotificationManager.IMPORTANCE_DEFAULT))
        }
        return nm
    }

    fun post(ctx: Context, title: String, text: String, id: Int = (title + text).hashCode()) {
        runCatching {
            val n = NotificationCompat.Builder(ctx, CH).setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).build()
            ensure(ctx).notify(id, n)
        }
    }

    fun undo(ctx: Context, box: String, r: SendRecord) {
        runCatching {
            val pi = PendingIntent.getBroadcast(ctx, r.id.hashCode(),
                Intent(ctx, MailUndoReceiver::class.java).putExtra("box", box).putExtra("rec", r.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CH).setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("${MailOutbox.UNDO_SECONDS} 秒后发送 · ${r.to}").setContentText(r.subject)
                .addAction(0, "撤销", pi).setTimeoutAfter(MailOutbox.UNDO_SECONDS * 1000L + 2000).build()
            ensure(ctx).notify(r.id.hashCode(), n)
        }
    }

    fun cancel(ctx: Context, id: Int) { runCatching { ensure(ctx).cancel(id) } }
}

/** 保活: periodic WorkManager job (default 6 h, not when battery is low). */
object MailKeepAlive {
    private const val WORK = "mibot_mail_keepalive"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("mibot_mail", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("keepalive", true)
    fun hours(ctx: Context) = prefs(ctx).getInt("keepalive_hours", 6)
    fun lastRun(ctx: Context) = prefs(ctx).getLong("keepalive_last", 0L)

    fun configure(ctx: Context, on: Boolean = enabled(ctx), hours: Int = hours(ctx)) {
        prefs(ctx).edit().putBoolean("keepalive", on).putInt("keepalive_hours", hours.coerceIn(1, 48)).apply()
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!on) { wm.cancelUniqueWork(WORK); return }
        // With the battery-optimisation exemption granted, run regardless of
        // battery-saver; otherwise defer while the battery is low.
        val exempt = com.openminis.app.mibot.settings.Capabilities.batteryExempt(ctx)
        val req = PeriodicWorkRequestBuilder<Worker>(hours.coerceIn(1, 48).toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(!exempt).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    class Worker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            val r = runCatching { MailCore.keepAlive(applicationContext) }.getOrNull() ?: return Result.retry()
            prefs(applicationContext).edit().putLong("keepalive_last", System.currentTimeMillis()).apply()
            if (r.failed.isNotEmpty()) MailNotify.post(applicationContext, "邮箱保活：${r.failed.size} 个异常", r.failed.joinToString("\n"))
            if (r.expiring.isNotEmpty()) MailNotify.post(applicationContext, "邮箱即将过期", r.expiring.joinToString("\n") + "\n平台会回收这些临时邮箱，相关账号请换绑")
            return Result.success()
        }
    }
}

/**
 * Debug/CI only: a loopback "platform" so the send/receive screens can be
 * shown without real credentials. Labelled 演示 · 本机回环 everywhere; it never
 * touches the network and sends nothing for real.
 */
object MailDemo {
    const val ID = "demo-loopback"
    @Volatile var enabled = false
    private val store = ConcurrentHashMap<String, MutableList<MailMessage>>()

    val provider: MailProvider get() = MailProvider(
        id = ID, name = "演示 · 本机回环（CI）", site = "", kind = "demo", canSend = true, enabledByDefault = true,
        disabledReason = null, needs = emptyList(), verified = "CI 演示，未真实收发", publicInbox = false,
        json = JSONObject().put("id", ID).put("create", JSONArray()), builtIn = true,
    )

    fun create(ctx: Context, link: String?): Mailbox {
        val n = store.size + 1
        val m = Mailbox(id = "mbdemo$n", provider = ID, address = "agent$n@demo.mibot.invalid", linked = listOfNotNull(link),
            autonomy = MailCore.defaultAutonomy(ctx), lastLogin = System.currentTimeMillis())
        store.putIfAbsent(m.address, mutableListOf())
        return m
    }

    fun inbox(m: Mailbox): List<MailMessage> = store.getOrPut(m.address) { mutableListOf() }.toList().reversed()

    fun deliver(ctx: Context, from: Mailbox, r: SendRecord) {
        val list = store.getOrPut(r.to.lowercase()) { mutableListOf() }
        list.add(MailMessage("d" + list.size, from.address, r.subject, java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()), r.text.take(80), r.text, null))
    }

    /** Seeds two loopback inboxes plus one verification mail. */
    fun seed(ctx: Context) {
        if (MailCore.boxes(ctx).value.any { it.provider == ID }) return
        kotlinx.coroutines.runBlocking {
            val a = MailCore.create(ctx, ID, "deepseek")
            MailCore.create(ctx, ID, null)
            store.getOrPut(a.address) { mutableListOf() }.add(MailMessage("v1", "noreply@demo-console.mibot.invalid", "你的验证码", "12:00:00",
                "你的验证码是 482913，10 分钟内有效。", "你的验证码是 482913，10 分钟内有效。", null))
        }
    }
}
