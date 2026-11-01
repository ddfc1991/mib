package com.openminis.app.mibot.design

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.theme.LocalMibotDark
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

/**
 * [fork/mibot-v4] Controls whose ON/OFF state must read at a glance in both
 * glass variants: radio, check, switch, permission state rows, glass text
 * field, brand logo tile.
 */

/** Radio: selected = brick-red filled disc with a white dot; unselected = empty outline ring. */
@Composable
fun RadioMark(selected: Boolean, size: Dp = 20.dp) {
    val accent = MibotTokens.accent()
    val ring = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(accent) else Modifier.border(1.6.dp, ring, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(size * 0.38f).clip(CircleShape).background(Color.White))
    }
}

/** Check: selected = filled accent disc with a white tick; unselected = nothing (or ring when [ringWhenOff]). */
@Composable
fun CheckMark(on: Boolean, size: Dp = 20.dp, ringWhenOff: Boolean = false, color: Color = MibotTokens.accent()) {
    if (!on && !ringWhenOff) return
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (on) Modifier.background(color)
                else Modifier.border(1.6.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Glyph(LineIcons.Check, size = size * 0.7f, tint = Color.White, duotone = false)
    }
}

/** A selectable option row: tinted background + filled radio when chosen. */
@Composable
fun MibotRadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, showDivider: Boolean = false) {
    val accent = MibotTokens.accent()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MibotSpace.RowMin)
                .background(if (selected) accent.copy(alpha = if (LocalMibotDark.current) 0.16f else 0.09f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = MibotSpace.M + 2.dp, vertical = MibotSpace.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioMark(selected)
            Spacer(Modifier.width(MibotSpace.M))
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontSize = MibotType.Body, lineHeight = MibotType.BodyLine,
                    fontWeight = if (selected) MibotType.Semi else MibotType.Medium,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showDivider) MibotDivider(inset = MibotSpace.M * 2 + 22.dp)
    }
}

@Composable
fun MibotDivider(inset: Dp = MibotSpace.M + 2.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset, end = MibotSpace.M + 2.dp)
            .heightIn(min = 0.5.dp, max = 0.5.dp)
            .background(MibotTokens.hairline()),
    )
}

/** High-contrast switch: accent track + white thumb when on; outlined track when off. */
@Composable
fun MibotSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val accent = MibotTokens.accent()
    val dark = LocalMibotDark.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = mibotSwitchColors(accent, dark),
        )
    }
}

@Composable
fun mibotSwitchColors(accent: Color = MibotTokens.accent(), dark: Boolean = LocalMibotDark.current) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = accent,
    checkedBorderColor = accent,
    checkedIconColor = accent,
    uncheckedThumbColor = if (dark) Color(0xFFB7BECC) else Color(0xFF7A8294),
    uncheckedTrackColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f),
    uncheckedBorderColor = if (dark) Color(0xFF9AA3B5) else Color(0xFF8A92A3),
)

/** Permission / capability state shown on a row. */
enum class PermState { GRANTED, MISSING, PARTIAL, UNAVAILABLE }

/**
 * One permission row with an unmistakable state: green check + "已开启",
 * amber "未开启" + a 去开启 button, grey "不可用" (reason in [subtitle]).
 */
@Composable
fun PermissionRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    state: PermState,
    stateText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    showDivider: Boolean = false,
) {
    val ok = MibotTokens.okColor()
    val warn = MibotTokens.warnColor()
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MibotSpace.RowMin)
                .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
                .padding(horizontal = MibotSpace.M + 2.dp, vertical = MibotSpace.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconChip(icon, Color.Unspecified)
                Spacer(Modifier.width(MibotSpace.M))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = MibotType.Body, lineHeight = MibotType.BodyLine, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurface)
                if (!subtitle.isNullOrEmpty()) Text(subtitle, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = grey)
            }
            Spacer(Modifier.width(MibotSpace.S))
            when (state) {
                PermState.GRANTED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckMark(true, size = 18.dp, color = ok)
                    Spacer(Modifier.width(5.dp))
                    Text(stateText ?: t("已开启", "On"), fontSize = MibotType.Secondary, fontWeight = MibotType.Semi, color = ok)
                }
                PermState.PARTIAL, PermState.MISSING -> Column(horizontalAlignment = Alignment.End) {
                    StatusChip(stateText ?: t("未开启", "Off"), ChipKind.WARN)
                    if (actionLabel != null && onAction != null) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            actionLabel,
                            modifier = Modifier
                                .clip(MibotRadius.Pill)
                                .background(MibotTokens.accent())
                                .clickable(onClick = onAction)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            color = Color.White, fontSize = MibotType.Caption, fontWeight = MibotType.Semi,
                        )
                    }
                }
                PermState.UNAVAILABLE -> StatusChip(stateText ?: t("不可用", "Unavailable"), ChipKind.NEUTRAL)
            }
        }
        if (showDivider) MibotDivider(inset = if (icon != null) MibotSpace.M * 2 + 30.dp else MibotSpace.M + 2.dp)
    }
}

/** "已开启 4/6" summary line above a permission card. */
@Composable
fun PermissionSummary(granted: Int, total: Int) {
    val all = granted >= total
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MibotSpace.Screen + MibotSpace.M, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(all, size = 16.dp, ringWhenOff = true, color = MibotTokens.okColor())
        Spacer(Modifier.width(6.dp))
        Text(
            t("已开启 $granted/$total", "$granted/$total on") + if (all) "" else t(" · 点「去开启」逐项补齐", " · tap Turn on to fix"),
            fontSize = MibotType.Secondary,
            fontWeight = MibotType.Semi,
            color = if (all) MibotTokens.okColor() else MibotTokens.warnColor(),
        )
    }
}

/** Glass text field (label above, frosted fill, accent border on focus). */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    secret: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    autofill: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var reveal by remember { mutableStateOf(false) }
    val shape = MibotRadius.RowShape
    Column(modifier.fillMaxWidth()) {
        Text(label, fontSize = MibotType.Caption, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MibotTokens.field())
                .border(if (focused) 1.4.dp else 1.dp, if (focused) MibotTokens.accent() else MibotTokens.hairline(), shape)
                .padding(horizontal = MibotSpace.M, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    minLines = minLines,
                    interactionSource = interaction,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MibotType.Body, lineHeight = MibotType.BodyLine),
                    cursorBrush = SolidColor(MibotTokens.accent()),
                    visualTransformation = if (secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
                    modifier = Modifier.fillMaxWidth().then(autofillModifier(autofill)),
                )
            }
            if (secret) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(CircleShape).clickable { reveal = !reveal }.padding(2.dp)) {
                    Glyph(if (reveal) LineIcons.EyeOff else LineIcons.Eye, size = MibotIcon.Dense, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Lets the user's Autofill / password manager offer credentials for the field. */
private fun autofillModifier(kind: String?): Modifier = when (kind) {
    "username" -> Modifier.semantics { contentType = ContentType.Username }
    "password" -> Modifier.semantics { contentType = ContentType.Password }
    else -> Modifier
}

// ------------------------------------------------------------- brand logos

private val logoCache = HashMap<String, Bitmap?>()

private fun loadLogo(ctx: Context, path: String): Bitmap? = synchronized(logoCache) {
    logoCache.getOrPut(path) { runCatching { ctx.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull() }
}

/** Brand logo in a white rounded tile (falls back to a coloured initial). */
@Composable
fun BrandLogo(logoAsset: String?, name: String, color: String? = null, size: Dp = 36.dp) {
    val ctx = LocalContext.current
    val bmp = remember(logoAsset) { logoAsset?.let { loadLogo(ctx.applicationContext, it) } }
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Color.White)
            .border(0.5.dp, Color.Black.copy(alpha = 0.08f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = name, modifier = Modifier.size(size * 0.62f))
        } else {
            val c = runCatching { Color(android.graphics.Color.parseColor(color ?: "#7A869A")) }.getOrDefault(Color(0xFF7A869A))
            Box(Modifier.size(size).background(c), contentAlignment = Alignment.Center) {
                Text(name.trim().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
            }
        }
    }
}

/** Round "+" button (connect). */
@Composable
fun PlusButton(onClick: () -> Unit, contentDescription: String) {
    val accent = MibotTokens.accent()
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = if (LocalMibotDark.current) 0.24f else 0.14f))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Glyph(LineIcons.Plus, size = 18.dp, tint = accent)
    }
}

@Composable
fun MibotPadding(): PaddingValues = PaddingValues(horizontal = MibotSpace.M + 2.dp, vertical = MibotSpace.S)

@Composable
fun Hstack(content: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(MibotSpace.S), verticalAlignment = Alignment.CenterVertically) { content() }
