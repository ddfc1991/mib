package com.openminis.app.mibot.feed

import android.content.Context
import com.openminis.app.mibot.design.tc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * [fork/mibot-feed] Process-wide 动态 entry: the store at `filesDir/feed/`
 * and the small emit helpers real sources call. Every emit is fire-and-forget
 * on IO and swallows its own errors, so a feed problem can never break the
 * caller (a scheduled run, a vault save, a plugin start, a group turn).
 */
object Feed {
    @Volatile private var store: FeedStore? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun store(context: Context): FeedStore =
        store ?: synchronized(this) {
            store ?: FeedStore(File(context.applicationContext.filesDir, "feed")).also { store = it }
        }

    fun emit(context: Context, kind: String, title: String, body: String = "", sourceRef: String? = null) {
        val app = runCatching { context.applicationContext }.getOrNull() ?: return
        scope.launch { runCatching { store(app).add(kind, title, body, sourceRef) } }
    }

    /** A scheduled task run finished. */
    fun taskFinished(context: Context, label: String, preview: String?, ok: Boolean, sessionId: String?) = runCatching {
        val title = if (ok) tc(context, "定时任务完成：$label", "Scheduled task done: $label")
        else tc(context, "定时任务失败：$label", "Scheduled task failed: $label")
        emit(context, FeedKind.TASK_RESULT, title, preview.orEmpty(), sessionId?.let { "session:$it" })
    }

    /** A key was saved to the vault. Only the provider/site name — never the secret. */
    fun keySaved(context: Context, provider: String) = runCatching {
        val name = provider.trim().take(60).ifEmpty { return@runCatching }
        emit(
            context, FeedKind.KEY_ACQUIRED,
            tc(context, "已保存 $name 的密钥", "Saved a key for $name"),
            tc(context, "密钥已加密存进保险库，AI 使用前会按你设置的权限处理。", "Encrypted in the vault; AI use follows the permission you set."),
            "vault:$name",
        )
    }

    /** Plugin installed / started / stopped / failed. */
    fun pluginEvent(context: Context, fullName: String, event: String, detail: String? = null) = runCatching {
        val (zh, en) = when (event) {
            "installed" -> "已安装插件" to "Plugin installed"
            "started" -> "插件已启动" to "Plugin started"
            "stopped" -> "插件已停止" to "Plugin stopped"
            "failed" -> "插件启动失败" to "Plugin failed to start"
            "blocked" -> "插件被拦截" to "Plugin blocked"
            else -> return@runCatching
        }
        emit(context, FeedKind.PLUGIN_EVENT, tc(context, "$zh：$fullName", "$en: $fullName"), detail.orEmpty().take(500), "plugin:$fullName")
    }

    /** DSH host result → feed (only the states worth a line). */
    fun pluginHostState(context: Context, fullName: String, state: String, error: String?) = when (state) {
        "RUNNING", "SKILLS" -> pluginEvent(context, fullName, "started")
        "FAILED" -> pluginEvent(context, fullName, "failed", error)
        "BLOCKED" -> pluginEvent(context, fullName, "blocked", error)
        else -> Result.success(Unit)
    }

    /** A group chat turn ended with at least one reply. [replies] = (bot name, text). */
    fun groupTurn(context: Context, groupId: String, groupTitle: String, replies: List<Pair<String, String>>) = runCatching {
        if (replies.isEmpty()) return@runCatching
        val body = replies.joinToString("\n") { (n, t) -> "$n：${t.replace('\n', ' ').take(160)}" }
        emit(context, FeedKind.GROUP_SUMMARY, tc(context, "群聊「$groupTitle」有 ${replies.size} 条新回复", "${replies.size} new replies in “$groupTitle”"), body, "group:$groupId")
    }
}
