package com.aivashin.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import javax.sql.DataSource


class ListDatabaseTablesTool(private val dataSource: DataSource) : SimpleTool<Unit>(
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
        dataSource.connection.use { conn ->
            conn.prepareStatement(QUERY).executeQuery().use { rs ->
                val tables = mutableListOf<String>()
                while (rs.next()) {
                    tables.add(rs.getString("table_name"))
                }
                return tables.joinToString(", ").ifEmpty { "No tables found in the public schema." }
            }
        }
    }
}

class GetTableSchemaTool(private val dataSource: DataSource) : SimpleTool<GetTableSchemaTool.Args>(
    argsType = typeToken<Args>(),
    name = "get_table_schema",
    description = "Retrieves the column definitions, data types, and nullability for a specific table."
) {

    companion object {
        private val QUERY = """
                SELECT column_name, data_type, is_nullable 
                FROM information_schema.columns 
                WHERE table_schema = 'public' AND table_name = ?
                ORDER BY ordinal_position
            """.trimIndent()
    }

    @Serializable
    data class Args(
        @property:LLMDescription("The exact name of the table to fetch the schema for.")
        val tableName: String
    )

    override suspend fun execute(args: Args): String {
        dataSource.connection.use { conn ->
            conn.prepareStatement(QUERY).use { stmt ->
                stmt.setString(1, args.tableName)
                stmt.executeQuery().use { rs ->
                    val schemaDetails = mutableListOf<String>()
                    while (rs.next()) {
                        val columnName = rs.getString("column_name")
                        val dataType = rs.getString("data_type")
                        val isNullable = rs.getString("is_nullable")
                        schemaDetails.add("$columnName: $dataType (Nullable: $isNullable)")
                    }
                    return schemaDetails.joinToString("\n").ifEmpty { "Table '${args.tableName}' not found or contains no columns." }
                }
            }
        }
    }
}
