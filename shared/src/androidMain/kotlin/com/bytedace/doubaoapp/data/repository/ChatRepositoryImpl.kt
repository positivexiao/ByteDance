package com.bytedace.doubaoapp.data.repository

import com.bytedace.doubaoapp.data.db.MessageDao
import com.bytedace.doubaoapp.data.db.MessageEntity
import com.bytedace.doubaoapp.data.db.SessionDao
import com.bytedace.doubaoapp.data.db.SessionEntity
import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.model.MessageRole
import com.bytedace.doubaoapp.data.model.SearchResult
import com.bytedace.doubaoapp.data.model.Session
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao,
) : ChatRepository {
    private val gson = Gson()

    override fun getMessagesBySession(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesBySession(sessionId).map { entities ->
            entities.map { it.toChatMessage() }
        }
    }

    override suspend fun insertMessage(message: ChatMessage) {
        messageDao.insertMessage(message.toEntity())
    }

    override suspend fun updateMessage(message: ChatMessage) {
        messageDao.insertMessage(message.toEntity())
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    override fun getAllSessions(): Flow<List<Session>> {
        return sessionDao.getAllSessions().map { entities ->
            entities.map { it.toSession() }
        }
    }

    override suspend fun insertSession(session: Session) {
        sessionDao.insertSession(session.toEntity())
    }

    override suspend fun updateSession(session: Session) {
        sessionDao.updateSession(session.toEntity())
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSessionById(sessionId)
    }

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
            followUpSuggestionsJson = if (followUpSuggestions.isNotEmpty()) gson.toJson(followUpSuggestions) else null,
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
            } ?: emptyList(),
        )
    }

    private fun SessionEntity.toSession() = Session(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Session.toEntity() = SessionEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
