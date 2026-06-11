package com.aivashin.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable



// Simulated database metadata repository
private val mockSchema = mapOf(
    "users" to "id: BIGINT (PK), email: VARCHAR(255), created_at: TIMESTAMP",
    "orders" to "id: BIGINT (PK), user_id: BIGINT (FK -> users.id), total_amount: DECIMAL(10,2), status: VARCHAR(50)",
    "products" to "id: BIGINT (PK), name: VARCHAR(255), price: DECIMAL(10,2), stock_quantity: INT"
)

object ListDatabaseTablesTool : SimpleTool<Unit>(
    argsType = typeToken<Unit>(),
    name = "list_database_tables",
    description = "Lists all tables in the database."
) {

    override suspend fun execute(args: Unit): String {
        return mockSchema.keys.joinToString()
    }
}

object GetTableSchemaTool : SimpleTool<GetTableSchemaTool.Args>(
    argsType = typeToken<Args>(),
    name = "get_table_schema",
    description = "Retrieves the schema of a specific table in the database."
) {

    @Serializable
    data class Args(
        @property:LLMDescription("The table name to fetch its schema")
        val tableName: String
    )

    override suspend fun execute(args: Args): String {
        return mockSchema[args.tableName] ?: "Table '${args.tableName}' not found in the database."
    }
}
