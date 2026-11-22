package com.mibot.ui.theme

import androidx.compose.runtime.Composable

/**
 * Mika 的 Compose 主题桩:不搬 Mika 的 ui/theme/ 整棵主题树(与 Mibot 主题冲突)。
 * cap 只在 [com.mibot.cap.phone.a11y.ScreenShareConsent] 里用 `MikaTheme { ... }`
 * 包一层 ComposeView 的内容;这里直接透传 content,主题由 Mibot 侧包在外层。
 */
@Composable
fun MikaTheme(
    dark: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    content()
}
