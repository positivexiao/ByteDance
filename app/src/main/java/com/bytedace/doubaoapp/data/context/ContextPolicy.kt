package com.bytedace.doubaoapp.data.context

import com.bytedace.doubaoapp.data.model.ChatMessage

/**
 * 上下文管理策略接口。
 * 控制发送给大模型的历史消息数量，避免 token 超限和响应延迟。
 *
 * 已实现：SlidingWindowPolicy（滑动窗口裁剪）
 * 后续扩展：
 *   - SummaryPolicy：超窗口时用大模型对旧消息生成摘要
 *   - RetrievalPolicy：将历史消息向量化，按相关性检索片段
 */
interface ContextPolicy {

    /**
     * 从完整历史消息中裁剪出要发送给模型的上下文。
     * @param messages 完整历史消息列表
     * @param maxTokens 最大 token 数量估算（用于更精细的裁剪）
     * @return 裁剪后的消息列表，将作为 messages 参数发送给 LLM
     */
    fun trimContext(
        messages: List<ChatMessage>,
        maxTokens: Int = 4096
    ): List<ChatMessage>
}