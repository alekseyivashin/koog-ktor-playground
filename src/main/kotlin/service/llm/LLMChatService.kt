package com.aivashin.service.llm

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.filterTextOnly
import com.aivashin.model.ChatMessage
import com.aivashin.model.ChatRole
import com.aivashin.repository.ChatHistoryRepository
import com.aivashin.tool.GetTableSchemaTool
import com.aivashin.tool.ListDatabaseTablesTool
import io.ktor.server.plugins.di.annotations.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.jsonPrimitive

class LLMChatService(
    @Property("agents.api.geminiApiKey") private val apiKey: String,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val listDatabaseTablesTool: ListDatabaseTablesTool,
    private val getTableSchemaTool: GetTableSchemaTool,
) {

    private val MODEL = GoogleModels.Gemini2_5FlashLite

    private val SYSTEM_PROMPT = """
        You are seasoned AI-architect, helping backend developer learn Koog and Ktor.
        You have access to local database inspection tools. Use them when asked about database structure.
        Answer briefly, technically accurately and with a touch of professional humor.
    """.trimIndent()

    private val client = GoogleLLMClient(apiKey)

    suspend fun askLLM(sessionId: String, userMessage: String): String {
        val history = chatHistoryRepository.getMessages(sessionId)

        // Save user's turn before execution
        chatHistoryRepository.appendMessage(sessionId, ChatMessage(ChatRole.USER, userMessage))

        // Phase 1: Send request to LLM with tools definition
        val response = client.execute(
            prompt = prompt("chat-with-tools") {
                system(SYSTEM_PROMPT)
                history.forEach { msg ->
                    when (msg.role) {
                        ChatRole.USER -> user(msg.content)
                        ChatRole.ASSISTANT -> assistant(msg.content)
                    }
                }
                user(userMessage)
            },
            model = MODEL,
            tools = listOf(listDatabaseTablesTool.descriptor, getTableSchemaTool.descriptor),
        )

        // Phase 2: Check if the model decided to call a function
        val functionCalls = response.parts.filterIsInstance<MessagePart.Tool.Call>()
        if (functionCalls.isNotEmpty()) {
            val call = functionCalls.first()
            val toolResult = when (call.tool) {
                "list_database_tables" -> {
                    listDatabaseTablesTool.execute(Unit)
                }
                "get_table_schema" -> {
                    // Extract arguments from JSON payload
                    val tableName = call.argsJson["tableName"]?.jsonPrimitive?.content ?: ""
                    getTableSchemaTool.execute(GetTableSchemaTool.Args(tableName))
                }
                else -> "Unknown tool called."
            }

            // Phase 3: Feed the tool execution results back to the LLM to form a final answer
            val finalResponse = client.execute(
                prompt = prompt("tool-resolution") {
                    system(SYSTEM_PROMPT)
                    // 1. Rebuild historical context
                    history.forEach { msg ->
                        when (msg.role) {
                            ChatRole.USER -> user(msg.content)
                            ChatRole.ASSISTANT -> assistant(msg.content)
                        }
                    }
                    user(userMessage)
                    message(response)
                    toolCall(call)
                    toolResult(call.tool, toolResult)
                },
                model = MODEL
            )

            val finalReply = finalResponse.textContent()
            chatHistoryRepository.appendMessage(sessionId, ChatMessage(ChatRole.ASSISTANT, finalReply))
            return finalReply
        }

        val reply = response.textContent()
        chatHistoryRepository.appendMessage(sessionId, ChatMessage(ChatRole.ASSISTANT, reply))
        return reply
    }

    fun streamLLM(sessionId: String, userMessage: String): Flow<String> = flow {
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