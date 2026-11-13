package com.mibot.cap.phone.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mibot.cap.phone.PhoneServices
import kotlinx.coroutines.launch

/**
 * 开机广播。只有用户打开了「重启后自动恢复」、并且 Mika 还拿着修改系统设置的授权时才动手,
 * 其余情况什么都不做(判断在 [BuiltinShellRecovery.restoreAfterBoot])。
 */
open class BootRestoreHandler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        // [destiny/v7-real] A boot receiver must NEVER crash the process.
        // Flavours that don't ship the phone capability (Owner not wired on
        // the Application yet) used to throw here on every boot and failed
        // the smoke test with a FATAL in logcat. Degrade to a no-op; once
        // PhoneServices.Owner is implemented this restores automatically.
        val phone = runCatching { PhoneServices.of(context) }.getOrNull()
        if (phone == null) {
            pending.finish()
            return
        }
        phone.pairing.scope.launch {
            try {
                runCatching { phone.recovery.restoreAfterBoot() }
            } finally {
                pending.finish()
            }
        }
    }
}
