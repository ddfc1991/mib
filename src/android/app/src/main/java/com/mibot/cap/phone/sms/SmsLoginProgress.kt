package com.mibot.cap.phone.sms

import android.view.Gravity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openminis.app.R
import com.mibot.cap.phone.a11y.OverlayPlacement
import com.mibot.cap.phone.a11y.ScreenAccessibility
import com.mibot.settings.AppAccessStore
import com.mibot.ui.theme.MikaTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Non-interactive progress with a cancellation control; no per-attempt confirmation. */
internal suspend fun <T> withSmsListening(service: ScreenAccessibility, store: AppAccessStore,
    cancelled: CompletableDeferred<Unit>, block: suspend () -> T): T = withContext(Dispatchers.Main.immediate) {
    val protection = store.protectUi()
    val window = service.overlays().open(OverlayPlacement(Gravity.CENTER), secure = true) {
        MikaTheme { Surface { Column(Modifier.widthIn(max = 340.dp).padding(20.dp)) {
            Text(stringResource(R.string.sms_login_listening))
            TextButton({ cancelled.complete(Unit) }) { Text(stringResource(R.string.cancel)) }
        } } }
    }
    try { check(window != null); block() } finally { window?.close(); protection.close() }
}
