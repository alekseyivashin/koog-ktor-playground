package com.aivashin.configuration.dependency

import com.aivashin.configuration.properties.ServiceProperties
import com.aivashin.configuration.telemetry.TelemetryConfig
import com.aivashin.service.agent.AgentChatService
import com.aivashin.service.graph.DatabaseOptimizerGraphService
import com.aivashin.service.llm.LLMChatService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide

fun Application.agentModuleDependencies() {
    dependencies {

        toolsDependencies()
        databaseDependencies()

        provide<ServiceProperties> { resolveProperty("service") }

        provide<TelemetryConfig>(::TelemetryConfig)

        provide<LLMChatService>(::LLMChatService)
        provide<AgentChatService>(::AgentChatService)
        provide<DatabaseOptimizerGraphService>(::DatabaseOptimizerGraphService)
    }
}
