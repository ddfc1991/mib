package com.openminis.app.mibot.vault

import android.content.Context
import com.openminis.app.sandbox.NativeOffloadHandler
import com.openminis.app.sandbox.NativeOffloadRequest
import com.openminis.app.sandbox.NativeOffloadResult
import com.openminis.app.sandbox.RootfsManager
import com.openminis.app.sandbox.ShellExecutor
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [fork/mibot-v2] `vault` — the Account Vault from inside the sandbox. Talks
 * to the app over the native-offload socket like the other built-in CLIs.
 * Never prints a plaintext secret. Every use is audited and honours the
 * account's 自动使用 / 每次询问 / 禁用 permission.
 */
class VaultOffloadHandler(private val context: Context) : NativeOffloadHandler {

    override fun handle(request: NativeOffloadRequest): NativeOffloadResult {
        val argv = request.argv.drop(1)
        if (argv.isEmpty() || argv[0] in setOf("-h", "--help", "help")) return NativeOffloadResult(0, HELP)
        VaultStore.ensureLoaded(context)
        return try {
            when (argv[0]) {
                "ls", "list" -> ls(argv.drop(1))
                "show" -> show(argv.drop(1))
                "status" -> status(argv.drop(1))
                "checkin" -> checkin(argv.drop(1))
                "use" -> use(argv.drop(1), request)
                "site" -> site(argv.drop(1), request)
                else -> NativeOffloadResult(2, "vault: unknown command '${argv[0]}'\n$HELP")
            }
        } catch (e: Throwable) {
            NativeOffloadResult(1, "vault: ${e.message ?: e.javaClass.simpleName}\n")
        }
    }

    private fun opt(args: List<String>, name: String): String? {
        val i = args.indexOf(name)
        return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
    }

    private fun select(args: List<String>): List<VaultEntry> {
        val all = VaultStore.entries(context).value
        val cat = opt(args, "--category")
        return when {
            "--all" in args -> all
            cat != null -> all.filter { it.category == cat }
            else -> args.firstOrNull { !it.startsWith("--") }?.let { id -> all.filter { it.id == id } } ?: emptyList()
        }
    }

    private fun ls(args: List<String>): NativeOffloadResult {
        val cat = opt(args, "--category")
        val list = VaultStore.entries(context).value.filter { cat == null || it.category == cat }
        if ("--json" in args) {
            val arr = JSONArray().apply { list.forEach { put(it.toPublicJson()) } }
            return NativeOffloadResult(0, arr.toString(2) + "\n")
        }
        if (list.isEmpty()) return NativeOffloadResult(0, "(no accounts)\n")
        val sb = StringBuilder()
        sb.append(String.format("%-9s %-10s %-26s %-24s %s\n", "ID", "CATEGORY", "SITE", "USERNAME", "PERMISSION"))
        list.forEach {
            sb.append(String.format("%-9s %-10s %-26s %-24s %s\n", it.id, it.category.take(10), it.site.take(26), it.username.take(24), it.permission.name.lowercase()))
        }
        return NativeOffloadResult(0, sb.toString())
    }

    private fun show(args: List<String>): NativeOffloadResult {
        val e = args.firstOrNull()?.let { VaultStore.get(context, it) } ?: return NativeOffloadResult(1, "vault show: unknown id\n")
        val o = e.toPublicJson().put("secret", if (e.hasSecret) "••••••" else JSONObject.NULL)
        return NativeOffloadResult(0, o.toString(2) + "\n")
    }

    private fun status(args: List<String>): NativeOffloadResult {
        val targets = if (args.isEmpty()) VaultStore.entries(context).value else select(args)
        if (targets.isEmpty()) return NativeOffloadResult(1, "vault status: nothing selected (use --all, an id, or --category X)\n")
        val refresh = "--cached" !in args
        val arr = JSONArray()
        for (e in targets) {
            val result = if (refresh) runBlocking { VaultSites.refreshStatus(context, e, "cli") } else "cached"
            val fresh = VaultStore.get(context, e.id) ?: e
            arr.put(JSONObject().put("id", e.id).put("site", e.site).put("result", result)
                .put("status", fresh.status?.toJson() ?: JSONObject()))
        }
        return NativeOffloadResult(0, arr.toString(2) + "\n")
    }

    private fun checkin(args: List<String>): NativeOffloadResult {
        val targets = select(args)
        if (targets.isEmpty()) return NativeOffloadResult(1, "vault checkin: nothing selected (use --all, an id, or --category X)\n")
        val arr = JSONArray()
        for (e in targets) {
            val r = runBlocking { VaultSites.checkin(context, e, "cli") }
            arr.put(JSONObject().put("id", e.id).put("site", e.site).put("state", r.state).put("message", r.message)
                .apply { r.url?.let { put("open_url", it) } })
        }
        return NativeOffloadResult(0, arr.toString(2) + "\n")
    }

    /** vault use <id> [--purpose "..."] -- <cmd...> */
    private fun use(args: List<String>, request: NativeOffloadRequest): NativeOffloadResult {
        val sep = args.indexOf("--")
        if (sep < 1 || sep == args.lastIndex) return NativeOffloadResult(2, "usage: vault use <id> [--purpose TEXT] -- <command>\n")
        val head = args.subList(0, sep)
        val command = args.subList(sep + 1, args.size).joinToString(" ") { shellQuote(it) }
        val e = VaultStore.get(context, head[0]) ?: return NativeOffloadResult(1, "vault use: unknown id ${head[0]}\n")
        val purpose = opt(head, "--purpose") ?: "cli: ${args.subList(sep + 1, args.size).joinToString(" ").take(80)}"
        val refusal = runBlocking { VaultBroker.authorize(context, e, "cli", purpose, VaultBroker.isHighRisk(purpose + " " + command)) }
        if (refusal != null) return NativeOffloadResult(3, "vault use: $refusal\n")
        val secret = VaultStore.revealSecret(context, e) ?: return NativeOffloadResult(1, "vault use: account has no stored secret\n")
        val env = VaultBroker.envFor(e, secret) +
            listOfNotNull(request.sessionId?.let { "MINIS_CHAT_SESSION_ID" to it }).toMap()
        val result = runBlocking {
            ShellExecutor.execute(context, "cd ${shellQuote(request.cwd)} 2>/dev/null; $command", timeout = 10 * 60_000L, environment = env)
        }
        val out = VaultBroker.scrub(result.output, listOf(secret))
        return NativeOffloadResult(result.exitCode, if (out.endsWith("\n") || out.isEmpty()) out else out + "\n")
    }

    private fun site(args: List<String>, request: NativeOffloadRequest): NativeOffloadResult {
        return when (args.firstOrNull()) {
            "ls", "list" -> {
                val arr = JSONArray()
                VaultSites.all(context).forEach {
                    arr.put(JSONObject().put("id", it.id).put("name", it.name).put("builtin", it.builtIn)
                        .put("status", it.hasStatus).put("checkin", it.hasCheckin))
                }
                NativeOffloadResult(0, arr.toString(2) + "\n")
            }
            "add" -> {
                val src = args.getOrNull(1) ?: return NativeOffloadResult(2, "usage: vault site add <manifest.json | '{...json...}'>\n")
                val json = if (src.trimStart().startsWith("{")) src else readGuestFile(src, request.cwd)
                    ?: return NativeOffloadResult(1, "vault site add: cannot read $src (pass the JSON inline if the file is under /var/minis)\n")
                val err = VaultSites.add(context, json)
                if (err != null) NativeOffloadResult(1, "vault site add: $err\n").also { VaultStore.log(context, null, "cli", "site add", "error") }
                else NativeOffloadResult(0, "ok\n").also { VaultStore.log(context, null, "cli", "site add", "ok") }
            }
            else -> NativeOffloadResult(2, "usage: vault site ls | vault site add <manifest.json>\n")
        }
    }

    private fun readGuestFile(path: String, cwd: String): String? {
        val abs = if (path.startsWith("/")) path else "${cwd.trimEnd('/')}/$path"
        val host = File(RootfsManager.getInstance(context).rootfsDir, abs.trimStart('/'))
        return runCatching { host.readText() }.getOrNull()
    }

    private fun shellQuote(s: String): String =
        if (s.matches(Regex("[A-Za-z0-9_@%+=:,./-]+"))) s else "'" + s.replace("'", "'\"'\"'") + "'"

    companion object {
        const val NAME = "vault"
        val HELP = """
            vault — Mibot Account Vault (secrets are never printed)

              vault ls [--category X] [--json]     list accounts (metadata only)
              vault show <id>                      one account, secret masked
              vault status [--all|<id>|--category X] [--cached]
                                                   refresh balance/points/quota via site manifests
              vault checkin [--all|<id>|--category X]
                                                   run check-ins (captcha sites report state=manual)
              vault use <id> [--purpose TEXT] -- <cmd...>
                                                   run cmd with VAULT_USERNAME / VAULT_SECRET env vars;
                                                   permission-checked, audited, secret scrubbed from output
              vault site ls | vault site add <manifest.json|'{json}'>
                                                   list / add status+checkin site manifests

            In tool calls you can also write {{vault:<id>}} or {{vault:<id>.username}}
            inside shell_execute commands or browser_use text; Mibot substitutes it.
        """.trimIndent() + "\n"
    }
}
