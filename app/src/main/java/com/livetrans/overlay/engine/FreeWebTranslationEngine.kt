package com.livetrans.overlay.engine

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.livetrans.overlay.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 100% Free Web Translation Engine (Zero API Key, Zero Model Download).
 * Direct HTTP fallback ensuring translations work immediately in all network environments.
 * Primary: Google Translate Web API
 * Secondary: MyMemory Free API
 */
class FreeWebTranslationEngine : ITranslationEngine {

    override val name: String = "免费网页翻译引擎 (免Key·免下载·即开即用)"
    override val isLocal: Boolean = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override suspend fun prepareModel(sourceLang: Language, targetLang: Language): Result<Unit> {
        // No local model download required
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

        // 1. Try Google Translate Web API
        val gResult = translateViaGoogleWeb(trimmed, sourceLang, targetLang)
        if (gResult.isSuccess && !gResult.getOrNull().isNullOrBlank()) {
            return@withContext gResult
        }

        // 2. Try MyMemory Free API Fallback
        val mResult = translateViaMyMemory(trimmed, sourceLang, targetLang)
        if (mResult.isSuccess && !mResult.getOrNull().isNullOrBlank()) {
            return@withContext mResult
        }

        Result.failure(Exception("免费网页翻译通道暂时无法连接，请检查网络或切换引擎"))
    }

    private fun translateViaGoogleWeb(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val sl = when (sourceLang) {
                Language.CHINESE -> "zh-CN"
                Language.ENGLISH -> "en"
                Language.JAPANESE -> "ja"
            }
            val tl = when (targetLang) {
                Language.CHINESE -> "zh-CN"
                Language.ENGLISH -> "en"
                Language.JAPANESE -> "ja"
            }

            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sl&tl=$tl&dt=t&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
                val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))

                val jsonArray = gson.fromJson(body, JsonArray::class.java)
                val sentences = jsonArray.get(0).asJsonArray
                val sb = StringBuilder()
                for (i in 0 until sentences.size()) {
                    val piece = sentences.get(i).asJsonArray.get(0).asString
                    sb.append(piece)
                }
                Result.success(sb.toString().trim())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Google Web API translation failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun translateViaMyMemory(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): Result<String> {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val sl = sourceLang.shortCode.lowercase()
            val tl = targetLang.shortCode.lowercase()
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$sl|$tl"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
                val body = response.body?.string() ?: return Result.failure(Exception("Empty body"))

                val json = gson.fromJson(body, JsonObject::class.java)
                val responseData = json.getAsJsonObject("responseData")
                val translatedText = responseData?.get("translatedText")?.asString ?: ""
                Result.success(translatedText.trim())
            }
        } catch (e: Exception) {
            Log.w(TAG, "MyMemory translation failed: ${e.message}")
            Result.failure(e)
        }
    }

    override fun close() {
        client.dispatcher.cancelAll()
    }

    companion object {
        private const val TAG = "FreeWebTransEngine"
    }
}
