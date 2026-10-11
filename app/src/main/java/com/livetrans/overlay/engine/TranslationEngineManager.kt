package com.livetrans.overlay.engine

import android.util.Log
import com.livetrans.overlay.data.AppPreferences
import com.livetrans.overlay.data.Language
import com.livetrans.overlay.data.TranslationEngineType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TranslationEngineManager(
    private val appPreferences: AppPreferences
) {
    val cloudEngine = CloudTranslationEngine(appPreferences)
    val freeWebEngine = FreeWebTranslationEngine()
    val localEngine = LocalMlKitTranslationEngine()
    val embeddedEngine = EmbeddedOfflineTranslationEngine()
    private val mutex = Mutex()

    suspend fun prepareCurrentEngine(sourceLang: Language, targetLang: Language): Result<Unit> {
        val type = appPreferences.loadEngineType()
        return when (type) {
            TranslationEngineType.EMBEDDED_OFFLINE -> embeddedEngine.prepareModel(sourceLang, targetLang)
            TranslationEngineType.LOCAL_ML_KIT -> localEngine.prepareModel(sourceLang, targetLang)
            TranslationEngineType.FREE_WEB -> freeWebEngine.prepareModel(sourceLang, targetLang)
            TranslationEngineType.CLOUD_LLM -> cloudEngine.prepareModel(sourceLang, targetLang)
            TranslationEngineType.CLOUD_DEEPL -> cloudEngine.prepareModel(sourceLang, targetLang)
        }
    }

    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> = mutex.withLock {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.success("")
        if (sourceLang == targetLang) return Result.success(trimmed)

        val type = appPreferences.loadEngineType()

        // 1. Built-in 100% Offline Engine (Zero network, Zero external model download)
        if (type == TranslationEngineType.EMBEDDED_OFFLINE) {
            return embeddedEngine.translate(trimmed, sourceLang, targetLang)
        }

        // 2. Google ML Kit On-Device Neural Engine
        if (type == TranslationEngineType.LOCAL_ML_KIT) {
            val localResult = localEngine.translate(trimmed, sourceLang, targetLang)
            if (localResult.isSuccess && !localResult.getOrNull().isNullOrBlank()) {
                return localResult
            }
            Log.w(TAG, "ML Kit model not downloaded or unavailable, smoothly falling back to Embedded Offline Engine")
            return embeddedEngine.translate(trimmed, sourceLang, targetLang)
        }

        // 3. Free Web Engine
        if (type == TranslationEngineType.FREE_WEB) {
            val webResult = freeWebEngine.translate(trimmed, sourceLang, targetLang)
            if (webResult.isSuccess && !webResult.getOrNull().isNullOrBlank()) {
                return webResult
            }
            Log.w(TAG, "Free Web translation unavailable, falling back to Embedded Offline Engine")
            return embeddedEngine.translate(trimmed, sourceLang, targetLang)
        }

        // 4. Cloud LLM / DeepSeek API
        if (type == TranslationEngineType.CLOUD_LLM) {
            val apiKey = appPreferences.llmApiKey.trim()
            if (apiKey.isEmpty()) {
                return Result.failure(Exception("请在下方【翻译引擎与 API 配置】卡片填写 API Key 并点击保存"))
            }
            val cloudResult = cloudEngine.translate(trimmed, sourceLang, targetLang)
            if (cloudResult.isSuccess && !cloudResult.getOrNull().isNullOrBlank()) {
                return cloudResult
            }
            return cloudResult
        }

        return embeddedEngine.translate(trimmed, sourceLang, targetLang)
    }

    fun close() {
        embeddedEngine.close()
        freeWebEngine.close()
        localEngine.close()
        cloudEngine.close()
    }

    companion object {
        private const val TAG = "TranslationEngineMgr"
    }
}
