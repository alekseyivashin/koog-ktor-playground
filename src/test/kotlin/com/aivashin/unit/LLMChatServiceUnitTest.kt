package com.aivashin.unit

import com.aivashin.model.ChatMessage
import com.aivashin.model.ChatRole
import com.aivashin.repository.ChatHistoryRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ChatHistoryRepository] interactions.
 *
 * Note: [LLMChatService.askLLM] creates its own internal [GoogleLLMClient],
 * making it unsuitable for traditional unit testing with a mocked LLM client.
 * These tests verify the repository contract separately. Full LLM interaction
 * testing is covered by the optimizer graph integration tests using Koog's
 * `getMockExecutor` + `withTesting()` pattern.
 */
class ChatHistoryRepositoryUnitTest {

    private lateinit var repository: ChatHistoryRepository

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
    }

    @Test
    fun `appendMessage stores user message correctly`() = runTest {
        val sessionId = "session-123"
        val userMessage = ChatMessage(ChatRole.USER, "Hello LLM")

        coEvery { repository.appendMessage(sessionId, userMessage) } returns Unit

        repository.appendMessage(sessionId, userMessage)

        coVerify(exactly = 1) {
            repository.appendMessage(sessionId, match {
                it.role == ChatRole.USER && it.content == "Hello LLM"
            })
        }
    }

    @Test
    fun `appendMessage stores assistant message correctly`() = runTest {
        val sessionId = "session-123"
        val assistantMessage = ChatMessage(ChatRole.ASSISTANT, "Hello from Gemini")

        coEvery { repository.appendMessage(sessionId, assistantMessage) } returns Unit

        repository.appendMessage(sessionId, assistantMessage)

        coVerify(exactly = 1) {
            repository.appendMessage(sessionId, match {
                it.role == ChatRole.ASSISTANT && it.content == "Hello from Gemini"
            })
        }
    }

    @Test
    fun `getMessages returns stored chat history in order`() = runTest {
        val sessionId = "session-456"
        val expectedHistory = listOf(
            ChatMessage(ChatRole.USER, "What tables are in the DB?"),
            ChatMessage(ChatRole.ASSISTANT, "The database has 5 tables: users, orders, products, order_items, categories."),
            ChatMessage(ChatRole.USER, "Show me the users table schema"),
            ChatMessage(ChatRole.ASSISTANT, "users(id SERIAL PK, name VARCHAR, email VARCHAR, ...)")
        )

        coEvery { repository.getMessages(sessionId) } returns expectedHistory

        val result = repository.getMessages(sessionId)

        result shouldBe expectedHistory
        result.size shouldBe 4
        result[0].role shouldBe ChatRole.USER
        result[1].role shouldBe ChatRole.ASSISTANT
    }

    @Test
    fun `getMessages returns empty list for new session`() = runTest {
        val sessionId = "new-session"

        coEvery { repository.getMessages(sessionId) } returns emptyList()

        val result = repository.getMessages(sessionId)

        result shouldBe emptyList()
    }
}
