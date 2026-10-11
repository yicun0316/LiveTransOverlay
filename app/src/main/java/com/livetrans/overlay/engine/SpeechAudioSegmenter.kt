package com.livetrans.overlay.engine

import java.io.ByteArrayOutputStream
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 实时动态自适应语音端点检测与分段器 (Adaptive Voice Activity Detection & Segmenter)
 * 从 16kHz 16-bit Mono PCM 音频流中动态识别说话起止，自动切分为用于 ASR 语音识别的 WAV 句子音频块。
 * 针对手机声音外放、轻声说话、远场音频进行灵敏度算法升级：
 * 1. 动态自适应底噪追踪 (Ambient Noise Tracking)，避免死板的固定分贝阈值。
 * 2. 环形前置缓冲 (~250ms) 彻底防止吞掉辅音字头。
 * 3. 缩短最小有效语音切片门限至 250ms，单字/短词/口语短句不漏检。
 */
class SpeechAudioSegmenter(
    private val silenceTimeoutMs: Long = 700L,
    private val maxSpeechDurationMs: Long = 4000L,
    private val minSpeechDurationMs: Long = 250L,
    private val onSpeechSegmentReady: (ByteArray) -> Unit
) {
    private var ambientNoiseDb: Float = 16.0f
    private val preSpeechRingBuffer = java.util.ArrayDeque<ByteArray>(8)
    private val activeSpeechStream = ByteArrayOutputStream()
    private var isSpeechActive = false
    private var speechStartTimeMs = 0L
    private var lastSpeechTimeMs = 0L

    @Synchronized
    fun processAudioChunk(buffer: ByteArray, bytesRead: Int) {
        if (bytesRead <= 0) return

        val chunk = buffer.copyOf(bytesRead)

        // 计算分块 RMS 能量与分贝 (dB)
        var sum = 0.0
        val samples = bytesRead / 2
        for (i in 0 until bytesRead step 2) {
            val sample = (chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            val sLong = sampleShort.toLong()
            sum += sLong * sLong
        }
        val rms = sqrt(sum / samples.coerceAtLeast(1))
        val db = if (rms > 1.0) (20.0 * log10(rms)).toFloat().coerceIn(0f, 100f) else 0f

        val now = System.currentTimeMillis()

        // 动态自适应判定：环境底噪 + 3.5dB，或绝对能量 >= 18.0dB
        val speechThreshold = maxOf(ambientNoiseDb + 3.5f, 18.0f)

        if (db >= speechThreshold) {
            // 声音超过阈值：检测到说话
            if (!isSpeechActive) {
                isSpeechActive = true
                speechStartTimeMs = now
                activeSpeechStream.reset()
                // 拼接语音前置缓冲，防止吞掉字头音节 (~250ms)
                for (prev in preSpeechRingBuffer) {
                    activeSpeechStream.write(prev)
                }
                preSpeechRingBuffer.clear()
            }
            lastSpeechTimeMs = now
            activeSpeechStream.write(chunk)
        } else {
            // 低于阈值：在非大音量时慢速跟踪环境底噪
            if (db < ambientNoiseDb + 5f) {
                ambientNoiseDb = (ambientNoiseDb * 0.96f + db * 0.04f).coerceIn(8f, 32f)
            }

            if (isSpeechActive) {
                activeSpeechStream.write(chunk)
                val silenceElapsed = now - lastSpeechTimeMs
                if (silenceElapsed >= silenceTimeoutMs) {
                    // 静音超时：当前语句说完，生成切片
                    finalizeSegment()
                }
            } else {
                // 未处于说话状态：滚动保存前置缓冲 (~250ms)
                if (preSpeechRingBuffer.size >= 6) {
                    preSpeechRingBuffer.pollFirst()
                }
                preSpeechRingBuffer.addLast(chunk)
            }
        }

        // 持续说话超过最大切片时长：强制切片发送，保证实时同传低延迟
        if (isSpeechActive && (now - speechStartTimeMs) >= maxSpeechDurationMs) {
            finalizeSegment()
        }
    }

    @Synchronized
    fun finalizeSegment() {
        if (!isSpeechActive && activeSpeechStream.size() == 0) return
        isSpeechActive = false

        val pcmData = activeSpeechStream.toByteArray()
        activeSpeechStream.reset()

        // 16000 采样率 * 2 字节/采样 = 32000 字节/秒
        val durationMs = (pcmData.size.toDouble() / 32000.0) * 1000.0
        if (durationMs >= minSpeechDurationMs) {
            val wavBytes = pcmToWav(pcmData)
            onSpeechSegmentReady(wavBytes)
        }
    }

    @Synchronized
    fun reset() {
        isSpeechActive = false
        activeSpeechStream.reset()
        preSpeechRingBuffer.clear()
        ambientNoiseDb = 16.0f
    }

    companion object {
        fun pcmToWav(
            pcmData: ByteArray,
            sampleRate: Int = 16000,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ): ByteArray {
            val totalAudioLen = pcmData.size.toLong()
            val totalDataLen = totalAudioLen + 36
            val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xffL).toByte()
            header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
            header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
            header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0 // PCM format
            header[22] = channels.toByte(); header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xffL).toByte()
            header[29] = ((byteRate shr 8) and 0xffL).toByte()
            header[30] = ((byteRate shr 16) and 0xffL).toByte()
            header[31] = ((byteRate shr 24) and 0xffL).toByte()
            header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
            header[34] = bitsPerSample.toByte(); header[35] = 0
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xffL).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

            val wavOut = ByteArray(44 + pcmData.size)
            System.arraycopy(header, 0, wavOut, 0, 44)
            System.arraycopy(pcmData, 0, wavOut, 44, pcmData.size)
            return wavOut
        }
    }
}
