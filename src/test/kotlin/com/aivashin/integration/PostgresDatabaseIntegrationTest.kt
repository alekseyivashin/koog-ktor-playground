package com.aivashin.integration

import ai.koog.agents.features.chathistory.jdbc.PostgresJdbcChatHistoryProvider
import com.aivashin.tool.GetTableSchemaTool
import com.aivashin.tool.ListDatabaseTablesTool
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PostgresDatabaseIntegrationTest : AbstractPostgresIntegrationTest() {

    @Test
    fun `ListDatabaseTablesTool lists all seeded user tables in database`() = runTest {
        val tool = ListDatabaseTablesTool(dataSource)
        val result = tool.execute(Unit)

        val tableList = result.split(", ").map { it.trim() }
        tableList.shouldContainAll("users", "products", "orders", "order_items", "audit_logs")
    }

    @Test
    fun `GetTableSchemaTool inspects users table column definitions`() = runTest {
        val tool = GetTableSchemaTool(dataSource)
        val schema = tool.execute(GetTableSchemaTool.Args("users"))

        schema shouldContain "id:"
        schema shouldContain "username:"
        schema shouldContain "email:"
        schema shouldContain "is_active:"
        schema shouldContain "created_at:"
    }

    @Test
    fun `GetTableSchemaTool inspects products table column definitions`() = runTest {
        val tool = GetTableSchemaTool(dataSource)
        val schema = tool.execute(GetTableSchemaTool.Args("products"))

        schema shouldContain "id:"
        schema shouldContain "name:"
        schema shouldContain "price:"
        schema shouldContain "stock_quantity:"
        schema shouldContain "category:"
    }

    @Test
    fun `GetTableSchemaTool inspects orders table column definitions`() = runTest {
        val tool = GetTableSchemaTool(dataSource)
        val schema = tool.execute(GetTableSchemaTool.Args("orders"))

        schema shouldContain "id:"
        schema shouldContain "user_id:"
        schema shouldContain "status:"
        schema shouldContain "total_amount:"
        schema shouldContain "order_date:"
    }

    @Test
    fun `GetTableSchemaTool inspects order_items table column definitions`() = runTest {
        val tool = GetTableSchemaTool(dataSource)
        val schema = tool.execute(GetTableSchemaTool.Args("order_items"))

        schema shouldContain "id:"
        schema shouldContain "order_id:"
        schema shouldContain "product_id:"
        schema shouldContain "quantity:"
        schema shouldContain "unit_price:"
    }

    @Test
    fun `GetTableSchemaTool returns fallback message when table does not exist`() = runTest {
        val tool = GetTableSchemaTool(dataSource)
        val result = tool.execute(GetTableSchemaTool.Args("non_existing_table"))

        result shouldBe "Table 'non_existing_table' not found or contains no columns."
    }

    @Test
    fun `database seeded rows can be queried directly via connection`() = runTest {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM users").use { rs ->
                    rs.next()
                    val userCount = rs.getInt(1)
                    (userCount >= 2) shouldBe true
                }

                stmt.executeQuery("SELECT COUNT(*) FROM products").use { rs ->
                    rs.next()
                    val productCount = rs.getInt(1)
                    (productCount >= 2) shouldBe true
                }
            }
        }
    }

    @Test
    fun `PostgresJdbcChatHistoryProvider creates history table and performs migration`() = runTest {
        val provider = PostgresJdbcChatHistoryProvider(dataSource, "integration_chat_history")
        provider.migrate()

        val tool = ListDatabaseTablesTool(dataSource)
        val tables = tool.execute(Unit)
        tables shouldContain "integration_chat_history"
    }
}
