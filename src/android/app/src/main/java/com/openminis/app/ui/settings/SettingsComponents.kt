package com.openminis.app.ui.settings

import com.openminis.app.ui.glass.frosted
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.i18n.uppercaseForDisplay

/**
 * Shared primitives for settings pages. Grouped-card layout (iOS inset-grouped style).
 *
 * Structure:
 *   SettingsScaffold(title, actions?) {
 *     SettingsSection(header?, footer?) { SettingsRow/SwitchRow/ValueRow(...) ... }
 *     SettingsSection(...) { ... }
 *   }
 */

// ─── Scaffold ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    // [T-android-settings-ui-md3] #11 onBack is nullable so an EDIT screen can
    // suppress the back arrow and use an explicit Cancel/Save action pair instead
    // (a back arrow + a "Cancel" action that both pop the screen is redundant and
    // semantically muddy). Non-edit screens keep passing a non-null onBack and get
    // the usual back arrow — unchanged.
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
    // [T-android-modeldetail-savecancel-ios-parity] Optional custom
    // navigation slot — e.g. a leading Cancel text action on modal-style
    // edit screens. When null, the slot falls back to the back arrow iff
    // onBack is set, so every existing caller renders unchanged.
    navigation: @Composable (() -> Unit)? = null,
    // [T-android-modeldetail-savecancel-ios-parity] Center the title
    // (CenterAlignedTopAppBar) for iOS-modal-style edit screens. Default
    // keeps the start-aligned TopAppBar.
    centerTitle: Boolean = false,
    floatingActionButton: @Composable (() -> Unit)? = null,
    scrollable: Boolean = true,
    // [T-android-storage-usage-cache] Optional bottom action bar (e.g. a
    // selection mode's actions). Null keeps every existing caller unchanged.
    bottomBar: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val mibotUi = com.openminis.app.ui.theme.LocalMibotActive.current
    Scaffold(
        topBar = {
            // [fork/mibot-v4] Same centred title + frosted round back button as Mibot pages.
            if (mibotUi && navigation == null) {
                com.openminis.app.mibot.design.MibotTopBar(title, onBack) { actions?.invoke() }
            } else {
            val titleSlot: @Composable () -> Unit = {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val navigationSlot: @Composable () -> Unit = {
                when {
                    navigation != null -> navigation()
                    onBack != null -> IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            }
            if (centerTitle) {
                CenterAlignedTopAppBar(
                    title = titleSlot,
                    navigationIcon = navigationSlot,
                    actions = { actions?.invoke() },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            } else {
                TopAppBar(
                    title = titleSlot,
                    navigationIcon = navigationSlot,
                    actions = { actions?.invoke() },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
            }
        },
        floatingActionButton = { floatingActionButton?.invoke() },
        bottomBar = { bottomBar?.invoke() },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // T183: imePadding() shrinks the scroll container by the IME's
        // height while the keyboard is up, giving Modifier.bringIntoView()
        // (used by `bringIntoViewOnFocus`) a meaningful "above the
        // keyboard" rect to scroll a focused TextField into. Without it,
        // adjustResize + edge-to-edge leaves the scrollable column at
        // full height behind the IME and bringIntoView is a no-op.
        val baseMod = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
        Column(
            modifier = if (scrollable) baseMod.verticalScroll(rememberScrollState()) else baseMod,
            content = content,
        )
    }
}

// ─── Switch ────────────────────────────────────────────────────────────────────

/**
 * [T-android-switch-row-height] A [Switch] that does not inflate the row it
 * sits in. Use this instead of Material's `Switch` inside any settings row.
 *
 * ## The problem it solves
 *
 * Compose applies `LocalMinimumInteractiveComponentSize` (48dp) to Switch, so
 * although the control DRAWS at 32dp it MEASURES at 48. Inside a row whose
 * height is `heightIn(min = 56.dp)` + 12dp vertical padding, that produced:
 *
 *     with a switch:  48 + 12 + 12  = 72dp   (heightIn never applies)
 *     without one:    30 + 12 + 12  = 54dp   -> clamped to 56dp
 *
 * Measured on a Pixel 6: switch rows rendered 190px and plain rows 147px, a
 * visible 16dp step between neighbouring rows of the same card. It read as
 * "the Encryption section has odd spacing" when in fact every switch row in
 * the app was taller than every non-switch row.
 *
 * iOS has no equivalent problem — SwiftUI's List sizes all its rows alike and
 * a Toggle does not stretch one — so this was an Android-only artefact rather
 * than an intentional difference.
 *
 * ## Why waiving the minimum is safe here
 *
 * The 48dp floor exists so a small control is still easy to hit. In these rows
 * the WHOLE ROW is clickable and at least 56dp tall, and tapping anywhere on it
 * toggles the same state, so the effective touch target grows rather than
 * shrinks. Material's own guidance allows waiving the minimum exactly when a
 * control is embedded in a larger interactive surface.
 *
 * Do NOT use this for a standalone switch that is the only touch target.
 */
@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.SwitchColors? = null,
) {
    val resolved = colors ?: if (com.openminis.app.ui.theme.LocalMibotActive.current)
        com.openminis.app.mibot.design.mibotSwitchColors() else SwitchDefaults.colors()
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = resolved,
        )
    }
}

// ─── Section ───────────────────────────────────────────────────────────────────

/**
 * A grouped section — optional small-caps header + rounded card + optional footer caption.
 * Children (SettingsRow / SettingsSwitchRow / …) appear inside the card; dividers auto-inset.
 */
@Composable
fun SettingsSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // [T-android-settings-ui-md3] Section vertical rhythm normalized to the 4dp
    // grid (fix_android_settings_ui.md #13): 24dp between sections instead of the
    // off-grid 20dp. Applied as top padding so the first section under a TopAppBar
    // keeps a consistent gap too.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (com.openminis.app.ui.theme.LocalMibotActive.current) 16.dp else 24.dp),
    ) {
        val mibotUi = com.openminis.app.ui.theme.LocalMibotActive.current
        if (header != null && mibotUi) {
            // [destiny/ui-unify] 与「设置」首屏同一套分组标题：10sp 加宽小字 +
            // 右侧发丝线，左边线 22dp 和卡片边缘对齐（原来是 13sp 灰字 + 28dp）。
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = header,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = com.openminis.app.mibot.v7.V7.Tx3,
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(1.dp).background(com.openminis.app.mibot.v7.V7.Ln))
            }
        } else if (header != null) {
            Text(
                text = header.uppercaseForDisplay(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                // [T-android-settings-ui-md3] #5 header→card gap = 8dp (was 6dp,
                // off-grid). Horizontal stays 32dp to align the header text with
                // the inset card's content.
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
            )
        }
        // [T-android-settings-section-symmetry] The card carries NO vertical
        // padding of its own: rows already supply 12dp top AND bottom, so any
        // tail here makes the gap below the content read larger than the gap
        // above it. An unconditional 8dp bottom pad used to live on this
        // Column to stop a trailing divider sitting flush against the rounded
        // edge — but every section either suppresses its last divider
        // (showDivider = false, or an `index < size - 1` guard) or follows it
        // with more content, so nothing actually needed the clearance. The
        // asymmetry it cost was invisible in a tall multi-row card and glaring
        // in a single-row one ("Status / Enabled", "Voice Services", the
        // thinking-rules note).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (mibotUi) com.openminis.app.mibot.design.MibotSpace.Screen else 16.dp)
                .then(
                    // [destiny/ui-one-look] 设置子页原来是一张 iOS 式「内嵌圆角卡片」
                    // （frosted 底 + 圆角），而四个 tab 是纯黑底上的平铺行 + 1dp 发丝线，
                    // 同一个 App 里两套语言。这里在 V7 下不再画卡片底：行直接铺在 V7.Bg
                    // 上，靠行尾发丝线分隔，和「设置」首屏 / 对话 / 动态 / 群聊完全同构。
                    if (mibotUi) Modifier
                    else Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                ),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                // [destiny/ui-one-look] V7 下页脚与行内说明同一字号/色阶（11.5sp / Tx3），
                // 不再是 Material 的 bodySmall + onSurfaceVariant。
                style = if (mibotUi) androidx.compose.ui.text.TextStyle(
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                ) else MaterialTheme.typography.bodySmall,
                color = if (mibotUi) com.openminis.app.mibot.v7.V7.Tx3 else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 4.dp),
                lineHeight = if (mibotUi) 18.sp else 16.sp,
            )
        }
    }
}

// ─── Row primitives ────────────────────────────────────────────────────────────

/**
 * Generic row: left icon (optional colored circle) + title/subtitle + trailing slot + optional chevron.
 * Pass `showDivider = false` on the last row of a section.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    showDivider: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    // [T-android-settings-ui-md3] #8 single-line List Item is 56dp; a caller with
    // two-line content (e.g. the model list: name + id) passes 72dp for the MD3
    // double-line height. Default keeps every other row at the single-line 56dp.
    minHeight: Dp = 56.dp,
    // Long-press on a row that is also tappable (e.g. a skill's context menu).
    // Null keeps the plain clickable, so no other row changes.
    onLongClick: (() -> Unit)? = null,
) {
    val mibotUi = com.openminis.app.ui.theme.LocalMibotActive.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // [destiny/ui-one-look] V7 行规格（与 V7SettingRow / V7FeedRow / 群聊行
                // 完全一致）：上下 13dp、左右 0（外层已给 22dp 页边距）、不被 56dp
                // 最小高度固定住 —— 高度由 14sp 标题 + 11.5sp 说明 + 26dp 内边距决定。
                .heightIn(min = if (mibotUi) 0.dp else minHeight)
                .then(
                    when {
                        onClick != null && onLongClick != null ->
                            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        onClick != null -> Modifier.clickable(onClick = onClick)
                        else -> Modifier
                    },
                )
                .padding(
                    horizontal = if (mibotUi) 0.dp else 14.dp,
                    vertical = if (mibotUi) 13.dp else 12.dp,
                ),
            // #10 keep the trailing control (Switch/value) vertically centered
            // against the title — already centered, kept explicit.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // [destiny/ui-one-look] V7 设置页的行不带图标块（设置首屏 28 个入口
            // 全部是纯文字行）。以前这里给部分子页画了 IconChip，同一排入口在
            // 首屏和子页长成两种样子。icon 参数保留，只是 V7 下不渲染。
            if (icon != null && !mibotUi) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(iconColor, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (mibotUi) 0.dp else 1.dp),
            ) {
                Text(
                    text = title,
                    style = if (mibotUi) androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ) else MaterialTheme.typography.bodyLarge,
                    color = if (mibotUi) com.openminis.app.mibot.v7.V7.Tx else titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = if (mibotUi) androidx.compose.ui.text.TextStyle(
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                        ) else MaterialTheme.typography.bodySmall,
                        color = if (mibotUi) com.openminis.app.mibot.v7.V7.Tx3
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }

            if (showChevron && mibotUi) {
                Spacer(Modifier.width(8.dp))
                // 设置首屏用的就是 17sp 的 ›，子页以前是 16dp 细线箭头，同一排
                // 入口两种箭头。统一成同一个字形。
                Text("\u203A", fontSize = 17.sp, color = com.openminis.app.mibot.v7.V7.Tx3)
            } else if (showChevron) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (showDivider) {
            // [destiny/ui-one-look] V7 下是满宽 1dp 发丝线（V7.Ln），不是内缩 0.5dp
            // 的 outlineVariant —— 和 tab 页、首屏的列表分隔完全一样。
            if (mibotUi) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(com.openminis.app.mibot.v7.V7.Ln),
                )
            } else {
                val insetStart = if (icon != null) 58.dp else 14.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = insetStart, end = 14.dp)
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

/** Title + Switch row. */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        showChevron = false,
        showDivider = showDivider,
        trailing = {
            SettingsSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

/** Title + right-aligned value text (tap opens picker/detail). */
@Composable
fun SettingsValueRow(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconColor = iconColor,
        onClick = onClick,
        showChevron = onClick != null,
        showDivider = showDivider,
        trailing = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/** Single-choice row (tap to select; shows check on selected). Used for radio-style lists. */
@Composable
fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val mibotUi = com.openminis.app.ui.theme.LocalMibotActive.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // [destiny/ui-one-look] 与 SettingsRow 同一套 V7 行规格：
                // 不定最小高度、左右 0（外层 22dp）、上下 13dp。
                .heightIn(min = if (mibotUi) 0.dp else 56.dp)
                .clickable(onClick = onSelect)
                .padding(
                    horizontal = if (mibotUi) 0.dp else 16.dp,
                    vertical = if (mibotUi) 13.dp else 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = if (mibotUi) androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ) else MaterialTheme.typography.bodyLarge,
                color = if (mibotUi) com.openminis.app.mibot.v7.V7.Tx else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected && mibotUi) {
                com.openminis.app.mibot.design.CheckMark(true)
            } else if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showDivider) {
            if (mibotUi) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(com.openminis.app.mibot.v7.V7.Ln),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 14.dp)
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

/**
 * Container for non-row content (sliders, segmented pickers, custom composables)
 * that still wants the grouped-card background.
 */
@Composable
fun SettingsCardBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // [destiny/ui-one-look] 非行内容（滑块、分段选择器）在 V7 下也不再内缩 16dp —
    // 外层已经给了 22dp 页边距，再缩一次会让滑块比上面的行短一截。
    val mibotUi = com.openminis.app.ui.theme.LocalMibotActive.current
    Column(
        modifier = modifier
            .padding(
                horizontal = if (mibotUi) 0.dp else 16.dp,
                vertical = if (mibotUi) 13.dp else 12.dp,
            )
            .fillMaxWidth(),
        content = content,
    )
}
