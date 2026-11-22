package com.mibot.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.mibot.core.model.ModelError
import java.io.File

/**
 * 本地 SenseVoice 转写:用 sherpa-onnx 的 SenseVoice 模型在手机上离线转写,
 * 绕开系统识别、不用 API 密钥。
 *
 * 模型文件放在 `files/sensevoice/`(int8 量化):
 * - model.onnx      SenseVoice 主模型
 * - tokens.txt      词表
 *
 * [ready] 为真(模型文件齐全)才能转写;否则界面引导去下载模型。
 * 输入 16kHz 单声道 PCM,输出识别文字(中/英/日/韩/粤)。
 */
class SenseVoiceLocal(context: Context) {
    private val modelDir = File(context.filesDir, "sensevoice")
    private val modelOnnx = File(modelDir, "model.onnx")
    private val tokens = File(modelDir, "tokens.txt")

    /** 模型文件齐全才可转写。 */
    val ready: Boolean get() = modelOnnx.isFile && tokens.isFile && modelOnnx.length() > 1024 * 1024

    /** 模型目录(界面显示下载路径)。 */
    val dir: File get() = modelDir

    companion object {
        val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
                "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2"
        val MODEL_TAR = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2"
    }

    @Volatile private var engine: OfflineRecognizer? = null
    @Volatile private var loadFailed: String? = null

    @Synchronized
    private fun ensureLoaded(): OfflineRecognizer {
        engine?.let { return it }
        loadFailed?.let { throw ModelError.BadResponse(it) }
        return try {
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = modelOnnx.absolutePath,
                        language = "auto",
                        useInverseTextNormalization = true,
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                    modelType = "sense_voice",
                ),
            )
            OfflineRecognizer(config = config).also { engine = it }
        } catch (e: Throwable) {
            val msg = "SenseVoice 引擎加载失败: ${e.message ?: e.javaClass.simpleName}"
            loadFailed = msg
            throw ModelError.BadResponse(msg)
        }
    }

    /**
     * 把一段 16kHz 单声道 PCM 转成文字。
     * @param pcm 16-bit PCM 采样
     */
    fun transcribe(pcm: ShortArray, sampleRate: Int = 16000): String {
        if (!ready) {
            throw ModelError.BadResponse("SenseVoice 模型未就绪,请先下载到 ${modelDir.path}")
        }
        val rec = ensureLoaded()
        // 16-bit -> float [-1, 1]
        val samples = FloatArray(pcm.size) { pcm[it] / 32768.0f }
        val stream = rec.createStream()
        return try {
            stream.acceptWaveform(samples, sampleRate)
            rec.decode(stream)
            rec.getResult(stream).text.trim()
        } finally {
            runCatching { stream.release() }
        }
    }

    /** 释放引擎(切换引擎/退出时)。 */
    @Synchronized
    fun close() {
        runCatching { engine?.release() }
        engine = null
    }
}
