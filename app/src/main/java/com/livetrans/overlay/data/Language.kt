package com.livetrans.overlay.data

import com.google.mlkit.nl.translate.TranslateLanguage

enum class Language(
    val displayName: String,
    val shortCode: String,
    val mlKitCode: String,
    val deepLCode: String
) {
    CHINESE("简体中文", "ZH", TranslateLanguage.CHINESE, "ZH"),
    ENGLISH("英语", "EN", TranslateLanguage.ENGLISH, "EN"),
    JAPANESE("日语", "JA", TranslateLanguage.JAPANESE, "JA");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.shortCode.equals(code, ignoreCase = true) } ?: CHINESE
        }
    }
}
