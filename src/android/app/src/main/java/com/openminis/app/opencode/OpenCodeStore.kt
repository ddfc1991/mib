package com.openminis.app.opencode

import android.content.Context

/**
 * OpenCode 连接配置：服务器地址 + token + 上次使用的工作目录。
 * 只存非敏感的连接信息；token 是配对换来的长期凭证，存本地即可。
 */
object OpenCodeStore {
    private const val PREFS = "opencode_connection"
    private const val K_BASE_URL = "base_url"
    private const val K_TOKEN = "token"
    private const val K_DIRECTORY = "directory"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(ctx: Context, baseUrl: String, token: String) {
        prefs(ctx).edit()
            .putString(K_BASE_URL, baseUrl.trimEnd('/'))
            .putString(K_TOKEN, token)
            .apply()
    }

    fun baseUrl(ctx: Context): String = prefs(ctx).getString(K_BASE_URL, "").orEmpty()

    fun token(ctx: Context): String = prefs(ctx).getString(K_TOKEN, "").orEmpty()

    fun isConnected(ctx: Context): Boolean = baseUrl(ctx).isNotBlank() && token(ctx).isNotBlank()

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    var directory: String?
        get() = null
        set(_) {}
}
