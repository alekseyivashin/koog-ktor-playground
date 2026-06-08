package com.aivashin.routing

import com.aivashin.properties.Agents
import com.aivashin.service.AgentService
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.getAs
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeString
import kotlinx.serialization.Serializable

fun Application.agentRouting() {
    val a = environment.config
    val agentsProperties = environment.config.config("agents").getAs<Agents>()

    install(ContentNegotiation) {
        json()
    }

    val agentService = AgentService(agentsProperties.api.geminiApiKey)

    routing {
        post("/chat") {
            val request = call.receive<ChatRequest>()
            val reply = agentService.askAgent(request.message)
            call.respond(ChatResponse(reply))
        }

        post("/chat/stream") {
            val request = call.receive<ChatRequest>()

            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                agentService.streamAgent(request.message).collect { chunk ->
                    writeString("data: $chunk\n\n")
                    flush()
                }
            }
        }
    }
}

@Serializable
data class ChatRequest(val message: String)

@Serializable
data class ChatResponse(val reply: String)