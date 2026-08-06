package com.aivashin.unit

import com.aivashin.tool.GetTableSchemaTool
import com.aivashin.tool.ListDatabaseTablesTool
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

class DatabaseToolsUnitTest {

    private lateinit var dataSource: DataSource
    private lateinit var connection: Connection
    private lateinit var preparedStatement: PreparedStatement
    private lateinit var resultSet: ResultSet

    @BeforeEach
    fun setUp() {
        dataSource = mockk()
        connection = mockk()
        preparedStatement = mockk()
        resultSet = mockk()

        every { dataSource.connection } returns connection
        every { connection.prepareStatement(any()) } returns preparedStatement
        every { preparedStatement.executeQuery() } returns resultSet
        every { preparedStatement.setString(any(), any()) } returns Unit
        every { connection.close() } returns Unit
        every { preparedStatement.close() } returns Unit
        every { resultSet.close() } returns Unit
    }

    @Test
    fun `ListDatabaseTablesTool returns comma separated list when tables exist`() = runTest {
        every { resultSet.next() } returnsMany listOf(true, true, false)
        every { resultSet.getString("table_name") } returnsMany listOf("users", "orders")

        val tool = ListDatabaseTablesTool(dataSource)
        val result = tool.execute(Unit)

        result shouldBe "users, orders"
        verify(exactly = 1) { dataSource.connection }
    }

    @Test
    fun `ListDatabaseTablesTool returns fallback message when no tables found`() = runTest {
        every { resultSet.next() } returns false

        val tool = ListDatabaseTablesTool(dataSource)
        val result = tool.execute(Unit)

        result shouldBe "No tables found in the public schema."
    }

    @Test
    fun `GetTableSchemaTool formats schema details correctly`() = runTest {
        every { resultSet.next() } returnsMany listOf(true, true, false)
        every { resultSet.getString("column_name") } returnsMany listOf("id", "email")
        every { resultSet.getString("data_type") } returnsMany listOf("bigint", "varchar")
        every { resultSet.getString("is_nullable") } returnsMany listOf("NO", "YES")

        val tool = GetTableSchemaTool(dataSource)
        val result = tool.execute(GetTableSchemaTool.Args("users"))

        result shouldContain "id: bigint (Nullable: NO)"
        result shouldContain "email: varchar (Nullable: YES)"
        verify { preparedStatement.setString(1, "users") }
    }

    @Test
    fun `GetTableSchemaTool returns fallback message when table not found`() = runTest {
        every { resultSet.next() } returns false

        val tool = GetTableSchemaTool(dataSource)
        val result = tool.execute(GetTableSchemaTool.Args("nonexistent"))

        result shouldBe "Table 'nonexistent' not found or contains no columns."
    }
}
