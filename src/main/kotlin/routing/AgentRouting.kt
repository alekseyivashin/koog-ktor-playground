package com.aivashin.routing

import com.aivashin.service.AgentService
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeString
import kotlinx.serialization.Serializable

fun Application.agentRouting() {

    val agentService: AgentService by dependencies

    routing {
        post("/chat") {
            val request = call.receive<ChatRequest>()
            val reply = agentService.askAgent(request.sessionId, request.message)
            call.respond(ChatResponse(reply))
        }

        post("/chat/stream") {
            val request = call.receive<ChatRequest>()

            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                agentService.streamAgent(request.sessionId, request.message).collect { chunk ->
                    writeString(chunk)
                    flush()
                }
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