package com.aivashin.configuration

import com.aivashin.configuration.dependency.agentModuleDependencies
import com.aivashin.routing.agentRouting
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun Application.agentModule() {
    install(ContentNegotiation) {
        json()
    }
    agentModuleDependencies()
    agentRouting()
}