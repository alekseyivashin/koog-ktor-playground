package com.aivashin.service.graph

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import com.aivashin.configuration.dependency.DatabaseDependencies.DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME
import com.aivashin.configuration.dependency.ToolsDependencies.COMMON_TOOL_REGISTRY_NAME
import com.aivashin.configuration.telemetry.TelemetryConfig
import com.aivashin.model.graph.OptimizerState
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.annotations.Named
import io.ktor.server.plugins.di.annotations.Property
import kotlinx.serialization.Serializable
import optimizerStrategy
import kotlin.time.Duration.Companion.seconds

@Serializable
data class OptimizerRetryConfig(
    val maxAttempts: Int = 3,
    val initialDelaySeconds: Long = 1,
    val maxDelaySeconds: Long = 30,
    val backoffMultiplier: Double = 2.0,
)

class DatabaseOptimizerGraphService(
    @Property("agents.api.geminiApiKey") private val apiKey: String,
    @Property("agents.optimizer.retry") private val optimizerRetryConfig: OptimizerRetryConfig,
    @Named(DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME) private val databaseOptimizerHistoryProvider: ChatHistoryProvider,
    @Named(COMMON_TOOL_REGISTRY_NAME) private val toolRegistry: ToolRegistry,
    private val telemetryConfig: TelemetryConfig,
) {

    private val logger = KotlinLogging.logger {}

    private val client = RetryingLLMClient(
        delegate = GoogleLLMClient(apiKey),
        config = RetryConfig(
            maxAttempts = optimizerRetryConfig.maxAttempts,
            initialDelay = optimizerRetryConfig.initialDelaySeconds.seconds,
            maxDelay = optimizerRetryConfig.maxDelaySeconds.seconds,
            backoffMultiplier = optimizerRetryConfig.backoffMultiplier,
        )
    )

    private val researchAgent = AIAgent(
        promptExecutor = PromptExecutor.builder()
            .addClient(client)
            .build(),
        llmModel = GoogleModels.Gemini3_1FlashLite,
        systemPrompt = """
            You are a senior database research assistant specializing in PostgreSQL performance optimization.
            Your job is to help investigate database performance issues, indexing strategies, 
            query optimization patterns, schema design concerns, and PostgreSQL best practices.
            """.trimIndent(),
        toolRegistry = toolRegistry,
        strategy = optimizerStrategy
    ) {
        install(ChatMemory.Feature) {
            chatHistoryProvider(databaseOptimizerHistoryProvider)
            windowSize(100)
        }
        install(OpenTelemetry, telemetryConfig::invoke)
        install(Tracing) {
            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
        }
    }

    suspend fun runOptimization(userQuery: String, sessionId: String): String {
        val initialState = OptimizerState(sessionId = sessionId, userQuery = userQuery)
        return researchAgent.run(initialState, sessionId)
    }

}