package com.livetrans.overlay.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.livetrans.overlay.data.Language

/**
 * Android system native SpeechRecognizer implementation.
 * Supports English, Japanese, and Chinese real-time partial streaming.
 * Automatically loops and continues listening for live simultaneous translation.
 */
class AndroidSystemSpeechEngine(
    private val context: Context
) : ISpeechRecognitionEngine {

    override val name: String = "系统内置语音识别 (System STT)"

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var currentLanguage: Language = Language.ENGLISH

    private var onPartialResultCallback: ((String) -> Unit)? = null
    private var onFinalResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun startListening(
        language: Language,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        this.currentLanguage = language
        this.onPartialResultCallback = onPartialResult
        this.onFinalResultCallback = onFinalResult
        this.onErrorCallback = onError
        this.isListening = true

        mainHandler.post {
            initAndStartRecognizer()
        }
    }

    private fun initAndStartRecognizer() {
        if (!isListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorCallback?.invoke("当前设备系统未开启语音识别服务")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }

        val localeTag = when (currentLanguage) {
            Language.CHINESE -> "zh-CN"
            Language.ENGLISH -> "en-US"
            Language.JAPANESE -> "ja-JP"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started speech recognition for language: $localeTag")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            restartListeningDelayed(1000)
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Speech ended for the segment, auto restart to keep live stream going
                if (isListening) {
                    restartListeningDelayed(200)
                }
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "未匹配到语音"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音输入超时"
                    SpeechRecognizer.ERROR_NETWORK -> "网络连接异常"
                    SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                    else -> "识别状态码: $error"
                }
                Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")

                if (isListening) {
                    restartListeningDelayed(400)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) {
                    onFinalResultCallback?.invoke(text)
                }

                if (isListening) {
                    restartListeningDelayed(100)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) {
                    onPartialResultCallback?.invoke(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun restartListeningDelayed(delayMs: Long) {
        if (!isListening) return
        mainHandler.postDelayed({
            if (isListening) {
                initAndStartRecognizer()
            }
        }, delayMs)
    }

    override fun stopListening() {
        isListening = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer", e)
            }
        }
    }

    override fun destroy() {
        isListening = false
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying recognizer", e)
            }
        }
    }

    companion object {
        private const val TAG = "SystemSpeechEngine"
    }
}
