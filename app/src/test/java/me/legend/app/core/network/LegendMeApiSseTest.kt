package me.legend.app.core.network

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LegendMeApiSseTest {
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
    fun resumesSseFromLastSeenEventId() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("id: 8\nevent: JOB_PROGRESS\ndata: {\"stage\":\"WRITING\",\"progress\":45}\n\n"),
        )
        val api = LegendMeApi(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val settings = ServerSettings(server.url("/").toString(), "test-token", "glm-5.3")

        val event = api.observeJobEvents(settings, "job-1", lastEventId = 7).first()

        assertEquals(8, event.id)
        assertEquals("JOB_PROGRESS", event.type)
        assertEquals("7", server.takeRequest().getHeader("Last-Event-ID"))
    }
}
