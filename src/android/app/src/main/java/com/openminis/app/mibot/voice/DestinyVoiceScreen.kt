package com.openminis.app.mibot.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.glass.LocalGlassBottomInset
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * [destiny/v7] Screen 34 「语音」 — a REAL voice pipeline, not a mock.
 *
 * Uses the platform SpeechRecognizer / TextToSpeech directly, so it works
 * even on builds where the app's own 120 speech classes are configured for
 * cloud providers. Press-and-hold to speak, release to fill the editable
 * box, two seconds of silence sends it, or flip 「松手直接发」 to send at
 * once. 「免提连说」 reopens the mic after each send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinyVoiceScreen(
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val bottom = LocalGlassBottomInset.current
    val accent = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val sendRef = rememberUpdatedState(onSend)

    var transcript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("按住说 · 松手填进框里") }
    var listening by remember { mutableStateOf(false) }
    var autoSend by remember { mutableStateOf(false) }
    var handsFree by remember { mutableStateOf(false) }
    var readAloud by remember { mutableStateOf(false) }
    var pendingSend by remember { mutableStateOf<String?>(null) }
    var sentAt by remember { mutableLongStateOf(0L) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var micGranted by remember {
        mutableStateOf(
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val reqPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { micGranted = it }

    fun asrError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再按一次"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到声音"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别服务没连上，可以直接打字发"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙，稍等一下"
        else -> "识别没成功，可以直接打字发"
    }

    // RecognitionListener calls this; it only writes state, never calls start(),
    // so there is no forward-reference cycle with the start() function below.
    fun handleResult(text: String) {
        val t = text.trim()
        listening = false
        if (t.isBlank()) {
            status = "没听清，再按一次"
            return
        }
        transcript = if (transcript.isBlank()) t else "$transcript $t"
        if (autoSend) {
            val out = transcript
            sendRef.value("【语音】$out")
            if (readAloud) {
                tts?.speak("已发送", TextToSpeech.QUEUE_FLUSH, null, "destiny-voice")
            }
            transcript = ""
            pendingSend = null
            status = "已发出"
            sentAt = System.currentTimeMillis()
        } else {
            status = "停 2 秒就发 · 点框里改字可取消"
            pendingSend = transcript
        }
    }

    val listener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status = "在听…" }
            override fun onBeginningOfSpeech() { status = "在听…" }
            override fun onRmsChanged(rmsdB: Float) { /* level meter is cosmetic */ }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { status = "识别中…" }
            override fun onError(error: Int) {
                listening = false
                status = asrError(error)
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                handleResult(list?.firstOrNull().orEmpty())
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val t = list?.firstOrNull().orEmpty()
                if (t.isNotBlank()) status = t.take(36)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
    }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        } else null
    }

    fun start() {
        if (!micGranted) {
            reqPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val r = recognizer ?: run {
            status = "这台设备不支持语音识别，可以直接打字发"
            return
        }
        pendingSend = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.setRecognitionListener(listener)
        listening = true
        status = "在听…"
        r.startListening(intent)
    }

    fun stop() {
        if (!listening) return
        recognizer?.stopListening()
        status = "识别中…"
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(ctx) { /* init is async; speak() queues safely */ }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            recognizer?.destroy()
        }
    }

    // 停 2 秒就发. Editing the field sets pendingSend = null, cancelling it.
    LaunchedEffect(pendingSend) {
        val text = pendingSend ?: return@LaunchedEffect
        delay(2000)
        if (pendingSend == text) {
            sendRef.value("【语音】$text")
            if (readAloud) {
                tts?.speak("已发送", TextToSpeech.QUEUE_FLUSH, null, "destiny-voice")
            }
            transcript = ""
            pendingSend = null
            status = "已发出"
            sentAt = System.currentTimeMillis()
        }
    }

    // 免提连说: reopen the mic shortly after a send, no second press.
    LaunchedEffect(sentAt) {
        if (sentAt > 0L && handsFree) {
            delay(700)
            start()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = bottom + 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "你不用再按一次。它说完你直接开口，说到一半它就停。",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = accent)
            }

            item {
                OutlinedTextField(
                    value = transcript,
                    onValueChange = {
                        transcript = it
                        pendingSend = null       // 改字就取消自动发
                        status = "已改成手动编辑"
                    },
                    label = { Text("转写结果 · 可改") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .background(
                            color = if (listening) accent.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                start()
                                tryAwaitRelease()
                                stop()
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (listening) "松手填进框里 · 停 2 秒就发" else "按住说",
                        fontWeight = FontWeight.SemiBold,
                        color = if (listening) accent else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            val t = transcript.trim()
                            if (t.isNotBlank()) {
                                sendRef.value("【语音】$t")
                                transcript = ""
                                pendingSend = null
                                status = "已发出"
                                sentAt = System.currentTimeMillis()
                            } else {
                                status = "先说一句，或者直接打字"
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("发 送") }
                }
            }

            item {
                VoiceToggle("松手直接发", autoSend, "跳过 2 秒等待，松手就发出去") { autoSend = it }
                VoiceToggle("免提连说", handsFree, "发完自动再开麦，不用再按") { handsFree = it }
                VoiceToggle("朗读回复", readAloud, "用系统 TTS 念一句确认") { readAloud = it }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "识别走系统 SpeechRecognizer，朗读走系统 TextToSpeech；" +
                        "说出来的内容会作为一条真实聊天草稿交给 Agent，不是本地假显示。" +
                        if (!micGranted) " 当前还没给麦克风权限，按住时会先申请。" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun VoiceToggle(
    label: String,
    checked: Boolean,
    hint: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
