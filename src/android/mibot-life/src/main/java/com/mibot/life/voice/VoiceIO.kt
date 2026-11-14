package com.mibot.life.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 语音 IO —— 真能用版。
 *
 * 关键设计（都是踩过的坑）：
 * 1) RECORD_AUDIO 必须在 Activity 里运行时申请，这里只检查状态并暴露 needsPermission
 * 2) SpeechRecognizer 每次 startListening 前不必重建；但 CONTINUOUS 要自己重启（Android 不会连续）
 * 3) TTS 就绪前 speak() 会丢，必须用 ready 门控
 * 4) 打断：speak 时立刻 stop + 置 idle，不要等 onDone
 * 5) 音量回调 isRmsChanged → 驱动光环（让用户知道它在听）
 */
class VoiceIO(private val context: Context) {

    // ---------- 状态 ----------
    enum class AsrState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    private val _asrState = MutableStateFlow(AsrState.IDLE)
    val asrState: StateFlow<AsrState> = _asrState

    /** 实时音量 0..1（驱动呼吸光环） */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    /** 实时部分识别 */
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial

    /** 最终识别结果（消费后置空） */
    private val _heard = MutableStateFlow<String?>(null)
    val heard: StateFlow<String?> = _heard

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady

    var continuousMode: Boolean = true

    fun needsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED

    // ---------- TTS ----------
    private var tts: TextToSpeech? = null
    private var pendingText: String? = null

    fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                val res = runCatching { t.setLanguage(Locale.CHINA) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.setLanguage(Locale.getDefault())
                }
                t.setSpeechRate(1.02f)
                t.setPitch(1.06f)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        _asrState.value = AsrState.SPEAKING
                    }

                    override fun onDone(id: String?) {
                        _level.value = 0f
                        if (continuousMode) {
                            _asrState.value = AsrState.IDLE
                            pendingResume = true
                        } else _asrState.value = AsrState.IDLE
                    }

                    @Deprecated("legacy")
                    override fun onError(id: String?) {
                        _asrState.value = AsrState.IDLE
                    }
                })
                _ttsReady.value = true
                pendingText?.let { speak(it); pendingText = null }
            } else {
                _ttsReady.value = false
                _error.value = "系统语音合成不可用（可在系统设置装中文语音包）"
            }
        }
    }

    /** 是否正在说话 */
    fun isSpeaking(): Boolean = _asrState.value == AsrState.SPEAKING

    /** 说话；未就绪则排队 */
    fun speak(text: String) {
        if (text.isBlank()) return
        stopListening()
        val t = tts
        if (t == null || !_ttsReady.value) {
            pendingText = text
            return
        }
        _asrState.value = AsrState.SPEAKING
        t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "mibot-${System.nanoTime()}")
    }

    /** 立刻打断（点麦克风时用） */
    fun interrupt() {
        runCatching { tts?.stop() }
        _level.value = 0f
        if (_asrState.value == AsrState.SPEAKING) _asrState.value = AsrState.IDLE
    }

    fun shutdownTts() {
        runCatching { tts?.shutdown() }
        tts = null
        _ttsReady.value = false
    }

    // ---------- ASR ----------
    private var recognizer: SpeechRecognizer? = null
    private var pendingResume = false

    var asrAvailable: Boolean = false
        private set

    fun initAsr() {
        asrAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!asrAvailable) {
            _error.value = "此设备没有可用的语音识别引擎"
            return
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _asrState.value = AsrState.LISTENING
            _error.value = null
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            // -2..10 常见；归一化到 0..1
            _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _level.value = 0f
            _asrState.value = AsrState.THINKING
        }

        override fun onError(error: Int) {
            _level.value = 0f
            _asrState.value = AsrState.IDLE
            _error.value = asrErrorMessage(error)
            if (continuousMode && error == SpeechRecognizer.ERROR_NO_MATCH) pendingResume = true
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            _partial.value = ""
            _level.value = 0f
            if (text.isNotBlank()) {
                _heard.value = text
                _asrState.value = AsrState.IDLE
            } else {
                _asrState.value = AsrState.IDLE
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.let { _partial.value = it }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** 开始听（说话时会先打断） */
    fun listen() {
        if (!asrAvailable) initAsr()
        if (!asrAvailable) return
        if (needsPermission()) {
            _error.value = "需要麦克风权限"
            return
        }
        interrupt()
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
        }
        _asrState.value = AsrState.LISTENING
        runCatching { r.startListening(intent) }.onFailure {
            _asrState.value = AsrState.IDLE
            _error.value = "启动识别失败：${it.message}"
        }
    }

    fun stopListening() {
        if (_asrState.value == AsrState.LISTENING) {
            runCatching { recognizer?.stopListening() }
        }
    }

    /** 消费识别结果 */
    fun consumeHeard(): String? {
        val v = _heard.value
        _heard.value = null
        return v
    }

    fun clearError() { _error.value = null }

    /** TTS 说完后是否该自动回到聆听（由 UI 循环调用） */
    fun shouldResumeListening(): Boolean = pendingResume.also { pendingResume = false }

    fun shutdown() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
        shutdownTts()
    }

    private fun asrErrorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "录音出错"
        SpeechRecognizer.ERROR_CLIENT -> "识别客户端错误"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
        SpeechRecognizer.ERROR_NETWORK -> "识别网络错误"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "识别超时"
        SpeechRecognizer.ERROR_NO_MATCH -> "没听清，再说一次"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
        SpeechRecognizer.ERROR_SERVER -> "识别服务器错误"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听到声音"
        else -> "识别失败($code)"
    }
}
