package com.mibot.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 把 16 位单声道 PCM 包成标准 WAV(44 字节头 + 小端数据),给服务商的转文字接口上传用。 */
object Wav {
    const val RATE = 16_000
    private const val HEADER = 44

    fun encode(pcm16: ShortArray, rate: Int = RATE): ByteArray {
        val dataSize = pcm16.size * 2
        val buf = ByteBuffer.allocate(HEADER + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)              // fmt 块长度
        buf.putShort(1)             // PCM
        buf.putShort(1)             // 单声道
        buf.putInt(rate)
        buf.putInt(rate * 2)        // 每秒字节数 = 采样率 × 声道 × 2 字节
        buf.putShort(2)             // 块对齐
        buf.putShort(16)            // 位深
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        for (s in pcm16) buf.putShort(s)
        return buf.array()
    }

    /** 一段全零的静音 WAV,用来试探某个模型能不能转文字(不需要真录音)。 */
    fun silence(ms: Int, rate: Int = RATE): ByteArray = encode(ShortArray(rate * ms / 1000), rate)

    /** 解析 WAV(16 位单声道)取回 PCM 采样;本地 SenseVoice 转写用。 */
    fun decode16(wav: ByteArray): ShortArray {
        require(wav.size > HEADER) { "wav too small" }
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.get(0).toChar() == 'R' && buf.get(1).toChar() == 'I' && buf.get(2).toChar() == 'F' && buf.get(3).toChar() == 'F') { "not wav" }
        // 找 data 块
        var pos = 12
        while (pos + 8 <= wav.size) {
            val id = String(buf.array(), pos, 4, Charsets.US_ASCII)
            val size = buf.getInt(pos + 4)
            if (id == "data") {
                val dataPos = pos + 8
                val samples = (wav.size - dataPos) / 2
                val out = ShortArray(samples)
                for (i in 0 until samples) out[i] = buf.getShort(dataPos + i * 2)
                return out
            }
            pos += 8 + size + (size and 1)
        }
        throw IllegalArgumentException("no data chunk")
    }
}
