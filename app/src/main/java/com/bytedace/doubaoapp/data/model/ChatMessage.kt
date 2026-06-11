package com.bytedace.doubaoapp.data.model

/**
 * 聊天消息数据类。
 * 使用 @Stable 兼容的不可变数据类，配合 Compose 状态管理。
 */
data class ChatMessage(
    val id: String,               // UUID
    val role: MessageRole,        // USER / ASSISTANT / SYSTEM
    val content: String,          // 文本内容
    val imageUris: List<String> = emptyList(), // 图片 URI 列表(进阶A)
    val timestamp: Long,          // 时间戳(ms)
    val isLiked: Boolean = false, // 点赞
    val isDisliked: Boolean = false, // 点踩
    val isStreaming: Boolean = false, // 是否正在流式输出
    val generatedImageUrl: String? = null, // 生图结果 URL(进阶C)
    val sessionId: String,        // 会话ID
    val usedWebSearch: Boolean = false, // 是否使用了联网搜索
    val searchResults: List<SearchResult> = emptyList(), // 联网搜索引用来源
    val followUpSuggestions: List<String> = emptyList(),  // 追问建议列表
)