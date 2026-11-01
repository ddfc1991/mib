/*
 * Adapted from Mika (https://github.com/wangzan101/Mika) — PairingCoordinator /
 * PairingNotifier. Copyright 2026 wangzan101 — Licensed under the Apache License,
 * Version 2.0. Modifications for Mibot (com.dl1991.mibot), GPL-3.0-or-later:
 * no accessibility auto-read of the code; zh/en copy inline.
 */
package com.openminis.app.mibot.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.openminis.app.MainActivity
import com.openminis.app.R
import com.openminis.app.mibot.design.tc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the pairing notification shows right now. */
sealed interface PairingNotice {
    data class EnterCode(val port: Int?, val error: String? = null) : PairingNotice
    data object Working : PairingNotice
    data object Done : PairingNotice
    data class Problem(val title: String) : PairingNotice
}

/**
 * One pairing session. The user opens Settings › Developer options › Wireless
 * debugging › "Pair device with pairing code"; switching apps would close that
 * dialog, so the 6-digit code is typed straight into a heads-up notification
 * (RemoteInput) shown over Settings. Mibot never reads or stores the code.
 */
class PairingCoordinator(context: Context, private val control: BuiltinShellControl) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watch: Job? = null

    @Synchronized
    fun begin() {
        control.startDiscovery()
        if (watch?.isActive == true) return
        watch = scope.launch {
            withTimeoutOrNull(WINDOW_MS) {
                combine(control.status, control.pairingDiscovery) { s, d -> notice(s, d) to (s == BuiltinShellStatus.Running) }
                    .distinctUntilChanged()
                    .transformWhile { emit(it); !it.second }
                    .collect { (n, _) -> if (n != null) PairingNotifier.show(app, n) }
            } ?: PairingNotifier.cancel(app)
            control.stopDiscovery()
        }
    }

    private fun notice(status: BuiltinShellStatus, discovery: PairingDiscovery): PairingNotice? {
        val port = (discovery as? PairingDiscovery.Found)?.port
        return when (status) {
            BuiltinShellStatus.Running -> PairingNotice.Done
            BuiltinShellStatus.Connecting -> PairingNotice.Working
            is BuiltinShellStatus.Failed ->
                if (status.reason.duringPairing) PairingNotice.EnterCode(port, PairingCopy.retry(app, status.reason))
                else PairingNotice.Problem(PairingCopy.failure(app, status.reason))
            BuiltinShellStatus.NotPaired, BuiltinShellStatus.WirelessDebuggingOff -> port?.let { PairingNotice.EnterCode(it) }
        }
    }

    @Synchronized
    fun end() {
        watch?.cancel()
        watch = null
        control.stopDiscovery()
        PairingNotifier.cancel(app)
    }

    fun submit(raw: String, port: Int?): Boolean {
        val code = PairingCode.normalize(raw)
        if (code == null) {
            PairingNotifier.show(app, PairingNotice.EnterCode(port, tc(app, "请输入 6 位数字配对码", "Enter the 6-digit pairing code")))
            return false
        }
        begin()
        PairingNotifier.show(app, PairingNotice.Working)
        scope.launch { control.startPairing(code, port) }
        return true
    }

    private companion object {
        const val WINDOW_MS = 10 * 60_000L
    }
}

object PairingCopy {
    fun failure(c: Context, f: BuiltinShellFailure): String = when (f) {
        BuiltinShellFailure.WrongCode -> tc(c, "配对码不对或已过期", "Wrong or expired pairing code")
        BuiltinShellFailure.PairingWindowClosed -> tc(c, "配对窗口已关闭，请重新打开「使用配对码配对设备」", "The pairing dialog closed — open “Pair device with pairing code” again")
        BuiltinShellFailure.NoWifi -> tc(c, "需要连接 Wi-Fi（无需能上网）", "Connect to any Wi-Fi (internet not required)")
        BuiltinShellFailure.WifiNotAllowed -> tc(c, "此 Wi-Fi 未允许无线调试，请手动打开一次", "Wireless debugging isn't allowed on this Wi-Fi yet — turn it on once by hand")
        BuiltinShellFailure.ConnectFailed -> tc(c, "连不上本机的无线调试", "Couldn't reach this phone's wireless debugging")
        BuiltinShellFailure.AuthorizationRevoked -> tc(c, "授权已被系统撤销，请重新配对", "Authorization was revoked — pair again")
        BuiltinShellFailure.HelperStartFailed -> tc(c, "后台助手没能启动", "The background helper didn't start")
        BuiltinShellFailure.UsbHelperStopped -> tc(c, "助手已停止，请重新配对", "Helper stopped — pair again")
        BuiltinShellFailure.Unknown -> tc(c, "出错了，请重试", "Something went wrong — try again")
    }

    fun retry(c: Context, f: BuiltinShellFailure): String = failure(c, f) + tc(c, "。在下方重新输入。", ". Enter it again below.")
}

object PairingNotifier {
    const val KEY_CODE = "pairing_code"
    const val EXTRA_PORT = "pairing_port"
    private const val ID = 4471
    private const val CHANNEL = "mibot_vd_pairing"

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tc(context, "无线调试配对", "Wireless debugging pairing"), NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    setShowBadge(false)
                },
            )
        }
    }

    fun show(context: Context, notice: PairingNotice) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(android.net.Uri.parse("minis://settings/virtual-display")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_question)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)
        when (notice) {
            is PairingNotice.EnterCode -> {
                val input = RemoteInput.Builder(KEY_CODE).setLabel(tc(context, "6 位配对码", "6-digit code")).build()
                val reply = PendingIntent.getBroadcast(
                    context, 1,
                    Intent(context, PairingCodeReceiver::class.java).apply { notice.port?.let { putExtra(EXTRA_PORT, it) } },
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val action = Notification.Action.Builder(null, tc(context, "输入配对码", "Enter code"), reply)
                    .addRemoteInput(input)
                    .setAllowGeneratedReplies(false)
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) action.setAuthenticationRequired(false)
                val text = notice.error ?: tc(context, "把系统弹窗里的 6 位配对码填到这里，不用离开设置页。", "Type the 6-digit code shown by Settings here — no need to leave Settings.")
                b.setContentTitle(tc(context, "Mibot：输入无线调试配对码", "Mibot: enter the pairing code"))
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setOngoing(true)
                    .setTimeoutAfter(10 * 60_000L)
                    .addAction(action.build())
            }
            PairingNotice.Working -> b.setContentTitle(tc(context, "正在配对并启动后台助手…", "Pairing and starting the helper…"))
                .setOngoing(true).setOnlyAlertOnce(true).setProgress(0, 0, true)
            PairingNotice.Done -> b.setContentTitle(tc(context, "后台操控已就绪", "Background control is ready"))
                .setContentText(tc(context, "以后会自动重连，无需再配对。", "It reconnects on its own from now on."))
                .setAutoCancel(true).setOnlyAlertOnce(true).setTimeoutAfter(8_000L)
            is PairingNotice.Problem -> b.setContentTitle(notice.title)
                .setContentText(tc(context, "回到 Mibot 查看处理办法", "Open Mibot to fix it")).setAutoCancel(true)
        }
        runCatching { nm.notify(ID, b.build()) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}

/** RemoteInput target of the pairing notification. */
class PairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PairingNotifier.KEY_CODE)?.toString() ?: return
        val port = intent.getIntExtra(PairingNotifier.EXTRA_PORT, -1).takeIf { it > 0 }
        MibotPhone.get(context).pairing.submit(code, port)
    }
}
