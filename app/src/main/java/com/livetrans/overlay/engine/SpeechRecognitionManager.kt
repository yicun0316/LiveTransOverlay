package com.livetrans.overlay.engine

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import com.livetrans.overlay.data.AppPreferences
import com.livetrans.overlay.data.AudioSourceType
import com.livetrans.overlay.data.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 语音识别中枢调度管理器 (Unified Speech Recognition Manager)
 * 彻底解决多组件冲突与系统内录无法识别的根本问题：
 * 1. 系统内录模式 (SYSTEM_INTERNAL)：通过 AudioCaptureManager 获取 PCM 流，经 VAD 切分后直通极速 ASR。
 * 2. 麦克风模式 (MIC) - 云端模式：AudioCaptureManager 拾音 + VAD 切片 + SenseVoice/Whisper 毫秒级高精度识别。
 * 3. 麦克风模式 (MIC) - 系统模式：独占麦克风运行 AndroidSystemSpeechEngine，避免 AudioRecord 资源冲突，直接通过 onRmsChanged 驱动分贝动画。
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val scope: CoroutineScope,
    private val onSpeechTextRecognized: (String) -> Unit,
    private val onAudioDbLevel: (Float) -> Unit,
    private val onStatusHintChanged: (String) -> Unit
) {
    private var audioCaptureManager: AudioCaptureManager? = null
    private var audioSegmenter: SpeechAudioSegmenter? = null
    private val cloudAsrEngine = CloudAsrEngine(appPreferences)
    private val systemSpeechEngine = AndroidSystemSpeechEngine(context)

    private var currentLanguage = Language.ENGLISH
    private var isRunning = false
    private var isCloudAsrActive = false

    fun start(
        language: Language,
        audioSource: AudioSourceType,
        mediaProjection: MediaProjection? = null
    ) {
        stop()
        this.currentLanguage = language
        this.isRunning = true

        val apiKey = if (appPreferences.asrApiKey.isNotBlank()) {
            appPreferences.asrApiKey.trim()
        } else {
            appPreferences.llmApiKey.trim()
        }
        val asrType = appPreferences.loadAsrEngineType()

        // 当存在内录需求、或选中云端引擎、或用户填写了 Key 时，运行流式 ASR (SenseVoice/Whisper)
        isCloudAsrActive = (audioSource == AudioSourceType.SYSTEM_INTERNAL) || (asrType == com.livetrans.overlay.data.AsrEngineType.AUTO_CLOUD) || apiKey.isNotEmpty()

        if (isCloudAsrActive) {
            startStreamingPipeline(audioSource, mediaProjection)
        } else {
            startSystemSpeechPipeline(language)
        }
    }

    private fun startStreamingPipeline(audioSource: AudioSourceType, mediaProjection: MediaProjection?) {
        val apiKey = if (appPreferences.asrApiKey.isNotBlank()) appPreferences.asrApiKey.trim() else appPreferences.llmApiKey.trim()
        val asrUrl = if (appPreferences.asrBaseUrl.isNotBlank()) appPreferences.asrBaseUrl else appPreferences.llmBaseUrl
        val asrHint = if (asrUrl.contains("siliconflow")) "SenseVoice 极速同传" else "Whisper 识别"

        if (apiKey.isEmpty()) {
            onStatusHintChanged("⚠️ 请在主页设置填入 API Key 启用【$asrHint】(注册即送额度)")
        } else {
            val srcDesc = if (audioSource == AudioSourceType.SYSTEM_INTERNAL) "系统内录声音" else "麦克风拾音"
            onStatusHintChanged("🎤 正在监听 $srcDesc · $asrHint")
        }

        // 1. 初始化实时 VAD 分段器
        audioSegmenter = SpeechAudioSegmenter(
            silenceThresholdDb = 35f,
            silenceTimeoutMs = 600L,
            maxSpeechDurationMs = 3800L,
            minSpeechDurationMs = 400L,
            onSpeechSegmentReady = { wavBytes ->
                if (!isRunning) return@SpeechAudioSegmenter
                scope.launch(Dispatchers.IO) {
                    onStatusHintChanged("⚡ 正在转写并翻译语音…")
                    val res = cloudAsrEngine.transcribe(wavBytes, currentLanguage)
                    if (res.isSuccess) {
                        val text = res.getOrNull()?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            onSpeechTextRecognized(text)
                        }
                    } else {
                        val err = res.exceptionOrNull()?.message ?: ""
                        Log.w(TAG, "Cloud ASR error: $err")
                        onStatusHintChanged("🎤 语音流监听中 · $err")
                    }
                }
            }
        )

        // 2. 启动音频采集（内录或麦克风独占）
        audioCaptureManager = AudioCaptureManager(
            context = context,
            onAudioChunkReady = { buffer, bytesRead ->
                if (isRunning) {
                    audioSegmenter?.processAudioChunk(buffer, bytesRead)
                }
            },
            onAudioDbLevel = { db ->
                if (isRunning) {
                    onAudioDbLevel(db)
                }
            }
        )

        scope.launch(Dispatchers.IO) {
            audioCaptureManager?.startCapture(audioSource, mediaProjection)
        }
    }

    private fun startSystemSpeechPipeline(language: Language) {
        onStatusHintChanged("🎤 系统语音识别运行中 · 独占麦克风")

        systemSpeechEngine.onRmsLevelCallback = { db ->
            if (isRunning) {
                onAudioDbLevel(db)
            }
        }

        systemSpeechEngine.startListening(
            language = language,
            onPartialResult = { text ->
                if (isRunning && text.isNotBlank()) {
                    onSpeechTextRecognized(text)
                }
            },
            onFinalResult = { text ->
                if (isRunning && text.isNotBlank()) {
                    onSpeechTextRecognized(text)
                }
            },
            onError = { errMsg ->
                if (isRunning) {
                    onStatusHintChanged("🎤 提示: $errMsg (推荐在主界面填入 API Key 开启 SenseVoice 极速同传)")
                }
            }
        )
    }

    fun stop() {
        isRunning = false
        audioSegmenter?.reset()
        audioSegmenter = null

        audioCaptureManager?.stopCapture()
        audioCaptureManager = null

        systemSpeechEngine.stopListening()
    }

    fun destroy() {
        stop()
        systemSpeechEngine.destroy()
    }

    companion object {
        private const val TAG = "SpeechRecognitionMgr"
    }
}
