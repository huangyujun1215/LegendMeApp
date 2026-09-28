package me.legend.app.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LegendMeApiEmbeddingTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun retrievesCapabilitiesAndEmbeddingsWithoutExposingProviderCredentials() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"enabled":true,"modelId":"embed-v1","maxBatchSize":16}"""),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody(
                    """{"model":"embed-v1","dimensions":2,"embeddings":[{"id":"doc-1","vector":[0.5,-0.25]}]}""",
                ),
        )
        val api = LegendMeApi(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val settings = ServerSettings(server.url("/").toString(), "device-token", "glm-5.3")

        val capabilities = api.embeddingCapabilities(settings)
        val response = api.embeddings(
            settings,
            listOf(EmbeddingInputRequest("doc-1", "一段人生记录")),
            requireNotNull(capabilities.modelId),
        )

        assertTrue(capabilities.enabled)
        assertEquals(16, capabilities.maxBatchSize)
        assertEquals(listOf(0.5f, -0.25f), response.embeddings.single().vector)
        val capabilityRequest = server.takeRequest()
        val embeddingRequest = server.takeRequest()
        assertEquals("Bearer device-token", capabilityRequest.getHeader("Authorization"))
        assertEquals("Bearer device-token", embeddingRequest.getHeader("Authorization"))
        assertTrue(embeddingRequest.body.readUtf8().contains("一段人生记录"))
    }

    @Test
    fun cancelsAJobThroughTheAuthenticatedServerEndpoint() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"cancelled":true}"""),
        )
        val api = LegendMeApi(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val settings = ServerSettings(server.url("/").toString(), "device-token", "glm-5.3")

        api.cancelJob(settings, "job-1")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/jobs/job-1/cancel", request.path)
        assertEquals("Bearer device-token", request.getHeader("Authorization"))
    }
}
