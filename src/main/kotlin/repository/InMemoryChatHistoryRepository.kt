package com.aivashin.repository

import com.aivashin.model.ChatMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class InMemoryChatHistoryRepository : ChatHistoryRepository {
    // Thread-safe storage for active sessions
    private val storage = ConcurrentHashMap<String, CopyOnWriteArrayList<ChatMessage>>()

    override suspend fun getMessages(sessionId: String): List<ChatMessage> {
        return storage[sessionId] ?: emptyList()
    }

    override suspend fun appendMessage(sessionId: String, message: ChatMessage) {
        storage.computeIfAbsent(sessionId) { CopyOnWriteArrayList() }.add(message)
    }

    override suspend fun clearSession(sessionId: String) {
        storage.remove(sessionId)
    }
}