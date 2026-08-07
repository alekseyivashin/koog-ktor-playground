package com.aivashin.web

import com.aivashin.routing.ChatRequest
import com.aivashin.routing.ChatResponse
import com.aivashin.routing.agentRouting
import com.aivashin.routing.optimizerRouting
import com.aivashin.service.agent.AgentChatService
import com.aivashin.service.graph.DatabaseOptimizerGraphService
import com.aivashin.service.llm.LLMChatService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.UUID

/**
 * Web-facing HTTP API tests for all Ktor endpoints using Ktor's [testApplication] engine.
 *
 * Tests routing, status codes, content negotiation, payload validation, and streaming.
 */
class RoutesWebTest {

    private fun runWebTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment { config = ApplicationConfig("application-test.yaml") }
        application {
            install(ServerContentNegotiation) { json() }

            val mockLlmService = mockk<LLMChatService>()
            coEvery { mockLlmService.askLLM(any(), any()) } returns "Mocked LLM Response"
            coEvery { mockLlmService.streamLLM(any(), any()) } returns flowOf("Mocked LLM Response")

            val mockAgentService = mockk<AgentChatService>()
            coEvery { mockAgentService.askAgent(any(), any()) } returns "Mocked LLM Response"

            val mockOptimizerService = mockk<DatabaseOptimizerGraphService>()
            coEvery { mockOptimizerService.runOptimization(any(), any()) } returns "CREATE INDEX idx_users_email ON users(email);"

            dependencies {
                provide<LLMChatService> { mockLlmService }
                provide<AgentChatService> { mockAgentService }
                provide<DatabaseOptimizerGraphService> { mockOptimizerService }
            }

            agentRouting()
            optimizerRouting()
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
        fun `POST llm chat returns 200 OK with valid ChatResponse JSON containing sessionId`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val request = ChatRequest("session-web-1", "How do I optimize DB performance?")
            val response = client.post("/llm/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            response.status shouldBe HttpStatusCode.OK
            response.contentType()?.contentType shouldBe ContentType.Application.Json.contentType

            val body = response.body<ChatResponse>()
            body.sessionId shouldBe "session-web-1"
            body.reply shouldContain "Mocked LLM Response"
        }

        @Test
        fun `POST llm chat generates valid random UUID sessionId when omitted from request payload`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/llm/chat") {
                contentType(ContentType.Application.Json)
                setBody("""{ "message": "Test prompt without sessionId" }""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ChatResponse>()
            body.sessionId.shouldNotBeBlank()
            assertDoesNotThrow { UUID.fromString(body.sessionId) }
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
        fun `POST agent chat returns 200 OK with valid ChatResponse JSON containing sessionId`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val request = ChatRequest("session-agent-1", "What tools are available?")
            val response = client.post("/agent/chat") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ChatResponse>()
            body.sessionId shouldBe "session-agent-1"
            body.reply shouldContain "Mocked LLM Response"
        }

        @Test
        fun `POST agent chat generates valid random UUID sessionId when omitted from request payload`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/agent/chat") {
                contentType(ContentType.Application.Json)
                setBody("""{ "message": "Test agent prompt without sessionId" }""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ChatResponse>()
            body.sessionId.shouldNotBeBlank()
            assertDoesNotThrow { UUID.fromString(body.sessionId) }
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
        fun `POST optimizer database returns 200 OK with optimization response containing sessionId`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val request = ChatRequest("session-opt-1", "SELECT * FROM users WHERE email = 'test@example.com'")
            val response = client.post("/optimizer/database") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ChatResponse>()
            body.sessionId shouldBe "session-opt-1"
            body.reply shouldContain "CREATE INDEX"
        }

        @Test
        fun `POST optimizer database generates valid random UUID sessionId when omitted from request payload`() = runWebTest {
            val client = createClient {
                install(ContentNegotiation) { json() }
            }

            val response = client.post("/optimizer/database") {
                contentType(ContentType.Application.Json)
                setBody("""{ "message": "SELECT * FROM users" }""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<ChatResponse>()
            body.sessionId.shouldNotBeBlank()
            assertDoesNotThrow { UUID.fromString(body.sessionId) }
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
