package com.aivashin.util

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle

suspend fun <T> ConnectionFactory.withConnection(block: suspend (Connection) -> T): T {
    // 1. Await connection acquisition from pool
    val connection = this.create().awaitSingle()
    return try {
        // 2. Execute suspending logic
        block(connection)
    } finally {
        // 3. Ensure non-blocking resource cleanup in the final block
        connection.close().awaitFirstOrNull()
    }
}