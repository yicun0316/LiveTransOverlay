package com.livetrans.overlay.engine

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.livetrans.overlay.data.AppPreferences
import com.livetrans.overlay.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 云端极速语音识别引擎 (Cloud Streaming ASR Engine)
 * 支持 SiliconFlow (FunAudioLLM/SenseVoiceSmall, 毫秒级) / OpenAI (whisper-1) / Groq (whisper-large-v3)
 */
class CloudAsrEngine(
    private val appPreferences: AppPreferences
) {
    val name: String = "云端极速语音识别 (SenseVoice/Whisper)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun transcribe(wavBytes: ByteArray, language: Language): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = if (appPreferences.asrApiKey.isNotBlank()) {
            appPreferences.asrApiKey.trim()
        } else {
            appPreferences.llmApiKey.trim()
        }

        var baseUrl = if (appPreferences.asrBaseUrl.isNotBlank()) {
            appPreferences.asrBaseUrl.trim()
        } else if (appPreferences.llmBaseUrl.isNotBlank()) {
            appPreferences.llmBaseUrl.trim()
        } else {
            "https://api.siliconflow.cn/v1"
        }.removeSuffix("/")

        if (apiKey.isEmpty()) {
            return@withContext Result.failure(Exception("请先在主界面填入 API Key"))
        }

        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://$baseUrl"
        }

        val endpoint = if (baseUrl.endsWith("/audio/transcriptions")) {
            baseUrl
        } else {
            "$baseUrl/audio/transcriptions"
        }

        // 根据配置或 Base URL 自动匹配最优 ASR 模型 (SiliconFlow 首选 SenseVoiceSmall 毫秒级超快响应)
        val model = if (appPreferences.asrModelName.isNotBlank()) {
            appPreferences.asrModelName.trim()
        } else when {
            baseUrl.contains("siliconflow") -> "FunAudioLLM/SenseVoiceSmall"
            baseUrl.contains("groq") -> "whisper-large-v3"
            else -> "whisper-1"
        }

        val langCode = when (language) {
            Language.CHINESE -> "zh"
            Language.ENGLISH -> "en"
            Language.JAPANESE -> "ja"
        }

        try {
            val audioRequestBody = wavBytes.toRequestBody("audio/wav".toMediaType())
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("file", "speech.wav", audioRequestBody)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", langCode)

            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $apiKey")
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("ASR 错误 HTTP ${response.code}: $bodyStr"))
                }

                val json = try {
                    gson.fromJson(bodyStr, JsonObject::class.java)
                } catch (e: Exception) {
                    null
                } ?: return@withContext Result.failure(Exception("无法解析语音识别响应"))

                if (json.has("error")) {
                    val errMsg = json.getAsJsonObject("error")?.get("message")?.asString ?: "ASR 识别错误"
                    return@withContext Result.failure(Exception(errMsg))
                }

                val text = json.get("text")?.asString?.trim() ?: ""
                if (text.isNotEmpty()) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("未检测到有效语音"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription call failed", e)
            Result.failure(e)
        }
    }

    /**
     * 测试 ASR 连通性
     */
    suspend fun testConnection(apiKey: String, baseUrl: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(Exception("API Key 不能为空"))
        }

        var cleanUrl = baseUrl.trim().removeSuffix("/")
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }

        val endpoint = if (cleanUrl.endsWith("/audio/transcriptions")) {
            cleanUrl
        } else {
            "$cleanUrl/audio/transcriptions"
        }

        // 构造极简 0.5 秒静音/空白 WAV 用于测试握手与鉴权
        val dummyWav = SpeechAudioSegmenter.pcmToWav(ByteArray(16000))

        try {
            val audioRequestBody = dummyWav.toRequestBody("audio/wav".toMediaType())
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model.trim())
                .addFormDataPart("file", "test.wav", audioRequestBody)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("language", "en")

            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $cleanKey")
                .post(multipartBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success("ASR 服务握手成功！模型【$model】可正常调用。")
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $bodyStr"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "CloudAsrEngine"
    }
}
