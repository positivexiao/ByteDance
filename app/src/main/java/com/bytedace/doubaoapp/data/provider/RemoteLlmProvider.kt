package com.bytedace.doubaoapp.data.provider

import com.bytedace.doubaoapp.data.api.ApiConfig
import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.model.MessageRole
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * 远端大模型 Provider — 火山引擎 Ark API 实现。
 * API 兼容 OpenAI 格式：POST /api/v3/chat/completions
 * 支持联网搜索（web_search tool_use）。
 */
class RemoteLlmProvider(
    private val apiKey: String,
    private val endpointId: String = ApiConfig.DEFAULT_ENDPOINT_ID
) : LlmProvider {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectionPool(okhttp3.ConnectionPool(
                ApiConfig.CONNECTION_POOL_MAX,
                ApiConfig.CONNECTION_POOL_KEEP_ALIVE_MIN,
                TimeUnit.MINUTES
            ))
            .build()
    }

    private val gson = Gson()

    override fun chatStream(
        messages: List<ChatMessage>,
        contextPolicy: ContextPolicy,
        enableWebSearch: Boolean
    ): Flow<String> = callbackFlow {
        val trimmedMessages = contextPolicy.trimContext(messages)

        // 联网搜索走 Responses API（web_search 内置工具）；普通对话走 chat/completions
        val request = if (enableWebSearch) {
            buildResponsesRequest(buildResponsesJson(trimmedMessages, stream = true))
        } else {
            buildRequest(buildRequestJson(trimmedMessages, stream = true))
        }

        val listener = if (enableWebSearch) {
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        close()
                        return
                    }
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val eventType = json.get("type")?.asString ?: ""
                        when (eventType) {
                            // 仅最终回答文本增量需要上屏；推理摘要/搜索过程等事件跳过
                            "response.output_text.delta" -> {
                                val delta = json.get("delta")?.asString
                                if (!delta.isNullOrEmpty()) trySend(delta)
                            }
                            "response.completed", "response.failed", "response.incomplete" -> close()
                        }
                    } catch (_: Exception) {
                    }
                }

                override fun onClosed(eventSource: EventSource) { close() }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    val errorMsg = t?.message ?: response?.body?.string()?.take(200) ?: "Unknown SSE error"
                    close(IllegalStateException("SSE connection failed: $errorMsg"))
                }
            }
        } else {
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") {
                        close()
                        return
                    }
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val choices = json.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                            if (delta != null && delta.has("content") && !delta["content"].isJsonNull) {
                                val content = delta["content"].asString
                                if (content.isNotEmpty()) trySend(content)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                override fun onClosed(eventSource: EventSource) { close() }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    val errorMsg = t?.message ?: response?.body?.string()?.take(200) ?: "Unknown SSE error"
                    close(IllegalStateException("SSE connection failed: $errorMsg"))
                }
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    override suspend fun chatCompletion(
        messages: List<ChatMessage>,
        contextPolicy: ContextPolicy,
        enableWebSearch: Boolean
    ): String = withContext(Dispatchers.IO) {
        val trimmedMessages = contextPolicy.trimContext(messages)
        val request = if (enableWebSearch) {
            buildResponsesRequest(buildResponsesJson(trimmedMessages, stream = false))
        } else {
            buildRequest(buildRequestJson(trimmedMessages, stream = false))
        }

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()?.take(500) ?: "Unknown error"
            throw IllegalStateException("API error ${response.code}: $errorBody")
        }
        val responseBody = response.body?.string() ?: ""
        val json = JsonParser.parseString(responseBody).asJsonObject
        if (enableWebSearch) parseResponsesOutputText(json)
        else json.getAsJsonArray("choices")[0].asJsonObject
            .getAsJsonObject("message")
            .get("content").asString
    }

    override fun supportsImage(): Boolean = true

    override fun supportsToolUse(): Boolean = true

    override fun modelName(): String = "豆包(远端)"

    override fun modelSource(): String = "remote"

    // ---- Private helpers ----

    private fun buildRequest(json: String): Request {
        return Request.Builder()
            .url("${ApiConfig.ARK_BASE_URL}${ApiConfig.CHAT_COMPLETIONS_PATH}")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildRequestJson(messages: List<ChatMessage>, stream: Boolean): String {
        val apiMessages = mutableListOf<Map<String, Any>>()

        // 添加 system prompt
        apiMessages.add(mapOf(
            "role" to "system",
            "content" to ApiConfig.DEFAULT_SYSTEM_PROMPT
        ))

        // 转换消息列表
        for (msg in messages) {
            if (msg.role == MessageRole.SYSTEM) {
                apiMessages.add(mapOf("role" to "system", "content" to msg.content))
                continue
            }

            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                else -> continue
            }

            // 如果消息包含图片，使用多模态格式
            if (msg.imageUris.isNotEmpty() && msg.role == MessageRole.USER) {
                val contentList = mutableListOf<Map<String, Any>>()
                for (imageUri in msg.imageUris) {
                    contentList.add(mapOf(
                        "type" to "image_url",
                        "image_url" to mapOf("url" to imageUri)
                    ))
                }
                if (msg.content.isNotEmpty()) {
                    contentList.add(mapOf(
                        "type" to "text",
                        "text" to msg.content
                    ))
                }
                apiMessages.add(mapOf("role" to role, "content" to contentList))
            } else {
                apiMessages.add(mapOf("role" to role, "content" to msg.content))
            }
        }

        val requestMap = mutableMapOf<String, Any>(
            "model" to endpointId,
            "messages" to apiMessages,
            "stream" to stream
        )

        return gson.toJson(requestMap)
    }

    // ---- Responses API（联网搜索 web_search） ----

    private fun buildResponsesRequest(json: String): Request {
        return Request.Builder()
            .url("${ApiConfig.ARK_BASE_URL}${ApiConfig.RESPONSES_PATH}")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /**
     * 构建 Responses API 请求体。
     * 注意：Responses API 用 `input` 而非 `messages`，system 提示通过 `instructions` 传入；
     * 联网搜索通过 `tools:[{type:"web_search"}]` 开启，模型自动决定是否检索。
     * 如果用户消息带图片，使用 Responses API 的 input_image 格式：
     * `{"type":"input_image","image_url":"data:image/..."}`。
     */
    private fun buildResponsesJson(messages: List<ChatMessage>, stream: Boolean): String {
        val input = mutableListOf<Map<String, Any>>()
        for (msg in messages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                else -> continue
            }

            if (msg.role == MessageRole.USER && msg.imageUris.isNotEmpty()) {
                val contentList = mutableListOf<Map<String, Any>>()
                if (msg.content.isNotBlank()) {
                    contentList.add(
                        mapOf(
                            "type" to "input_text",
                            "text" to msg.content
                        )
                    )
                }
                for (imageUri in msg.imageUris) {
                    contentList.add(
                        mapOf(
                            "type" to "input_image",
                            // Responses API 要求 image_url 为字符串；不能使用 {"url": ...} 对象
                            "image_url" to imageUri
                        )
                    )
                }
                input.add(mapOf("role" to role, "content" to contentList))
            } else if (msg.content.isNotBlank()) {
                input.add(mapOf("role" to role, "content" to msg.content))
            }
        }

        val requestMap = mapOf(
            "model" to endpointId,
            "instructions" to ApiConfig.DEFAULT_SYSTEM_PROMPT,
            "input" to input,
            "tools" to listOf(mapOf("type" to "web_search")),
            "stream" to stream
        )
        return gson.toJson(requestMap)
    }

    /** 从 Responses API 非流式响应的 output 数组中提取最终回答文本。 */
    private fun parseResponsesOutputText(json: com.google.gson.JsonObject): String {
        val output = json.getAsJsonArray("output") ?: return ""
        val sb = StringBuilder()
        for (item in output) {
            val obj = item.asJsonObject
            if (obj.get("type")?.asString != "message") continue
            val content = obj.getAsJsonArray("content") ?: continue
            for (part in content) {
                val partObj = part.asJsonObject
                if (partObj.get("type")?.asString == "output_text") {
                    sb.append(partObj.get("text")?.asString ?: "")
                }
            }
        }
        return sb.toString()
    }
}