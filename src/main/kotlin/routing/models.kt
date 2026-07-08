package com.aivashin.routing

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatRequest(
    val sessionId: String = UUID.randomUUID().toString(),
    val message: String
)

@Serializable
data class ChatResponse(val reply: String)