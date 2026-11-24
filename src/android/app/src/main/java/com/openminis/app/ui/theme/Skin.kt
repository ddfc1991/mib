package com.openminis.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [destiny/omega-skin] User-selectable UI skin. One entry = page background,
 * card surface, two text tones and one accent. The whole app (Hermes frame +
 * Mibot component pages + chat) derives its MaterialTheme scheme and chat
 * palette from the selected skin, so every page reads as one app.
 */
data class Skin(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val text: Color,
    val text2: Color,
    val accent: Color,
    val onAccent: Color = Color.White,
)

object Skins {
    /**
     * [全 App 视觉统一] 皮肤只保留强调色差异。
     * 背景/表面/文字全部来自 AppTokens，因此 11 套皮肤之间切换时
     * 页面结构、底色、圆角、字号完全一致，不会再出现"换肤像换 App"。
     * 浅色皮肤的强调色已压深，保证白底对比度 ≥ 4.5:1。
     */
    val HERMES = Skin(
        id = "hermes",
        name = "OpenCode 青绿",
        isDark = true,
        bg = AppTokens.dark.bg,
        surface = AppTokens.dark.surface,
        text = AppTokens.dark.ink,
        text2 = AppTokens.dark.ink2,
        accent = AppTokens.dark.accent,
        onAccent = AppTokens.dark.onAccent,
    )

    val ALL: List<Skin> = listOf(
        HERMES,
        Skin("black_white", "亮绿", true, AppTokens.dark.bg, AppTokens.dark.surface, AppTokens.dark.ink, AppTokens.dark.ink2, Color(0xFF47C86D), Color(0xFF04140A)),
        Skin("mist_forest", "森林绿", false, AppTokens.light.bg, AppTokens.light.surface, AppTokens.light.ink, AppTokens.light.ink2, Color(0xFF2E7D4F)),
        Skin("cold_red", "正红", false, AppTokens.light.bg, AppTokens.light.surface, AppTokens.light.ink, AppTokens.light.ink2, Color(0xFFD71019)),
        Skin("grey_white", "石墨", false, AppTokens.light.bg, AppTokens.light.surface, AppTokens.light.ink, AppTokens.light.ink2, Color(0xFF1A1A1A)),
        Skin("silver_mist", "银灰", false, AppTokens.light.bg, AppTokens.light.surface, AppTokens.light.ink, AppTokens.light.ink2, Color(0xFF4A5560)),
        Skin("ice_blue", "玫红", false, AppTokens.light.bg, AppTokens.light.surface, AppTokens.light.ink, AppTokens.light.ink2, Color(0xFFC25454)),
        Skin("navy_orange", "亮橙", true, AppTokens.dark.bg, AppTokens.dark.surface, AppTokens.dark.ink, AppTokens.dark.ink2, Color(0xFFF2743A), Color(0xFF1A0C02)),
        Skin("ink_green", "酸绿", true, AppTokens.dark.bg, AppTokens.dark.surface, AppTokens.dark.ink, AppTokens.dark.ink2, Color(0xFF58B04B), Color(0xFF061203)),
        Skin("charcoal_lime", "荧光黄", true, AppTokens.dark.bg, AppTokens.dark.surface, AppTokens.dark.ink, AppTokens.dark.ink2, Color(0xFFC8E54A), Color(0xFF101703)),
        Skin("warm_black", "墨黑", false, AppTokens.light.bg, AppTokens.light.surface, AppTokens.light.ink, AppTokens.light.ink2, Color(0xFF190300)),
    )

    fun byId(id: String?): Skin = ALL.firstOrNull { it.id == id } ?: HERMES
}

/** Persists the selected skin and publishes it to Compose. */
object SkinStore {
    private const val PREFS = "omega_skin"
    private const val KEY = "skin_id"

    private val _current = MutableStateFlow(Skins.HERMES)
    val current: StateFlow<Skin> = _current.asStateFlow()

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _current.value = Skins.byId(sp.getString(KEY, null))
    }

    fun select(context: Context, skin: Skin) {
        _current.value = skin
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, skin.id).apply()
    }
}
