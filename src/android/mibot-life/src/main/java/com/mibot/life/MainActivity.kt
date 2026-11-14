package com.mibot.life

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.life.exec.LifeExecutor
import com.mibot.life.life.*
import com.mibot.life.settings.LifeSettings
import com.mibot.life.settings.SettingsStore
import com.mibot.life.ui.EconomyScreen
import com.mibot.life.ui.GrowthScreen
import com.mibot.life.ui.SettingsScreen
import com.mibot.life.voice.VoiceIO
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- Hark 色 ----
val SkyTop = Color(0xFFDDEAF5)
val SkyBottom = Color(0xFFF7F0E6)
val Porcelain = Color(0xFFFAF5EF)
val Coral = Color(0xFFE8825A)
val Ink = Color(0xFF2E2A26)
val InkSoft = Color(0xFF6B625A)
val GlassCard = Color(0xCCFFFFFF)
val BubbleMe = Color(0xFFC8E5FE)
val BubbleThem = Color(0xFFE1EAEC)

class MainActivity : ComponentActivity() {

    private lateinit var settings: LifeSettings
    private lateinit var settingsStore: SettingsStore
    private lateinit var system: LifeSystem
    private lateinit var agent: LifeAgent
    private lateinit var voice: VoiceIO
    private lateinit var executor: LifeExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(this)
        settings = settingsStore.load()
        system = LifeSystem(this, settings)
        executor = LifeExecutor(this)
        voice = VoiceIO(this).also { v -> v.initTts(); v.initAsr() }
        agent = LifeAgent(
            LifeAgent.AgentConfig(
                baseUrl = settings.gatewayUrl,
                apiKey = settings.apiKey,
                dailyModel = settings.dailyModel,
                deepModel = settings.deepModel,
            ),
            system,
        )
        setContent { Root(system, agent, voice, settings, executor, onSaveSettings = { newS -> settingsStore.save(newS); settings = newS }) }
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }
}

@Composable
fun Root(
    system: LifeSystem,
    agent: LifeAgent,
    voice: VoiceIO,
    settings: LifeSettings,
    executor: LifeExecutor,
    onSaveSettings: (LifeSettings) -> Unit = {},
) {
    var curSettings by remember { mutableStateOf(settings) }
    var tab by remember { mutableIntStateOf(0) }
    var decision by remember { mutableStateOf<LifeDecision?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) { decision = system.decide() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabBar(tab) { tab = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> LifeScreen(system, decision, executor) { refreshTick++ }
                    1 -> ChatScreen(system, agent, voice)
                    2 -> GrowthScreen(system)
                    3 -> EconomyScreen(system, curSettings)
                    else -> SettingsScreen(curSettings) { newS -> onSaveSettings(newS); curSettings = newS; refreshTick++ }
                }
            }
        }
    }
}

@Composable
private fun TabBar(active: Int, onPick: (Int) -> Unit) {
    val titles = listOf("生活", "对话", "成长", "经济", "设置")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        titles.forEachIndexed { i, t ->
            val on = i == active
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) Ink else GlassCard, RoundedCornerShape(50))
                    .clickable { onPick(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(t, color = if (on) Porcelain else InkSoft, fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

// ---------------- 生活 ----------------

@Composable
private fun LifeScreen(
    system: LifeSystem,
    decision: LifeDecision?,
    executor: LifeExecutor,
    onRefresh: () -> Unit,
) {
    val st = system.snapshot()
    val phase = system.phase()
    val chosen = decision?.chosen

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HarkCard {
                Text(phaseInfo(phase), fontSize = 12.sp, color = Coral, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(chosen?.let { "现在想做：${it.action.zh}" } ?: "在想…",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(4.dp))
                Text(chosen?.reason ?: "", fontSize = 12.sp, color = InkSoft)
                chosen?.factors?.take(3)?.forEach { f ->
                    Spacer(Modifier.height(3.dp))
                    Text("+ ${f.reason}  ${String.format("%.1f", f.points)}", fontSize = 11.sp, color = Coral)
                }
                decision?.excluded?.take(2)?.forEach { e ->
                    Spacer(Modifier.height(2.dp))
                    Text("× ${e.reason}", fontSize = 11.sp, color = InkSoft.copy(alpha = 0.7f))
                }
            }
        }

        item {
            HarkCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("心情", fontSize = 13.sp, color = InkSoft)
                    Text(st.affect.moodHint(), fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                AffectBar("愉悦", st.affect.pleasure, Coral)
                AffectBar("能量", st.affect.energy, Color(0xFF7BA7D9))
                AffectBar("牵挂", st.affect.care, Color(0xFFB58BD9))
                AffectBar("挫败", st.affect.frustration, Color(0xFFB0A79E))

                if (st.interests.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("我的兴趣", fontSize = 11.sp, color = InkSoft)
                    Text(
                        st.interests.sortedByDescending { it.strength }.take(3)
                            .joinToString("、") { "${it.domain} ${String.format("%.1f", it.strength)}" },
                        fontSize = 12.sp, color = Coral,
                    )
                }
                val dueCares = CareLedger().due(st.cares)
                if (dueCares.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("惦记着", fontSize = 11.sp, color = InkSoft)
                    dueCares.take(2).forEach { Text(it.text, fontSize = 12.sp, color = Ink) }
                }
            }
        }

        item {
            HarkCard {
                Text("去生活", fontSize = 13.sp, color = InkSoft)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("reading" to "读书", "video" to "看视频", "forum" to "逛论坛").forEach { (k, label) ->
                        Box(
                            Modifier.weight(1f).background(Porcelain, RoundedCornerShape(14.dp))
                                .clickable {
                                    val app = executor.pickByScene(k)
                                    if (app != null) {
                                        executor.launchApp(app.first)
                                        system.recordEpisode(kindOf(k), "去$label：${app.second}")
                                        system.feel(AffectEvent.SUCCESS)
                                    } else {
                                        system.recordEpisode(EpisodeKind.PHONE, "没找到${label}App")
                                        system.feel(AffectEvent.FAIL)
                                    }
                                    onRefresh()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(label, fontSize = 13.sp, color = Ink) }
                    }
                }
            }
        }

        val recent = st.episodes.takeLast(4).reversed()
        items(recent.size) { i ->
            val e = recent[i]
            HarkCard {
                Text(fmtTime(e.occurredAt), fontSize = 10.sp, color = InkSoft.copy(alpha = 0.7f))
                Text(e.title, fontSize = 13.sp, color = Ink)
            }
        }
    }
}

private fun kindOf(scene: String): EpisodeKind = when (scene) {
    "reading" -> EpisodeKind.READ
    "video" -> EpisodeKind.VIDEO
    "forum" -> EpisodeKind.FORUM
    else -> EpisodeKind.PHONE
}

@Composable
private fun AffectBar(label: String, v: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = InkSoft, modifier = Modifier.width(38.dp))
        Box(Modifier.weight(1f).height(6.dp).background(Porcelain, RoundedCornerShape(50))) {
            Box(
                Modifier
                    .fillMaxWidth(v.coerceIn(0.0, 1.0).toFloat())
                    .height(6.dp)
                    .background(color.copy(alpha = 0.75f), RoundedCornerShape(50))
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(String.format("%.1f", v), fontSize = 10.sp, color = InkSoft)
    }
}

// ---------------- 对话 ----------------

@Composable
private fun ChatScreen(system: LifeSystem, agent: LifeAgent, voice: VoiceIO) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Pair<Boolean, String>>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val partial by voice.partial.collectAsState()
    val heard by voice.heard.collectAsState()

    LaunchedEffect(Unit) {
        messages.add(false to "我醒了。${system.snapshot().affect.moodHint()}。")
    }
    LaunchedEffect(heard) {
        heard?.takeIf { it.isNotBlank() }?.let { input = it }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            items(messages.size) { idx ->
                val i = messages.size - 1 - idx
                val (me, text) = messages[i]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (me) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        Modifier.widthIn(max = 280.dp)
                            .background(if (me) BubbleMe else BubbleThem, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text(text, fontSize = 13.sp, color = Ink) }
                }
            }
        }
        if (partial.isNotBlank()) {
            Text("听：$partial", fontSize = 11.sp, color = Coral, modifier = Modifier.padding(bottom = 4.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).background(GlassCard, RoundedCornerShape(50))
                    .clickable { if (voice.available) voice.listen() },
                contentAlignment = Alignment.Center
            ) { Text("说", fontSize = 13.sp, color = if (voice.available) Ink else InkSoft.copy(alpha = 0.4f)) }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.weight(1f).background(GlassCard, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (input.isEmpty()) Text("跟它说话…", fontSize = 13.sp, color = InkSoft.copy(alpha = 0.55f))
                BasicTextField(
                    value = input, onValueChange = { input = it },
                    textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(42.dp)
                    .background(if (input.isBlank() || busy) InkSoft.copy(alpha = 0.25f) else Coral, RoundedCornerShape(50))
                    .clickable(enabled = input.isNotBlank() && !busy) {
                        val text = input.trim()
                        input = ""
                        messages.add(true to text)
                        busy = true
                        scope.launch {
                            val r = agent.reply(text)
                            messages.add(false to r)
                            voice.speak(r)
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text(if (busy) "…" else "发", fontSize = 13.sp, color = Porcelain) }
        }
    }
}

// ---------------- 通用 ----------------

@Composable
private fun HarkCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(GlassCard, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) { content() }
}

private fun phaseInfo(p: RhythmPhase): String = when (p) {
    RhythmPhase.MORNING -> "晨间 · 清醒"
    RhythmPhase.DAY -> "白日"
    RhythmPhase.EVENING -> "睡前 · 松弛"
    RhythmPhase.NIGHT -> "夜间"
}

private fun fmtTime(ms: Long) = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ms))
