package com.aivashin.configuration.dependency

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.fromProcess
import com.aivashin.tool.GetTableSchemaTool
import com.aivashin.tool.ListDatabaseTablesTool
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.DependencyRegistry
import javax.sql.DataSource

const val COMMON_TOOL_REGISTRY_NAME = "commonToolRegistry"

fun DependencyRegistry.toolsDependencies() {

    val logger = KotlinLogging.logger {}

    val localToolRegistryName = "localToolRegistry"
    val mcpToolRegistryName = "mcpToolRegistry"

    provide<ListDatabaseTablesTool> { ListDatabaseTablesTool(resolve<DataSource>()) }
    provide<GetTableSchemaTool> { GetTableSchemaTool(resolve<DataSource>()) }

    provide(localToolRegistryName) {
        val tools = listOf(
            resolve<ListDatabaseTablesTool>(),
            resolve<GetTableSchemaTool>(),
        )
        ToolRegistry {
            tools(tools)
        }
    }

    provide(mcpToolRegistryName) {
        runCatching {
            val tavilyProcess = ProcessBuilder("docker", "run", "-i", "--rm",
                "-e", "TAVILY_API_KEY",
                "mcp/tavily"
            ).apply {
                environment()["TAVILY_API_KEY"] = resolveProperty("agents.mcp.tavilyApiKey")
            }.start()

            McpToolRegistryProvider.fromProcess(tavilyProcess)

        }.getOrElse {
            logger.error { "Failed to start MCP gateway: ${it.message}. Proceed with other tool registry." }
            ToolRegistry.EMPTY
        }
    }

    provide(COMMON_TOOL_REGISTRY_NAME) {
        val localRegistry = resolve<ToolRegistry>(localToolRegistryName)
        val mcpRegistry = resolve<ToolRegistry>(mcpToolRegistryName)
        localRegistry + mcpRegistry
    }
}
