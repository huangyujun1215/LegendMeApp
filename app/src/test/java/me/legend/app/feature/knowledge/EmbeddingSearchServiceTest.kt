package me.legend.app.feature.knowledge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.legend.app.core.database.LegendMeDatabase
import me.legend.app.core.database.SearchDocumentEntity
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.ServerSettingsRepository
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddingSearchServiceTest {
    private lateinit var database: LegendMeDatabase
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LegendMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun fetchesMissingDocumentVectorsThenReusesTheLocalCache() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = ServerSettingsRepository(context)
        settings.update(server.url("/").toString(), "device-token-for-vector-test", "glm-5.3")
        val dao = database.knowledgeDao()
        val documents = listOf(
            document("one", "创业失败"),
            document("two", "重新找到希望"),
        )
        documents.forEach { dao.upsertSearchDocument(it) }
        server.enqueue(jsonResponse("""{"enabled":true,"modelId":"embed-v1","maxBatchSize":16}"""))
        server.enqueue(queryVector())
        server.enqueue(
            jsonResponse(
                """{"model":"embed-v1","dimensions":2,"embeddings":[{"id":"EVENT:one","vector":[0,1]},{"id":"EVENT:two","vector":[1,0]}]}""",
            ),
        )
        val service = EmbeddingSearchService(
            dao,
            LegendMeApi(OkHttpClient(), Json { ignoreUnknownKeys = true }),
            settings,
        )

        val first = requireNotNull(service.search("走出低谷", documents, emptyList(), 10))
        assertEquals("two", first.first().entityId)
        assertEquals(2, dao.searchEmbeddings("embed-v1", documents.map { it.id }).size)

        server.enqueue(queryVector())
        val second = requireNotNull(service.search("再次振作", documents, emptyList(), 10))
        assertEquals("two", second.first().entityId)
        assertEquals(4, server.requestCount)
        assertTrue(second.first().score > 0.0)
    }

    private fun document(id: String, text: String) = SearchDocumentEntity(
        id = "EVENT:$id",
        entityType = "EVENT",
        entityId = id,
        originalText = text,
        tokenizedText = text,
        updatedAtEpochMillis = 0,
    )

    private fun queryVector() = jsonResponse(
        """{"model":"embed-v1","dimensions":2,"embeddings":[{"id":"__legendme_query__","vector":[1,0]}]}""",
    )

    private fun jsonResponse(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
