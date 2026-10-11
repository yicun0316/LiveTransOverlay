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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manages raw audio recording:
 * 1. Microphone capture (external voice / face-to-face)
 * 2. System Audio Loopback capture (via AudioPlaybackCapture API on Android 10+ MediaProjection)
 */
class AudioCaptureManager(
    private val context: Context,
    private val onAudioChunkReady: (ByteArray, Int) -> Unit
) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    val sampleRate = 16000 // 16kHz standard for ASR models
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val minBufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        channelConfig,
        audioFormat
    ).coerceAtLeast(sampleRate * 2) // at least 1-2 sec buffer

    /**
     * Starts audio capture based on chosen source mode.
     */
    @SuppressLint("MissingPermission")
    suspend fun startCapture(
        sourceType: AudioSourceType,
        mediaProjection: MediaProjection? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopCapture()

            audioRecord = when (sourceType) {
                AudioSourceType.MIC -> {
                    createMicAudioRecord()
                }
                AudioSourceType.SYSTEM_INTERNAL -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                        createSystemAudioRecord(mediaProjection)
                    } else {
                        Log.w(TAG, "System audio capture requires Android 10+ and MediaProjection. Falling back to Mic.")
                        createMicAudioRecord()
                    }
                }
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext Result.failure(IOException("AudioRecord initialization failed."))
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "Audio recording started with source: $sourceType")

            val buffer = ByteArray(2048)
            while (isActive && isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (bytesRead > 0) {
                    onAudioChunkReady(buffer, bytesRead)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio capture", e)
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createMicAudioRecord(): AudioRecord {
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize
        )
    }

    @SuppressLint("MissingPermission")
    private fun createSystemAudioRecord(mediaProjection: MediaProjection): AudioRecord {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return createMicAudioRecord()
        }

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormatConfig = AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(audioFormatConfig)
            .setBufferSizeInBytes(minBufferSize)
            .build()
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
