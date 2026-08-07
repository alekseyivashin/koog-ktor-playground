# Graph Report - .  (2026-08-07)

## Corpus Check
- 8 files · ~22,135 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 196 nodes · 222 edges · 39 communities (18 shown, 21 thin omitted)
- Extraction: 78% EXTRACTED · 22% INFERRED · 0% AMBIGUOUS · INFERRED: 48 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Chat Domain Models & History
- Database Tool Registry & Execution
- Ktor Web Routes & Endpoint Tests
- Database Optimizer Graph Service
- Ktor Dependency Injection & Module
- OpenTelemetry & Jaeger Observability
- PostgreSQL Integration Test Rig
- Graph Optimization State Machine
- Structured LLM Response Models
- Graphify File Watcher & Ingestion
- Gradle Wrapper Script
- HikariCP Connection Pool Config
- Graphify Core Engine Protocols
- Ktor Koog Playground Overview
- Agent Memory Chat Service
- JDBC Connection Helper Utilities
- Ktor Application Properties Config
- Cross-Repo Git Graph Merger
- Service Property Configurations
- AST Structural Extraction
- Graph Exporter Formats
- MCP Server Integration
- AGENTS.md Guidelines Integration
- Whisper Audio Transcriber
- JSON Serializer Utility
- Koog Clock Provider
- Message Part Structure
- Kotlin Async Flow Stream
- Result Handling Model
- Content Moderation Result
- Prompt Executor Engine
- Ktor Engine & Async Pipeline
- SSE Stream Frame
- Tokenizer Helper
- Tool Call Protocol

## God Nodes (most connected - your core abstractions)
1. `DatabaseOptimizerGraphServiceIntegrationTest` - 16 edges
2. `GetTableSchemaTool` - 15 edges
3. `ChatMessage` - 12 edges
4. `OptimizerState` - 10 edges
5. `PostgresDatabaseIntegrationTest` - 10 edges
6. `DatabaseToolsUnitTest` - 10 edges
7. `ListDatabaseTablesTool` - 9 edges
8. `LlmChatRoutes` - 9 edges
9. `InMemoryChatHistoryRepository` - 7 edges
10. `ChatHistoryRepositoryUnitTest` - 7 edges

## Surprising Connections (you probably didn't know these)
- `Ktor Koog Playground Architecture` --semantically_similar_to--> `JetBrains Koog AI Integration`  [INFERRED] [semantically similar]
  AGENTS.md → README.md
- `ToolsDependencies` --calls--> `resolveProperty()`  [INFERRED]
  src/main/kotlin/configuration/dependency/ToolsDependencies.kt → src/main/kotlin/configuration/dependency/Utils.kt
- `Ktor Koog Playground Architecture` --references--> `Docker Infrastructure Stack`  [EXTRACTED]
  AGENTS.md → docker-compose.yaml
- `Ktor Service Configuration` --semantically_similar_to--> `Ktor Test Configuration`  [INFERRED] [semantically similar]
  src/main/resources/application.yaml → src/test/resources/application-test.yaml
- `agentModule()` --calls--> `agentModuleDependencies()`  [INFERRED]
  src/main/kotlin/configuration/AgentModule.kt → src/main/kotlin/configuration/dependency/AgentModuleDependencies.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Graphify Watch and Update Workflow** — addwatch_folder_watcher, agents_skills_graphify_references_hooks_commit_hook, agents_skills_graphify_references_update_incremental_extractor [INFERRED 0.85]
- **Database Optimizer Pipeline Graph Nodes** — agents_security_guard_node, agents_context_aggregator_node, agents_solution_architect_node, agents_self_reflection_node [EXTRACTED 1.00]

## Communities (39 total, 21 thin omitted)

### Community 0 - "Chat Domain Models & History"
Cohesion: 0.08
Nodes (10): ChatMessage, ChatRole, ASSISTANT, USER, ChatHistoryRepository, InMemoryChatHistoryRepository, Flow, LLMChatService (+2 more)

### Community 1 - "Database Tool Registry & Execution"
Cohesion: 0.09
Nodes (12): Connection, ConnectionFactory, SimpleTool, ToolsDependencies, Args, GetTableSchemaTool, ListDatabaseTablesTool, PostgresDatabaseIntegrationTest (+4 more)

### Community 2 - "Ktor Web Routes & Endpoint Tests"
Cohesion: 0.16
Nodes (6): ChatRequest, AgentRoutes, GeneralRoutingErrorTests, LlmChatRoutes, OptimizerRoutes, RoutesWebTest

### Community 3 - "Database Optimizer Graph Service"
Cohesion: 0.14
Nodes (4): OptimizerState, DatabaseOptimizerGraphService, OptimizerRetryConfig, DatabaseOptimizerGraphServiceIntegrationTest

### Community 4 - "Ktor Dependency Injection & Module"
Cohesion: 0.17
Nodes (7): agentModule(), agentModuleDependencies(), T, resolveProperty(), agentRouting(), ChatResponse, optimizerRouting()

### Community 5 - "OpenTelemetry & Jaeger Observability"
Cohesion: 0.29
Nodes (5): OpenTelemetryConfig, ConsoleTelemetry, JaegerTelemetry, TelemetryConfig, TelemetryProperties

### Community 6 - "PostgreSQL Integration Test Rig"
Cohesion: 0.40
Nodes (4): DataSource, AbstractPostgresIntegrationTest, seedDatabaseSchema(), setupBaseDatabase()

### Community 7 - "Graph Optimization State Machine"
Cohesion: 0.40
Nodes (5): Context Aggregator Node, Database Optimizer State Machine Graph, Security Guard Node, Self Reflection Audit Node, Solution Architect Node

### Community 8 - "Structured LLM Response Models"
Cohesion: 0.40
Nodes (4): QueryAnalyzerStructuredResponse, SecurityGuardStructuredResponse, SelfReflectionStructuredResponse, SolutionArchitectStructuredResponse

### Community 9 - "Graphify File Watcher & Ingestion"
Cohesion: 0.50
Nodes (4): Folder Watcher, URL Ingestion, Git Post-Commit Hook, Incremental Graph Extractor

### Community 10 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 11 - "HikariCP Connection Pool Config"
Cohesion: 0.50
Nodes (3): ConnectionPoolConfig, DatabaseDependencies, DatabaseProperties

### Community 12 - "Graphify Core Engine Protocols"
Cohesion: 0.67
Nodes (3): Semantic Extraction Subagent Protocol, Graph Query Traversal Engine, Graphify Knowledge Graph Pipeline

### Community 13 - "Ktor Koog Playground Overview"
Cohesion: 0.67
Nodes (3): Ktor Koog Playground Architecture, Docker Infrastructure Stack, JetBrains Koog AI Integration

## Knowledge Gaps
- **32 isolated node(s):** `ConnectionPoolConfig`, `DatabaseDependencies`, `ServiceProperties`, `ConsoleTelemetry`, `JaegerTelemetry` (+27 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **21 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ToolsDependencies` connect `Database Tool Registry & Execution` to `Ktor Dependency Injection & Module`?**
  _High betweenness centrality (0.115) - this node is a cross-community bridge._
- **Why does `resolveProperty()` connect `Ktor Dependency Injection & Module` to `Database Tool Registry & Execution`?**
  _High betweenness centrality (0.114) - this node is a cross-community bridge._
- **Are the 9 inferred relationships involving `GetTableSchemaTool` (e.g. with `ToolsDependencies` and `.setUp()`) actually correct?**
  _`GetTableSchemaTool` has 9 INFERRED edges - model-reasoned connections that need verification._
- **Are the 7 inferred relationships involving `ChatMessage` (e.g. with `.askLLM()` and `.streamLLM()`) actually correct?**
  _`ChatMessage` has 7 INFERRED edges - model-reasoned connections that need verification._
- **Are the 9 inferred relationships involving `OptimizerState` (e.g. with `.runOptimization()` and `.`graph structure - security guard edge routing based on isSafe`()`) actually correct?**
  _`OptimizerState` has 9 INFERRED edges - model-reasoned connections that need verification._
- **What connects `ConnectionPoolConfig`, `DatabaseDependencies`, `ServiceProperties` to the rest of the system?**
  _32 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Chat Domain Models & History` be split into smaller, more focused modules?**
  _Cohesion score 0.07954545454545454 - nodes in this community are weakly interconnected._