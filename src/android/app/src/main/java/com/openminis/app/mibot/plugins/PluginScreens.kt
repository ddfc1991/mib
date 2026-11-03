package com.openminis.app.mibot.plugins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openminis.app.mibot.design.ChipKind
import com.openminis.app.mibot.design.Glyph
import com.openminis.app.mibot.design.LineIcons
import com.openminis.app.mibot.design.MibotButton
import com.openminis.app.mibot.design.MibotCard
import com.openminis.app.mibot.design.MibotDivider
import com.openminis.app.mibot.design.MibotFilterChips
import com.openminis.app.mibot.design.MibotFootnote
import com.openminis.app.mibot.design.MibotIcon
import com.openminis.app.mibot.design.MibotRow
import com.openminis.app.mibot.design.MibotScaffold
import com.openminis.app.mibot.design.MibotSectionHeader
import com.openminis.app.mibot.design.MibotSpace
import com.openminis.app.mibot.design.MibotSwitch
import com.openminis.app.mibot.design.MibotTokens
import com.openminis.app.mibot.design.MibotTopBar
import com.openminis.app.mibot.design.MibotType
import com.openminis.app.mibot.design.StatusChip
import com.openminis.app.ui.glass.LocalGlassBottomInset
import com.openminis.app.ui.markdown.MarkdownText
import kotlinx.coroutines.launch
import com.openminis.app.ui.theme.LocalMibotDark

object PluginRoutes {
    const val MARKET = "mibot_plugins"
    const val DETAIL = "mibot_plugin/{name}"
    fun detail(fullName: String) = "mibot_plugin/" + Uri.encode(fullName)
}

private fun starText(n: Int): String = when {
    n >= 10_000 -> String.format("%.1fw", n / 10_000f).replace(".0w", "w")
    n >= 1_000 -> String.format("%.1fk", n / 1_000f).replace(".0k", "k")
    else -> n.toString()
}

/** [fork/mibot-v5] One-shot deep-link hints (CI / links): which tab / section to show. */
object PluginMarketNav {
    @Volatile var pendingTab: String? = null
    @Volatile var pendingSection: String? = null
    private val tabAliases = mapOf(
        "workflow" to "工作流", "dev" to "开发编程", "knowledge" to "知识管理", "agent" to "AI Agent",
        "efficiency" to "效率", "fun" to "娱乐", "life" to "生活", "other" to "其他",
    )
    fun tabFor(alias: String?): String? = alias?.let { tabAliases[it] ?: it.takeIf { t -> t in PluginCategories.tabs } }
}

/**
 * README cleanup for the in-app Markdown renderer: drop raw HTML tags,
 * badge images and image-links (shown as noise otherwise), keep the text.
 */
internal fun cleanReadme(md: String): String = md
    .replace(Regex("""(?s)<!--.*?-->"""), "")
    .replace(Regex("""\[!\[[^\]]*]\([^)]*\)]\([^)]*\)"""), "")
    .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
    .replace(Regex("""(?i)</?(p|div|img|br|picture|source|a|span|sup|sub|center|details|summary|table|tr|td|th|tbody|thead|h[1-6]|b|strong|em|i|video|kbd)\b[^>]*>"""), "")
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()

/** Shared in-memory list so the detail page can find a plugin without refetching. */
internal object MarketCache {
    val byName = HashMap<String, MarketPlugin>()
}

@Composable
private fun rememberZh(text: String): String? {
    val ctx = LocalContext.current
    val v by PluginTranslator.version.collectAsState()
    return remember(text, v) { PluginTranslator.zh(ctx, text) }
}

// ------------------------------------------------------------------ list
// [fork/mibot-v5b2] Industrial / tool-like list: dense aligned rows on hairlines, 40 dp icons,
// one-line titles, 2-line descriptions, tabular meta, a fixed-width action column, a thin
// download bar, compact section headers with counts and a sticky filter bar.

private val Tnum = TextStyle(fontFeatureSettings = "tnum")
private val IconSize = 40.dp
private val RowPadH = 16.dp
private val TextInset = RowPadH + IconSize + 12.dp
private val ActionWidth = 64.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PluginMarketScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(MarketSort.STARS) }
    var oneClickOnly by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(PluginMarketNav.pendingTab ?: PluginCategories.ALL) }
    var noticeDismissed by rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val items = remember { mutableStateListOf<MarketPlugin>() }
    var page by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val installed by remember { PluginStore.flow(ctx) }.collectAsState()
    val manifests by PluginManifests.facts.collectAsState()
    val downloads by PluginInstaller.downloads.collectAsState()
    val trError by PluginTranslator.lastError.collectAsState()
    val showOriginal = remember { mutableStateMapOf<String, Boolean>() }
    val busy = remember { mutableStateMapOf<String, String>() }
    val desktopIndex = remember { intArrayOf(0) }

    suspend fun load(next: Int) {
        loading = true; error = null
        runCatching { SkillHubApi.list(next, sort, submitted.ifBlank { null }) }
            .onSuccess { pg ->
                if (next == 1) items.clear()
                val seen = items.map { it.fullName }.toHashSet()
                pg.items.filter { seen.add(it.fullName) }.forEach { items += it; MarketCache.byName[it.fullName] = it }
                total = pg.total; page = next
                PluginTranslator.translateDescriptions(ctx, pg.items.map { it.description })
            }
            .onFailure { error = "加载失败：${it.message ?: "网络错误"}" }
        loading = false
    }
    LaunchedEffect(sort, submitted, reload) {
        load(1)
        PluginMarketNav.pendingTab = null
        if (PluginMarketNav.pendingSection == "desktop") {
            PluginMarketNav.pendingSection = null
            kotlinx.coroutines.delay(2500) // let cached manifests settle the sections first
            runCatching { listState.animateScrollToItem(desktopIndex[0]) }
        }
    }
    // Real manifests decide 「手机能用」 (cached per pushedAt; fetched once).
    LaunchedEffect(items.size) { if (items.isNotEmpty()) PluginManifests.ensure(ctx, items.toList()) }
    LaunchedEffect(Unit) { runCatching { DshMobileCatalog.ensure(ctx) } }
    val mobileCatalog by DshMobileCatalog.entries.collectAsState()

    val filtered = items.filter { p ->
        (!oneClickOnly || p.oneClick) && (tab == PluginCategories.ALL || PluginCategories.of(p) == tab)
    }
    val fit = filtered.associate { it.fullName to PluginCompat.verdict(it, installed[it.fullName], manifests[it.fullName]).kind }
    val phone = filtered.filter { fit[it.fullName] == PluginCompat.Fit.PHONE }
    val desktop = filtered.filter { fit[it.fullName]?.let(PluginCompat::notPhone) == true }
    val unverified = filtered.filter { fit[it.fullName] == PluginCompat.Fit.UNVERIFIED }
    // search, filter bar, status line, phone header, phone rows (or empty line) → desktop header.
    desktopIndex[0] = 4 + maxOf(phone.size, 1)

    fun install(p: MarketPlugin) {
        if (busy.containsKey(p.fullName)) return
        busy[p.fullName] = "准备中"
        scope.launch {
            val err = PluginInstaller.install(ctx, p) { st -> busy[p.fullName] = st }
            busy.remove(p.fullName)
            if (err != null) android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    fun convert(p: MarketPlugin) {
        if (busy.containsKey(p.fullName)) return
        busy[p.fullName] = "准备中"
        scope.launch {
            if (installed[p.fullName] == null) {
                val err = PluginInstaller.install(ctx, p) { st -> busy[p.fullName] = st }
                if (err != null) { busy.remove(p.fullName); android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show(); return@launch }
            }
            busy[p.fullName] = "转换中"
            val c = PluginConverter.convert(ctx, p.fullName) { _ -> }
            busy.remove(p.fullName)
            android.widget.Toast.makeText(ctx, "${p.name}：${c.state.zh}" + (c.reason?.let { "（$it）" } ?: ""), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val bottom = LocalGlassBottomInset.current +
        androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    @Composable
    fun Rows(list: List<MarketPlugin>, convertible: Boolean) {
        list.forEachIndexed { i, p ->
            MarketRow(
                p, installed[p.fullName], busy[p.fullName], downloads[p.fullName], showOriginal[p.fullName] == true,
                onToggleOriginal = { showOriginal[p.fullName] = showOriginal[p.fullName] != true },
                onOpen = { onOpen(p.fullName) }, onInstall = { install(p) },
                onConvert = if (convertible && fit[p.fullName] != PluginCompat.Fit.UNSUPPORTED && fit[p.fullName] != PluginCompat.Fit.PENDING) ({ convert(p) }) else null,
                fitTag = when (fit[p.fullName]) { PluginCompat.Fit.PENDING -> "待支持"; PluginCompat.Fit.UNSUPPORTED -> "不支持"; else -> null },
                mobile = p.fullName.lowercase() in mobileCatalog,
            )
            if (i < list.lastIndex) Hairline(start = TextInset)
        }
    }

    Column(Modifier.fillMaxSize()) {
        MibotTopBar(title = "插件市场", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
            item(key = "search") { SearchField(query, { query = it }, onSubmit = { submitted = query }) }
            stickyHeader(key = "filters") {
                FilterBar(sort, { sort = it }, oneClickOnly, { oneClickOnly = !oneClickOnly }, tab, { tab = it })
            }
            item(key = "status") {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = RowPadH, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("SkillHub · 已载入 ${items.size} / ${"%,d".format(total)}", modifier = Modifier.weight(1f), style = Tnum,
                            fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        if (loading) Text("加载中…", fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (trError != null && !noticeDismissed) NoticeRow("译文：$trError") { noticeDismissed = true }
                    error?.let { msg ->
                        NoticeRow(msg, actionLabel = "重试", onAction = { reload++ }, onDismiss = null)
                    }
                }
            }
            if (filtered.isNotEmpty()) {
                item(key = "h-phone") { SectionHeader("手机能用", phone.size, "在 Mibot 沙箱或原生适配中可用") }
                if (phone.isEmpty()) item(key = "e-phone") { EmptyLine("当前筛选下没有确认手机能用的插件") }
                else item(key = "r-phone") { Column { Rows(phone, convertible = false) } }
                item(key = "h-desktop") { SectionHeader("待支持 / 需电脑", desktop.size, "DSH 宿主尚缺服务、桌面专用或不支持") }
                if (desktop.isEmpty()) item(key = "e-desktop") { EmptyLine("无") }
                else item(key = "r-desktop") { Column { Rows(desktop, convertible = true) } }
                if (unverified.isNotEmpty()) {
                    item(key = "h-unv") { SectionHeader("未核实", unverified.size, "还没读到 package.json / requirements.txt") }
                    item(key = "r-unv") { Column { Rows(unverified, convertible = true) } }
                }
            } else if (items.isNotEmpty()) {
                item(key = "e-filter") { EmptyLine("当前筛选下没有插件") }
            }
            item(key = "more") {
                if (items.isNotEmpty() && items.size < total) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        MibotButton(if (loading) "加载中…" else "加载更多（${items.size} / ${"%,d".format(total)}）", tonal = true, enabled = !loading,
                            onClick = { scope.launch { load(page + 1) } })
                    }
                }
                MibotFootnote("数据来自 SkillHub DSH Plugin 广场，插件为 GitHub 开源项目（许可证见详情）。「手机能用」依据插件的 package.json / requirements.txt / cordis.patch.yml 与 Mibot DSH 宿主的实际加载结果；「移动目录」= 收录于 dsh-mobile-plugin-catalog（CC0）。安装只下载代码到本机插件目录。")
            }
        }
    }
}

@Composable
private fun Hairline(start: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth().background(MibotTokens.card()).padding(start = start).height(0.5.dp).background(MibotTokens.hairline()))
}

@Composable
private fun SectionHeader(title: String, count: Int, sub: String) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = RowPadH, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = MibotType.Section, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(6.dp))
            Text("$count", style = Tnum, fontSize = MibotType.Section, fontWeight = MibotType.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(sub, modifier = Modifier.weight(1f), fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(MibotTokens.hairline()))
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().background(MibotTokens.card()).padding(horizontal = RowPadH, vertical = 12.dp),
        fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NoticeRow(text: String, actionLabel: String? = null, onAction: () -> Unit = {}, onDismiss: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = RowPadH, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp)).background(MibotTokens.tile()).padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcons.Info, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, modifier = Modifier.weight(1f), fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (actionLabel != null) Text(actionLabel, fontSize = MibotType.Caption, fontWeight = MibotType.Semi, color = MibotTokens.accent(),
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 2.dp))
        if (onDismiss != null) Box(Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onDismiss).padding(4.dp)
            .semantics { contentDescription = "关闭提示" }) {
            Glyph(LineIcons.X, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RowPadH, vertical = 4.dp)
            .height(40.dp)
            .clip(shape)
            .background(MibotTokens.card())
            .border(0.5.dp, MibotTokens.hairline(), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcons.Search, size = MibotIcon.Small, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("搜索插件（名称、功能）", fontSize = MibotType.Body, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MibotType.Body),
                cursorBrush = SolidColor(MibotTokens.accent()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.clip(RoundedCornerShape(4.dp)).clickable { onChange(""); onSubmit() }.padding(2.dp)) {
                Glyph(LineIcons.X, size = MibotIcon.Small, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FilterBar(
    sort: MarketSort, onSort: (MarketSort) -> Unit,
    oneClick: Boolean, onOneClick: () -> Unit,
    tab: String, onTab: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(if (LocalMibotDark.current) Color(0xFF191D23).copy(alpha = 0.97f) else Color(0xFFFBFCFC).copy(alpha = 0.97f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = RowPadH, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("星标" to MarketSort.STARS, "最新" to MarketSort.NEWEST), sort, onSort)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOneClick).padding(horizontal = 6.dp, vertical = 4.dp)
                    .semantics { contentDescription = "只看可一键装" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shape = RoundedCornerShape(3.dp)
                Box(
                    Modifier.size(14.dp).clip(shape)
                        .background(if (oneClick) MibotTokens.accent() else Color.Transparent)
                        .border(1.dp, if (oneClick) MibotTokens.accent() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), shape),
                    contentAlignment = Alignment.Center,
                ) { if (oneClick) Glyph(LineIcons.Check, size = 11.dp, tint = MaterialTheme.colorScheme.onPrimary) }
                Spacer(Modifier.width(6.dp))
                Text("仅可一键装", fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = RowPadH, end = RowPadH, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PluginCategories.tabs.forEach { c -> CategoryChip(c, c == tab) { onTab(c) } }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(MibotTokens.hairline()))
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val outer = RoundedCornerShape(8.dp)
    Row(Modifier.height(30.dp).clip(outer).background(MibotTokens.tile()).border(0.5.dp, MibotTokens.hairline(), outer).padding(2.dp)) {
        options.forEach { (label, v) ->
            val on = v == selected
            Box(
                Modifier.fillMaxHeight().width(60.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (on) MibotTokens.card() else Color.Transparent)
                    .then(if (on) Modifier.border(0.5.dp, MibotTokens.hairline(), RoundedCornerShape(6.dp)) else Modifier)
                    .clickable { onSelect(v) }
                    .semantics { contentDescription = "排序：$label" },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = MibotType.Secondary, fontWeight = if (on) MibotType.Semi else MibotType.Regular,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.height(28.dp).clip(shape)
            .background(if (on) MibotTokens.selected() else MibotTokens.card())
            .border(0.5.dp, if (on) MibotTokens.accent().copy(alpha = 0.45f) else MibotTokens.hairline(), shape)
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = MibotType.Secondary, fontWeight = if (on) MibotType.Semi else MibotType.Regular,
            color = if (on) MibotTokens.accent() else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/** Fixed-width compact action: 试装 / 更新 / 已安装 / 转换. */
@Composable
private fun ActionButton(label: String, kind: Int, onClick: (() -> Unit)?) {
    // kind: 0 outline-accent, 1 filled-accent, 2 inert, 3 outline-neutral
    val shape = RoundedCornerShape(6.dp)
    val enabled = onClick != null && kind != 2
    Box(
        Modifier.width(ActionWidth).height(28.dp).clip(shape)
            .background(when (kind) { 1 -> MibotTokens.accent(); 2 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f); else -> Color.Transparent })
            .then(if (kind == 0 || kind == 3) Modifier.border(0.75.dp, if (kind == 0) MibotTokens.accent().copy(alpha = 0.6f) else MibotTokens.hairline(), shape) else Modifier)
            .then(if (enabled) Modifier.clickable { onClick?.invoke() } else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = MibotType.Secondary, fontWeight = MibotType.Semi, maxLines = 1,
            color = when (kind) {
                1 -> MaterialTheme.colorScheme.onPrimary
                0 -> MibotTokens.accent()
                2 -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            })
    }
}

@Composable
internal fun PluginAvatar(url: String?, name: String, size: Dp = IconSize) {
    val shape = RoundedCornerShape(if (size <= 44.dp) 8.dp else size * 0.2f)
    Box(
        Modifier.size(size).clip(shape).background(MibotTokens.tile()).border(0.5.dp, MibotTokens.hairline(), shape),
        contentAlignment = Alignment.Center,
    ) {
        // Monochrome glyph fallback; the avatar (if it loads) covers it.
        Glyph(LineIcons.Puzzle, size = size * 0.5f, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
        if (url != null) {
            coil.compose.AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarketRow(
    p: MarketPlugin,
    inst: InstalledPlugin?,
    busy: String?,
    dl: PluginInstaller.Dl?,
    showOriginal: Boolean,
    onToggleOriginal: () -> Unit,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onConvert: (() -> Unit)?,
    fitTag: String? = null,
    mobile: Boolean = false,
) {
    val zh = rememberZh(p.description)
    val desc = if (showOriginal || zh == null) p.description else zh
    Row(
        Modifier
            .fillMaxWidth()
            .background(MibotTokens.card())
            .combinedClickable(onClick = onOpen, onLongClick = onToggleOriginal)
            .padding(horizontal = RowPadH, vertical = 10.dp)
            .semantics { contentDescription = "插件 ${p.name}" },
        verticalAlignment = Alignment.Top,
    ) {
        PluginAvatar(p.avatarUrl, p.name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = MibotType.Body, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(desc.ifBlank { "（无描述）" }, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 34.dp))
                    val tags = buildList {
                        if (showOriginal) add("原文")
                        fitTag?.let { add(it) }
                        if (mobile) add("移动目录")
                        inst?.conversion?.let { c -> add(when (c.state) { ConvState.OK -> "已转换"; ConvState.PARTIAL -> "部分可用"; ConvState.FAIL -> "无法转换" }) }
                    }
                    Text(
                        "★ ${starText(p.stars)} · ${PluginCategories.of(p)} · ${p.license.ifBlank { "未声明" }}" + tags.joinToString("") { " · $it" },
                        style = Tnum, fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                when {
                    busy != null -> ActionButton(if (busy.startsWith("转换")) "转换中" else "处理中", 2, null)
                    onConvert != null && inst?.conversion == null && !(inst != null && p.pushedAt > inst.pushedAt + 60_000) -> ActionButton("转换", 3, onConvert)
                    inst == null -> ActionButton("试装", 0, onInstall)
                    p.pushedAt > inst.pushedAt + 60_000 -> ActionButton("更新", 1, onInstall)
                    else -> ActionButton("已安装", 2, null)
                }
            }
            if (busy != null) DownloadLine(busy, dl)
        }
    }
}

/** Thin progress bar + small 「下载中 32%」 (no host here — that lives in the detail page). */
@Composable
private fun DownloadLine(stage: String, dl: PluginInstaller.Dl?) {
    val downloading = stage.startsWith("下载中")
    val frac = if (downloading) dl?.fraction else null
    val label = when {
        downloading && frac != null -> "下载中 ${(frac * 100).toInt()}%"
        downloading && dl != null && dl.bytes > 0 -> "下载中 " + String.format("%.1f MB", dl.bytes / 1_048_576f)
        downloading -> "下载中"
        else -> stage.substringBefore('（').take(12)
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(MibotTokens.hairline())) {
            if (frac != null) Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(MibotTokens.accent()))
            else androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(), color = MibotTokens.accent(), trackColor = Color.Transparent,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = Tnum, fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

// ------------------------------------------------------------------ detail

@Composable
fun PluginDetailScreen(fullName: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugin by remember { mutableStateOf(MarketCache.byName[fullName]) }
    var loadErr by remember { mutableStateOf<String?>(null) }
    val installed by remember { PluginStore.flow(ctx) }.collectAsState()
    val inst = installed[fullName]
    var busy by remember { mutableStateOf<String?>(null) }
    var readme by remember { mutableStateOf<String?>(null) }
    var readmeZh by remember { mutableStateOf<String?>(null) }
    var readmeState by remember { mutableStateOf("加载中…") }
    var showOriginal by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var extraReasons by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(fullName) {
        if (plugin == null) {
            // Deep link / process restart: find it through the search API.
            // SkillHub search does not always match the exact repo name (e.g. "dsh-our-free-model"
            // returns nothing), so try the repo name, the owner, then the name as words.
            val repoName = fullName.substringAfter('/')
            val queries = listOf(repoName, fullName.substringBefore('/'), repoName.removePrefix("dsh-").replace('-', ' ')).distinct()
            var lastErr: Throwable? = null
            for (q in queries) {
                runCatching { SkillHubApi.list(1, MarketSort.STARS, q, 24) }
                    .onSuccess { pg -> plugin = pg.items.firstOrNull { it.fullName.equals(fullName, true) } }
                    .onFailure { lastErr = it }
                if (plugin != null) break
            }
            if (plugin == null) loadErr = lastErr?.let { "加载失败：${it.message}" } ?: "找不到插件 $fullName"
            plugin?.let { MarketCache.byName[it.fullName] = it }
        }
        val p = plugin ?: return@LaunchedEffect
        PluginTranslator.translateDescriptions(ctx, listOf(p.description))
        PluginManifests.ensure(ctx, listOf(p))
        // Refine "phone-usable" from the real manifests.
        val pkg = SkillHubApi.rawFile(p, "package.json")
        val req = SkillHubApi.rawFile(p, "requirements.txt")
        val r = mutableListOf<String>()
        if (pkg != null) {
            val low = pkg.lowercase()
            if ("\"electron\"" in low) r += "package.json 依赖 Electron"
            if (listOf("robotjs", "nut-js", "iohook", "active-win").any { it in low }) r += "package.json 依赖桌面自动化"
            if ("\"win32\"" in low || "\"darwin\"" in low) r += "package.json 限定 Windows/macOS"
        }
        if (req != null && listOf("pyautogui", "pynput", "pywin32", "pywinauto", "pyobjc").any { it in req.lowercase() }) r += "requirements.txt 含桌面专用库"
        extraReasons = r
        val md = SkillHubApi.readme(p)
        readme = md
        if (md.isBlank()) { readmeState = "该插件暂时没有公开说明，可前往 GitHub 查看。"; return@LaunchedEffect }
        // Prefer the repo's own Chinese README when it ships one.
        if (!PluginTranslator.isChinese(md)) {
            for (name in listOf("README.zh.md", "README_zh.md", "README.zh-CN.md", "README_CN.md", "README.zh_CN.md", "docs/README.zh.md")) {
                val zhDoc = SkillHubApi.rawFile(p, name)
                if (zhDoc != null && PluginTranslator.isChinese(zhDoc)) {
                    readmeZh = zhDoc
                    readmeState = "使用仓库自带的中文说明"
                    return@LaunchedEffect
                }
            }
        }
        readmeState = "翻译中…（使用你配置的模型，免费模型优先）"
        val zh = PluginTranslator.translateReadme(ctx, md)
        readmeZh = zh
        readmeState = if (zh == null) (PluginTranslator.lastError.value ?: "翻译不可用") + "，以下为原文" else ""
    }

    MibotScaffold(title = plugin?.name ?: fullName.substringAfter('/'), onBack = onBack) {
        val p = plugin
        if (p == null) {
            MibotFootnote(loadErr ?: "加载中…")
            return@MibotScaffold
        }
        val zhDesc = rememberZh(p.description)
        val manifests by PluginManifests.facts.collectAsState()
        val v = PluginCompat.verdict(p, inst, manifests[p.fullName])
        val phone = v.kind == PluginCompat.Fit.PHONE && extraReasons.isEmpty()
        val reasons = if (phone) emptyList() else (v.reasons + extraReasons).distinct()

        // Header
        MibotCard(padding = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PluginAvatar(p.avatarUrl, p.name, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = MibotType.Title, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                    Text("${p.owner} · ★ ${starText(p.stars)}", fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (showOriginal || zhDesc == null) p.description else zhDesc, fontSize = MibotType.Body, lineHeight = MibotType.BodyLine,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip("许可证 ${p.license}", ChipKind.NEUTRAL)
                StatusChip(PluginCategories.of(p), ChipKind.NEUTRAL)
                StatusChip(if (p.oneClick) "可一键装" else "仓库项目", ChipKind.NEUTRAL)
                when {
                    inst?.conversion?.state == ConvState.OK -> StatusChip("已转换 · 手机能用", ChipKind.OK)
                    inst?.conversion?.state == ConvState.PARTIAL -> StatusChip("已转换 · 部分可用", ChipKind.WARN)
                    phone -> StatusChip("手机能用", ChipKind.OK)
                    v.kind == PluginCompat.Fit.UNVERIFIED && extraReasons.isEmpty() -> StatusChip("未核实", ChipKind.NEUTRAL)
                    v.kind == PluginCompat.Fit.UNSUPPORTED -> StatusChip("不支持", ChipKind.ERR)
                    v.kind == PluginCompat.Fit.PENDING -> StatusChip("待支持", ChipKind.WARN)
                    else -> StatusChip("需电脑", ChipKind.WARN)
                }
                if (DshMobileCatalog.contains(p.fullName)) StatusChip("移动目录收录", ChipKind.NEUTRAL)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val action: (suspend () -> Unit) = {
                    val err = PluginInstaller.install(ctx, p) { s -> busy = s }
                    busy = null
                    if (err != null) android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
                }
                when {
                    busy != null -> MibotButton(busy ?: "", onClick = {}, enabled = false, modifier = Modifier.weight(1f))
                    inst == null -> MibotButton("试装", modifier = Modifier.weight(1f), onClick = { busy = "准备中"; scope.launch { action() } })
                    p.pushedAt > inst.pushedAt + 60_000 -> MibotButton("更新", modifier = Modifier.weight(1f), onClick = { busy = "准备中"; scope.launch { action() } })
                    else -> MibotButton("已安装", onClick = {}, enabled = false, modifier = Modifier.weight(1f))
                }
                MibotButton("打开仓库", tonal = true, modifier = Modifier.weight(1f), onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.repoUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
            }
            val dshPlugin = manifests[p.fullName]?.dshHost == true
            if ((!phone || inst?.conversion != null) && !dshPlugin && v.kind != PluginCompat.Fit.UNSUPPORTED) {
                Spacer(Modifier.height(10.dp))
                MibotButton(
                    if (busy != null) "处理中…" else if (inst?.conversion != null) "重新尝试转换（实验）" else "尝试转换为手机版（实验）",
                    tonal = true, enabled = busy == null, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = "准备中"
                        scope.launch {
                            if (PluginStore.get(ctx, p.fullName) == null) {
                                val err = PluginInstaller.install(ctx, p) { s -> busy = s }
                                if (err != null) { busy = null; android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show(); return@launch }
                            }
                            PluginConverter.convert(ctx, p.fullName) { line -> busy = line.trim().take(22) }
                            busy = null
                            showLog = true
                        }
                    },
                )
            }
        }

        if (reasons.isNotEmpty() && inst?.conversion == null) {
            MibotSectionHeader(when {
                v.kind == PluginCompat.Fit.UNSUPPORTED -> "不支持的原因"
                v.kind == PluginCompat.Fit.PENDING -> "待支持"
                v.kind == PluginCompat.Fit.UNVERIFIED && extraReasons.isEmpty() -> "为什么未核实"
                else -> "为什么不算手机能用"
            })
            MibotCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                reasons.forEach { Text("· $it", fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }

        inst?.conversion?.let { c ->
            MibotSectionHeader("转换结果（实验）")
            MibotCard(padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(c.state.zh, when (c.state) { ConvState.OK -> ChipKind.OK; ConvState.PARTIAL -> ChipKind.WARN; ConvState.FAIL -> ChipKind.ERR })
                    Spacer(Modifier.width(8.dp))
                    Text(java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(c.at)),
                        fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                c.reason?.let { Text("原因：$it", fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp)) }
                if (c.missing.isNotEmpty()) {
                    Text(if (c.state == ConvState.FAIL) "同时存在的限制：" else "缺失 / 降级的功能：", fontSize = MibotType.Secondary,
                        fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
                    c.missing.forEach { Text("· $it", fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(if (showLog) "收起日志" else "查看日志", fontSize = MibotType.Secondary, color = MibotTokens.accent(),
                    modifier = Modifier.padding(top = 8.dp).clickable { showLog = !showLog })
                if (showLog) Text(c.log.takeLast(4000), fontSize = MibotType.Caption, lineHeight = MibotType.SecondaryLine,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            MibotFootnote("转换为实验功能：兼容层把桌面窗口改为插件面板、把桌面自动化请求排入虚拟屏/无障碍桥队列（执行端尚未接入），并把系统路径重定向到沙箱。")
        }

        val adapterReport = remember(inst?.installedAt, inst?.status) {
            if (inst != null && PluginAdapters.hasAdapter(fullName)) PluginAdapters.audit(inst) else PluginAdapters.preview(fullName)
        }
        if (adapterReport != null) {
            MibotSectionHeader("Mibot 原生适配 · 源码审核")
            MibotCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Text(adapterReport.note, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurface)
                adapterReport.findings.forEach { f ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                        StatusChip(f.verdict, when (f.verdict) { "已启用" -> ChipKind.OK; "拒绝" -> ChipKind.ERR; "不运行" -> ChipKind.WARN; else -> ChipKind.NEUTRAL })
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.part, fontSize = MibotType.Secondary, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface)
                            Text(f.reason, fontSize = MibotType.Caption, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            MibotFootnote(PluginAdapters.ATTRIBUTION)
        }

        if (manifests[p.fullName]?.dshHost == true || (inst != null && remember(inst.installedAt) { DshHostManager.isDshPlugin(inst) })) {
            DshHostSection(p, inst, v)
        }

        if (inst != null) {
            val running = inst.status == PluginStatus.RUNNING
            MibotSectionHeader("插件设置")
            MibotCard {
                MibotRow(
                    title = "状态",
                    subtitle = inst.note ?: PluginRunner.IDLE_NOTE,
                    chevron = false, showDivider = true,
                    trailing = {
                        StatusChip(
                            (if (inst.status == PluginStatus.PREPARING) "◌ " else if (running) "● " else "○ ") + inst.status.zh,
                            when (inst.status) { PluginStatus.RUNNING -> ChipKind.OK; PluginStatus.PREPARING -> ChipKind.WARN; PluginStatus.STOPPED, PluginStatus.IDLE -> ChipKind.NEUTRAL },
                        )
                    },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        running && !PluginAdapters.hasAdapter(fullName) -> Unit // DSH host plugins: start/stop live in the 「DSH 宿主」 card
                    running -> MibotButton("停止", tonal = true, modifier = Modifier.weight(1f), onClick = { PluginRunner.stop(ctx, fullName) })
                        inst.status == PluginStatus.PREPARING -> MibotButton("启用中…", tonal = true, enabled = false, modifier = Modifier.weight(1f), onClick = {})
                        PluginAdapters.hasAdapter(fullName) -> MibotButton("启用原生适配", tonal = true, modifier = Modifier.weight(1f), onClick = { scope.launch { PluginRunner.start(ctx, fullName) } })
                    }
                    MibotButton("卸载", tonal = true, modifier = Modifier.weight(1f), onClick = {
                        scope.launch { DshHostManager.forget(ctx, fullName); PluginStore.remove(ctx, fullName) }
                    })
                }
                MibotDivider()
                Text("权限（默认全部关闭）", fontSize = MibotType.Section, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp))
                PluginPerms.all.forEachIndexed { i, (k, label) ->
                    MibotRow(title = label, chevron = false, showDivider = i < PluginPerms.all.lastIndex,
                        trailing = {
                            MibotSwitch(checked = inst.perms[k] == true, onCheckedChange = { on ->
                                PluginStore.update(ctx, fullName) { it.copy(perms = it.perms + (k to on)) }
                            })
                        })
                }
            }
            MibotFootnote("安装位置：应用插件目录" + (if (PluginStore.sandboxDir(ctx, inst.id) != null) "，沙箱内 /root/.mibot/plugins/${inst.id}" else "（沙箱未初始化，尚未同步）") +
                " · ${inst.sizeBytes / 1024} KB · ${inst.ref.take(10)}")
        }

        MibotSectionHeader("申请的权限（据说明推测）")
        MibotCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            val hay = ((readme ?: "") + " " + p.description).lowercase()
            val perms = buildList {
                if (listOf("http", "api", "search", "联网", "fetch", "model", "模型").any { it in hay }) add("网络：访问外部服务 / 模型接口")
                if (listOf("file", "文件", "fs.", "目录", "workspace").any { it in hay }) add("文件：读写插件或工作区文件")
                if (listOf("notify", "通知", "@ 你", "提醒").any { it in hay }) add("通知")
                if (listOf("overlay", "悬浮", "widget", "右下角").any { it in hay }) add("悬浮窗")
                if (listOf("automation", "自动操作", "robot", "click", "抖音", "刷", "screen").any { it in hay }) add("无障碍 / 虚拟屏操控")
                if (listOf("key", "token", "账号", "login", "登录", "余额", "balance").any { it in hay }) add("账号读取：API Key / 登录状态")
            }
            if (perms.isEmpty()) Text("未发现明显的权限需求", fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            perms.forEach { Text("· $it", fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        MibotSectionHeader("说明", actionLabel = if (readmeZh != null) (if (showOriginal) "看中文" else "看原文") else null, onAction = { showOriginal = !showOriginal })
        MibotCard(padding = PaddingValues(16.dp)) {
            if (readmeState.isNotEmpty()) Text(readmeState, fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            val md = if (showOriginal) readme else (readmeZh ?: readme)
            if (!md.isNullOrBlank()) {
                val clean = remember(md) { cleanReadme(md.take(14_000)) }
                MarkdownText(clean, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        MibotSectionHeader("仓库")
        MibotCard {
            MibotRow(title = p.repoUrl.removePrefix("https://"), subtitle = "GitHub · 许可证 ${p.license}", onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.repoUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
        }
    }
}

// ------------------------------------------------------------------ DSH host (stage 1)

/** [fork/mibot-v5b4] Real host status: 未启动 / 准备中 / 运行中 / 待支持 / 失败 + 日志 / 不支持; start/stop; log viewer. */
@Composable
private fun DshHostSection(p: MarketPlugin, inst: InstalledPlugin?, v: PluginCompat.Verdict) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lives by DshHostManager.live.collectAsState()
    val logsMap by DshHostManager.logs.collectAsState()
    val live = lives[p.fullName]
    val h = inst?.host
    var showLog by remember { mutableStateOf(false) }
    var cmdOut by remember { mutableStateOf<String?>(null) }
    val blocked = v.kind == PluginCompat.Fit.UNSUPPORTED
    val (label, kind) = when {
        blocked -> "不支持" to ChipKind.ERR
        live?.phase == DshHostManager.Phase.PREPARING -> "准备中" to ChipKind.WARN
        live?.phase == DshHostManager.Phase.RUNNING || (live == null && h?.state == "SKILLS") -> "运行中" to ChipKind.OK
        live?.phase == DshHostManager.Phase.PENDING || (live == null && h?.state == "PENDING") -> "待支持" to ChipKind.WARN
        live?.phase == DshHostManager.Phase.FAILED || (live == null && h?.state == "FAILED") -> "失败" to ChipKind.ERR
        else -> "未启动" to ChipKind.NEUTRAL
    }
    MibotSectionHeader("DSH 宿主（实验）")
    MibotCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(label, kind)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    blocked -> v.reasons.firstOrNull() ?: "在 Mibot 中不支持"
                    live?.phase == DshHostManager.Phase.PREPARING -> live?.progress ?: "准备中…"
                    label == "运行中" && h?.skills?.isNotEmpty() == true -> "已导入 ${h?.skills?.size} 个技能：${h?.skills?.joinToString()}"
                    label == "运行中" -> "插件已在 Node 宿主中加载（Cordis ACTIVE）"
                    label == "待支持" -> "待支持：" + (h?.missing?.ifEmpty { null }?.joinToString(", ") ?: "宿主服务")
                    label == "失败" -> h?.error?.lines()?.firstOrNull()?.take(160) ?: "加载失败"
                    inst == null -> "试装后可启动"
                    else -> "未启动 · 点「启动」在沙箱 Node 22 中加载"
                },
                fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (h != null && h.requested.isNotEmpty()) Text("申请的宿主服务：" + h.requested.joinToString(", "), fontSize = MibotType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        if (h?.node != null) Text("Node ${h?.node} · 已提供：logger, locale, config, commands", fontSize = MibotType.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        if (!blocked && inst != null) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val preparing = live?.phase == DshHostManager.Phase.PREPARING
                val active = label == "运行中"
                if (active) MibotButton("停止", tonal = true, modifier = Modifier.weight(1f), onClick = { DshHostManager.launchStop(ctx, p.fullName) })
                MibotButton(if (preparing) "启动中…" else if (active) "重新启动" else if (h != null) "重新加载" else "启动",
                    enabled = !preparing, modifier = Modifier.weight(1f), onClick = { DshHostManager.launchStart(ctx, p.fullName, restart = active) })
            }
        }
        live?.commands?.takeIf { it.isNotEmpty() && label == "运行中" }?.let { cmds ->
            Text("命令", fontSize = MibotType.Secondary, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp))
            cmds.forEach { c ->
                Text("/$c  ▸ 运行", fontSize = MibotType.Secondary, color = MibotTokens.accent(), modifier = Modifier.padding(top = 4.dp).clickable {
                    scope.launch { cmdOut = runCatching { DshHostManager.runCommand(p.fullName, c) }.getOrElse { "失败：${it.message}" } }
                })
            }
            cmdOut?.let { Text(it.take(1200), fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
        if (inst != null) {
            val lines = logsMap[p.fullName] ?: remember(inst.installedAt, h?.at) { DshHostManager.logLines(ctx, p.fullName) }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (showLog) "收起日志" else "查看日志（${lines.size}）", fontSize = MibotType.Secondary, color = MibotTokens.accent(),
                    modifier = Modifier.clickable { showLog = !showLog })
                if (showLog && lines.isNotEmpty()) {
                    Spacer(Modifier.width(16.dp))
                    Text("清空", fontSize = MibotType.Secondary, color = MibotTokens.accent(), modifier = Modifier.clickable { DshHostManager.clearLog(ctx, p.fullName) })
                }
            }
            if (showLog) Text(
                if (lines.isEmpty()) "（暂无日志）" else lines.takeLast(200).joinToString("\n"),
                fontSize = MibotType.Caption, lineHeight = MibotType.SecondaryLine, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    MibotFootnote("实验：Mibot 用沙箱里的 Node 22 + 真实 Cordis 内核加载 DSH 插件，每个插件一个进程。第一阶段提供 logger / locale / config / commands；" +
        "插件需要的其他宿主服务会列为「待支持」。技能插件会导入为 Mibot 技能，停止时停用、卸载时删除。安装依赖时不执行 npm 脚本。")
}
