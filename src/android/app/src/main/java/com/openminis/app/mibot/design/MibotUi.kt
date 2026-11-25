package com.openminis.app.mibot.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.theme.LocalMibotDark
import com.openminis.app.ui.theme.AppTokens
import com.openminis.app.ui.glass.FrostLevel
import com.openminis.app.ui.glass.frosted

/**
 * [fork/mibot-v3] Design tokens — the ONE place sizes come from. Every Mibot
 * screen (and the glass sidebar / tab bar) uses these instead of literals, so
 * the type scale, icon sizes, spacing and radii stay consistent. Compact scale:
 * body 14sp, secondary 12–13sp, titles 16–17sp, page headers 20sp; icons 20dp
 * (18dp in dense rows, 24dp only in the bottom navigation). One family: the
 * system sans (Roboto / Noto Sans CJK SC for Chinese), weights 400/500/600.
 */
object MibotType {
    val PageHeader: TextUnit = AppTokens.title
    val Title: TextUnit = AppTokens.section
    val TitleSmall: TextUnit = AppTokens.body
    val Body: TextUnit = AppTokens.body
    val BodyLine: TextUnit = 20.sp
    val Secondary: TextUnit = AppTokens.body2
    val SecondaryLine: TextUnit = 17.sp
    val Caption: TextUnit = AppTokens.micro
    val Footnote: TextUnit = AppTokens.caption
    val Section: TextUnit = AppTokens.body2
    val Button: TextUnit = AppTokens.body
    val Hero: TextUnit = 22.sp
    val Family: FontFamily = FontFamily.Default
    val Regular = FontWeight.Normal
    val Medium = FontWeight.Medium
    val Semi = FontWeight.SemiBold
}

object MibotIcon {
    val Standard: Dp = 20.dp
    val Dense: Dp = 18.dp
    val Small: Dp = 16.dp
    val Nav: Dp = 24.dp
    /** Tinted round-square behind a row icon. */
    val RowChip: Dp = 32.dp
    /** Filled circle in the apps grid. */
    val GridChip: Dp = 42.dp
    /** Round frosted back / action button in the top bar. */
    val TopButton: Dp = 36.dp
}

object MibotSpace {
    val XS: Dp = AppTokens.spaceXs
    val S: Dp = AppTokens.spaceSm
    val M: Dp = AppTokens.spaceMd
    val L: Dp = AppTokens.spaceLg
    val XL: Dp = AppTokens.spaceXl
    // 与 HQ / OpenCode 页边距统一为 16dp。
    val Screen: Dp = AppTokens.page
    val RowMin: Dp = 50.dp
    val TopBar: Dp = 52.dp
    val ButtonMin: Dp = 38.dp
}

object MibotRadius {
    val Card: Dp = AppTokens.radiusLg
    val Row: Dp = AppTokens.radiusMd
    val Chip: Dp = AppTokens.radiusSm
    val CardShape = RoundedCornerShape(AppTokens.radiusLg)
    val RowShape = RoundedCornerShape(AppTokens.radiusMd)
    val Pill = RoundedCornerShape(50)
}

/**
 * [fork/mibot-v2] The Mibot design system: one set of tokens (colours, radius,
 * hairlines) and the handful of building blocks every Mibot page is made of —
 * top bar, frosted card, list row with an icon chip, apps grid, status chip,
 * buttons. Colours come from MaterialTheme (MinisTheme(mibot = true)), so the
 * same components read correctly in the light cloud-sky and the night variant.
 */
object MibotTokens {
    val CardRadius: Dp = MibotRadius.Card
    val RowRadius: Dp = MibotRadius.Row
    val ScreenPadding: Dp = MibotSpace.Screen
    val CardShape = MibotRadius.CardShape

    // [fork/mibot-v5] Identity colours (avatars, tags): low-saturation, mid
    // lightness so they sit quietly on both porcelain and graphite. The UI
    // accent itself is MaterialTheme.colorScheme.primary (deep cinnabar).
    val Slate = AppTokens.dark.ink2
    val Sky = AppTokens.dark.ink2
    val Brick = AppTokens.dark.warn
    val Green = AppTokens.dark.success
    val Purple = AppTokens.dark.ink3
    val Red = AppTokens.dark.danger
    val Teal = AppTokens.dark.accent
    val Amber = AppTokens.dark.warn
    val Taupe = AppTokens.dark.ink2
    val Rose = AppTokens.dark.danger
    val Navy = AppTokens.dark.ink2
    val Steel = AppTokens.dark.ink2

    val Ok = AppTokens.dark.success
    val Warn = AppTokens.dark.warn
    val Err = AppTokens.dark.danger

    // [destiny/ui-unify] 以下全部改为从唯一真源 AppTokens.current 取色。
    // 过去这里读 LocalMibotDark + FrostTokens + MaterialTheme 三处真源，
    // 导致同一张卡片在 MIbot 页、设置页、HQ 页呈现三种底色。

    @Composable
    @ReadOnlyComposable
    fun hairline(): Color = AppTokens.current.border

    /** 卡片底色。深色/浅色/皮肤全部跟随 AppTokens，不再走毛玻璃独立色板。 */
    @Composable
    @ReadOnlyComposable
    fun card(): Color = AppTokens.current.surface

    /** Muted tint for every line icon in rows (one consistent colour, no multicolour). */
    @Composable
    @ReadOnlyComposable
    fun iconTint(): Color = AppTokens.current.ink2

    /** Neutral tile behind grid icons. */
    @Composable
    @ReadOnlyComposable
    fun tile(): Color = AppTokens.current.surface2

    /** Field fill inside cards. */
    @Composable
    @ReadOnlyComposable
    fun field(): Color = AppTokens.current.surface2.copy(alpha = 0.6f)

    @Composable
    @ReadOnlyComposable
    fun okColor(): Color = AppTokens.current.success

    @Composable
    @ReadOnlyComposable
    fun warnColor(): Color = AppTokens.current.warn

    @Composable
    @ReadOnlyComposable
    fun errColor(): Color = AppTokens.current.danger

    /** Secondary (tonal) button / chip fill. */
    @Composable
    @ReadOnlyComposable
    fun tonal(): Color = AppTokens.current.surface2

    /** Selected chip fill: a faint accent wash, never a solid block. */
    @Composable
    @ReadOnlyComposable
    fun selected(): Color = AppTokens.current.accent.copy(alpha = 0.16f)

    /** The one accent. Use sparingly. */
    @Composable
    @ReadOnlyComposable
    fun accent(): Color = AppTokens.current.accent
}

/** Page frame: transparent scaffold over the shared backdrop + Mibot top bar. */
@Composable
fun MibotScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    bottomPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = { MibotTopBar(title, onBack, actions) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = bottomPadding),
            content = content,
        )
    }
}

/**
 * [destiny/ui-unify] 统一顶栏，规格照 V7 的页头：
 * 靠左 22sp 粗标题 + 无底色箭头（44dp 热区）+ 右侧动作 + 底部 1dp 发丝线。
 *
 * 原来是「居中标题 + 圆形毛玻璃返回键」，和 V7 里那些页头（设置、任务队列、
 * 未接入页）放在一起就是两种设计语言，进出一个功能页标题会横跳。现在全 App
 * 只有这一个顶栏实现。
 */
@Composable
fun MibotTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    // 全 App 视觉统一：Mibot 页顶栏与 OpenCode / Omega 共用同一实现。
    com.openminis.app.ui.theme.AppTopBar(title = title, onBack = onBack, actions = actions)
}

/** Large left-aligned page title for top-level tabs. */
@Composable
fun MibotPageTitle(title: String, subtitle: String? = null) {
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = MibotSpace.XL, end = MibotSpace.XL, top = MibotSpace.L, bottom = MibotSpace.S),
    ) {
        Text(title, fontSize = MibotType.PageHeader, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Spacer(Modifier.height(MibotSpace.XS))
            Text(subtitle, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Frosted rounded card with a hairline border. */
@Composable
fun MibotCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MibotTokens.ScreenPadding)
            // [fork/opencode-ui] One card treatment across the whole app:
            // blurred translucent glass + lit rim, not a solid fill. The rim is
            // drawn by `frosted` now so the card cannot drift from the shared
            // sheen / inner-shade recipe.
            .frosted(MibotTokens.CardShape, FrostLevel.Card, border = true, elevation = 2.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Section label above a card, with an optional trailing action ("管理 ›"). */
@Composable
fun MibotSectionHeader(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = MibotSpace.Screen + MibotSpace.M, end = MibotSpace.Screen + MibotSpace.S, top = MibotSpace.L, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontSize = MibotType.Section,
            fontWeight = MibotType.Semi,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                "$actionLabel ›",
                modifier = Modifier.clickable(onClick = onAction),
                fontSize = MibotType.Section,
                color = MibotTokens.accent(),
            )
        }
    }
}

/**
 * [fork/mibot-v4] Icon chip. Every icon is drawn from the refined line set
 * (Material vectors are mapped by name) in ONE muted tint — no multicolour.
 * [filled] = a neutral frosted tile (apps grid); otherwise the bare glyph
 * (list rows). [color] is kept for source compatibility and ignored.
 */
@Composable
fun IconChip(icon: ImageVector, color: Color, size: Dp = MibotIcon.RowChip, filled: Boolean = false, iconSize: Dp = if (filled) MibotIcon.Standard else MibotIcon.Standard) {
    val tint = if (filled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f) else MibotTokens.iconTint()
    val line = MibotIcons.lineFor(icon)
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier
            .size(if (filled) size else if (size > 28.dp) 28.dp else size)
            .then(if (filled) Modifier.clip(shape).background(MibotTokens.tile()).border(0.5.dp, MibotTokens.hairline(), shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (line != null) Glyph(line, size = iconSize, tint = tint)
        else Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

private fun lighten(c: Color): Color = Color(
    red = c.red + (1f - c.red) * 0.35f,
    green = c.green + (1f - c.green) * 0.35f,
    blue = c.blue + (1f - c.blue) * 0.35f,
    alpha = 1f,
)

/** Standard list row inside a [MibotCard]. */
@Composable
fun MibotRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    color: Color = MibotTokens.Brick,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = onClick != null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MibotSpace.RowMin)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = MibotSpace.M + 2.dp, vertical = MibotSpace.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconChip(icon, color)
                Spacer(Modifier.width(MibotSpace.M))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = MibotType.Body, lineHeight = MibotType.BodyLine, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(MibotSpace.S))
                trailing()
            }
            if (chevron) {
                Glyph(LineIcons.ChevronRight, size = MibotIcon.Small, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 28.dp + MibotSpace.M * 2 + 2.dp else MibotSpace.M + 2.dp, end = MibotSpace.M + 2.dp),
                thickness = 0.5.dp,
                color = MibotTokens.hairline(),
            )
        }
    }
}

data class MibotGridItem(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

/** 4-column apps grid of coloured circular icons (reference "应用" panel). */
@Composable
fun MibotGrid(items: List<MibotGridItem>, columns: Int = 4) {
    MibotCard(padding = PaddingValues(vertical = MibotSpace.S, horizontal = MibotSpace.XS)) {
        items.chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(MibotRadius.RowShape)
                            .clickable(onClick = item.onClick)
                            .padding(vertical = MibotSpace.S)
                            .semantics { contentDescription = item.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconChip(item.icon, item.color, size = MibotIcon.GridChip, filled = true)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.label,
                            fontSize = MibotType.Secondary,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

enum class ChipKind { OK, WARN, ERR, NEUTRAL, ACCENT }

/** Small rounded status chip (可用 / 冷却中 / 失效 / 等你 / 完成 / 出错). Low-saturation fill. */
@Composable
fun StatusChip(text: String, kind: ChipKind) {
    val c = when (kind) {
        ChipKind.OK -> MibotTokens.okColor()
        ChipKind.WARN -> MibotTokens.warnColor()
        ChipKind.ERR -> MibotTokens.errColor()
        ChipKind.ACCENT -> MibotTokens.accent()
        ChipKind.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dark = LocalMibotDark.current
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = if (dark) 0.16f else 0.11f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        fontSize = MibotType.Caption,
        fontWeight = FontWeight.SemiBold,
        color = c,
        maxLines = 1,
    )
}

/**
 * [fork/mibot-v5] Capsule button. Filled accent = the ONE main action on a
 * screen; [tonal] = secondary, a neutral frosted-glass capsule with a hairline
 * and ink text (high contrast in both themes, no tinted text on tinted fill).
 */
@Composable
fun MibotButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tonal: Boolean = false, compact: Boolean = false) {
    // [destiny/ui-unify] 颜色取 AppTokens，形状取 HQ 统一圆角：
    // 不再有 MIbot 专属的彩色实心药丸，主/次按钮与全 App 同一语言。
    val p = AppTokens.current
    val shape = com.mibot.ui.hq.HqShape.Button
    val bg = when {
        !enabled -> p.surface2
        tonal -> p.surface2
        else -> p.accent
    }
    val fg = when {
        !enabled -> p.ink3
        tonal -> p.ink
        else -> p.onAccent
    }
    Box(
        modifier
            .heightIn(min = if (compact) MibotSpace.S else MibotSpace.ButtonMin)
            .clip(shape)
            .background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (compact) MibotSpace.M else MibotSpace.L, vertical = if (compact) MibotSpace.XS else MibotSpace.S),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = if (compact) MibotType.Caption else MibotType.Button, fontWeight = MibotType.Semi, maxLines = 1)
    }
}

/** Plain caption text under a card. */
@Composable
fun MibotFootnote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = MibotSpace.Screen + MibotSpace.M, end = MibotSpace.Screen + MibotSpace.M, top = MibotSpace.S),
        fontSize = MibotType.Footnote,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 16.sp,
    )
}

@Composable
fun MibotSpacer(h: Dp = 12.dp) = Spacer(Modifier.height(h))

/** Horizontal filter chips row (categories). */
@Composable
fun MibotFilterChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScrollCompat()
            .padding(horizontal = MibotSpace.Screen, vertical = MibotSpace.XS),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { opt ->
            val sel = opt == selected
            Text(
                opt,
                modifier = Modifier
                    .frosted(RoundedCornerShape(50), tint = if (sel) MibotTokens.selected() else Color.Transparent)
                    .then(if (sel) Modifier.border(1.dp, MibotTokens.accent().copy(alpha = 0.35f), RoundedCornerShape(50)) else Modifier)
                    .clickable { onSelect(opt) }
                    .padding(horizontal = MibotSpace.M, vertical = 5.dp),
                color = if (sel) MibotTokens.accent() else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (sel) MibotType.Semi else MibotType.Regular,
                fontSize = MibotType.Section,
            )
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))
