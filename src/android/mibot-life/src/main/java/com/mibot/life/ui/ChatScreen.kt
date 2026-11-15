package com.mibot.life.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mibot.life.life.LifeAgent
import com.mibot.life.life.LifeSystem
import com.mibot.life.voice.VoiceIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Coral = Color(0xFFE8825A)
private val Ink = Color(0xFF2E2A26)
private val InkSoft = Color(0xFF6B625A)
private val Glass = Color(0xCCFFFFFF)
private val BubbleMe = Color(0xFFC8E5FE)
private val BubbleThem = Color(0xFFE1EAEC)

/**
 * 对话页 —— 语音优先。
 *
 * 交互循环（continuousMode）：
 *   点麦 → 听 → 识别出文字 → 自动发送 → 模型回复 → TTS 说话 → 说完自动回到听
 * 说话时点麦 = 立刻打断并开始听（不和它抢话）
 */
@Composable
fun ChatScreen(system: LifeSystem, agent: LifeAgent, voice: VoiceIO) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Pair<Boolean, String>>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wantListening by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(true) }   // 免提通话：像打电话，不按锁自动听/说

    val state by voice.asrState.collectAsState()
    val level by voice.level.collectAsState()
    val partial by voice.partial.collectAsState()
    val heard by voice.heard.collectAsState()
    val err by voice.error.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voice.initAsr(); voice.listen()
        } else {
            messages.add(false to "没有麦克风权限，我只能打字聊天了。")
        }
    }

    LaunchedEffect(Unit) {
        voice.initTts(); voice.initAsr()
        // 免触控：说话时你开口 → 自动打断转听（像打电话）
        voice.onBarge = {
            wantListening = true
            if (!busy) runCatching { voice.listen() }
        }
        messages.add(false to "我醒了。${system.snapshot().affect.moodHint()}。")
        // 免提默认开：进入即自动开始听，不用按锁
        if (handsFree && !voice.needsPermission()) {
            wantListening = true
            delay(300)
            runCatching { voice.listen() }
        }
    }

    // 识别完成 → 自动发送（语音优先的核心）
    LaunchedEffect(heard) {
        heard?.takeIf { it.isNotBlank() }?.let { text ->
            voice.consumeHeard()
            if (busy) return@let
            messages.add(true to text)
            busy = true
            voice.stopListening()
            val r = agent.reply(text)
            messages.add(false to r)
            voice.speak(r)   // 说完 onDone → pendingResume → 下面自动续听
            busy = false
        }
    }

    // 说完自动回到听（连续对话）
    LaunchedEffect(state) {
        if (wantListening && state == VoiceIO.AsrState.IDLE) {
            if (voice.shouldResumeListening() || !busy) {
                delay(250)
                if (wantListening && voice.asrState.value == VoiceIO.AsrState.IDLE && !busy) {
                    runCatching { voice.listen() }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // （虚拟形象素材到位后在此显示；当前不显示自绘形象）
        Spacer(Modifier.height(4.dp))
        // 免提通话开关：像打电话，开=不按锁自动听/说
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                Modifier.background(Glass, RoundedCornerShape(50))
                    .clickable {
                        handsFree = !handsFree
                        wantListening = handsFree
                        if (handsFree) {
                            if (voice.needsPermission()) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else voice.listen()
                        } else voice.stopListening()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(8.dp).background(
                        if (handsFree) Color(0xFF4CAF7D) else InkSoft.copy(alpha = 0.3f),
                        RoundedCornerShape(50)
                    )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (handsFree) "免提通话中 · 开口就听" else "免提已关 · 点麦说话",
                    fontSize = 11.sp,
                    color = if (handsFree) Color(0xFF2F6B4F) else InkSoft
                )
            }
        }
        // 消息
        LazyColumn(Modifier.weight(1f), reverseLayout = true) {
            items(messages.size) { idx ->
                val i = messages.size - 1 - idx
                val (me, text) = messages[i]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (me) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        Modifier.widthIn(max = 290.dp)
                            .background(if (me) BubbleMe else BubbleThem, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text(text, fontSize = 13.sp, color = Ink) }
                }
            }
        }

        // 实时识别 / 错误
        if (partial.isNotBlank()) {
            Text("「$partial」", fontSize = 12.sp, color = Coral,
                modifier = Modifier.padding(vertical = 4.dp))
        }
        err?.let {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, fontSize = 11.sp, color = Coral, modifier = Modifier.weight(1f))
                Text("知道了", fontSize = 11.sp, color = InkSoft,
                    modifier = Modifier.clickable { voice.clearError() })
            }
        }

        // 输入行
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MicButton(
                state = state,
                level = level,
                onClick = {
                    when {
                        state == VoiceIO.AsrState.SPEAKING -> {
                            voice.interrupt()
                            wantListening = true
                            if (voice.needsPermission()) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else voice.listen()
                        }
                        wantListening && state == VoiceIO.AsrState.LISTENING -> {
                            wantListening = false
                            voice.stopListening()
                        }
                        else -> {
                            wantListening = true
                            if (voice.needsPermission()) permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            else voice.listen()
                        }
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.weight(1f).background(Glass, RoundedCornerShape(50))
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
            val canSend = input.isNotBlank() && !busy
            Box(
                Modifier.size(46.dp)
                    .background(if (canSend) Coral else InkSoft.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .clickable(enabled = canSend) {
                        val text = input.trim()
                        input = ""
                        messages.add(true to text)
                        busy = true
                        scope.launch {
                            // 打字进 → 打字出（不朗读，用户"如果我打字他就打字"）
                            val r = agent.reply(text)
                            messages.add(false to r)
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text(if (busy) "…" else "发", fontSize = 13.sp, color = Color(0xFFFAF5EF)) }
        }
    }
}

/** 麦克风按钮：呼吸光环随音量放大 + 状态色 */
@Composable
private fun MicButton(state: VoiceIO.AsrState, level: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val breath by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "breath"
    )
    val listening = state == VoiceIO.AsrState.LISTENING
    val speaking = state == VoiceIO.AsrState.SPEAKING
    val thinking = state == VoiceIO.AsrState.THINKING
    val scale = when {
        listening -> 1f + level * 0.35f
        speaking -> breath
        thinking -> breath * 0.98f
        else -> 1f
    }
    val ring = when {
        listening -> Coral
        speaking -> Color(0xFF7BA7D9)
        thinking -> Coral.copy(alpha = 0.5f)
        else -> InkSoft.copy(alpha = 0.25f)
    }
    Box(contentAlignment = Alignment.Center) {
        if (listening || speaking || thinking) {
            Box(
                Modifier.size(46.dp).scale(scale * 1.25f)
                    .border(2.dp, ring.copy(alpha = 0.35f), RoundedCornerShape(50))
            )
        }
        Box(
            Modifier.size(46.dp).scale(scale).background(Glass, RoundedCornerShape(50))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (state) {
                    VoiceIO.AsrState.LISTENING -> "听"
                    VoiceIO.AsrState.THINKING -> "…"
                    VoiceIO.AsrState.SPEAKING -> "停"
                    else -> "说"
                },
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (listening || speaking) Coral else Ink,
            )
        }
    }
}
