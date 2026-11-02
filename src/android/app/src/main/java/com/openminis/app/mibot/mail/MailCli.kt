package com.openminis.app.mibot.mail

import android.content.Context
import com.openminis.app.sandbox.NativeOffloadHandler
import com.openminis.app.sandbox.NativeOffloadRequest
import com.openminis.app.sandbox.NativeOffloadResult
import com.openminis.app.sandbox.RootfsManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [fork/mibot-v5b2] `mibot-mail` — 临时邮箱 / AI 邮箱 from inside the sandbox.
 * Same core (MailCore) as the app screens and the agent tool. JSON output.
 */
class MailOffloadHandler(private val context: Context) : NativeOffloadHandler {
    override fun handle(request: NativeOffloadRequest): NativeOffloadResult {
        val out = MailCli.run(context, request.argv.drop(1)) { path -> guestFile(path, request.cwd) }
        return NativeOffloadResult(out.first, out.second + "\n")
    }

    private fun guestFile(path: String, cwd: String): File {
        val abs = if (path.startsWith("/")) path else "${cwd.trimEnd('/')}/$path"
        return File(RootfsManager.getInstance(context).rootfsDir, abs.trimStart('/'))
    }
}

object MailCli {
    const val HELP = """mibot-mail — 临时邮箱 / AI 邮箱（输出 JSON）
  providers                         平台列表 + 实时探测
  providers add <file.json>         添加自定义平台模板
  providers enable|disable <id>
  providers config <id> <key> <value>   apiKey / base / adminToken / domain（加密保存）
  new [--provider ID] [--service S] [--send]   新建邮箱（失败自动换平台；--send 只用可发信平台）
  list                              台账
  inbox <addr>                      收件箱
  read <addr> <msg-id>              读一封（含自动提取的验证码/链接）
  wait-code <addr> [--from domain] [--timeout 120]
  search <query> [--box addr]
  send <addr> --to X --subject S --text T     发信（按邮箱的自动化设置：可能需要你确认）
  reply <addr> <msg-id> --text T
  forward <addr> <msg-id> --to X [--text T]
  outbox <addr> | confirm <addr> <out-id> | undo <addr> <out-id>
  autonomy <addr> auto|service|confirm
  probe [addr|--all]                探针：平台可达 / 登录 / token / 收件箱
  keepalive                         立即保活一次
  watch add <addr|*> [--from X] [--subject Y] [--note N] | watch list | watch rm <id> | watch run
  link <addr> <service>             记录该邮箱注册了哪个服务
  delete <addr>
  export <file> --passphrase P | import <file> --passphrase P
不解验证码，不接短信接码平台；临时邮箱平台只能收信。"""

    private fun opt(a: List<String>, n: String): String? { val i = a.indexOf(n); return if (i >= 0 && i + 1 < a.size) a[i + 1] else null }
    private fun pos(a: List<String>): List<String> {
        val out = mutableListOf<String>(); var i = 0
        while (i < a.size) { if (a[i].startsWith("--")) { i += if (a[i] in setOf("--all", "--send", "--confirm")) 1 else 2 } else { out += a[i]; i++ } }
        return out
    }
    private fun ok(o: Any) = 0 to (if (o is JSONObject) o.toString(2) else if (o is JSONArray) o.toString(2) else o.toString())
    private fun err(msg: String, code: Int = 1) = code to JSONObject().put("error", msg).toString(2)

    fun run(ctx: Context, argv: List<String>, file: (String) -> File = { File(it) }): Pair<Int, String> {
        if (argv.isEmpty() || argv[0] in setOf("-h", "--help", "help")) return 0 to HELP
        val a = argv.drop(1); val p = pos(a)
        fun box(i: Int = 0) = p.getOrNull(i)?.let { MailCore.get(ctx, it) }
        return try {
            runBlocking {
                when (argv[0]) {
                    "providers" -> when (p.getOrNull(0)) {
                        "add" -> MailProviders.add(ctx, file(p.getOrElse(1) { "" }).readText())?.let { err(it) } ?: ok(JSONObject().put("added", true))
                        "enable", "disable" -> { MailProviders.setEnabled(ctx, p.getOrElse(1) { "" }, p[0] == "enable"); ok(JSONObject().put("ok", true)) }
                        "config" -> { MailProviders.setConfig(ctx, p.getOrElse(1) { "" }, p.getOrElse(2) { "" }, p.getOrNull(3)); ok(JSONObject().put("saved", true).put("encrypted", true)) }
                        else -> {
                            val arr = JSONArray()
                            for (pr in MailProviders.all(ctx)) {
                                val r = MailCore.probeProvider(ctx, pr)
                                arr.put(JSONObject().put("id", pr.id).put("name", pr.name).put("kind", pr.kind)
                                    .put("send", pr.canSend).put("receive_only", !pr.canSend).put("enabled", MailProviders.enabled(ctx, pr))
                                    .put("status", r.status.zh).put("ms", r.ms).put("detail", r.detail)
                                    .put("public_inbox", pr.publicInbox).put("verified", pr.verified ?: JSONObject.NULL))
                            }
                            ok(arr)
                        }
                    }
                    "new" -> ok(MailCore.create(ctx, opt(a, "--provider"), opt(a, "--service"), "--send" in a).toPublicJson(ctx))
                    "list", "ls" -> ok(JSONArray().also { arr -> MailCore.boxes(ctx).value.forEach { arr.put(it.toPublicJson(ctx)) } })
                    "inbox" -> { val m = box() ?: return@runBlocking err("unknown mailbox"); ok(JSONArray().also { arr -> MailCore.inbox(ctx, m).forEach { arr.put(it.toJson()) } }) }
                    "read" -> {
                        val m = box() ?: return@runBlocking err("unknown mailbox")
                        val msg = MailCore.inbox(ctx, m).find { it.id == p.getOrNull(1) } ?: return@runBlocking err("unknown message")
                        val body = MailCore.read(ctx, m, msg)
                        val (code, link) = MailCore.extract(msg.subject + "\n" + body)
                        ok(msg.toJson().put("text", body.take(20_000)).put("code", code ?: JSONObject.NULL).put("link", link ?: JSONObject.NULL))
                    }
                    "wait-code" -> {
                        val m = box() ?: return@runBlocking err("unknown mailbox")
                        val f = MailCore.waitCode(ctx, m, opt(a, "--from"), (opt(a, "--timeout")?.toIntOrNull() ?: 120).coerceIn(5, 900))
                        if (f == null) err("超时：没有收到验证码", 3) else ok(f.toJson().put("address", m.address))
                    }
                    "search" -> ok(JSONArray().also { arr ->
                        MailCore.search(ctx, p.getOrElse(0) { "" }, opt(a, "--box")?.let { MailCore.get(ctx, it) }).forEach { (b, msg) -> arr.put(msg.toJson().put("mailbox", b.address)) }
                    })
                    "send" -> {
                        val m = box() ?: return@runBlocking err("unknown mailbox")
                        val to = opt(a, "--to") ?: return@runBlocking err("--to required")
                        ok(MailOutbox.submit(ctx, m, to, opt(a, "--subject").orEmpty(), opt(a, "--text").orEmpty()).toJson())
                    }
                    "reply", "forward" -> {
                        val m = box() ?: return@runBlocking err("unknown mailbox")
                        val msgId = p.getOrNull(1) ?: return@runBlocking err("message id required")
                        val orig = MailCore.inbox(ctx, m).find { it.id == msgId }
                        val to = if (argv[0] == "reply") (orig?.from ?: return@runBlocking err("unknown message")) else (opt(a, "--to") ?: return@runBlocking err("--to required"))
                        val subj = (if (argv[0] == "reply") "Re: " else "Fwd: ") + (orig?.subject ?: "")
                        ok(MailOutbox.submit(ctx, m, to, subj, opt(a, "--text").orEmpty(), argv[0], msgId).toJson())
                    }
                    "outbox" -> { val m = box() ?: return@runBlocking err("unknown mailbox"); ok(JSONArray().also { arr -> m.outbox.forEach { arr.put(it.toJson()) } }) }
                    "confirm" -> { val m = box() ?: return@runBlocking err("unknown mailbox"); MailOutbox.confirm(ctx, m.id, p.getOrElse(1) { "" }); ok(JSONObject().put("queued", true).put("undo_seconds", MailOutbox.UNDO_SECONDS)) }
                    "undo" -> { val m = box() ?: return@runBlocking err("unknown mailbox"); MailOutbox.undo(ctx, m.id, p.getOrElse(1) { "" }); ok(JSONObject().put("undone", true)) }
                    "autonomy" -> {
                        val m = box() ?: return@runBlocking err("unknown mailbox")
                        val v = runCatching { MailAutonomy.valueOf(p.getOrElse(1) { "" }.uppercase()) }.getOrElse { return@runBlocking err("auto|service|confirm") }
                        MailCore.update(ctx, m.id) { it.copy(autonomy = v) }; ok(JSONObject().put("autonomy", v.zh))
                    }
                    "probe" -> {
                        val targets = if ("--all" in a || p.isEmpty()) MailCore.boxes(ctx).value else listOfNotNull(box())
                        ok(JSONArray().also { arr -> targets.forEach { m -> val r = MailCore.probe(ctx, m); arr.put(JSONObject().put("address", m.address).put("status", r.status.zh).put("ms", r.ms).put("detail", r.detail)) } })
                    }
                    "keepalive" -> { val r = MailCore.keepAlive(ctx); ok(JSONObject().put("ok", r.ok).put("failed", JSONArray(r.failed)).put("expiring", JSONArray(r.expiring))) }
                    "watch" -> when (p.getOrNull(0)) {
                        "add" -> { val r = WatchRule("w" + System.currentTimeMillis().toString(36).takeLast(5), p.getOrElse(1) { "*" }, opt(a, "--from").orEmpty(), opt(a, "--subject").orEmpty(), "notify", opt(a, "--note").orEmpty()); MailCore.addRule(ctx, r); ok(r.toJson()) }
                        "rm" -> { MailCore.removeRule(ctx, p.getOrElse(1) { "" }); ok(JSONObject().put("removed", true)) }
                        "run" -> ok(JSONArray().also { arr -> MailCore.boxes(ctx).value.forEach { m -> MailCore.runWatch(ctx, m).forEach { arr.put(it) } } })
                        else -> ok(JSONArray().also { arr -> MailCore.rules(ctx).forEach { arr.put(it.toJson()) } })
                    }
                    "link" -> { val m = box() ?: return@runBlocking err("unknown mailbox"); MailCore.link(ctx, m, p.getOrElse(1) { "" }); ok(JSONObject().put("linked", true)) }
                    "delete", "rm" -> { val m = box() ?: return@runBlocking err("unknown mailbox"); ok(JSONObject().put("deleted", m.address).put("remote", MailCore.delete(ctx, m))) }
                    "export" -> {
                        val pass = opt(a, "--passphrase") ?: return@runBlocking err("--passphrase required")
                        val f = file(p.getOrElse(0) { "mibot-mail-ledger.json" }); f.writeText(MailCore.export(ctx, pass))
                        ok(JSONObject().put("exported", MailCore.boxes(ctx).value.size).put("encrypted", "AES-256-GCM / PBKDF2"))
                    }
                    "import" -> {
                        val pass = opt(a, "--passphrase") ?: return@runBlocking err("--passphrase required")
                        ok(JSONObject().put("imported", MailCore.import(ctx, file(p.getOrElse(0) { "" }).readText(), pass)))
                    }
                    else -> 2 to "mibot-mail: unknown command '${argv[0]}'\n$HELP"
                }
            }
        } catch (e: Throwable) {
            err(e.message ?: e.javaClass.simpleName)
        }
    }
}
