package com.aivashin.unit

import com.aivashin.model.ChatMessage
import com.aivashin.model.ChatRole
import com.aivashin.repository.InMemoryChatHistoryRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InMemoryChatHistoryRepositoryTest {

    private lateinit var repository: InMemoryChatHistoryRepository

    @BeforeEach
    fun setUp() {
        repository = InMemoryChatHistoryRepository()
    }

    @Test
    fun `getMessages returns empty list for unknown session`() = runTest {
        val messages = repository.getMessages("session-1")
        messages.shouldBeEmpty()
    }

    @Test
    fun `appendMessage stores and retrieves chat history in order`() = runTest {
        val sessionId = "session-test"
        val userMsg = ChatMessage(ChatRole.USER, "Hello AI")
        val assistantMsg = ChatMessage(ChatRole.ASSISTANT, "Hello Human")

        repository.appendMessage(sessionId, userMsg)
        repository.appendMessage(sessionId, assistantMsg)

        val history = repository.getMessages(sessionId)
        history shouldHaveSize 2
        history[0] shouldBe userMsg
        history[1] shouldBe assistantMsg
    }

    @Test
    fun `clearSession removes history for specific session without affecting others`() = runTest {
        repository.appendMessage("session-1", ChatMessage(ChatRole.USER, "Msg 1"))
        repository.appendMessage("session-2", ChatMessage(ChatRole.USER, "Msg 2"))

        repository.clearSession("session-1")

        repository.getMessages("session-1").shouldBeEmpty()
        repository.getMessages("session-2") shouldHaveSize 1
    }
}
