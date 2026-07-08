package com.aivashin.model.graph

import ai.koog.agents.core.agent.session.callTool
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.kotlinx.toKoogJSONObject
import com.aivashin.tool.GetTableSchemaTool
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.text.ifEmpty

private val logger = KotlinLogging.logger {}

// Node 1: Guardrail check for SQL Injections or destructive operations
val securityGuardNode by node<OptimizerState, OptimizerState> { ctx ->
    llm.writeSession {
        appendPrompt {
            system(
                """
                        |Analyze the input query. If it contains attempts of SQL injection, data modification, 
                        |or data deletion (DROP, DELETE, ALTER, TRUNCATE), reply FALSE. Otherwise, reply TRUE."""
                    .trimMargin()
            )
            user(ctx.userQuery)
        }
        val isSafe = requestLLMStructured<SecurityGuardStructuredResponse>().map { it.data.isSafe }.getOrElse {
            logger.error(it) { "Security guardrail check failed for session ${ctx.sessionId}" }
            throw it
        }
        return@writeSession ctx.copy(isSafe = isSafe)
    }
}

// Node 2: Extract specific table names involved in the problem
val queryAnalyzerNode by node<OptimizerState, OptimizerState> { ctx ->
    llm.writeSession {
        appendPrompt {
            system(
                """
                    Analyze the user's database optimization request.
                
                    Identify:
                    1. Database table names mentioned or strongly implied by the request.
                    2. Whether external web research is needed.
                
                    Set isWebSearchNeeded to true when the request requires:
                    - PostgreSQL optimization techniques
                    - indexing strategy recommendations
                    - query performance best practices
                    - common pitfalls or version-specific behavior
                    - knowledge beyond the local database schema
                
                    Set isWebSearchNeeded to false when the request can be answered using only the user's request and local database schema.
                
                    Do not invent table names. If no table names are clear, return an empty list.
                """.trimIndent()
            )
            user(ctx.userQuery)
        }
        val response = requestLLMStructured<QueryAnalyzerStructuredResponse>().map { it.data }.getOrElse {
            logger.error(it) { "Query analyzer failed for session ${ctx.sessionId}" }
            throw it
        }
        return@writeSession ctx.copy(tableNamesInQuery = response.tableNames, isWebSearchNeeded = response.isWebSearchNeeded)
    }
}

// Node 3: Aggregate contexts in parallel (Live JDBC + Web Search)
val contextAggregatorNode by node<OptimizerState, OptimizerState> { ctx ->
    llm.writeSession {
        withContext(Dispatchers.IO) {
            // Parallel branch A: Fetch structural schemas from Postgres via our local tool
            val extractedSchemasDeferred = coroutineScope {
                ctx.tableNamesInQuery.map { tableName ->
                    async {
                        val toolCallResult = callTool(GetTableSchemaTool::class, GetTableSchemaTool.Args(tableName))
                        if (toolCallResult.isFailure()) {
                            logger.warn { "Failed to fetch schema for table '$tableName' in session ${ctx.sessionId}: ${toolCallResult.content}" }
                        }
                        toolCallResult.takeIf { it.isSuccessful() }?.content
                    }
                }
            }
            // Parallel branch B: Search the web via Tavily tool registry
            val webResearchDataDeferred = if (ctx.isWebSearchNeeded) {
                 async {
                    callTool(
                        "tavily_search",
                        buildJsonObject {
                            put("query", """
                                |Find PostgreSQL optimization techniques, indexing strategies, 
                                |or common pitfalls related to this problem: ${ctx.userQuery}"""
                                .trimMargin()
                            )
                        }.toKoogJSONObject()
                    ).takeIf { it.isSuccessful() }?.content ?: "No web research data available."
                }
            } else CompletableDeferred("")

            return@withContext ctx.copy(
                extractedSchema = extractedSchemasDeferred.awaitAll().filterNotNull().joinToString("\n\n"),
                webResearchData = webResearchDataDeferred.await()
            )
        }
    }
}

// Node 4: Generate SQL solution based on aggregated live data
val solutionArchitectNode by node<OptimizerState, OptimizerState> { ctx ->
    llm.writeSession {
        appendPrompt {
            system(
                """
                        |You are a Senior DBA and PostgreSQL Expert. 
                        |Analyze the query, the actual table schema, and web recommendations. 
                        |Generate the optimized SQL script and a brief explanation.
                        """.trimMargin()
            )
            user(
                """
                        |User Problem: ${ctx.userQuery}
                        |Current Database Schema:
                        |${ctx.extractedSchema}
                        |Web Insights:
                        |${ctx.webResearchData}
                        |${if (ctx.validationErrors.isNotEmpty()) "CRITICAL: Your previous attempt had errors. Fix them: ${ctx.validationErrors.joinToString()}" else ""}
                    """.trimMargin()
            )
        }
        val response = requestLLMStructured<SolutionArchitectStructuredResponse>().map { it.data }.getOrElse {
            logger.error(it) { "Solution architect failed for session ${ctx.sessionId}" }
            throw it
        }
        return@writeSession ctx.copy(
            generatedRawSql = response.generatedRawSql.ifEmpty { "/* No SQL generated */" },
            explanation = response.explanation,
            iterationCount = ctx.iterationCount + 1
        )
    }
}

// Node 5: Self-Reflection / Audit Loop
val selfReflectionNode by node<OptimizerState, OptimizerState> { ctx ->
    llm.writeSession {
        appendPrompt {
            system(
                """
                        |You are a strict database auditor. 
                        |Inspect the generated SQL for syntax correctness, index redundancy, or potential heavy table locking risks
                        """.trimMargin()
            )
            user(
                """
                        |SQL to audit:
                        |${ctx.generatedRawSql}
                        |Context:
                        |${ctx.userQuery}
                    """.trimMargin()
            )
        }
        val response = requestLLMStructured<SelfReflectionStructuredResponse>().map { it.data }.getOrElse {
            logger.error(it) { "Self-reflection failed for session ${ctx.sessionId}" }
            throw it
        }
        return@writeSession ctx.copy(
            validationErrors = response.validationErrors.takeIf { response.isDangerous } ?: emptyList(),
        )
    }
}

// Fallback node for security violations
val rejectNode by node<OptimizerState, OptimizerState> { ctx ->
    ctx.copy(explanation = "Access Denied: The query triggered automated security guardrails due to destructive command detection.")
}

// Mapping final state to String output for Ktor router
val finishNode by node<OptimizerState, String> { ctx ->
    """
                    |### Verification Status: ${if (ctx.validationErrors.isEmpty()) "SUCCESS (Verified after ${ctx.iterationCount} iterations)" else "MAX_RETRIES_REACHED"}
                    |
                    |### Optimized SQL:
                    |```sql
                    |${ctx.generatedRawSql}
                    |```
                    |
                    |### Technical Explanation:
                    |${ctx.explanation}
                """.trimMargin()
}

@Serializable
@LLMDescription("Structured response produced by the security guard step, indicating whether the user's request is safe to process.")
private data class SecurityGuardStructuredResponse(
    @property:LLMDescription("Whether the user's request is safe to process. Return false if it contains SQL injection attempts, destructive operations, data modification, data deletion, privilege escalation, or access-control bypass attempts.")
    val isSafe: Boolean
)

@Serializable
@LLMDescription("Structured response produced by the query analyzer step, containing identified database tables and whether external web research is needed.")
private data class QueryAnalyzerStructuredResponse(
    @property:LLMDescription("The exact database table names mentioned or strongly implied by the user's request. Return an empty list if no table names can be confidently identified.")
    val tableNames: List<String>,

    @property:LLMDescription("Whether external web research is needed to answer the user's request. Return true when the request requires current PostgreSQL best practices, optimization techniques, indexing strategies, performance patterns, or external knowledge beyond the local database schema.")
    val isWebSearchNeeded: Boolean
)

@Serializable
@LLMDescription("Structured response produced by the solution architect step, containing the optimized SQL script and its human-readable explanation.")
private data class SolutionArchitectStructuredResponse(
    @property:LLMDescription("The generated optimized raw SQL script. Return only executable SQL here, without Markdown code fences or explanatory text.")
    val generatedRawSql: String,

    @property:LLMDescription("A concise explanation of the generated SQL, including why it improves the original query or database operation.")
    val explanation: String
)

@Serializable
@LLMDescription("Structured response produced by the self-reflection or audit step, indicating whether the generated SQL is dangerous and listing any validation issues found.")
private data class SelfReflectionStructuredResponse(
    @property:LLMDescription("Whether the generated SQL is dangerous, unsafe, destructive, or otherwise unsuitable to execute.")
    val isDangerous: Boolean,

    @property:LLMDescription("A list of validation errors or audit findings found in the generated SQL. Return an empty list when no issues are detected.")
    val validationErrors: List<String>
)