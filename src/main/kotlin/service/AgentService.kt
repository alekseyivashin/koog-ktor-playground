package com.aivashin.service

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.text.TextContentBuilder
import kotlinx.coroutines.flow.Flow

class AgentService(apiKey: String) {

    companion object {
        private val MODEL = GoogleModels.Gemini3_Flash_Preview
        private val SYSTEM_PROMPT: TextContentBuilder.() -> Unit = {
            text("You are seasoned AI-architect, helping backend developer learn Koog and Ktor")
            text("Answer briefly, technically accurately and with a touch of professional humor.")
        }
    }

    init {
        require(apiKey.isNotBlank()) { "API key must not be blank" }
    }

    private val client = GoogleLLMClient(apiKey)

    suspend fun askAgent(userMessage: String): String {
        val response = client.execute(
            prompt = prompt("simple-ask") {
                system(SYSTEM_PROMPT)
                user(userMessage)
            },
            model = MODEL
        )
        return response.textContent()
    }

    fun streamAgent(userMessage: String): Flow<StreamFrame> {
        return client.executeStreaming(
            prompt = prompt("simple-ask") {
                system(SYSTEM_PROMPT)
                user(userMessage)
            },
            model = MODEL
        )
    }
}