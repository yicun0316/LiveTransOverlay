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
    private val freeWebEngine = FreeWebTranslationEngine()
    private val localEngine = LocalMlKitTranslationEngine()
    private val mutex = Mutex()

    suspend fun prepareCurrentEngine(sourceLang: Language, targetLang: Language): Result<Unit> {
        val type = appPreferences.loadEngineType()
        return when (type) {
            TranslationEngineType.CLOUD_LLM -> cloudEngine.prepareModel(sourceLang, targetLang)
            TranslationEngineType.FREE_WEB -> freeWebEngine.prepareModel(sourceLang, targetLang)
            TranslationEngineType.LOCAL_ML_KIT -> localEngine.prepareModel(sourceLang, targetLang)
            else -> cloudEngine.prepareModel(sourceLang, targetLang)
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

        // 1. Cloud LLM / DeepSeek
        if (type == TranslationEngineType.CLOUD_LLM) {
            val apiKey = appPreferences.llmApiKey.trim()
            if (apiKey.isEmpty()) {
                return Result.failure(Exception("请在下方填写 API Key 并点击【保存配置】"))
            }
            return cloudEngine.translate(trimmed, sourceLang, targetLang)
        }

        // 2. Free Web
        if (type == TranslationEngineType.FREE_WEB) {
            return freeWebEngine.translate(trimmed, sourceLang, targetLang)
        }

        // 3. Local ML Kit
        if (type == TranslationEngineType.LOCAL_ML_KIT) {
            val localResult = localEngine.translate(trimmed, sourceLang, targetLang)
            if (localResult.isSuccess && !localResult.getOrNull().isNullOrBlank()) {
                return localResult
            }
            Log.w(TAG, "ML Kit translation unavailable, falling back to Web")
            return freeWebEngine.translate(trimmed, sourceLang, targetLang)
        }

        return cloudEngine.translate(trimmed, sourceLang, targetLang)
    }

    fun close() {
        freeWebEngine.close()
        localEngine.close()
        cloudEngine.close()
    }

    companion object {
        private const val TAG = "TranslationEngineMgr"
    }
}
