package com.aivashin.service

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.text.TextContentBuilder
import com.aivashin.model.ChatMessage
import com.aivashin.model.ChatRole
import com.aivashin.repository.ChatHistoryRepository
import io.ktor.server.plugins.di.annotations.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AgentService(
    @Property("agents.api.geminiApiKey") apiKey: String,
    private val chatHistoryRepository: ChatHistoryRepository
) {

    companion object {
        private val MODEL = GoogleModels.Gemini2_5FlashLite
        private val SYSTEM_PROMPT: TextContentBuilder.() -> Unit = {
            text("You are seasoned AI-architect, helping backend developer learn Koog and Ktor")
            text("Answer briefly, technically accurately and with a touch of professional humor.")
        }
    }

    init {
        require(apiKey.isNotBlank()) { "API key must not be blank" }
    }

    private val client = GoogleLLMClient(apiKey)

    suspend fun askAgent(sessionId: String, userMessage: String): String {
        val history = chatHistoryRepository.getMessages(sessionId)

        // Save user's turn before execution
        chatHistoryRepository.appendMessage(sessionId, ChatMessage(ChatRole.USER, userMessage))

        val response = client.execute(
            prompt = prompt("chat-with-history") {
                system(SYSTEM_PROMPT)
                // Inject previous dialog turns into the prompt context
                history.forEach { msg ->
                    when (msg.role) {
                        ChatRole.USER -> user(msg.content)
                        ChatRole.ASSISTANT -> assistant(msg.content)
                    }
                }
                user(userMessage)
            },
            model = MODEL
        )

        val reply = response.textContent()

        // Save assistant's turn
        chatHistoryRepository.appendMessage(sessionId, ChatMessage(ChatRole.ASSISTANT, reply))
        return reply
    }

    fun streamAgent(sessionId: String, userMessage: String): Flow<String> = flow {
        val history = chatHistoryRepository.getMessages(sessionId)
        chatHistoryRepository.appendMessage(sessionId, ChatMessage(ChatRole.USER, userMessage))

        // Buffer to accumulate the full assistant response during streaming
        val assistantResponseBuffer = StringBuilder()

        client.executeStreaming(
            prompt = prompt("chat-with-history") {
                system(SYSTEM_PROMPT)
                history.forEach { msg ->
                    when (msg.role) {
                        ChatRole.USER -> user(msg.content)
                        ChatRole.ASSISTANT -> assistant(msg.content)
                    }
                }
                user(userMessage)
            },
            model = MODEL
        ).filterTextOnly().collect { frame ->
            // Accumulate text chunk from the StreamFrame
            assistantResponseBuffer.append(frame)
            emit(frame)
        }

        // Once the stream is fully collected, persist the aggregated message
        chatHistoryRepository.appendMessage(
            sessionId,
            ChatMessage(ChatRole.ASSISTANT, assistantResponseBuffer.toString())
        )
    }
}