package com.livetrans.overlay.engine

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.livetrans.overlay.data.AppPreferences
import com.livetrans.overlay.data.Language
import com.livetrans.overlay.data.TranslationEngineType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Optional Cloud Translation Engine supporting:
 * 1. DeepL Free API (high natural translation quality)
 * 2. OpenAI / DeepSeek compatible Chat Completions (contextual simultaneous translation)
 */
class CloudTranslationEngine(
    private val appPreferences: AppPreferences
) : ITranslationEngine {

    override val name: String
        get() = when (appPreferences.loadEngineType()) {
            TranslationEngineType.CLOUD_DEEPL -> "DeepL API (高精度云端)"
            TranslationEngineType.CLOUD_LLM -> "AI大模型同传 (${appPreferences.llmModelName})"
            else -> "云端引擎"
        }

    override val isLocal: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override suspend fun prepareModel(
        sourceLang: Language,
        targetLang: Language
    ): Result<Unit> {
        // Cloud API requires no local model download
        return Result.success(Unit)
    }

    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")
        if (sourceLang == targetLang) return@withContext Result.success(trimmed)

        val engineType = appPreferences.loadEngineType()
        when (engineType) {
            TranslationEngineType.CLOUD_DEEPL -> translateViaDeepL(trimmed, sourceLang, targetLang)
            TranslationEngineType.CLOUD_LLM -> translateViaLLM(trimmed, sourceLang, targetLang)
            else -> Result.failure(IllegalStateException("Unsupported cloud engine type: $engineType"))
        }
    }

    private fun translateViaDeepL(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> {
        val apiKey = appPreferences.deepLApiKey.trim()
        if (apiKey.isEmpty()) {
            return Result.failure(IllegalArgumentException("请先在设置中填写 DeepL API Key"))
        }

        val url = if (apiKey.endsWith(":fx")) {
            "https://api-free.deepl.com/v2/translate"
        } else {
            "https://api.deepl.com/v2/translate"
        }

        val body = FormBody.Builder()
            .add("text", text)
            .add("source_lang", sourceLang.deepLCode)
            .add("target_lang", targetLang.deepLCode)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "DeepL-Auth-Key $apiKey")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("DeepL API HTTP Error: ${response.code}"))
                }
                val respBody = response.body?.string() ?: ""
                val json = gson.fromJson(respBody, JsonObject::class.java)
                val translations = json.getAsJsonArray("translations")
                if (translations != null && translations.size() > 0) {
                    val translatedText = translations[0].asJsonObject.get("text").asString
                    Result.success(translatedText)
                } else {
                    Result.failure(Exception("Empty translation response from DeepL"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DeepL translation call failed", e)
            Result.failure(e)
        }
    }

    private fun translateViaLLM(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> {
        val apiKey = appPreferences.llmApiKey.trim()
        val baseUrl = appPreferences.llmBaseUrl.trim().removeSuffix("/")
        val model = appPreferences.llmModelName.trim()

        if (apiKey.isEmpty()) {
            return Result.failure(IllegalArgumentException("请在设置中配置大模型 API Key (如 DeepSeek)"))
        }

        val endpoint = "$baseUrl/chat/completions"
        val systemPrompt = "你是一名专业实时的外语同传翻译员。请将输入的${sourceLang.displayName}文本准确、自然、简洁地翻译成${targetLang.displayName}。注意：直接输出翻译结果，不要输出任何解释、不要输出拼音或假名注音、不要带引号。"

        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0.3)
            addProperty("max_tokens", 256)
            val messages = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", text)
                })
            }
            add("messages", messages)
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("LLM API HTTP Error: ${response.code}"))
                }
                val respBody = response.body?.string() ?: ""
                val json = gson.fromJson(respBody, JsonObject::class.java)
                val choices = json.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val content = choices[0].asJsonObject
                        .getAsJsonObject("message")
                        .get("content").asString.trim()
                    Result.success(content)
                } else {
                    Result.failure(Exception("Empty response choice from LLM"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM translation call failed", e)
            Result.failure(e)
        }
    }

    override fun close() {
        client.dispatcher.cancelAll()
    }

    companion object {
        private const val TAG = "CloudTranslationEngine"
    }
}
