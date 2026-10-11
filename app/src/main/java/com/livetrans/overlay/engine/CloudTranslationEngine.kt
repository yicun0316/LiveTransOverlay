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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class CloudTranslationEngine(
    private val appPreferences: AppPreferences
) : ITranslationEngine {

    override val name: String
        get() = when (appPreferences.loadEngineType()) {
            TranslationEngineType.CLOUD_DEEPL -> "DeepL API"
            TranslationEngineType.CLOUD_LLM -> "API (${appPreferences.llmModelName})"
            else -> "云端 API"
        }

    override val isLocal: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override suspend fun prepareModel(
        sourceLang: Language,
        targetLang: Language
    ): Result<Unit> = Result.success(Unit)

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
            TranslationEngineType.CLOUD_LLM -> translateViaLLM(
                trimmed,
                sourceLang,
                targetLang,
                appPreferences.llmApiKey.trim(),
                appPreferences.llmBaseUrl.trim(),
                appPreferences.llmModelName.trim()
            )
            else -> Result.failure(IllegalStateException("未配置引擎类型"))
        }
    }

    private fun translateViaDeepL(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> {
        val apiKey = appPreferences.deepLApiKey.trim()
        if (apiKey.isEmpty()) {
            return Result.failure(IllegalArgumentException("请填写 DeepL API Key"))
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
                    return Result.failure(Exception("DeepL 错误: HTTP ${response.code}"))
                }
                val respBody = response.body?.string() ?: ""
                val json = gson.fromJson(respBody, JsonObject::class.java)
                val translations = json.getAsJsonArray("translations")
                if (translations != null && translations.size() > 0) {
                    val translatedText = translations[0].asJsonObject.get("text").asString
                    Result.success(translatedText.trim())
                } else {
                    Result.failure(Exception("DeepL 返回为空"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DeepL translation failed", e)
            Result.failure(e)
        }
    }

    suspend fun testConnection(
        apiKey: String,
        baseUrl: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val testText = "Hello, this is a live translation test."
        val res = translateViaLLM(testText, Language.ENGLISH, Language.CHINESE, apiKey, baseUrl, model)
        val elapsed = System.currentTimeMillis() - start
        if (res.isSuccess) {
            Result.success("耗时: ${elapsed}ms | 译文: \"${res.getOrNull()}\"")
        } else {
            res
        }
    }

    private fun translateViaLLM(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        apiKey: String,
        baseUrl: String,
        model: String
    ): Result<String> {
        return try {
            val key = apiKey.trim()
            if (key.isEmpty()) {
                return Result.failure(IllegalArgumentException("请填写 API Key 密钥"))
            }

            var cleanBaseUrl = baseUrl.trim().removeSuffix("/")
            if (cleanBaseUrl.isEmpty()) {
                cleanBaseUrl = "https://api.deepseek.com/v1"
            }
            if (!cleanBaseUrl.startsWith("http://") && !cleanBaseUrl.startsWith("https://")) {
                cleanBaseUrl = "https://$cleanBaseUrl"
            }

            val endpoint = if (cleanBaseUrl.endsWith("/chat/completions")) {
                cleanBaseUrl
            } else {
                "$cleanBaseUrl/chat/completions"
            }

            val prompt = "你是一名实时同传翻译员。请将输入的${sourceLang.displayName}文本准确翻译成${targetLang.displayName}。直接输出译文，不要解释，不要带引号。"

            val payload = JsonObject().apply {
                addProperty("model", model.trim().ifEmpty { "deepseek-chat" })
                addProperty("temperature", 0.3)
                addProperty("max_tokens", 256)
                val messages = com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", prompt)
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
                .header("Authorization", "Bearer $key")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val code = response.code
                    val msg = when (code) {
                        401 -> "HTTP 401 密钥无效，请核对 API Key"
                        404 -> "HTTP 404 地址错误，请检查 Base URL (通常需带 /v1，如 https://api.deepseek.com/v1)"
                        429 -> "HTTP 429 额度耗尽或频次超限"
                        else -> "HTTP $code: $respBody"
                    }
                    return Result.failure(Exception(msg))
                }

                val json = try {
                    gson.fromJson(respBody, JsonObject::class.java)
                } catch (e: Exception) {
                    null
                } ?: return Result.failure(Exception("无法解析模型响应: $respBody"))

                if (json.has("error")) {
                    val errMsg = json.getAsJsonObject("error")?.get("message")?.asString ?: "API 返回错误"
                    return Result.failure(Exception("API 错误: $errMsg"))
                }

                val choices = json.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val choiceObj = choices[0].asJsonObject
                    val messageObj = choiceObj.getAsJsonObject("message")
                    val content = messageObj?.get("content")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        ?: choiceObj.get("text")?.takeIf { !it.isJsonNull }?.asString?.trim()
                        ?: ""
                    if (content.isNotEmpty()) {
                        Result.success(content)
                    } else {
                        Result.failure(Exception("模型响应内容为空"))
                    }
                } else {
                    Result.failure(Exception("模型响应格式不兼容或为空"))
                }
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("网络连接超时，请检查网络是否能访问该 API"))
        } catch (e: ConnectException) {
            Result.failure(Exception("无法连接至服务器，请检查接口地址与网络连接"))
        } catch (e: IllegalArgumentException) {
            Result.failure(Exception("接口地址或参数格式错误: ${e.message}"))
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
