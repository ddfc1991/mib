package com.openminis.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// 聊天语义色全量收敛到 AppTokens —— 明暗两套，皮肤只覆盖强调色。
// 原先 DarkChatPalette / MibotDarkChatPalette / HermesDarkChatPalette
// 三套用户气泡（#2F3A5C / #2A2D34 / #3478F6）是「跳页像换 App」的主因，
// 现在三套 alias 到同一份基准。
@Immutable
data class ChatPalette(
    val isDark: Boolean,
    val background: Color,
    val secondaryBg: Color,
    val inputBg: Color,
    val inputIconBg: Color,
    val inputIconBorder: Color,
    val inputBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val disabledText: Color,
    val userBubble: Color,
    val toolBg: Color,
    val toolBorder: Color,
    val toolCapsuleBg: Color,
    val separator: Color,
    val sendButton: Color,
    val sendButtonDisabled: Color,
    val codeBlockBg: Color,
    val codeBlockText: Color,
    val inlineCodeBg: Color,
    val inlineCodeText: Color,
    val link: Color,
    val blockquoteBar: Color,
    val thinking: Color,
    val warningBg: Color,
    val warningText: Color,
    val tableBorder: Color,
    val inputShadow: Color,
    val toastBg: Color,
    val thumbnailBorder: Color,
    val sheetHeaderBg: Color,
    val sheetHeaderBorder: Color,
    val fabAccent: Color,
)

private val lp = AppTokens.light
private val dp = AppTokens.dark

val LightChatPalette = ChatPalette(
    isDark = false,
    background = lp.bg,
    secondaryBg = lp.surface2,
    inputBg = lp.surface,
    inputIconBg = lp.surface2,
    inputIconBorder = Color.Transparent,
    inputBorder = lp.border.copy(alpha = 0.5f),
    primaryText = lp.ink,
    secondaryText = lp.ink2,
    tertiaryText = lp.ink3,
    disabledText = lp.ink3.copy(alpha = 0.45f),
    userBubble = lp.bubbleUser,
    toolBg = lp.surface2,
    toolBorder = lp.border.copy(alpha = 0.4f),
    toolCapsuleBg = lp.surface2,
    separator = lp.border,
    sendButton = lp.accent,
    sendButtonDisabled = lp.ink3.copy(alpha = 0.3f),
    codeBlockBg = lp.surface2,
    codeBlockText = lp.ink,
    inlineCodeBg = lp.surface2,
    inlineCodeText = lp.accent,
    link = lp.accent,
    blockquoteBar = lp.accent.copy(alpha = 0.5f),
    thinking = lp.accent,
    warningBg = lp.warn.copy(alpha = 0.08f),
    warningText = lp.ink,
    tableBorder = lp.border.copy(alpha = 0.4f),
    inputShadow = Color.Transparent,
    toastBg = lp.accent.copy(alpha = 0.18f),
    thumbnailBorder = lp.border,
    sheetHeaderBg = lp.surface,
    sheetHeaderBorder = lp.border.copy(alpha = 0.4f),
    fabAccent = lp.accent,
)

val DarkChatPalette = ChatPalette(
    isDark = true,
    background = dp.bg,
    secondaryBg = dp.surface2,
    inputBg = dp.surface2,
    inputIconBg = dp.surface2,
    inputIconBorder = dp.border,
    inputBorder = dp.border.copy(alpha = 0.7f),
    primaryText = dp.ink,
    secondaryText = dp.ink2,
    tertiaryText = dp.ink3,
    disabledText = dp.ink3.copy(alpha = 0.45f),
    userBubble = dp.bubbleUser,
    toolBg = dp.surface2,
    toolBorder = dp.border.copy(alpha = 0.7f),
    toolCapsuleBg = dp.surface2,
    separator = dp.border,
    sendButton = dp.accent,
    sendButtonDisabled = dp.ink3.copy(alpha = 0.3f),
    codeBlockBg = dp.surface2,
    codeBlockText = dp.ink,
    inlineCodeBg = dp.surface2,
    inlineCodeText = dp.accent,
    link = dp.accent,
    blockquoteBar = dp.accent.copy(alpha = 0.5f),
    thinking = dp.accent,
    warningBg = dp.warn.copy(alpha = 0.12f),
    warningText = dp.ink,
    tableBorder = dp.border.copy(alpha = 0.7f),
    inputShadow = Color(0x80000000),
    toastBg = dp.accent.copy(alpha = 0.22f),
    thumbnailBorder = dp.border,
    sheetHeaderBg = dp.surface,
    sheetHeaderBorder = dp.border.copy(alpha = 0.6f),
    fabAccent = dp.accent,
)

// 统一别名：原有三套 chat palette 不再各自带色，全部继承同一基准。
val MibotLightChatPalette: ChatPalette get() = LightChatPalette
val MibotDarkChatPalette: ChatPalette get() = DarkChatPalette
val HermesDarkChatPalette: ChatPalette get() = DarkChatPalette

val LocalChatPalette = compositionLocalOf { LightChatPalette }

val ChatColors: ChatPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalChatPalette.current
