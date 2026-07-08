package com.aivashin.configuration.dependency

import ai.koog.agents.features.chathistory.jdbc.PostgresJdbcChatHistoryProvider
import ai.koog.agents.features.chatmemory.sql.SQLChatHistoryProvider
import com.aivashin.configuration.dependency.DatabaseDependencies.AGENT_CHAT_HISTORY_PROVIDER_NAME
import com.aivashin.configuration.dependency.DatabaseDependencies.DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME
import com.aivashin.repository.ChatHistoryRepository
import com.aivashin.repository.InMemoryChatHistoryRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.provide
import javax.sql.DataSource


object DatabaseDependencies {
    const val AGENT_CHAT_HISTORY_PROVIDER_NAME = "agentChatHistoryProvider"
    const val DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME = "databaseOptimizerHistoryProvider"
}
const val COMMON_TOOL_REGISTRY_NAME = "commonToolRegistry"

fun DependencyRegistry.databaseDependencies() {

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

    provide<SQLChatHistoryProvider>(AGENT_CHAT_HISTORY_PROVIDER_NAME) {
        PostgresJdbcChatHistoryProvider(
            dataSource = resolve<DataSource>(),
            tableName = "agent_chat_history",
        ).also {
            it.migrate()
        }
    }.also {
        require(it.key)
    }

    provide<SQLChatHistoryProvider>(DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME) {
        PostgresJdbcChatHistoryProvider(
            dataSource = resolve<DataSource>(),
            tableName = "database_optimizer_history",
        ).also {
            it.migrate()
        }
    }.also {
        require(it.key)
    }

    provide<ChatHistoryRepository>(::InMemoryChatHistoryRepository)
}
