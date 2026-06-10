package com.aivashin.configuration

import com.aivashin.repository.ChatHistoryRepository
import com.aivashin.repository.InMemoryChatHistoryRepository
import com.aivashin.service.AgentService
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide

fun Application.agentModuleDependencies() {
    dependencies {

        provide<ChatHistoryRepository>(::InMemoryChatHistoryRepository)

        provide<AgentService>(::AgentService)
    }
}