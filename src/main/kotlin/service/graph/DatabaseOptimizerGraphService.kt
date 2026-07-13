package com.aivashin.service.graph

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.session.callTool
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryablePattern
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import com.aivashin.configuration.dependency.COMMON_TOOL_REGISTRY_NAME
import com.aivashin.configuration.dependency.DatabaseDependencies.DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME
import com.aivashin.model.graph.OptimizerState
import com.aivashin.model.graph.contextAggregatorNode
import com.aivashin.model.graph.finishNode
import com.aivashin.model.graph.queryAnalyzerNode
import com.aivashin.model.graph.rejectNode
import com.aivashin.model.graph.securityGuardNode
import com.aivashin.model.graph.selfReflectionNode
import com.aivashin.model.graph.solutionArchitectNode
import com.aivashin.tool.GetTableSchemaTool
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.annotations.Named
import io.ktor.server.plugins.di.annotations.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.map
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
) {

    private val logger = KotlinLogging.logger {}

    // Building the core declarative Koog Strategy Graph
    private val optimizerStrategy = strategy<OptimizerState, String>("db-optimization-pipeline") {

        // --- Topology and Routing Transitions ---
        edge(nodeStart forwardTo securityGuardNode)

        edge(securityGuardNode forwardTo queryAnalyzerNode onCondition { it.isSafe })
        edge(securityGuardNode forwardTo rejectNode onCondition { it.isSafe.not() })

        edge(queryAnalyzerNode forwardTo contextAggregatorNode)
        edge(contextAggregatorNode forwardTo solutionArchitectNode)
        edge(solutionArchitectNode forwardTo selfReflectionNode)

        // The Self-Correction Loop edge
        edge(selfReflectionNode forwardTo solutionArchitectNode onCondition {
            it.validationErrors.isNotEmpty() && it.iterationCount < 3
        })

        // Safe exit edge
        edge(selfReflectionNode forwardTo finishNode onCondition {
            it.validationErrors.isEmpty() || it.iterationCount >= 3
        })

        edge(rejectNode forwardTo finishNode)
        edge(finishNode forwardTo nodeFinish)
    }

    val client = RetryingLLMClient(
        delegate = GoogleLLMClient(apiKey),
        config = RetryConfig(
            maxAttempts = optimizerRetryConfig.maxAttempts,
            initialDelay = optimizerRetryConfig.initialDelaySeconds.seconds,
            maxDelay = optimizerRetryConfig.maxDelaySeconds.seconds,
            backoffMultiplier = optimizerRetryConfig.backoffMultiplier,
        )
    )

    // Dedicated agent for web research via Tavily (contained in the common registry)
    private val researchAgent = AIAgent(
        promptExecutor = PromptExecutor.builder()
            .addClient(client)
            .build(),
        llmModel = GoogleModels.Gemini2_5Flash,
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
        handleEvents {
            onNodeExecutionStarting {
                logger.info { "Node ${it.node.name} execution starting with input: ${it.input}" }
            }
            onNodeExecutionCompleted {
                logger.info { "Node ${it.node.name} execution completed with output: ${it.output}" }
            }
            onLLMCallCompleted {
                logger.info { "LLM call completed. Response: ${it.response}" }
            }
            onToolCallCompleted {
                logger.info { "Tool call completed: $it" }
            }
        }
    }

    suspend fun runOptimization(userQuery: String, sessionId: String): String {
        val initialState = OptimizerState(sessionId = sessionId, userQuery = userQuery)
        return researchAgent.run(initialState, sessionId)
    }

}