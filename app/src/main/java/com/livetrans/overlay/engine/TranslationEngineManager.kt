package com.livetrans.overlay.engine

import android.util.Log
import com.livetrans.overlay.data.AppPreferences
import com.livetrans.overlay.data.Language
import com.livetrans.overlay.data.TranslationEngineType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates translation engines, handles automatic fallback to local ML Kit,
 * and maintains performance stats.
 */
class TranslationEngineManager(
    private val appPreferences: AppPreferences
) {
    private val localEngine = LocalMlKitTranslationEngine()
    private val cloudEngine = CloudTranslationEngine(appPreferences)
    private val mutex = Mutex()

    suspend fun prepareCurrentEngine(sourceLang: Language, targetLang: Language): Result<Unit> {
        val type = appPreferences.loadEngineType()
        return if (type == TranslationEngineType.LOCAL_ML_KIT) {
            localEngine.prepareModel(sourceLang, targetLang)
        } else {
            cloudEngine.prepareModel(sourceLang, targetLang)
        }
    }

    /**
     * Translates input text using the preferred engine.
     * Automatically falls back to Local ML Kit if Cloud fails.
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> = mutex.withLock {
        val type = appPreferences.loadEngineType()

        if (type == TranslationEngineType.LOCAL_ML_KIT) {
            return localEngine.translate(text, sourceLang, targetLang)
        }

        // Try Cloud engine first
        val cloudResult = cloudEngine.translate(text, sourceLang, targetLang)
        if (cloudResult.isSuccess) {
            return cloudResult
        }

        // Cloud failed, fallback to local offline ML Kit
        Log.w(TAG, "Cloud translation failed: ${cloudResult.exceptionOrNull()?.message}, falling back to Local ML Kit")
        return localEngine.translate(text, sourceLang, targetLang)
    }

    fun close() {
        localEngine.close()
        cloudEngine.close()
    }

    companion object {
        private const val TAG = "TranslationEngineMgr"
    }
}
