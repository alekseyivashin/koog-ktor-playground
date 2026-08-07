package com.aivashin.unit

import com.aivashin.tool.GetTableSchemaTool
import com.aivashin.tool.ListDatabaseTablesTool
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Row
import io.r2dbc.spi.RowMetadata
import io.r2dbc.spi.Statement
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.BiFunction

class DatabaseToolsUnitTest {

    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var connection: Connection
    private lateinit var statement: Statement
    private lateinit var result: Result

    @BeforeEach
    fun setUp() {
        connectionFactory = mockk()
        connection = mockk()
        statement = mockk()
        result = mockk()

        every { connectionFactory.create() } returns Mono.just(connection)
        every { connection.createStatement(any()) } returns statement
        every { statement.bind(any<String>(), any()) } returns statement
        every { statement.bind(any<Int>(), any()) } returns statement
        every { statement.execute() } returns Mono.just(result)
        every { connection.close() } returns Mono.empty()
    }

    @Test
    fun `ListDatabaseTablesTool returns comma separated list when tables exist`() = runTest {
        val row1 = mockk<Row>()
        val row2 = mockk<Row>()
        every { row1.get("table_name", String::class.java) } returns "users"
        every { row2.get("table_name", String::class.java) } returns "orders"

        every {
            result.map(any<BiFunction<Row, RowMetadata, Any>>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val mapper = firstArg<BiFunction<Row, RowMetadata, String>>()
            val metadata = mockk<RowMetadata>()
            @Suppress("UNCHECKED_CAST")
            Flux.just(
                mapper.apply(row1, metadata),
                mapper.apply(row2, metadata)
            ) as Flux<Any>
        }

        val tool = ListDatabaseTablesTool(connectionFactory)
        val res = tool.execute(Unit)

        res shouldBe "users, orders"
        verify(exactly = 1) { connectionFactory.create() }
    }

    @Test
    fun `ListDatabaseTablesTool returns fallback message when no tables found`() = runTest {
        every {
            result.map(any<BiFunction<Row, RowMetadata, Any>>())
        } returns Flux.empty()

        val tool = ListDatabaseTablesTool(connectionFactory)
        val res = tool.execute(Unit)

        res shouldBe "No tables found in the public schema."
    }

    @Test
    fun `GetTableSchemaTool formats schema details correctly`() = runTest {
        val row1 = mockk<Row>()
        val row2 = mockk<Row>()
        every { row1.get("column_name", String::class.java) } returns "id"
        every { row1.get("data_type", String::class.java) } returns "bigint"
        every { row1.get("is_nullable", String::class.java) } returns "NO"

        every { row2.get("column_name", String::class.java) } returns "email"
        every { row2.get("data_type", String::class.java) } returns "varchar"
        every { row2.get("is_nullable", String::class.java) } returns "YES"

        every {
            result.map(any<BiFunction<Row, RowMetadata, Any>>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val mapper = firstArg<BiFunction<Row, RowMetadata, String>>()
            val metadata = mockk<RowMetadata>()
            @Suppress("UNCHECKED_CAST")
            Flux.just(
                mapper.apply(row1, metadata),
                mapper.apply(row2, metadata)
            ) as Flux<Any>
        }

        val tool = GetTableSchemaTool(connectionFactory)
        val res = tool.execute(GetTableSchemaTool.Args("users"))

        res shouldContain "id: bigint (Nullable: NO)"
        res shouldContain "email: varchar (Nullable: YES)"
        verify { statement.bind("$1", "users") }
    }

    @Test
    fun `GetTableSchemaTool returns fallback message when table not found`() = runTest {
        every {
            result.map(any<BiFunction<Row, RowMetadata, Any>>())
        } returns Flux.empty()

        val tool = GetTableSchemaTool(connectionFactory)
        val res = tool.execute(GetTableSchemaTool.Args("nonexistent"))

        res shouldBe "Table 'nonexistent' not found or contains no columns."
    }
}
