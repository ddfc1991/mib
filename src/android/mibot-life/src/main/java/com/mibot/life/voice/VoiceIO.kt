package com.mibot.life.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 语音：先接系统能力（零依赖），后续可换 sherpa-onnx 离线模型。
 *
 * - 说话：TextToSpeech（中文音色，可调语速/音调）
 * - 听话：SpeechRecognizer（连续识别，部分结果实时回调）
 */
class VoiceIO(private val context: Context) {

    // ---- TTS ----
    private var tts: TextToSpeech? = null
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    fun initTts(onReady: (Boolean) -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                tts?.language = Locale.CHINA
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.05f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { _speaking.value = true }
                    override fun onDone(utteranceId: String?) { _speaking.value = false }
                    @Deprecated("legacy") override fun onError(utteranceId: String?) { _speaking.value = false }
                })
            }
            onReady(ok)
        }
    }

    /** 让它说话 */
    fun speak(text: String, utteranceId: String = "mibot-${System.nanoTime()}") {
        if (text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
        _speaking.value = false
    }

    fun shutdownTts() {
        tts?.shutdown()
        tts = null
    }

    // ---- ASR ----
    private var recognizer: SpeechRecognizer? = null
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial
    private val _heard = MutableStateFlow<String?>(null)
    val heard: StateFlow<String?> = _heard

    var available: Boolean = false
        private set

    fun initAsr(onFinal: (String) -> Unit = {}) {
        available = SpeechRecognizer.isRecognitionAvailable(context)
        if (!available) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { _partial.value = "" }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    _partial.value = ""
                    if (text.isNotBlank()) {
                        _heard.value = text
                        onFinal(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { _partial.value = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    /** 开始听（说完自动结束，再调一次可继续） */
    fun listen() {
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { r.startListening(intent) }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun shutdown() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
        shutdownTts()
    }
}
