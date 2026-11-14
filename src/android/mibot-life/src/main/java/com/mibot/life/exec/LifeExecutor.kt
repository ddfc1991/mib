package com.mibot.life.exec

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 执行层 —— 让它真的能去生活（当前阶段：App 启动/深链，截图能力后续接虚拟屏）。
 *
 * 虚拟屏（vd_*）是 Mibot 本体的能力，mibot-life 作为独立 App 先用自己的 App 启动能力；
 * 等接入 Mibot 的 VdController（共享 core）后可实现全屏操控。
 */
class LifeExecutor(private val context: Context) {

    /** 打开某个 App（按包名） */
    fun launchApp(pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 打开 URL（浏览器/深链） */
    fun openUrl(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 设备上已安装的第三方 App（用于"生活地点"） */
    fun installedApps(): List<Pair<String, String>> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
            .mapNotNull { info ->
                val label = pm.getApplicationLabel(info).toString()
                info.packageName to label
            }
    }

    /** 按生活场景挑一个 App（读书/视频/论坛/购物/游戏） */
    fun pickByScene(scene: String): Pair<String, String>? {
        val keywords = when (scene) {
            "reading" -> listOf("书", "阅读", "读", "小说", "kindle", "read", "book")
            "video" -> listOf("视频", "b站", "抖音", "快手", "影视", "播放", "video", "bili", "tiktok")
            "forum" -> listOf("论坛", "贴吧", "知乎", "社区", "虎扑", "v2ex", "forum", "reddit")
            "shopping" -> listOf("淘宝", "京东", "拼多多", "购物", "商城", "shop", "mall")
            "game" -> listOf("游戏", "game", "play")
            else -> emptyList()
        }
        return installedApps().firstOrNull { (pkg, label) ->
            keywords.any { k ->
                label.contains(k, ignoreCase = true) || pkg.contains(k, ignoreCase = true)
            }
        }
    }
}
