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
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.serialization.Serializable
import javax.sql.DataSource


@Serializable
data class DatabaseProperties(
    val host: String,
    val port: Int,
    val name: String,
    val user: String,
    val password: String,
    val jdbcPool: ConnectionPoolConfig = ConnectionPoolConfig(),
    val r2dbcPool: ConnectionPoolConfig = ConnectionPoolConfig(),
) {

    @Serializable
    data class ConnectionPoolConfig(
        val minIdleSize: Int = 0,
        val maxPoolSize: Int = 10,
    )
}

object DatabaseDependencies {
    const val AGENT_CHAT_HISTORY_PROVIDER_NAME = "agentChatHistoryProvider"
    const val DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME = "databaseOptimizerHistoryProvider"
}

fun DependencyRegistry.databaseDependencies() {

    provide<DataSource> {
        val properties = resolveProperty<DatabaseProperties>("database")
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:postgresql://${properties.host}:${properties.port}/${properties.name}"
                username = properties.user
                password = properties.password
                minimumIdle = properties.jdbcPool.minIdleSize
                maximumPoolSize = properties.jdbcPool.maxPoolSize
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

    provide<ConnectionFactory> {
        val properties = resolveProperty<DatabaseProperties>("database")
        val connectionFactory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, "pool")
            .option(ConnectionFactoryOptions.PROTOCOL, "postgresql")
            .option(ConnectionFactoryOptions.HOST, properties.host)
            .option(ConnectionFactoryOptions.PORT, properties.port)
            .option(ConnectionFactoryOptions.DATABASE, properties.name)
            .option(ConnectionFactoryOptions.USER, properties.user)
            .option(ConnectionFactoryOptions.PASSWORD, properties.password)
            .build()
        )
        val configuration = ConnectionPoolConfiguration.builder(connectionFactory)
            .minIdle(properties.r2dbcPool.minIdleSize)
            .maxSize(properties.r2dbcPool.maxPoolSize)
            .build()
        ConnectionPool(configuration)
    }.also {
        require(it.key)
    }

    provide<ChatHistoryRepository>(::InMemoryChatHistoryRepository)
}
