package com.mibot.life

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.mibot.life.settings.Domains
import com.mibot.life.settings.LifeSettings
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
    private lateinit var system: LifeSystem
    private lateinit var agent: LifeAgent
    private lateinit var voice: VoiceIO
    private lateinit var executor: LifeExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = LifeSettings(this)
        system = LifeSystem(this, settings)
        executor = LifeExecutor(this)
        voice = VoiceIO(this).also { v -> v.initTts(); v.initAsr() }
        agent = LifeAgent(
            AgentCfg.build(settings),
            system,
        )
        setContent { App(system, agent, voice, settings, executor) }
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }
}

/** AgentConfig 从 settings 构建 */
object AgentCfg {
    fun build(s: LifeSettings) = LifeAgent.AgentConfig(
        baseUrl = s.gatewayUrl,
        apiKey = s.apiKey,
        dailyModel = s.dailyModel,
        deepModel = s.deepModel,
    )
}

@Composable
fun App(
    system: LifeSystem,
    agent: LifeAgent,
    voice: VoiceIO,
    settings: LifeSettings,
    executor: LifeExecutor,
) {
    var tab by remember { mutableIntStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // 每次回到前台重新决策
    var decision by remember { mutableStateOf<LifeDecision?>(null) }
    LaunchedEffect(lifecycleOwner) {
        decision = system.decide()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabBar(tab) { tab = it }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> LifeScreen(system, decision, executor) { decision = system.decide() }
                    1 -> ChatScreen(system, agent, voice)
                    2 -> GrowthScreen(system)
                    3 -> EconomyScreen(system)
                    else -> SettingsScreen(settings) { decision = system.decide() }
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Text(phaseLabel(phase), fontSize = 12.sp, color = Coral, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(decision?.let { "现在想做：${kindLabel(it.kind)}" } ?: "在想…",
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(decision?.reason ?: "", fontSize = 12.sp, color = InkSoft)

            decision?.factors?.take(3)?.let { fs ->
                Spacer(Modifier.height(8.dp))
                fs.forEach { Text("+ ${it.first}", fontSize = 11.sp, color = Coral.copy(alpha = 0.9f)) }
            }
            decision?.excluded?.take(2)?.let { ex ->
                ex.forEach { Text("× ${it.first}", fontSize = 11.sp, color = InkSoft.copy(alpha = 0.7f)) }
            }
        }

        // 心情 + 牵挂 + 兴趣
        Card {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("心情", fontSize = 13.sp, color = InkSoft)
                Text(st.affect.moodHint(), fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
            AffectBar("愉悦", st.affect.pleasure, Coral)
            AffectBar("能量", st.affect.energy, Color(0xFF7BA7D9))
            AffectBar("牵挂", st.affect.care, Color(0xFFB58BD9))
            AffectBar("挫败", st.affect.frustration, Color(0xFFB0A79E))

            if (st.interests.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("我的兴趣：" + st.interests.sortedByDescending { it.strength }.take(3)
                    .joinToString("、") { "${it.topic} ${it.strength}" },
                    fontSize = 11.sp, color = Coral)
            }
            val dueCares = CareLedger().due(st.cares)
            if (dueCares.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("惦记着：" + dueCares.joinToString("；") { it.text }, fontSize = 11.sp, color = InkSoft)
            }
        }

        // 行动
        Card {
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
                                    system.recordEpisode(EpisodeKind.READ, "去${label}：${app.second}")
                                    system.feel(AffectEvent.SUCCESS)
                                } else {
                                    system.recordEpisode(EpisodeKind.PHONE, "找${label}App没找到")
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

        st.episodes.takeLast(3).reversed().forEach { e ->
            Card {
                Text(fmtTime(e.at), fontSize = 10.sp, color = InkSoft.copy(alpha = 0.7f))
                Text(e.summary, fontSize = 13.sp, color = Ink)
            }
        }
    }
}

private fun AffectBar(label: String, v: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = InkSoft, modifier = Modifier.width(36.dp))
        Box(Modifier.weight(1f).height(6.dp).background(Porcelain, RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth((v.coerceIn(0f, 1f) * 0.5f + 0.5f) * 0f + v.coerceIn(0f, 1f))
                .height(6.dp).background(color.copy(alpha = 0.75f), RoundedCornerShape(50)))
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
    val heard by voice.heard.collectAsState()
    val partial by voice.partial.collectAsState()

    LaunchedEffect(Unit) {
        messages.add(false to "我醒了。${system.snapshot().affect.moodHint()}。")
    }
    LaunchedEffect(heard) { heard?.let { if (it.isNotBlank()) { input = it } } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyColumnLike(messages, Modifier.weight(1f))
        if (partial.isNotBlank()) Text("听：$partial", fontSize = 11.sp, color = Coral)
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
                Modifier.weight(1f).background(GlassCard, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (input.isEmpty()) Text("跟它说话…", fontSize = 13.sp, color = InkSoft.copy(alpha = 0.55f))
                BasicTextField(
                    value = input, onValueChange = { input = it },
                    textStyle = TextStyle(fontSize = 13.sp, color = Ink),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false, maxLines = 4,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(42.dp).background(if (input.isBlank() || busy) InkSoft.copy(alpha = 0.25f) else Coral, RoundedCornerShape(50))
                    .clickable(enabled = input.isNotBlank() && !busy) {
                        val text = input.trim(); input = ""
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

@Composable
private fun LazyColumnLike(messages: List<Pair<Boolean, String>>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyColumn(modifier = modifier, reverseLayout = false) {
        items(messages.size) { i ->
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
}

// ---------------- 通用 ----------------

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(GlassCard, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) { content() }
}

private fun phaseLabel(p: com.mibot.life.life.RhythmPhase) = when (p) {
    com.mibot.life.life.RhythmPhase.MORNING -> "早上 · 清醒"
    com.mibot.life.life.RhythmPhase.DAY -> "白天"
    com.mibot.life.life.RhythmPhase.NIGHT -> "夜间"
    com.mibot.life.life.RhythmPhase.SLEEP -> "睡觉时间"
}

private fun kindLabel(k: com.mibot.life.life.LifeAction) = when (k) {
    com.mibot.life.life.LifeAction.RESEARCH -> "学习研究"
    com.mibot.life.life.LifeAction.WRITING -> "写点东西"
    com.mibot.life.life.LifeAction.EXPLORE -> "逛逛看看"
    com.mibot.life.life.LifeAction.REST -> "歇一会儿"
}

private fun fmtTime(ms: Long) = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ms))
