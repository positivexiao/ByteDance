package com.bytedace.doubaoapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val role: String,          // "USER" / "ASSISTANT" / "SYSTEM"
    val content: String,
    val imageUrisJson: String?, // JSON序列化的图片URI列表
    val timestamp: Long,
    val isLiked: Boolean,
    val isDisliked: Boolean,
    val generatedImageUrl: String?,
    val sessionId: String,
    val usedWebSearch: Boolean = false, // 是否使用了联网搜索
    val searchResultsJson: String? = null, // JSON序列化的搜索结果列表
    val followUpSuggestionsJson: String? = null, // JSON序列化的追问建议列表
)