package com.bytedace.doubaoapp.data.repository

import com.bytedace.doubaoapp.data.db.MessageDao
import com.bytedace.doubaoapp.data.db.MessageEntity
import com.bytedace.doubaoapp.data.db.SessionDao
import com.bytedace.doubaoapp.data.db.SessionEntity
import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.model.MessageRole
import com.bytedace.doubaoapp.data.model.SearchResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 聊天数据仓库，整合 Room DAO 操作。
 * 负责 ChatMessage ↔ MessageEntity 的转换。
 */
class ChatRepository(
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao
) {
    private val gson = Gson()

    // ---- 消息操作 ----

    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesBySession(sessionId).map { entities ->
            entities.map { it.toChatMessage() }
        }
    }

    suspend fun insertMessage(message: ChatMessage) {
        messageDao.insertMessage(message.toEntity())
    }

    suspend fun updateMessage(message: ChatMessage) {
        messageDao.insertMessage(message.toEntity())  // REPLACE策略
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    // ---- 会话操作 ----

    fun getAllSessions(): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessions()
    }

    suspend fun insertSession(session: SessionEntity) {
        sessionDao.insertSession(session)
    }

    suspend fun updateSession(session: SessionEntity) {
        sessionDao.updateSession(session)
    }

    suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSessionById(sessionId)
    }

    // ---- 转换辅助 ----

    private fun ChatMessage.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            role = role.name,
            content = content,
            imageUrisJson = if (imageUris.isNotEmpty()) gson.toJson(imageUris) else null,
            timestamp = timestamp,
            isLiked = isLiked,
            isDisliked = isDisliked,
            generatedImageUrl = generatedImageUrl,
            sessionId = sessionId,
            usedWebSearch = usedWebSearch,
            searchResultsJson = if (searchResults.isNotEmpty()) gson.toJson(searchResults) else null,
            followUpSuggestionsJson = if (followUpSuggestions.isNotEmpty()) gson.toJson(followUpSuggestions) else null
        )
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        val searchResultType = object : TypeToken<List<SearchResult>>() {}.type
        return ChatMessage(
            id = id,
            role = try { MessageRole.valueOf(role) } catch (_: Exception) { MessageRole.USER },
            content = content,
            imageUris = imageUrisJson?.let {
                try { gson.fromJson(it, Array<String>::class.java).toList() } catch (_: Exception) { emptyList() }
            } ?: emptyList(),
            timestamp = timestamp,
            isLiked = isLiked,
            isDisliked = isDisliked,
            generatedImageUrl = generatedImageUrl,
            sessionId = sessionId,
            usedWebSearch = usedWebSearch,
            searchResults = searchResultsJson?.let {
                try { gson.fromJson(it, searchResultType) } catch (_: Exception) { emptyList() }
            } ?: emptyList(),
            followUpSuggestions = followUpSuggestionsJson?.let {
                try { gson.fromJson(it, Array<String>::class.java).toList() } catch (_: Exception) { emptyList() }
            } ?: emptyList()
        )
    }
}