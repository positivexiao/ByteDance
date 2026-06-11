package com.bytedace.doubaoapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,         // 会话标题（取首条用户消息前20字）
    val createdAt: Long,
    val updatedAt: Long
)