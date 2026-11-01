package com.openminis.app.mibot.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * [fork/mibot-v2] Inline zh/en strings for Mibot-owned screens. Chinese is
 * first-class: any `zh*` locale gets the Chinese text, everything else gets
 * English. (Upstream screens keep their full strings.xml localisation.)
 */
@Composable
@ReadOnlyComposable
fun t(zh: String, en: String): String {
    val locales = LocalConfiguration.current.locales
    val lang = if (locales.isEmpty) "en" else locales[0].language
    return if (lang == "zh") zh else en
}

fun tc(context: Context, zh: String, en: String): String {
    val locales = context.resources.configuration.locales
    val lang = if (locales.isEmpty) "en" else locales[0].language
    return if (lang == "zh") zh else en
}
