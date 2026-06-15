package com.aivashin.service.agent

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.chatmemory.sql.SQLChatHistoryProvider
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.message.Message
import com.aivashin.tool.GetTableSchemaTool
import com.aivashin.tool.ListDatabaseTablesTool
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.annotations.Property

class AgentChatService(
    @Property("agents.api.geminiApiKey") private val apiKey: String,
    private val agentChatHistoryProvider: ChatHistoryProvider,
) {

    private val logger = KotlinLogging.logger {}

    private val AGENT = AIAgent(
        promptExecutor = simpleGoogleAIExecutor(
            apiKey.also { require(it.isNotBlank()) { "API key must not be blank" } }
        ),
        llmModel = GoogleModels.Gemini2_5FlashLite,
        systemPrompt = """
                You are seasoned AI-architect, helping backend developer learn Koog and Ktor.
                You have access to local database inspection tools. Use them when asked about database structure.
                Answer briefly, technically accurately and with a touch of professional humor.
            """.trimIndent(),
        toolRegistry = ToolRegistry {
            tool(ListDatabaseTablesTool)
            tool(GetTableSchemaTool)
        },
        temperature = 0.7,
    ) {
        install(ChatMemory.Feature) {
            chatHistoryProvider(agentChatHistoryProvider)
            windowSize(100)
            filterMessages { it is Message.User || it is Message.Assistant }
        }
        handleEvents {
            // Handle tool calls
            onLLMCallCompleted { eventContext ->
                logger.info { "LLM call completed. Response: ${eventContext.response}" }
            }
        }
    }

    suspend fun askAgent(sessionId: String, userMessage: String): String {
        return AGENT.run(userMessage, sessionId)
    }
}