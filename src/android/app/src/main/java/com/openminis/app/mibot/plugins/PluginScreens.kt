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
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val items = remember { mutableStateListOf<MarketPlugin>() }
    var page by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val installed by remember { PluginStore.flow(ctx) }.collectAsState()
    val trError by PluginTranslator.lastError.collectAsState()
    val showOriginal = remember { mutableStateMapOf<String, Boolean>() }
    val busy = remember { mutableStateMapOf<String, String>() }

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
            kotlinx.coroutines.delay(400)
            // Header item + phone banner + phone card (or empty note) → desktop banner is index 3.
            runCatching { listState.animateScrollToItem(3) }
        }
    }

    val filtered = items.filter { p ->
        (!oneClickOnly || p.oneClick) && (tab == PluginCategories.ALL || PluginCategories.of(p) == tab)
    }
    val phone = filtered.filter { PluginCompat.phoneUsable(it, installed[it.fullName]) }
    val desktop = filtered.filter { !PluginCompat.phoneUsable(it, installed[it.fullName]) }

    fun install(p: MarketPlugin) {
        if (busy.containsKey(p.fullName)) return
        busy[p.fullName] = "准备中"
        scope.launch {
            val err = PluginInstaller.install(ctx, p) { s -> busy[p.fullName] = s }
            busy.remove(p.fullName)
            if (err != null) android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    fun convert(p: MarketPlugin) {
        if (busy.containsKey(p.fullName)) return
        busy[p.fullName] = "准备中"
        scope.launch {
            if (installed[p.fullName] == null) {
                val err = PluginInstaller.install(ctx, p) { s -> busy[p.fullName] = s }
                if (err != null) { busy.remove(p.fullName); android.widget.Toast.makeText(ctx, err, android.widget.Toast.LENGTH_LONG).show(); return@launch }
            }
            busy[p.fullName] = "转换中（实验）"
            val c = PluginConverter.convert(ctx, p.fullName) { line -> busy[p.fullName] = line.trim().take(18) }
            busy.remove(p.fullName)
            android.widget.Toast.makeText(ctx, "${p.name}：${c.state.zh}" + (c.reason?.let { "（$it）" } ?: ""), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val bottom = LocalGlassBottomInset.current +
        androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        MibotTopBar(title = "插件市场", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottom + 32.dp)) {
            item {
                SearchCapsule(query, { query = it }, onSubmit = { submitted = query })
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = MibotSpace.Screen, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToggleChip("星标最多", sort == MarketSort.STARS) { sort = MarketSort.STARS }
                    ToggleChip("最新", sort == MarketSort.NEWEST) { sort = MarketSort.NEWEST }
                    ToggleChip("只看可一键装", oneClickOnly) { oneClickOnly = !oneClickOnly }
                }
                MibotFilterChips(PluginCategories.tabs, tab) { tab = it }
                Row(
                    Modifier.fillMaxWidth().padding(start = MibotSpace.Screen + MibotSpace.M, end = MibotSpace.Screen + MibotSpace.M, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("SkillHub 插件 · ${phone.size} 个手机能用", modifier = Modifier.weight(1f), fontSize = MibotType.TitleSmall,
                        fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onBackground)
                    Text("skillhub.cn", fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (trError != null) MibotFootnote("译文：$trError（长按条目可看原文）")
                error?.let { msg ->
                    MibotFootnote(msg)
                    Row(Modifier.padding(16.dp)) { MibotButton("重试", tonal = true, onClick = { reload++ }) }
                }
                if (loading && items.isEmpty()) MibotFootnote("正在加载 SkillHub 插件广场…")
            }
            if (phone.isNotEmpty() || desktop.isNotEmpty()) {
                item { SectionBanner("手机能用", phone.size, "可在 Mibot 沙箱（Node / Python）中使用", accent = true) }
                if (phone.isEmpty()) item { MibotFootnote("当前筛选下没有手机能用的插件。") }
                else item {
                    MibotCard {
                        phone.forEachIndexed { i, p ->
                            PluginRow(p, installed[p.fullName], busy[p.fullName], showOriginal[p.fullName] == true,
                                onToggleOriginal = { showOriginal[p.fullName] = showOriginal[p.fullName] != true },
                                onOpen = { onOpen(p.fullName) }, onInstall = { install(p) }, onConvert = null)
                            if (i < phone.lastIndex) MibotDivider(inset = 72.dp)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(18.dp))
                    SectionBanner("需电脑/其他", desktop.size, "依赖桌面窗口、系统原生程序或 DSH 桌面客户端；可尝试转换（实验）", accent = false)
                }
                if (desktop.isEmpty()) item { MibotFootnote("当前筛选下没有需要电脑的插件。") }
                else item {
                    MibotCard {
                        desktop.forEachIndexed { i, p ->
                            PluginRow(p, installed[p.fullName], busy[p.fullName], showOriginal[p.fullName] == true,
                                onToggleOriginal = { showOriginal[p.fullName] = showOriginal[p.fullName] != true },
                                onOpen = { onOpen(p.fullName) }, onInstall = { install(p) }, onConvert = { convert(p) })
                            if (i < desktop.lastIndex) MibotDivider(inset = 72.dp)
                        }
                    }
                }
            }
            item {
                if (items.isNotEmpty() && items.size < total) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        MibotButton(if (loading) "加载中…" else "加载更多（已显示 ${items.size} / $total）", tonal = true, enabled = !loading,
                            onClick = { scope.launch { load(page + 1) } })
                    }
                }
                MibotFootnote("数据来自 SkillHub DSH Plugin 广场，插件为 GitHub 开源项目（许可证见详情）。「手机能用」为启发式判断；安装只下载代码到本机插件目录。")
            }
        }
    }
}

@Composable
private fun SectionBanner(title: String, count: Int, sub: String, accent: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = MibotSpace.Screen + MibotSpace.M, end = MibotSpace.Screen + MibotSpace.M, top = MibotSpace.L, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 14.dp).clip(RoundedCornerShape(2.dp))
                .background(if (accent) MibotTokens.accent() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
            Spacer(Modifier.width(8.dp))
            Text("「$title」", fontSize = MibotType.TitleSmall, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(6.dp))
            StatusChip("$count", if (accent) ChipKind.ACCENT else ChipKind.NEUTRAL)
        }
        Text(sub, fontSize = MibotType.Secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 11.dp, top = 2.dp))
    }
}

@Composable
private fun SearchCapsule(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MibotSpace.Screen, vertical = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(MibotTokens.card())
            .border(1.dp, MibotTokens.hairline(), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(LineIcons.Search, size = MibotIcon.Dense, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Box(Modifier.clip(RoundedCornerShape(50)).clickable { onChange(""); onSubmit() }.padding(2.dp)) {
                Glyph(LineIcons.X, size = MibotIcon.Small, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        modifier = Modifier
            .clip(shape)
            .background(if (on) MibotTokens.selected() else MibotTokens.card())
            .border(1.dp, if (on) MibotTokens.accent().copy(alpha = 0.35f) else MibotTokens.hairline(), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        fontSize = MibotType.Section,
        fontWeight = if (on) MibotType.Semi else MibotType.Regular,
        color = if (on) MibotTokens.accent() else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SmallPill(text: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        modifier = Modifier
            .heightIn(min = 30.dp)
            .clip(shape)
            .background(if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f) else if (filled) MibotTokens.accent() else MibotTokens.tonal())
            .then(if (!filled) Modifier.border(1.dp, MibotTokens.hairline(), shape) else Modifier)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        fontSize = MibotType.Secondary,
        fontWeight = MibotType.Semi,
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            filled -> MaterialTheme.colorScheme.onPrimary
            else -> MibotTokens.accent()
        },
        maxLines = 1,
    )
}

@Composable
internal fun PluginAvatar(url: String?, name: String, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.27f)
    Box(
        Modifier.size(size).clip(shape).background(MibotTokens.tile()).border(0.5.dp, MibotTokens.hairline(), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), fontSize = MibotType.Title, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) {
            coil.compose.AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PluginRow(
    p: MarketPlugin,
    inst: InstalledPlugin?,
    busy: String?,
    showOriginal: Boolean,
    onToggleOriginal: () -> Unit,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onConvert: (() -> Unit)?,
) {
    val zh = rememberZh(p.description)
    val desc = if (showOriginal || zh == null) p.description else zh
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onToggleOriginal)
            .padding(horizontal = MibotSpace.M + 2.dp, vertical = 12.dp)
            .semantics { contentDescription = "插件 ${p.name}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PluginAvatar(p.avatarUrl, p.name)
        Spacer(Modifier.width(MibotSpace.M))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = MibotType.Body, fontWeight = MibotType.Semi, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (desc.isNotBlank()) {
                Text(desc, fontSize = MibotType.Secondary, lineHeight = MibotType.SecondaryLine, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("★ ${starText(p.stars)} · ${PluginCategories.of(p)}", fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showOriginal) StatusChip("原文", ChipKind.NEUTRAL)
                else if (zh == null && p.description.isNotBlank()) StatusChip("待翻译", ChipKind.NEUTRAL)
                inst?.conversion?.let { c ->
                    when (c.state) {
                        ConvState.OK -> StatusChip("已转换", ChipKind.OK)
                        ConvState.PARTIAL -> StatusChip("已转换 · 部分可用", ChipKind.WARN)
                        ConvState.FAIL -> StatusChip("无法转换", ChipKind.ERR)
                    }
                }
            }
        }
        Spacer(Modifier.width(MibotSpace.S))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                busy != null -> StatusChip(busy, ChipKind.WARN)
                onConvert != null && inst?.conversion == null -> {
                    SmallPill("尝试转换", filled = false, onClick = onConvert)
                    Text("实验", fontSize = MibotType.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inst == null -> SmallPill("试装", filled = false, onClick = onInstall)
                p.pushedAt > inst.pushedAt + 60_000 -> {
                    SmallPill("更新", filled = true, onClick = onInstall)
                    StatusChip("已安装", ChipKind.OK)
                }
                else -> StatusChip("已安装", ChipKind.OK)
            }
        }
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
            runCatching { SkillHubApi.list(1, MarketSort.STARS, fullName.substringAfter('/'), 24) }
                .onSuccess { pg -> plugin = pg.items.firstOrNull { it.fullName.equals(fullName, true) }; if (plugin == null) loadErr = "找不到插件 $fullName" }
                .onFailure { loadErr = "加载失败：${it.message}" }
            plugin?.let { MarketCache.byName[it.fullName] = it }
        }
        val p = plugin ?: return@LaunchedEffect
        PluginTranslator.translateDescriptions(ctx, listOf(p.description))
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
        val listReasons = PluginCompat.desktopReasons(p)
        val reasons = (listReasons + extraReasons).distinct()
        val phone = inst?.conversion?.let { it.state != ConvState.FAIL } ?: reasons.isEmpty()

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
                    else -> StatusChip("需电脑/其他", ChipKind.WARN)
                }
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
            if (!phone || inst?.conversion != null) {
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
            MibotSectionHeader("为什么需要电脑")
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

        if (inst != null) {
            MibotSectionHeader("插件设置")
            MibotCard {
                MibotRow(
                    title = "状态",
                    subtitle = inst.note ?: "实验 · 占位运行器（不执行插件代码）",
                    chevron = false, showDivider = true,
                    trailing = {
                        StatusChip(
                            (if (inst.status == PluginStatus.PREPARING) "◌ " else if (inst.status == PluginStatus.RUNNING) "● " else "○ ") + inst.status.zh,
                            when (inst.status) { PluginStatus.RUNNING -> ChipKind.OK; PluginStatus.PREPARING -> ChipKind.WARN; PluginStatus.STOPPED -> ChipKind.NEUTRAL },
                        )
                    },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (inst.status == PluginStatus.STOPPED) MibotButton("启动（实验）", tonal = true, modifier = Modifier.weight(1f), onClick = { scope.launch { PluginRunner.start(ctx, fullName) } })
                    else MibotButton("停止", tonal = true, modifier = Modifier.weight(1f), enabled = inst.status == PluginStatus.RUNNING, onClick = { PluginRunner.stop(ctx, fullName) })
                    MibotButton("卸载", tonal = true, modifier = Modifier.weight(1f), onClick = { PluginStore.remove(ctx, fullName) })
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
