package com.mibot.ui

/** Maps a device locale to a human classifier label (for AppAccess recommendations). */
internal fun classifierLanguage(locale: java.util.Locale): String = when {
    locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO") -> "Traditional Chinese"
    locale.language == "zh" -> "Simplified Chinese"
    else -> locale.getDisplayLanguage(java.util.Locale.ENGLISH)
}
