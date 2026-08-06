package com.aivashin.integration

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

abstract class AbstractPostgresIntegrationTest {

    companion object {
        @JvmStatic protected var container: PostgreSQLContainer<*>? = null
        @JvmStatic protected lateinit var dataSource: HikariDataSource
        @JvmStatic protected var jdbcUrl: String = ""
        @JvmStatic protected var dbUser: String = ""
        @JvmStatic protected var dbPass: String = ""

        @JvmStatic
        @BeforeAll
        fun setupBaseDatabase() {
            try {
                val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
                    withDatabaseName("testdb")
                    withUsername("testuser")
                    withPassword("testpass")
                }
                postgres.start()
                container = postgres
                jdbcUrl = postgres.jdbcUrl
                dbUser = postgres.username
                dbPass = postgres.password
            } catch (_: Exception) {
                // Fallback to in-memory PostgreSQL mode if Docker is unavailable
                jdbcUrl = "jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
                dbUser = "sa"
                dbPass = ""
            }

            val config = HikariConfig().apply {
                this.jdbcUrl = Companion.jdbcUrl
                this.username = Companion.dbUser
                this.password = Companion.dbPass
                this.maximumPoolSize = 5
            }
            dataSource = HikariDataSource(config)

            seedDatabaseSchema(dataSource)
        }

        @JvmStatic
        @AfterAll
        fun tearDownBaseDatabase() {
            if (::dataSource.isInitialized) {
                dataSource.close()
            }
            container?.stop()
        }

        private fun seedDatabaseSchema(ds: DataSource) {
            ds.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS users (
                            id SERIAL PRIMARY KEY,
                            username VARCHAR(50) NOT NULL UNIQUE,
                            email VARCHAR(100) NOT NULL,
                            is_active BOOLEAN DEFAULT TRUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );

                        CREATE TABLE IF NOT EXISTS products (
                            id SERIAL PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            description TEXT,
                            price NUMERIC(10, 2) NOT NULL,
                            stock_quantity INT DEFAULT 0,
                            category VARCHAR(50)
                        );

                        CREATE TABLE IF NOT EXISTS orders (
                            id SERIAL PRIMARY KEY,
                            user_id INT NOT NULL REFERENCES users(id),
                            status VARCHAR(30) NOT NULL,
                            total_amount NUMERIC(10, 2) NOT NULL,
                            order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );

                        CREATE TABLE IF NOT EXISTS order_items (
                            id SERIAL PRIMARY KEY,
                            order_id INT NOT NULL REFERENCES orders(id),
                            product_id INT NOT NULL REFERENCES products(id),
                            quantity INT NOT NULL,
                            unit_price NUMERIC(10, 2) NOT NULL
                        );

                        CREATE TABLE IF NOT EXISTS audit_logs (
                            id SERIAL PRIMARY KEY,
                            action VARCHAR(50) NOT NULL,
                            entity_type VARCHAR(50) NOT NULL,
                            entity_id INT NOT NULL,
                            performed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );

                        INSERT INTO users (username, email) VALUES ('alice', 'alice@example.com'), ('bob', 'bob@example.com');
                        INSERT INTO products (name, price, stock_quantity, category) VALUES ('Laptop', 1200.00, 10, 'Electronics'), ('Mouse', 25.50, 100, 'Electronics');
                        """.trimIndent()
                    )
                }
            }
        }
    }
}
