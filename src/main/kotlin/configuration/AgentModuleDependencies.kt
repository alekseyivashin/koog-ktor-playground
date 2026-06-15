package com.aivashin.configuration

import ai.koog.agents.features.chathistory.jdbc.PostgresJdbcChatHistoryProvider
import ai.koog.agents.features.chatmemory.sql.SQLChatHistoryProvider
import com.aivashin.repository.ChatHistoryRepository
import com.aivashin.repository.InMemoryChatHistoryRepository
import com.aivashin.service.agent.AgentChatService
import com.aivashin.service.llm.LLMChatService
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.annotations.Property
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import javax.sql.DataSource

fun Application.agentModuleDependencies() {
    dependencies {

        provide<DataSource>(::provideDatasource).also {
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

fun provideDatasource(
    @Property("database.url") url: String,
    @Property("database.user") user: String,
    @Property("database.password") password: String,
): DataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = url
        username = user
        this.password = password
    }
)