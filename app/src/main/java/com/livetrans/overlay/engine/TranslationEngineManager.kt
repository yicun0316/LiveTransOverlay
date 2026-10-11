package com.livetrans.overlay.engine

import android.util.Log
import com.livetrans.overlay.data.AppPreferences
import com.livetrans.overlay.data.Language
import com.livetrans.overlay.data.TranslationEngineType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates translation engines, handles automatic fallback to free web translation,
 * and ensures 100% availability.
 */
class TranslationEngineManager(
    private val appPreferences: AppPreferences
) {
    private val freeWebEngine = FreeWebTranslationEngine()
    private val localEngine = LocalMlKitTranslationEngine()
    private val cloudEngine = CloudTranslationEngine(appPreferences)
    private val mutex = Mutex()

    suspend fun prepareCurrentEngine(sourceLang: Language, targetLang: Language): Result<Unit> {
        val type = appPreferences.loadEngineType()
        return when (type) {
            TranslationEngineType.FREE_WEB -> freeWebEngine.prepareModel(sourceLang, targetLang)
            TranslationEngineType.LOCAL_ML_KIT -> localEngine.prepareModel(sourceLang, targetLang)
            else -> cloudEngine.prepareModel(sourceLang, targetLang)
        }
    }

    /**
     * Translates input text using the preferred engine.
     * Automatically falls back to Free Web engine if current engine fails.
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> = mutex.withLock {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.success("")
        if (sourceLang == targetLang) return Result.success(trimmed)

        val type = appPreferences.loadEngineType()

        // 1. Direct Free Web Engine
        if (type == TranslationEngineType.FREE_WEB) {
            return freeWebEngine.translate(trimmed, sourceLang, targetLang)
        }

        // 2. Local ML Kit with Free Web Fallback
        if (type == TranslationEngineType.LOCAL_ML_KIT) {
            val localResult = localEngine.translate(trimmed, sourceLang, targetLang)
            if (localResult.isSuccess && !localResult.getOrNull().isNullOrBlank()) {
                return localResult
            }
            Log.w(TAG, "ML Kit translation unavailable (${localResult.exceptionOrNull()?.message}), falling back to Free Web Engine")
            return freeWebEngine.translate(trimmed, sourceLang, targetLang)
        }

        // 3. Cloud (DeepL / LLM) with Free Web Fallback
        val cloudResult = cloudEngine.translate(trimmed, sourceLang, targetLang)
        if (cloudResult.isSuccess && !cloudResult.getOrNull().isNullOrBlank()) {
            return cloudResult
        }

        Log.w(TAG, "Cloud translation failed (${cloudResult.exceptionOrNull()?.message}), falling back to Free Web Engine")
        return freeWebEngine.translate(trimmed, sourceLang, targetLang)
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
