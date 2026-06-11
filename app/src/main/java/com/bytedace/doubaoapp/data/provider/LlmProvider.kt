package com.bytedace.doubaoapp.data.provider

import com.bytedace.doubaoapp.data.context.ContextPolicy
import com.bytedace.doubaoapp.data.context.SlidingWindowPolicy
import com.bytedace.doubaoapp.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * 大语言模型 Provider 接口。
 * ChatViewModel 只依赖此接口，不依赖具体实现。
 * 远端实现：RemoteLlmProvider（火山引擎 Ark API）
 * 本地实现（未来）：LocalLlmProvider（llama.cpp / mnn 等）
 */
interface LlmProvider {

    /**
     * 流式对话，逐 token 返回。
     * @param messages 完整历史消息列表
     * @param contextPolicy 上下文裁剪策略，控制发送给模型的消息数量
     * @param enableWebSearch 是否启用联网搜索
     * @return Flow<String> 逐 token 的文本流
     */
    fun chatStream(
        messages: List<ChatMessage>,
        contextPolicy: ContextPolicy = SlidingWindowPolicy(),
        enableWebSearch: Boolean = false
    ): Flow<String>

    /**
     * 非流式对话，一次性返回完整文本。
     * @param messages 完整历史消息列表
     * @param contextPolicy 上下文裁剪策略
     * @param enableWebSearch 是否启用联网搜索
     */
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        contextPolicy: ContextPolicy = SlidingWindowPolicy(),
        enableWebSearch: Boolean = false
    ): String

    /** 是否支持图片输入（多模态） */
    fun supportsImage(): Boolean = false

    /** 是否支持工具调用（联网搜索等） */
    fun supportsToolUse(): Boolean = false

    /** 模型名称，用于 UI 展示 */
    fun modelName(): String

    /** 模型来源标识：remote / local */
    fun modelSource(): String
}