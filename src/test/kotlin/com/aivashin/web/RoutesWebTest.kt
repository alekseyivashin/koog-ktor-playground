package com.aivashin.web

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import com.aivashin.configuration.dependency.DatabaseDependencies.AGENT_CHAT_HISTORY_PROVIDER_NAME
import com.aivashin.configuration.dependency.DatabaseDependencies.DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME
import com.aivashin.routing.ChatRequest
import com.aivashin.routing.ChatResponse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Web-facing HTTP API tests for all Ktor endpoints using Ktor's [testApplication] engine.
 *
 * Configuration is loaded directly from classpath resource `application-test.yaml` without
 * needing a PostgreSQL Testcontainer or external Docker environment.
 */
class RoutesWebTest {

    @BeforeEach
    fun setUp() {
        mockkConstructor(GoogleLLMClient::class)

        val universalJsonReply = """{
            "reply": "Mocked LLM Response",
            "isSafe": true,
            "tableNames": ["users"],
            "isWebSearchNeeded": false,
            "generatedRawSql": "CREATE INDEX idx_users_email ON users(email);",
            "explanation": "Add index for faster lookups",
            "isDangerous": false,
            "validationErrors": []
        }""".trimIndent()

        val mockAssistantMessage = Message.Assistant(
            content = universalJsonReply,
            metaInfo = ResponseMetaInfo.create(KoogClock.System)
        )

        coEvery {
            anyConstructed<GoogleLLMClient>().execute(any(), any(), any())
        } returns mockAssistantMessage

        coEvery {
            anyConstructed<GoogleLLMClient>().execute(any(), any())
        } returns mockAssistantMessage

        val mockFrame = mockk<StreamFrame>(relaxed = true)
        coEvery {
            anyConstructed<GoogleLLMClient>().executeStreaming(any(), any(), any())
        } returns flowOf(mockFrame)

        coEvery {
            anyConstructed<GoogleLLMClient>().executeStreaming(any(), any())
        } returns flowOf(mockFrame)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun runWebTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment { config = ApplicationConfig("application-test.yaml") }
        application {
            dependencies {
                provide<ChatHistoryProvider>(AGENT_CHAT_HISTORY_PROVIDER_NAME) { mockk(relaxed = true) }
                provide<ChatHistoryProvider>(DATABASE_OPTIMIZER_HISTORY_PROVIDER_NAME) { mockk(relaxed = true) }
            }
        }
        block()
    }

    // ───────────────────────────────────────────────────────────────────────
    // 1. LLM Chat Routes (/llm/chat, /llm/chat/stream)
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /llm/chat Endpoints")
    inner class LlmChatRoutes {

        @Test
        fun `POST llm chat returns 200 OK with valid ChatResponse JSON`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/llm/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest("session-web-1", "How do I optimize DB performance?"))
            }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.contentType shouldBe ContentType.Application.Json.contentType

            val body = response.body<ChatResponse>()
            body.reply shouldContain "Mocked LLM Response"
        }

        @Test
        fun `POST llm chat returns 400 Bad Request for malformed JSON`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/llm/chat") {
                contentType(ContentType.Application.Json)
                setBody("{ \"invalid\": malformed_json_without_quotes }")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }

        @Test
        fun `POST llm chat returns 400 Bad Request when required message field is missing`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/llm/chat") {
                contentType(ContentType.Application.Json)
                setBody("""{ "sessionId": "session-missing-field" }""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }

        @Test
        fun `POST llm chat returns 415 or 400 when Content-Type is unsupported`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/llm/chat") {
                contentType(ContentType.Text.Plain)
                setBody("Plain text request body instead of JSON")
            }

            (response.status == HttpStatusCode.UnsupportedMediaType || response.status == HttpStatusCode.BadRequest) shouldBe true
        }

        @Test
        fun `POST llm chat stream returns 200 OK with event stream content type`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/llm/chat/stream") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest("session-stream-1", "Stream me a response"))
            }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.contentType shouldBe ContentType.Text.EventStream.contentType
        }

        @Test
        fun `POST llm chat stream returns 400 Bad Request for malformed body`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/llm/chat/stream") {
                contentType(ContentType.Application.Json)
                setBody("{ invalid stream payload }")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }

        @Test
        fun `GET llm chat returns 404 or 405 when using wrong HTTP method`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.get("/llm/chat")

            (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.MethodNotAllowed) shouldBe true
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 2. Agent Routes (/agent/chat)
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /agent/chat Endpoints")
    inner class AgentRoutes {

        @Test
        fun `POST agent chat returns 200 OK with valid ChatResponse JSON`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/agent/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest("session-agent-1", "What tools are available?"))
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ChatResponse>()
            body.reply shouldContain "Mocked LLM Response"
        }

        @Test
        fun `POST agent chat returns 400 Bad Request for malformed JSON`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/agent/chat") {
                contentType(ContentType.Application.Json)
                setBody("{ malformed json }")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 3. Optimizer Routes (/optimizer/database)
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /optimizer/database Endpoints")
    inner class OptimizerRoutes {

        @Test
        fun `POST optimizer database returns 200 OK with optimization response`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/optimizer/database") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest("session-opt-1", "SELECT * FROM users WHERE email = 'test@example.com'"))
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ChatResponse>()
            body.reply shouldContain "CREATE INDEX"
        }

        @Test
        fun `POST optimizer database returns 400 Bad Request for malformed payload`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/optimizer/database") {
                contentType(ContentType.Application.Json)
                setBody("Not valid JSON payload")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // 4. Routing Error Cases (404 Not Found)
    // ───────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Routing Errors & General Edge Cases")
    inner class GeneralRoutingErrorTests {

        @Test
        fun `GET non-existent route returns 404 Not Found`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.get("/api/non-existent-route")

            response.status shouldBe HttpStatusCode.NotFound
        }

        @Test
        fun `POST non-existent route returns 404 Not Found`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/unknown/endpoint") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest("s-1", "Hello"))
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
}
