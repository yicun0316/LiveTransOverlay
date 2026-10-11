package com.livetrans.overlay.engine

import com.livetrans.overlay.data.Language

interface ITranslationEngine {
    val name: String
    val isLocal: Boolean

    /**
     * Translates a given text snippet from source language to target language.
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String>

    /**
     * Prepares/downloads required local models if needed.
     */
    suspend fun prepareModel(
        sourceLang: Language,
        targetLang: Language
    ): Result<Unit>

    /**
     * Releases any background resources or models.
     */
    fun close()
}
