package com.aivashin.repository

import com.aivashin.model.ChatMessage

interface ChatHistoryRepository {

    suspend fun getMessages(sessionId: String): List<ChatMessage>

    suspend fun appendMessage(sessionId: String, message: ChatMessage)

    suspend fun clearSession(sessionId: String)
}