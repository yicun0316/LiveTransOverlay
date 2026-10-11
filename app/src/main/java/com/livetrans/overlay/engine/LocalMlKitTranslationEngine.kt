package com.livetrans.overlay.engine

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.livetrans.overlay.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 100% Free & Offline On-Device Translation Engine powered by Google ML Kit.
 * - Zero API key required.
 * - Zero cost, unlimited characters.
 * - Downloads ~30MB neural model per language once, runs completely offline afterwards.
 * - Ultra-low inference latency (~20ms to 60ms).
 */
class LocalMlKitTranslationEngine : ITranslationEngine {

    override val name: String = "Google ML Kit (本地离线·免费)"
    override val isLocal: Boolean = true

    private val translatorCache = ConcurrentHashMap<String, Translator>()

    private fun getCacheKey(sourceLang: Language, targetLang: Language): String {
        return "${sourceLang.shortCode}_${targetLang.shortCode}"
    }

    private fun getOrCreateTranslator(sourceLang: Language, targetLang: Language): Translator {
        val key = getCacheKey(sourceLang, targetLang)
        return translatorCache.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang.mlKitCode)
                .setTargetLanguage(targetLang.mlKitCode)
                .build()
            Translation.getClient(options)
        }
    }

    override suspend fun prepareModel(
        sourceLang: Language,
        targetLang: Language
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val translator = getOrCreateTranslator(sourceLang, targetLang)
            // Allow download on mobile data or wifi so user can start immediately
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            Log.d(TAG, "ML Kit model ready for ${sourceLang.shortCode} -> ${targetLang.shortCode}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download/prepare ML Kit model", e)
            Result.failure(e)
        }
    }

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.success("")
        }

        if (sourceLang == targetLang) {
            return@withContext Result.success(trimmed)
        }

        try {
            val translator = getOrCreateTranslator(sourceLang, targetLang)
            // Ensure model is downloaded
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()

            val translatedText = translator.translate(trimmed).await()
            Result.success(translatedText)
        } catch (e: Exception) {
            Log.e(TAG, "Error translating text with ML Kit: $trimmed", e)
            Result.failure(e)
        }
    }

    override fun close() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
    }

    companion object {
        private const val TAG = "LocalMlKitEngine"
    }
}
