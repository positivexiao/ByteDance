package com.bytedace.doubaoapp.data.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 火山引擎 Ark 图片生成 API Client。
 * POST /api/v3/images/generations
 * 使用 Seedream 模型生成图片。
 */
class ArkImageClient(
    private val apiKey: String,
    private val endpointId: String = ApiConfig.IMAGE_GEN_ENDPOINT_ID
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        // Seedream 5.0 lite 生成 1920x1920 图片耗时可能明显超过 OkHttp 默认 10 秒。
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(ApiConfig.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()

    /**
     * 生成图片。
     * @param prompt 图片描述文本
     * @return 生成的图片 URL
     */
    suspend fun generateImage(prompt: String): String = withContext(Dispatchers.IO) {
        val requestMap = mapOf(
            "model" to endpointId,
            "prompt" to prompt,
            "size" to ApiConfig.IMAGE_GEN_SIZE,
            "response_format" to "url"
        )

        val requestJson = gson.toJson(requestMap)
        val request = Request.Builder()
            .url("${ApiConfig.ARK_BASE_URL}${ApiConfig.IMAGE_GENERATIONS_PATH}")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()?.take(500) ?: "Unknown error"
            throw IllegalStateException("Image generation API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string() ?: ""
        val json = JsonParser.parseString(responseBody).asJsonObject
        json.getAsJsonArray("data")[0].asJsonObject.get("url").asString
    }
}