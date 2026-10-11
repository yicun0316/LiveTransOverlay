package com.livetrans.overlay.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.livetrans.overlay.data.AudioSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 稳健音频采集管理器 (Robust Audio Capture Manager)
 * 彻底解决 Android 各种机型声音监听不到、AudioRecord 状态未初始化、死循环及采样率冲突问题：
 * 1. 麦克风优先使用 VOICE_RECOGNITION 语音识别调优源，智能降级至 MIC / DEFAULT，防止外放音频被系统消噪门限完全过滤。
 * 2. 具备 16kHz -> 44.1kHz -> 48kHz 多采样率自适应回退与极速线性重采样算法，保障输出统一 16kHz PCM 给 ASR 模型。
 * 3. 规避 read <= 0 错误码导致的 100% CPU 紧密死循环，完善异常重试与释放逻辑。
 * 4. 大整数防溢出高灵敏度分贝 RMS 实时计算。
 */
class AudioCaptureManager(
    private val context: Context,
    private val onAudioChunkReady: (ByteArray, Int) -> Unit = { _, _ -> },
    private val onAudioDbLevel: (Float) -> Unit = {}
) {

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    private var actualSampleRate = 16000

    @SuppressLint("MissingPermission")
    suspend fun startCapture(
        sourceType: AudioSourceType,
        mediaProjection: MediaProjection? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopCapture()

            val initResult = when (sourceType) {
                AudioSourceType.MIC -> {
                    createMicAudioRecord()
                }
                AudioSourceType.SYSTEM_INTERNAL -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                        createSystemAudioRecord(mediaProjection)
                    } else {
                        Log.w(TAG, "系统内录需要 Android 10+ 且用户授权 MediaProjection，自动降级至麦克风拾音")
                        createMicAudioRecord()
                    }
                }
            }

            if (initResult == null) {
                val err = "所有音频录制通道初始化均失败 (请检查系统录音权限或是否有其他应用独占麦克风)"
                Log.e(TAG, err)
                return@withContext Result.failure(IOException(err))
            }

            audioRecord = initResult.first
            actualSampleRate = initResult.second

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext Result.failure(IOException("AudioRecord 初始化状态异常: STATE_UNINITIALIZED"))
            }

            audioRecord?.startRecording()
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                return@withContext Result.failure(IOException("AudioRecord 启动录音失败: recordingState=${audioRecord?.recordingState}"))
            }

            isRecording = true
            Log.d(TAG, "Audio capture started successfully. Source: $sourceType, actualRate: $actualSampleRate")

            val rawBuffer = ByteArray(2048)
            var consecutiveErrors = 0

            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(rawBuffer, 0, rawBuffer.size) ?: 0
                if (bytesRead > 0) {
                    consecutiveErrors = 0

                    // 若实际硬件采样率非 16000Hz，实时线性重采样到 16000Hz 供 ASR / VAD 模型使用
                    val (chunk16k, len16k) = if (actualSampleRate != 16000) {
                        resampleTo16k(rawBuffer, bytesRead, actualSampleRate)
                    } else {
                        Pair(rawBuffer, bytesRead)
                    }

                    if (len16k > 0) {
                        onAudioChunkReady(chunk16k, len16k)

                        // 计算高灵敏度 RMS 能量与分贝 (dB)
                        var sum = 0.0
                        val samples = len16k / 2
                        for (i in 0 until len16k step 2) {
                            val sample = (chunk16k[i].toInt() and 0xFF) or (chunk16k[i + 1].toInt() shl 8)
                            val sampleShort = sample.toShort()
                            val sLong = sampleShort.toLong()
                            sum += sLong * sLong
                        }
                        val rms = sqrt(sum / samples.coerceAtLeast(1))
                        val db = if (rms > 1.0) (20.0 * log10(rms)).toFloat().coerceIn(0f, 100f) else 0f
                        onAudioDbLevel(db)
                    }
                } else {
                    // bytesRead <= 0: 捕捉 Android 系统录音错误码，避免 100% CPU 紧密死循环
                    consecutiveErrors++
                    Log.w(TAG, "AudioRecord read returned error/zero: $bytesRead (consecutive: $consecutiveErrors)")
                    if (consecutiveErrors >= 8) {
                        Log.e(TAG, "AudioRecord 连续读取失败超过阈值，退出录音循环")
                        break
                    }
                    delay(30)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture exception", e)
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createMicAudioRecord(): Pair<AudioRecord, Int>? {
        // 音频输入源优先级：VOICE_RECOGNITION (ASR专属，不屏蔽外放) -> MIC -> DEFAULT
        val candidateSources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
        // 采样率候选列表：优先 16kHz (无需重采样)，备选 44.1kHz / 48kHz (普遍兼容)
        val candidateSampleRates = intArrayOf(16000, 44100, 48000)

        for (source in candidateSources) {
            for (rate in candidateSampleRates) {
                try {
                    val minBuf = AudioRecord.getMinBufferSize(
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    if (minBuf <= 0) continue

                    val bufSize = maxOf(minBuf * 2, rate / 2) // ~0.25-0.5s 缓冲
                    val record = AudioRecord(
                        source,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufSize
                    )

                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        Log.d(TAG, "AudioRecord initialized: source=$source, rate=$rate, bufSize=$bufSize")
                        return Pair(record, rate)
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord init failed for source=$source rate=$rate: ${e.message}")
                }
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun createSystemAudioRecord(mediaProjection: MediaProjection): Pair<AudioRecord, Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return createMicAudioRecord()
        }

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val candidateRates = intArrayOf(16000, 44100, 48000)
            for (rate in candidateRates) {
                val minBuf = AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) continue

                val audioFormatConfig = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()

                val record = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(audioFormatConfig)
                    .setBufferSizeInBytes(maxOf(minBuf * 2, rate / 2))
                    .build()

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "System AudioRecord initialized at rate=$rate")
                    return Pair(record, rate)
                } else {
                    record.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create System AudioRecord: ${e.message}")
        }
        return createMicAudioRecord()
    }

    /**
     * 将 44.1kHz 或 48kHz PCM 数据线性重采样为 16kHz
     */
    private fun resampleTo16k(input: ByteArray, inputBytes: Int, srcRate: Int): Pair<ByteArray, Int> {
        if (srcRate == 16000) return Pair(input, inputBytes)
        val inputSamples = inputBytes / 2
        val outputSamples = (inputSamples.toLong() * 16000L / srcRate).toInt()
        if (outputSamples <= 0) return Pair(ByteArray(0), 0)
        val outputBytes = outputSamples * 2
        val output = ByteArray(outputBytes)
        val ratio = srcRate.toDouble() / 16000.0

        for (i in 0 until outputSamples) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt().coerceIn(0, inputSamples - 1)
            val srcIndexNext = (srcIndex + 1).coerceIn(0, inputSamples - 1)
            val fraction = srcPos - srcIndex

            val s1 = ((input[srcIndex * 2].toInt() and 0xFF) or (input[srcIndex * 2 + 1].toInt() shl 8)).toShort()
            val s2 = ((input[srcIndexNext * 2].toInt() and 0xFF) or (input[srcIndexNext * 2 + 1].toInt() shl 8)).toShort()
            val interpolated = (s1 + fraction * (s2 - s1)).toInt().coerceIn(-32768, 32767).toShort()

            output[i * 2] = (interpolated.toInt() and 0xFF).toByte()
            output[i * 2 + 1] = ((interpolated.toInt() shr 8) and 0xFF).toByte()
        }
        return Pair(output, outputBytes)
    }

    fun stopCapture() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    companion object {
        private const val TAG = "AudioCaptureManager"
    }
}
