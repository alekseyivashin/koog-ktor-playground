package com.aivashin.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.aivashin.util.withConnection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.Serializable
import javax.sql.DataSource


class ListDatabaseTablesTool(private val connectionFactory: ConnectionFactory) : SimpleTool<Unit>(
    argsType = typeToken<Unit>(),
    name = "list_database_tables",
    description = "Lists all user tables available in the public schema of the database."
) {

    companion object {
        private val QUERY = """
                SELECT table_name 
                FROM information_schema.tables 
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
            """.trimIndent()
    }

    override suspend fun execute(args: Unit): String {
        return connectionFactory.withConnection { conn ->
            conn.createStatement(QUERY)
                .execute()
                .awaitSingle()
                .map { row, _ ->
                    row.get("table_name", String::class.java)
                }.asFlow()
                .filterNotNull()
                .toList()
                .joinToString(", ")
                .ifEmpty { "No tables found in the public schema." }
        }
    }
}

class GetTableSchemaTool(private val connectionFactory: ConnectionFactory) : SimpleTool<GetTableSchemaTool.Args>(
    argsType = typeToken<Args>(),
    name = "get_table_schema",
    description = "Retrieves the column definitions, data types, and nullability for a specific table."
) {

    companion object {
        private val QUERY = """
                SELECT column_name, data_type, is_nullable 
                FROM information_schema.columns 
                WHERE table_schema = 'public' AND table_name = $1
                ORDER BY ordinal_position
            """.trimIndent()
    }

    @Serializable
    data class Args(
        @property:LLMDescription("The exact name of the table to fetch the schema for.")
        val tableName: String
    )

    override suspend fun execute(args: Args): String {
        return connectionFactory.withConnection { conn ->
            conn.createStatement(QUERY)
                .bind("$1", args.tableName)
                .execute()
                .awaitSingle()
                .map { row, _ ->

                    val columnName = row.get("column_name", String::class.java)
                    val dataType = row.get("data_type", String::class.java)
                    val isNullable = row.get("is_nullable", String::class.java)
                    "$columnName: $dataType (Nullable: $isNullable)"
                }.asFlow()
                .toList()
                .joinToString("\n")
                .ifEmpty { "Table '${args.tableName}' not found or contains no columns." }
        }
    }
}
