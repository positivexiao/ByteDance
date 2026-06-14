package com.bytedace.doubaoapp.data.repository

import com.bytedace.doubaoapp.data.model.ChatMessage
import com.bytedace.doubaoapp.data.model.Session
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessage>>
    suspend fun insertMessage(message: ChatMessage)
    suspend fun updateMessage(message: ChatMessage)
    suspend fun deleteMessage(messageId: String)
    fun getAllSessions(): Flow<List<Session>>
    suspend fun insertSession(session: Session)
    suspend fun updateSession(session: Session)
    suspend fun deleteSession(sessionId: String)
}
