package com.bytedace.doubaoapp.data.context

import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.model.MessageRole

/**
 * 滑动窗口裁剪策略 — 最容易实现的上下文管理方案。
 *
 * 策略：始终保留 system prompt + 最近 N 条对话消息。
 * 超出窗口的旧消息直接丢弃，不发送给模型。
 *
 * 优势：零额外 API 调用，实现简单，能有效控制 token 数
 * 劣势：丢失早期上下文（后续可用 SummaryPolicy 补充）
 */
class SlidingWindowPolicy(
    private val maxMessageCount: Int = 20  // 保留最近20条对话消息
) : ContextPolicy {

    override fun trimContext(
        messages: List<ChatMessage>,
        maxTokens: Int
    ): List<ChatMessage> {
        // 分离 system 消息和对话消息
        val systemMessages = messages.filter { it.role == MessageRole.SYSTEM }
        val conversationMessages = messages.filter { it.role != MessageRole.SYSTEM }

        // 粗略估算 token 数：中文约 1.5 token/字，英文约 1 token/4字符
        // 如果裁剪后的消息预估 token 超过 maxTokens，进一步缩减
        var selectedConversation = conversationMessages.takeLast(maxMessageCount)

        // 简易 token 估算与二次裁剪
        var estimatedTokens = estimateTokens(systemMessages + selectedConversation)
        while (estimatedTokens > maxTokens && selectedConversation.size > 2) {
            // 每次移除最早的一条对话消息
            selectedConversation = selectedConversation.drop(1)
            estimatedTokens = estimateTokens(systemMessages + selectedConversation)
        }

        return systemMessages + selectedConversation
    }

    /** 简易 token 估算：中文约 1.5x，英文约 0.25x，混合取平均 */
    private fun estimateTokens(messages: List<ChatMessage>): Int {
        var totalChars = 0
        for (msg in messages) {
            totalChars += msg.content.length
            // 图片消息额外估算 token（多模态图片约 1000-2000 token）
            if (msg.imageUris.isNotEmpty()) {
                totalChars += msg.imageUris.size * 1500
            }
        }
        // 保守估算：每字符约 1 token（中文更密集）
        return totalChars
    }
}