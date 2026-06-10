package com.aivashin.model

import java.time.LocalDateTime

enum class ChatRole {
    USER, ASSISTANT
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)