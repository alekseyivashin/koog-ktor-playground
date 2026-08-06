package com.aivashin.integration

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.feature.testGraph
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.google.GoogleModels
import com.aivashin.model.graph.OptimizerState
import com.aivashin.tool.GetTableSchemaTool
import com.aivashin.tool.ListDatabaseTablesTool
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import optimizerStrategy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration tests for the Database Optimizer Graph pipeline.
 *
 * These tests recreate test-only [AIAgent] instances using the same public
 * [optimizerStrategy] and nodes from [com.aivashin.model.graph] to verify
 * graph structure, edge routing, and end-to-end mock LLM interactions
 * without depending on [com.aivashin.service.graph.DatabaseOptimizerGraphService].
 *
 * **Mock LLM notes:**
 * All nodes use `requestLLMStructured<T>()`, which goes through `executeStructured`.
 * In the mock executor, this results in the last message having empty textContent,
 * so `onCondition`/`onRequestContains` never match. Only `asDefaultResponse` is
 * effective. Each structured response type ignores unknown JSON fields, so a single
 * "universal" JSON containing all fields from all structured types satisfies every node.
 *
 * Different pipeline behaviors (safe/unsafe, validation pass/fail) are controlled
 * by varying the fields in the default response JSON.
 */
class DatabaseOptimizerGraphServiceIntegrationTest : AbstractPostgresIntegrationTest() {

    private lateinit var toolRegistry: ToolRegistry

    @BeforeEach
    fun setUp() {
        toolRegistry = ToolRegistry {
            tool(ListDatabaseTablesTool(dataSource))
            tool(GetTableSchemaTool(dataSource))
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 1. Graph Structure & Reachability (testGraph DSL)
    // ───────────────────────────────────────────────────────────────────────

    @Test
    fun `graph structure - all nodes exist and are reachable`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{"isSafe": true}""").asDefaultResponse
        }

        AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) {
            withTesting()

            testGraph<OptimizerState, String>("db-optimization-pipeline") {
                val securityGuard = assertNodeByName<OptimizerState, OptimizerState>("securityGuardNode")
                val queryAnalyzer = assertNodeByName<OptimizerState, OptimizerState>("queryAnalyzerNode")
                val contextAggregator = assertNodeByName<OptimizerState, OptimizerState>("contextAggregatorNode")
                val solutionArchitect = assertNodeByName<OptimizerState, OptimizerState>("solutionArchitectNode")
                val selfReflection = assertNodeByName<OptimizerState, OptimizerState>("selfReflectionNode")
                val reject = assertNodeByName<OptimizerState, OptimizerState>("rejectNode")
                val finish = assertNodeByName<OptimizerState, String>("finishNode")

                // Happy path reachability
                assertReachable(startNode(), securityGuard)
                assertReachable(securityGuard, queryAnalyzer)
                assertReachable(queryAnalyzer, contextAggregator)
                assertReachable(contextAggregator, solutionArchitect)
                assertReachable(solutionArchitect, selfReflection)
                assertReachable(selfReflection, finish)
                assertReachable(finish, finishNode())

                // Rejection path reachability
                assertReachable(securityGuard, reject)
                assertReachable(reject, finish)

                // Self-correction loop
                assertReachable(selfReflection, solutionArchitect)
            }
        }
    }

    @Test
    fun `graph structure - security guard edge routing based on isSafe`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{"isSafe": true}""").asDefaultResponse
        }

        AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) {
            withTesting()

            testGraph<OptimizerState, String>("db-optimization-pipeline") {
                val securityGuard = assertNodeByName<OptimizerState, OptimizerState>("securityGuardNode")
                val queryAnalyzer = assertNodeByName<OptimizerState, OptimizerState>("queryAnalyzerNode")
                val reject = assertNodeByName<OptimizerState, OptimizerState>("rejectNode")

                assertEdges {
                    securityGuard withOutput OptimizerState(sessionId = "s", userQuery = "q", isSafe = true) goesTo queryAnalyzer
                    securityGuard withOutput OptimizerState(sessionId = "s", userQuery = "q", isSafe = false) goesTo reject
                }
            }
        }
    }

    @Test
    fun `graph structure - self-correction loop vs exit edge routing`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{"isSafe": true}""").asDefaultResponse
        }

        AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) {
            withTesting()

            testGraph<OptimizerState, String>("db-optimization-pipeline") {
                val solutionArchitect = assertNodeByName<OptimizerState, OptimizerState>("solutionArchitectNode")
                val selfReflection = assertNodeByName<OptimizerState, OptimizerState>("selfReflectionNode")
                val finish = assertNodeByName<OptimizerState, String>("finishNode")

                assertEdges {
                    // Validation errors + low iteration → loops back to solutionArchitect
                    selfReflection withOutput OptimizerState(
                        sessionId = "s", userQuery = "q",
                        validationErrors = listOf("bad index"), iterationCount = 1
                    ) goesTo solutionArchitect

                    // No validation errors → exits to finishNode
                    selfReflection withOutput OptimizerState(
                        sessionId = "s", userQuery = "q",
                        validationErrors = emptyList(), iterationCount = 1
                    ) goesTo finish

                    // Max retries (iterationCount >= 3) → exits regardless of errors
                    selfReflection withOutput OptimizerState(
                        sessionId = "s", userQuery = "q",
                        validationErrors = listOf("still bad"), iterationCount = 3
                    ) goesTo finish
                }
            }
        }
    }

    @Test
    fun `graph structure - start node always routes to security guard`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{"isSafe": true}""").asDefaultResponse
        }

        AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) {
            withTesting()

            testGraph<OptimizerState, String>("db-optimization-pipeline") {
                val securityGuard = assertNodeByName<OptimizerState, OptimizerState>("securityGuardNode")
                assertEdges {
                    startNode() alwaysGoesTo securityGuard
                }
            }
        }
    }

    @Test
    fun `graph structure - reject and finish always route to terminal`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{"isSafe": true}""").asDefaultResponse
        }

        AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) {
            withTesting()

            testGraph<OptimizerState, String>("db-optimization-pipeline") {
                val reject = assertNodeByName<OptimizerState, OptimizerState>("rejectNode")
                val finish = assertNodeByName<OptimizerState, String>("finishNode")

                assertEdges {
                    reject alwaysGoesTo finish
                    finish alwaysGoesTo finishNode()
                }
            }
        }
    }

    @Test
    fun `graph structure - linear chain from queryAnalyzer to selfReflection`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{"isSafe": true}""").asDefaultResponse
        }

        AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) {
            withTesting()

            testGraph<OptimizerState, String>("db-optimization-pipeline") {
                val queryAnalyzer = assertNodeByName<OptimizerState, OptimizerState>("queryAnalyzerNode")
                val contextAggregator = assertNodeByName<OptimizerState, OptimizerState>("contextAggregatorNode")
                val solutionArchitect = assertNodeByName<OptimizerState, OptimizerState>("solutionArchitectNode")
                val selfReflection = assertNodeByName<OptimizerState, OptimizerState>("selfReflectionNode")

                assertEdges {
                    queryAnalyzer alwaysGoesTo contextAggregator
                    contextAggregator alwaysGoesTo solutionArchitect
                    solutionArchitect alwaysGoesTo selfReflection
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 2. Pipeline E2E Tests (agent.run with mock LLM)
    //
    // requestLLMStructured sends prompts whose last message has empty
    // textContent in the mock, so only asDefaultResponse fires.
    // A single "universal" JSON with all fields satisfies every node.
    //
    // Behavior is varied by changing the response fields:
    //  - isSafe: true/false → security guard pass/reject
    //  - isDangerous + validationErrors → self-reflection pass/fail
    //  - generatedRawSql/explanation → solution architect output
    // ───────────────────────────────────────────────────────────────────────

    @Test
    fun `pipeline - successful single-pass optimization (happy path)`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{
                "isSafe": true,
                "tableNames": ["users"],
                "isWebSearchNeeded": false,
                "generatedRawSql": "CREATE INDEX idx_users_email ON users (email);",
                "explanation": "B-tree index on email for fast equality lookups.",
                "isDangerous": false,
                "validationErrors": []
            }""").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) { withTesting() }

        val result = agent.run(
            OptimizerState(sessionId = "session-happy", userQuery = "Slow query filtering users by email"),
            "session-happy"
        )

        result shouldContain "SUCCESS"
        result shouldContain "CREATE INDEX idx_users_email ON users (email);"
        result shouldContain "B-tree index on email for fast equality lookups."
    }

    @Test
    fun `pipeline - security guard blocks unsafe query`() = runTest {
        val mockLLM = getMockExecutor {
            // isSafe=false → securityGuardNode sets isSafe=false → routes to rejectNode
            mockLLMAnswer("""{
                "isSafe": false,
                "tableNames": [],
                "isWebSearchNeeded": false,
                "generatedRawSql": "",
                "explanation": "",
                "isDangerous": false,
                "validationErrors": []
            }""").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) { withTesting() }

        val result = agent.run(
            OptimizerState(sessionId = "session-blocked", userQuery = "DROP TABLE users; DELETE FROM audit_logs;"),
            "session-blocked"
        )

        result shouldContain "Access Denied"
        result shouldContain "destructive command detection"
        result shouldNotContain "CREATE INDEX"
    }

    @Test
    fun `pipeline - output format includes SQL fenced block and explanation`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{
                "isSafe": true,
                "tableNames": ["orders"],
                "isWebSearchNeeded": false,
                "generatedRawSql": "CREATE INDEX CONCURRENTLY idx_orders_created_at ON orders (created_at);",
                "explanation": "Non-blocking index creation on timestamp column.",
                "isDangerous": false,
                "validationErrors": []
            }""").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) { withTesting() }

        val result = agent.run(
            OptimizerState(sessionId = "session-format", userQuery = "Optimize date range queries on orders"),
            "session-format"
        )

        result shouldContain "### Verification Status: SUCCESS"
        result shouldContain "### Optimized SQL:"
        result shouldContain "```sql"
        result shouldContain "CREATE INDEX CONCURRENTLY idx_orders_created_at ON orders (created_at);"
        result shouldContain "### Technical Explanation:"
        result shouldContain "Non-blocking index creation on timestamp column."
    }

    @Test
    fun `pipeline - system error in security guard produces interrupted message`() = runTest {
        val mockLLM = getMockExecutor {
            // Return invalid JSON so requestLLMStructured fails → systemErrors populated
            mockLLMAnswer("THIS IS NOT JSON").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) { withTesting() }

        val result = agent.run(
            OptimizerState(sessionId = "session-syserr", userQuery = "Some query"),
            "session-syserr"
        )

        result shouldContain "Optimization Process Interrupted"
        result shouldContain "Upstream AI Model Providers are currently unavailable"
    }

    @Test
    fun `pipeline - empty generatedRawSql gets fallback placeholder`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{
                "isSafe": true,
                "tableNames": ["metrics"],
                "isWebSearchNeeded": false,
                "generatedRawSql": "",
                "explanation": "No optimization needed",
                "isDangerous": false,
                "validationErrors": []
            }""").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) { withTesting() }

        val result = agent.run(
            OptimizerState(sessionId = "session-empty-sql", userQuery = "Check metrics table performance"),
            "session-empty-sql"
        )

        // solutionArchitectNode replaces empty SQL with "/* No SQL generated */"
        result shouldContain "/* No SQL generated */"
    }

    @Test
    fun `pipeline - multiple tables extracted by query analyzer`() = runTest {
        val mockLLM = getMockExecutor {
            mockLLMAnswer("""{
                "isSafe": true,
                "tableNames": ["orders", "order_items", "products"],
                "isWebSearchNeeded": false,
                "generatedRawSql": "CREATE INDEX idx_oi_order_id ON order_items (order_id);",
                "explanation": "Index join key for order-items relationship.",
                "isDangerous": false,
                "validationErrors": []
            }""").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = mockLLM,
            llmModel = GoogleModels.Gemini3_1FlashLite,
            strategy = optimizerStrategy,
            toolRegistry = toolRegistry
        ) { withTesting() }

        val result = agent.run(
            OptimizerState(sessionId = "session-multi-table", userQuery = "Optimize order items join with orders and products"),
            "session-multi-table"
        )

        result shouldContain "SUCCESS"
        result shouldContain "CREATE INDEX idx_oi_order_id ON order_items (order_id);"
    }
}
