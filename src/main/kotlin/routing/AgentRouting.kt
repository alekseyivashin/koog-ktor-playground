package com.aivashin.routing

import com.aivashin.service.agent.AgentChatService
import com.aivashin.service.llm.LLMChatService
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeString
import kotlinx.serialization.Serializable

fun Application.agentRouting() {

    val llmChatService: LLMChatService by dependencies
    val agentChatService: AgentChatService by dependencies

    routing {
        route("/llm") {
            post("/chat") {
                val request = call.receive<ChatRequest>()
                val reply = llmChatService.askLLM(request.sessionId, request.message)
                call.respond(ChatResponse(reply))
            }

            post("/chat/stream") {
                val request = call.receive<ChatRequest>()

                call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                    llmChatService.streamLLM(request.sessionId, request.message).collect { chunk ->
                        writeString(chunk)
                        flush()
                    }
                }
            }
        }

        route("/agent") {
            post("/chat") {
                val request = call.receive<ChatRequest>()
                val reply = agentChatService.askAgent(request.sessionId, request.message)
                call.respond(ChatResponse(reply))
            }
        }
    }
}

@Serializable
data class ChatRequest(
    val sessionId: String,
    val message: String
)

@Serializable
data class ChatResponse(val reply: String)