package com.aivashin.routing

import com.aivashin.service.graph.DatabaseOptimizerGraphService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.optimizerRouting() {

    val databaseOptimizerGraphService: DatabaseOptimizerGraphService by dependencies

    routing {
        route("/optimizer") {
            post("/database") {
                val request = call.receive<ChatRequest>()
                val reply = databaseOptimizerGraphService.runOptimization(request.message, request.sessionId)
                call.respond(ChatResponse(request.sessionId, reply))
            }
        }
    }
}