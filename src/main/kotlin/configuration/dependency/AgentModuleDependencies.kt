package com.aivashin.configuration.dependency

import ai.koog.agents.features.chathistory.jdbc.PostgresJdbcChatHistoryProvider
import ai.koog.agents.features.chatmemory.sql.SQLChatHistoryProvider
import com.aivashin.repository.ChatHistoryRepository
import com.aivashin.repository.InMemoryChatHistoryRepository
import com.aivashin.service.agent.AgentChatService
import com.aivashin.service.llm.LLMChatService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import javax.sql.DataSource

fun Application.agentModuleDependencies() {
    dependencies {

        toolsDependencies()

        provide<DataSource> {
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = resolveProperty("database.url")
                    username = resolveProperty("database.user")
                    this.password = resolveProperty("database.password")
                }
            )
        }.also {
            require(it.key)
        }

        provide<SQLChatHistoryProvider> {
            PostgresJdbcChatHistoryProvider(
                dataSource = resolve<DataSource>(),
                tableName = "agent_chat_history",
            ).also {
                it.migrate()
            }
        }.also {
            require(it.key)
        }

        provide<ChatHistoryRepository>(::InMemoryChatHistoryRepository)

        provide<LLMChatService>(::LLMChatService)
        provide<AgentChatService>(::AgentChatService)
    }
}
