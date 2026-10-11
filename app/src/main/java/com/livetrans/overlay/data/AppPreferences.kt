package com.livetrans.overlay.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioSourceType {
    MIC,             // Microphone capture
    SYSTEM_INTERNAL  // Internal audio capture
}

enum class TranslationEngineType(val displayName: String, val isLocalOffline: Boolean) {
    EMBEDDED_OFFLINE("内置离线引擎 (免下载·即装即用)", true),
    LOCAL_ML_KIT("Google ML Kit (端侧神经网络模型)", true),
    FREE_WEB("免费网页引擎 (免Key·即开即用)", false),
    CLOUD_LLM("大模型云端 API (DeepSeek/Gemini)", false),
    CLOUD_DEEPL("DeepL API", false)
}

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("livetrans_settings", Context.MODE_PRIVATE)

    private val _styleFlow = MutableStateFlow(loadSubtitleStyle())
    val styleFlow: StateFlow<SubtitleStyle> = _styleFlow.asStateFlow()

    private val _sourceLangFlow = MutableStateFlow(loadSourceLanguage())
    val sourceLangFlow: StateFlow<Language> = _sourceLangFlow.asStateFlow()

    private val _targetLangFlow = MutableStateFlow(loadTargetLanguage())
    val targetLangFlow: StateFlow<Language> = _targetLangFlow.asStateFlow()

    private val _audioSourceFlow = MutableStateFlow(loadAudioSource())
    val audioSourceFlow: StateFlow<AudioSourceType> = _audioSourceFlow.asStateFlow()

    private val _engineTypeFlow = MutableStateFlow(loadEngineType())
    val engineTypeFlow: StateFlow<TranslationEngineType> = _engineTypeFlow.asStateFlow()

    private val _isLightThemeFlow = MutableStateFlow(prefs.getBoolean("app_light_theme", true))
    val isLightThemeFlow: StateFlow<Boolean> = _isLightThemeFlow.asStateFlow()

    fun setAppLightTheme(isLight: Boolean) {
        prefs.edit().putBoolean("app_light_theme", isLight).apply()
        _isLightThemeFlow.value = isLight
    }

    fun loadSubtitleStyle(): SubtitleStyle {
        return SubtitleStyle(
            textColor = prefs.getInt("style_text_color", Color.BLACK),
            textSizeSp = prefs.getFloat("style_text_size", 18f),
            backgroundColor = prefs.getInt("style_bg_color", Color.WHITE),
            showOriginalText = prefs.getBoolean("style_show_original", true),
            originalTextColor = prefs.getInt("style_original_color", Color.parseColor("#64748B")),
            originalTextSizeSp = prefs.getFloat("style_original_text_size", 13f),
            enableTextShadow = prefs.getBoolean("style_shadow", false),
            isLockedTouchThrough = prefs.getBoolean("style_is_locked", false),
            windowWidthDp = prefs.getInt("style_window_width", 340),
            windowHeightDp = prefs.getInt("style_window_height", 150),
            isFolded = prefs.getBoolean("style_is_folded", false)
        )
    }

    fun updateSubtitleStyle(style: SubtitleStyle) {
        prefs.edit().apply {
            putInt("style_text_color", style.textColor)
            putFloat("style_text_size", style.textSizeSp)
            putInt("style_bg_color", style.backgroundColor)
            putBoolean("style_show_original", style.showOriginalText)
            putInt("style_original_color", style.originalTextColor)
            putFloat("style_original_text_size", style.originalTextSizeSp)
            putBoolean("style_shadow", style.enableTextShadow)
            putBoolean("style_is_locked", style.isLockedTouchThrough)
            putInt("style_window_width", style.windowWidthDp)
            putInt("style_window_height", style.windowHeightDp)
            putBoolean("style_is_folded", style.isFolded)
            apply()
        }
        _styleFlow.value = style
    }

    fun loadSourceLanguage(): Language {
        val code = prefs.getString("source_lang", Language.ENGLISH.shortCode) ?: Language.ENGLISH.shortCode
        return Language.fromCode(code)
    }

    fun setSourceLanguage(language: Language) {
        prefs.edit().putString("source_lang", language.shortCode).apply()
        _sourceLangFlow.value = language
    }

    fun loadTargetLanguage(): Language {
        val code = prefs.getString("target_lang", Language.CHINESE.shortCode) ?: Language.CHINESE.shortCode
        return Language.fromCode(code)
    }

    fun setTargetLanguage(language: Language) {
        prefs.edit().putString("target_lang", language.shortCode).apply()
        _targetLangFlow.value = language
    }

    fun loadAudioSource(): AudioSourceType {
        val name = prefs.getString("audio_source", AudioSourceType.MIC.name)
        return try {
            AudioSourceType.valueOf(name ?: AudioSourceType.MIC.name)
        } catch (_: Exception) {
            AudioSourceType.MIC
        }
    }

    fun setAudioSource(source: AudioSourceType) {
        prefs.edit().putString("audio_source", source.name).apply()
        _audioSourceFlow.value = source
    }

    fun loadEngineType(): TranslationEngineType {
        val name = prefs.getString("engine_type", TranslationEngineType.EMBEDDED_OFFLINE.name)
        return try {
            TranslationEngineType.valueOf(name ?: TranslationEngineType.EMBEDDED_OFFLINE.name)
        } catch (_: Exception) {
            TranslationEngineType.EMBEDDED_OFFLINE
        }
    }

    fun setEngineType(type: TranslationEngineType) {
        prefs.edit().putString("engine_type", type.name).apply()
        _engineTypeFlow.value = type
    }

    // Cloud API settings
    var deepLApiKey: String
        get() = prefs.getString("deepl_api_key", "") ?: ""
        set(value) = prefs.edit().putString("deepl_api_key", value).apply()

    var llmApiKey: String
        get() = prefs.getString("llm_api_key", "") ?: ""
        set(value) = prefs.edit().putString("llm_api_key", value).apply()

    var llmBaseUrl: String
        get() = prefs.getString("llm_base_url", "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1"
        set(value) = prefs.edit().putString("llm_base_url", value).apply()

    var llmModelName: String
        get() = prefs.getString("llm_model_name", "deepseek-chat") ?: "deepseek-chat"
        set(value) = prefs.edit().putString("llm_model_name", value).apply()
}
