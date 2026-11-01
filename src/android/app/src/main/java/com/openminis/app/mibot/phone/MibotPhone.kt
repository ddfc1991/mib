package com.openminis.app.mibot.phone

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.openminis.app.offload.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * [fork/mibot-v3] Process-wide holder of the background phone-control pieces:
 * the helper Binder hub, the Wireless-Debugging helper manager (primary path),
 * pairing, and the Shizuku launch path (alternative). Created lazily and only
 * in Mibot's own process — the helper process never touches this.
 */
class MibotPhone private constructor(context: Context) {
    val app: Context = context.applicationContext
    val hub = HelperHub(app)
    val builtin = BuiltinShellManager(app, hub)
    val pairing: PairingCoordinator by lazy { PairingCoordinator(app, builtin.engine) }

    /** Wireless debugging needs Android 11; Mibot's hidden-display control is enabled from Android 12. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= BuiltinShellManager.MIN_SDK

    fun start() {
        if (!supported) return
        builtin.start()
    }

    /**
     * Alternative path: Shizuku is installed, running and granted. Starts the
     * same helper (as Shizuku's uid — shell in adb mode) and waits for the
     * handoff. Returns null on success or a short error.
     */
    @SuppressLint("PrivateApi")
    suspend fun launchViaShizuku(): String? = withContext(Dispatchers.IO) {
        if (!supported) return@withContext "Android 12+ required"
        if (!ShizukuManager.isReady()) return@withContext "Shizuku is not running or not granted"
        val uid = runCatching { Shizuku.getUid() }.getOrDefault(-1)
        if (uid != HelperHandoff.SHELL_UID && uid != HelperHandoff.ROOT_UID) return@withContext "Unexpected Shizuku uid $uid"
        val command = HelperHandoff.command(app.applicationInfo.sourceDir, app.packageName, Process.myUid(), ownCertPin(app))
        val before = hub.shell.value
        hub.source = "shizuku"
        val proc = runCatching {
            val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            m.invoke(null, arrayOf("sh", "-c", command), null, null) as java.lang.Process
        }.getOrElse { return@withContext "Shizuku newProcess failed: ${it.message}" }
        try {
            proc.outputStream.write(HelperHandoff.stdinLine(hub.issueLaunchNonce()))
            proc.outputStream.flush()
            val ok = withTimeoutOrNull(15_000) { hub.shell.first { it != null && it !== before } } != null
            if (ok) null else "Helper handoff timed out"
        } catch (t: Throwable) {
            Log.w(TAG, "shizuku launch failed", t)
            "Shizuku launch failed: ${t.message}"
        } finally {
            runCatching { proc.outputStream.close() }
            runCatching { proc.waitFor(2, TimeUnit.SECONDS) }
            runCatching { proc.destroy() }
        }
    }

    /**
     * "Start from a computer": writes a script into the app's external files dir;
     * `adb shell sh <path>` from a PC (USB debugging) starts the same helper.
     * Returns the command to run, or null when the dir is unavailable.
     */
    fun writeUsbScript(): String? = runCatching {
        val dir = app.getExternalFilesDir(null) ?: return null
        val f = java.io.File(dir, HelperHandoff.USB_SCRIPT)
        val script = HelperHandoff.usbScript(
            app.applicationInfo.sourceDir, app.packageName, Process.myUid(), ownCertPin(app), hub.usbNonce(), f.absolutePath,
        )
        f.writeText(script)
        HelperHandoff.usbCommand(f.absolutePath)
    }.getOrNull()

    companion object {
        private const val TAG = "MibotPhone"
        @Volatile private var instance: MibotPhone? = null
        fun get(context: Context): MibotPhone =
            instance ?: synchronized(this) { instance ?: MibotPhone(context).also { instance = it } }
    }
}
