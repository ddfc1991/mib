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
    /** Default = Hermes HQ: OLED black + iOS blue. */
    val HERMES = Skin(
        id = "hermes",
        name = "Hermes 黑蓝",
        isDark = true,
        bg = Color(0xFF0A0A0A),
        surface = Color(0xFF1C1C1E),
        text = Color(0xFFFFFFFF),
        text2 = Color(0xFF8E8E93),
        accent = Color(0xFF0A84FF),
    )

    val ALL: List<Skin> = listOf(
        HERMES,
        // 1 黑+透白：黑 #080808、透白 #DADADB、亮绿 #47C86D
        Skin("black_white", "黑 + 透白", true, Color(0xFF080808), Color(0xFF171717), Color(0xFFDADADB), Color(0x99DADADB), Color(0xFF47C86D)),
        // 2 雾绿+森林绿：雾绿 #DDE7DF、森林绿 #1B412D、亮绿 #47C86D
        Skin("mist_forest", "雾绿 + 森林绿", false, Color(0xFFDDE7DF), Color(0xFF1B412D), Color(0xFF1B412D), Color(0xCC1B412D), Color(0xFF47C86D)),
        // 3 冷白+银灰+正红：冷白 #EBECEE、银灰 #BCBBBF、正红 #D71019
        Skin("cold_red", "冷白 + 银灰 + 正红", false, Color(0xFFEBECEE), Color(0xFFBCBBBF), Color(0xFF1A1A1A), Color(0x991A1A1A), Color(0xFFD71019)),
        // 4 浅灰+纯白：浅灰 #D0D0D0、纯白 #FFFFFF
        Skin("grey_white", "浅灰 + 纯白", false, Color(0xFFD0D0D0), Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0x991A1A1A), Color(0xFF1A1A1A)),
        // 5 银灰+雾白：银灰 #DBE1E4、雾白 #F2F7F8
        Skin("silver_mist", "银灰 + 雾白", false, Color(0xFFDBE1E4), Color(0xFFF2F7F8), Color(0xFF1A1A1A), Color(0x991A1A1A), Color(0xFF1A1A1A)),
        // 6 冰白+灰蓝+柠檬黄：冰白 #F0F1F6、灰蓝 #C2CADD、柠檬黄 #FDA2A2
        Skin("ice_blue", "冰白 + 灰蓝 + 柠檬黄", false, Color(0xFFF0F1F6), Color(0xFFC2CADD), Color(0xFF1A1A1A), Color(0x991A1A1A), Color(0xFFFDA2A2)),
        // 7 深蓝灰+冷灰+亮橙：深蓝灰 #263844、冷灰 #A0A1A3、亮橙 #F2743A
        Skin("navy_orange", "深蓝灰 + 冷灰 + 亮橙", true, Color(0xFF263844), Color(0xFF33495A), Color(0xFFF2F4F5), Color(0xB3F2F4F5), Color(0xFFF2743A)),
        // 8 墨绿+银灰+酸绿：墨绿 #062320、银灰 #B4B9B4、酸绿 #58B04B
        Skin("ink_green", "墨绿 + 银灰 + 酸绿", true, Color(0xFF062320), Color(0xFF0E332E), Color(0xFFB4B9B4), Color(0x99B4B9B4), Color(0xFF58B04B)),
        // 9 炭黑+石墨灰+酸黄绿：炭黑 #0F0F0F、石墨灰 #282828、酸黄绿 #E1FD09
        Skin("charcoal_lime", "炭黑 + 石墨灰 + 酸黄绿", true, Color(0xFF0F0F0F), Color(0xFF282828), Color(0xFFEDEDED), Color(0x99EDEDED), Color(0xFFE1FD09)),
        // 10 暖白+黑：暖白 #FFF8F2、黑 #190300
        Skin("warm_black", "暖白 + 黑", false, Color(0xFFFFF8F2), Color(0xFFFFFFFF), Color(0xFF190300), Color(0x99190300), Color(0xFF190300)),
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
