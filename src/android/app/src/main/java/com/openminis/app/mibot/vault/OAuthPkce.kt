package com.openminis.app.mibot.vault

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.openminis.app.auth.OAuthCallbackServer
import com.openminis.app.auth.OAuthForegroundGate
import com.openminis.app.auth.OAuthTabLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * [fork/mibot-autokey-v2] Recipe flow `oauth_pkce` (OpenRouter): the key
 * comes from the provider's own OAuth PKCE exchange, no page automation.
 *
 * 1. PKCE verifier + S256 challenge + state ([Pkce]).
 * 2. `authUrl?callback_url=…&code_challenge=…&code_challenge_method=S256&state=…&key_label=Mibot`
 *    opens in a Custom Tab. Callbacks are tried in the recipe's order:
 *    - `deeplink`: callback_url = the app deep link (mibot://openrouter-callback,
 *      intent-filter on MainActivity → [onDeepLink]).
 *    - `loopback`: callback_url = http://localhost:<port>/callback
 *      ([OAuthCallbackServer]; the only non-https form OpenRouter documents).
 *    When the user comes back to Mibot without a callback (e.g. the auth page
 *    refused the app scheme), the next kind is opened. A late code from an
 *    earlier attempt is still accepted (its verifier is kept by state).
 * 3. Back in the foreground, POST `exchangeUrl` {code, code_verifier,
 *    code_challenge_method:S256} → {key}.
 */
object OAuthPkce {
    private const val TAG = "MibotOAuthPkce"
    const val DEEP_LINK = "mibot://openrouter-callback"

    private val callbacks = Channel<Pkce.Callback>(capacity = 8)
    @Volatile private var activeDeepLink: String? = null
    @Volatile private var cancelled = false
    private val resumes = MutableStateFlow(0)

    /** MainActivity (onCreate / onNewIntent): true when [uri] is our callback and was consumed. */
    fun onDeepLink(uri: Uri?): Boolean {
        val u = uri?.toString() ?: return false
        val cb = Pkce.parseCallback(u, activeDeepLink ?: DEEP_LINK) ?: return false
        if (activeDeepLink == null) { Log.w(TAG, "callback with no flow running (app restarted?) — ignored"); return true }
        callbacks.trySend(cb)
        return true
    }

    fun cancel() { cancelled = true }

    class Failed(msg: String) : Exception(msg)
    class Cancelled : Exception("cancelled")

    /** Runs the flow for [r]; returns the key. [onStep] updates the task row. */
    suspend fun run(app: Context, r: KeyRecipes.Recipe, onStep: (String) -> Unit): String {
        val j = r.json
        val authUrl = j.optString("authUrl").ifBlank { "https://openrouter.ai/auth" }
        val exchangeUrl = j.optString("exchangeUrl").ifBlank { "https://openrouter.ai/api/v1/auth/keys" }
        val deepLink = j.optString("deepLink").ifBlank { DEEP_LINK }
        val label = j.optString("keyLabel").ifBlank { "Mibot" }
        val kinds = j.optJSONArray("callbacks")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in Pkce.CALLBACK_KINDS } }
            ?.takeIf { it.isNotEmpty() } ?: listOf("deeplink", "loopback")
        cancelled = false
        while (callbacks.tryReceive().isSuccess) { /* drop stale */ }
        activeDeepLink = deepLink
        val attempts = LinkedHashMap<String, String>()
        var server: OAuthCallbackServer? = null
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_START) resumes.value = resumes.value + 1 }
        withContext(Dispatchers.Main) { ProcessLifecycleOwner.get().lifecycle.addObserver(obs) }
        val t0 = System.currentTimeMillis()
        try {
            var kindIdx = 0
            var got: Pkce.Callback? = null
            var verifier: String? = null
            while (got == null) {
                if (kindIdx >= kinds.size) throw Failed(tc(app, "没有收到授权回调（可点重试）", "No authorization callback arrived (tap retry)"))
                val kind = kinds[kindIdx]
                val v = Pkce.verifier(ByteArray(64).also { SecureRandom().nextBytes(it) })
                val state = Pkce.verifier(ByteArray(32).also { SecureRandom().nextBytes(it) })
                attempts[state] = v
                val callbackUrl = when (kind) {
                    "loopback" -> {
                        server?.stop()
                        val srv = OAuthCallbackServer(3000, listOf(3001, 3002), onError = { err ->
                            callbacks.trySend(Pkce.Callback(null, null, err.description ?: err.error))
                        }) { c, st -> callbacks.trySend(Pkce.Callback(c, st, null)) }
                        server = srv
                        srv.start()
                        "http://localhost:${srv.boundPort}/callback"
                    }
                    else -> deepLink
                }
                val url = Pkce.authUrl(authUrl, callbackUrl, Pkce.challengeS256(v), state, label)
                onStep(if (kindIdx == 0) tc(app, "在打开的页面里登录并点「授权」", "Sign in and tap Authorize on the page that opened")
                    else tc(app, "授权页没有回到 App，已改用本机回调重新打开：请再点一次「授权」", "Reopened with a localhost callback — tap Authorize again"))
                Log.i(TAG, "attempt ${kindIdx + 1}/${kinds.size} via $kind")
                OAuthTabLauncher.launch(app, Uri.parse(url), newTask = true)
                val resumesAtLaunch = resumes.value
                // Wait for a callback; a return to the app without one moves to the next kind (after a short grace).
                var cameBackAt = 0L
                while (got == null) {
                    if (cancelled) throw Cancelled()
                    if (System.currentTimeMillis() - t0 > 12 * 60_000L) throw Failed(tc(app, "授权超时（12 分钟）", "Authorization timed out (12 min)"))
                    val cb = withTimeoutOrNull(700) { callbacks.receive() }
                    if (cb != null) {
                        if (cb.error != null && cb.code == null) throw Failed(tc(app, "授权被拒绝或失败：${cb.error.take(80)}", "Authorization failed: ${cb.error.take(80)}"))
                        val ver = cb.code?.let { Pkce.verifierFor(cb, attempts) }
                        if (ver == null) { Log.w(TAG, "callback without code / unknown state — ignored"); continue }
                        got = cb; verifier = ver; break
                    }
                    if (resumes.value > resumesAtLaunch && kindIdx < kinds.size - 1) {
                        if (cameBackAt == 0L) cameBackAt = System.currentTimeMillis()
                        else if (System.currentTimeMillis() - cameBackAt > 3_000) break
                    }
                }
                if (got == null) { kindIdx++; delay(300) }
            }
            server?.stop(); server = null
            onStep(tc(app, "已授权，换取 Key…", "Authorized — exchanging for a key…"))
            OAuthForegroundGate.awaitForeground(TAG)
            val key = exchange(exchangeUrl, got!!.code!!, verifier!!)
            if (!KeyMatcher(r.keyRegex).matches(key) && !KeyMatcher.looksLikeToken(key)) throw Failed(tc(app, "OpenRouter 返回的不像是 Key", "OpenRouter returned something that is not a key"))
            return key
        } finally {
            server?.stop()
            activeDeepLink = null
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { ProcessLifecycleOwner.get().lifecycle.removeObserver(obs) }
        }
    }

    private suspend fun exchange(url: String, code: String, verifier: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("HTTP-Referer", "https://github.com/dl1991/OpenMinis")
            setRequestProperty("X-Title", "Mibot")
        }
        try {
            conn.outputStream.use { it.write(Pkce.exchangeBody(code, verifier).toString().toByteArray()) }
            val rc = conn.responseCode
            val body = (if (rc in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (rc !in 200..299) {
                val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                throw Failed("OpenRouter HTTP $rc ${msg.take(80)}".trim())
            }
            JSONObject(body).getString("key")
        } finally { conn.disconnect() }
    }

    private fun tc(ctx: Context, zh: String, en: String) = com.openminis.app.mibot.design.tc(ctx, zh, en)
}
