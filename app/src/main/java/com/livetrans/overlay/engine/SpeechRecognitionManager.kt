package com.livetrans.overlay.engine

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import com.livetrans.overlay.data.AppPreferences
import com.livetrans.overlay.data.AsrEngineType
import com.livetrans.overlay.data.AudioSourceType
import com.livetrans.overlay.data.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 语音识别中枢调度管理器 (Unified Speech Recognition Manager)
 * 彻底解决多组件冲突与系统内录无法识别的根本问题：
 * 1. 系统内录模式 (SYSTEM_INTERNAL)：通过 AudioCaptureManager 获取 PCM 流，经高灵敏 VAD 切分后直通极速 ASR。
 * 2. 麦克风模式 (MIC) - 云端模式：AudioCaptureManager 拾音 + 自适应动态 VAD 切片 + SenseVoice/Whisper 毫秒级高精度识别。
 * 3. 麦克风模式 (MIC) - 系统模式：独占麦克风运行 AndroidSystemSpeechEngine，避免 AudioRecord 资源冲突，直接通过 onRmsChanged 驱动分贝动画。
 * 4. 具备 Mutex 互斥锁与 Job 明确生命周期管理，杜绝多任务并发启动造成的底层 AudioRecord 资源锁死。
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
    @Volatile
    private var isRunning = false
    private var isCloudAsrActive = false

    private val pipelineMutex = Mutex()
    private var captureJob: Job? = null

    fun start(
        language: Language,
        audioSource: AudioSourceType,
        mediaProjection: MediaProjection? = null
    ) {
        scope.launch(Dispatchers.Main) {
            pipelineMutex.withLock {
                stopInternal()
                currentLanguage = language
                isRunning = true

                val apiKey = if (appPreferences.asrApiKey.isNotBlank()) {
                    appPreferences.asrApiKey.trim()
                } else {
                    appPreferences.llmApiKey.trim()
                }
                val asrType = appPreferences.loadAsrEngineType()

                // 判断是否使用云端 ASR (SenseVoice/Whisper)
                // 1. 系统内录必须使用 AudioCaptureManager 采流 + 流式 ASR
                // 2. 有 API Key 时，运行 SenseVoice / Whisper 毫秒级极速同传
                // 3. 麦克风且无 Key 时：若系统语音服务可用，自动无缝降级兜底至系统 STT，避免监听无声
                if (audioSource == AudioSourceType.SYSTEM_INTERNAL) {
                    isCloudAsrActive = true
                    startStreamingPipeline(audioSource, mediaProjection)
                } else if (asrType == AsrEngineType.AUTO_CLOUD) {
                    if (apiKey.isNotEmpty()) {
                        isCloudAsrActive = true
                        startStreamingPipeline(audioSource, mediaProjection)
                    } else if (AndroidSystemSpeechEngine.isAvailable(context)) {
                        isCloudAsrActive = false
                        onStatusHintChanged("🎤 运行系统内置识别 (主界面填入 Key 可升级 SenseVoice)")
                        startSystemSpeechPipeline(language)
                    } else {
                        isCloudAsrActive = true
                        startStreamingPipeline(audioSource, mediaProjection)
                    }
                } else {
                    isCloudAsrActive = false
                    startSystemSpeechPipeline(language)
                }
            }
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

        // 1. 初始化自适应高灵敏 VAD 分段器
        audioSegmenter = SpeechAudioSegmenter(
            silenceTimeoutMs = 700L,
            maxSpeechDurationMs = 4000L,
            minSpeechDurationMs = 250L,
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

        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            audioCaptureManager?.startCapture(audioSource, mediaProjection)
        }
    }

    private fun startSystemSpeechPipeline(language: Language) {
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

    private fun stopInternal() {
        isRunning = false
        captureJob?.cancel()
        captureJob = null

        audioSegmenter?.reset()
        audioSegmenter = null

        audioCaptureManager?.stopCapture()
        audioCaptureManager = null

        systemSpeechEngine.stopListening()
    }

    fun stop() {
        scope.launch(Dispatchers.Main) {
            pipelineMutex.withLock {
                stopInternal()
            }
        }
    }

    fun destroy() {
        stop()
        systemSpeechEngine.destroy()
    }

    companion object {
        private const val TAG = "SpeechRecognitionMgr"
    }
}
