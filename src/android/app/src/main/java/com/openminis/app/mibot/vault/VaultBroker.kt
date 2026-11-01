package com.openminis.app.mibot.vault

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import com.openminis.app.mibot.design.tc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * [fork/mibot-v2] App-wide confirmation prompt used by the vault (每次询问 /
 * high-risk actions) and by agent tools that need the user's yes. Any
 * coroutine can [ask]; [VaultConfirmHost] at the app root shows the dialog.
 * No answer within two minutes counts as "no".
 */
object VaultConfirmBus {
    class Request(val title: String, val message: String, val danger: Boolean, val answer: CompletableDeferred<Boolean>)

    val pending = MutableStateFlow<Request?>(null)
    private val mutex = Mutex()

    suspend fun ask(title: String, message: String, danger: Boolean = false): Boolean = mutex.withLock {
        val req = Request(title, message, danger, CompletableDeferred())
        pending.value = req
        try {
            withTimeoutOrNull(120_000) { req.answer.await() } ?: false
        } finally {
            pending.value = null
        }
    }
}

@Composable
fun VaultConfirmHost() {
    val req by VaultConfirmBus.pending.collectAsState()
    val r = req ?: return
    val zh = androidx.compose.ui.platform.LocalConfiguration.current.locales.let { !it.isEmpty && it[0].language == "zh" }
    AlertDialog(
        onDismissRequest = { r.answer.complete(false) },
        title = { Text(r.title, fontWeight = FontWeight.SemiBold) },
        text = { Text(r.message) },
        confirmButton = {
            TextButton(onClick = { r.answer.complete(true) }) {
                Text(
                    if (zh) "允许" else "Allow",
                    color = if (r.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { r.answer.complete(false) }) { Text(if (zh) "拒绝" else "Deny") }
        },
    )
}

/**
 * [fork/mibot-v2] The only path by which a vault secret is used on the
 * agent's behalf. The agent references an account as `{{vault:<id>}}`
 * (secret) or `{{vault:<id>.username}}` inside a shell command or a
 * browser_use action; the app substitutes the value right before running it,
 * after the permission check, and scrubs it from whatever comes back. The
 * model never receives plaintext. Every use is written to the audit log.
 */
object VaultBroker {
    private val PLACEHOLDER = Regex("""\{\{vault:([A-Za-z0-9_-]+)(?:\.(secret|password|username))?\}\}""")

    private val HIGH_RISK = listOf(
        "pay", "payment", "purchase", "buy", "checkout", "transfer", "withdraw", "delete", "remove account",
        "send as", "post as", "支付", "付款", "购买", "转账", "提现", "删除", "注销", "以我的名义", "发送",
    )

    fun isHighRisk(text: String): Boolean {
        val t = text.lowercase()
        return HIGH_RISK.any { t.contains(it) }
    }

    /** Permission gate. Returns null when allowed, else the refusal text for the agent. */
    suspend fun authorize(ctx: Context, e: VaultEntry, actor: String, purpose: String, highRisk: Boolean): String? {
        if (e.permission == VaultPermission.DISABLED) {
            VaultStore.log(ctx, e, actor, purpose, "disabled")
            return "Account ${e.id} is disabled for AI use (禁用). Ask the user to enable it in 账号台账."
        }
        val needAsk = e.permission == VaultPermission.ASK || highRisk
        if (needAsk) {
            val title = if (highRisk) tc(ctx, "高风险操作需确认", "Confirm a high-risk action") else tc(ctx, "允许使用账号？", "Allow account use?")
            val msg = tc(
                ctx,
                "AI 请求使用「${e.site} · ${e.username}」\n用途：$purpose\n\n密码不会显示给 AI。",
                "The AI wants to use \"${e.site} · ${e.username}\"\nPurpose: $purpose\n\nThe secret is never shown to the AI.",
            )
            val ok = VaultConfirmBus.ask(title, msg, danger = highRisk)
            if (!ok) {
                VaultStore.log(ctx, e, actor, purpose, "denied")
                return "The user denied use of account ${e.id}. Do not retry without asking."
            }
        }
        VaultStore.log(ctx, e, actor, purpose, "allowed")
        return null
    }

    class Substitution(val text: String, val secrets: List<String>, val error: String?)

    /**
     * Replace every placeholder in [text]. [jsonEscape] = the text is a JSON
     * document (tool arguments), so values are inserted JSON-string-escaped.
     */
    suspend fun substitute(ctx: Context, text: String, actor: String, purpose: String, jsonEscape: Boolean): Substitution {
        VaultStore.ensureLoaded(ctx)
        val matches = PLACEHOLDER.findAll(text).toList()
        if (matches.isEmpty()) return Substitution(text, emptyList(), null)
        val highRisk = isHighRisk(text)
        val authorized = mutableSetOf<String>()
        val secrets = mutableListOf<String>()
        var out = text
        for (m in matches.distinctBy { it.value }) {
            val id = m.groupValues[1]
            val field = m.groupValues[2].ifEmpty { "secret" }
            val e = VaultStore.get(ctx, id) ?: return Substitution(text, emptyList(), "Unknown vault account id: $id. Use account_vault action=list.")
            if (id !in authorized) {
                authorize(ctx, e, actor, purpose, highRisk)?.let { return Substitution(text, emptyList(), it) }
                authorized += id
            }
            val value = if (field == "username") e.username else {
                VaultStore.revealSecret(ctx, e) ?: return Substitution(text, emptyList(), "Account $id has no stored secret.")
            }
            if (field != "username") secrets += value
            val inserted = if (jsonEscape) JSONObject.quote(value).let { it.substring(1, it.length - 1) } else value
            out = out.replace(m.value, inserted)
        }
        return Substitution(out, secrets, null)
    }

    /** Remove any secret that leaked into output. */
    fun scrub(output: String, secrets: List<String>): String =
        secrets.filter { it.length >= 4 }.fold(output) { acc, s -> acc.replace(s, "••••••") }

    /** Env vars for `vault use <id> -- cmd`. */
    fun envFor(e: VaultEntry, secret: String): Map<String, String> = mapOf(
        "VAULT_ID" to e.id,
        "VAULT_SITE" to e.site,
        "VAULT_USERNAME" to e.username,
        "VAULT_SECRET" to secret,
        "VAULT_PASSWORD" to secret,
    )
}
