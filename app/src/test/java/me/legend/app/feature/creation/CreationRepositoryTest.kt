package me.legend.app.feature.creation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.legend.app.core.database.ConversationSessionEntity
import me.legend.app.core.database.LegendMeDatabase
import me.legend.app.core.database.MessageEntity
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.ServerSettingsRepository
import me.legend.app.feature.knowledge.KnowledgeRepository
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreationRepositoryTest {
    private lateinit var database: LegendMeDatabase
    private lateinit var server: MockWebServer
    private lateinit var repository: CreationRepository
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LegendMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        val settings = ServerSettingsRepository(context)
        settings.update(server.url("/").toString(), "creation-test-device-token", "glm-5.3")
        database.recordingDao().insertSession(ConversationSessionEntity("session-1", 100L, 200L))
        database.recordingDao().insertMessage(
            MessageEntity("message-1", "session-1", "USER", "LISTEN", "一次难忘的重逢", 150L),
        )
        repository = CreationRepository(
            database.creationDao(),
            database.recordingDao(),
            settings,
            LegendMeApi(OkHttpClient(), json),
            json,
            KnowledgeRepository(database, database.knowledgeDao(), json),
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun retriesFailedCorpusUploadWithTheSameIdempotencyKey() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("temporary failure"))
        val essay = LiteraryGenre.Options.first { it.wireValue == "ESSAY" }
        assertNotNull(
            runCatching {
                repository.createProject("写一篇关于重逢的散文", essay, CreationMode.AUTONOMOUS)
            }.exceptionOrNull(),
        )
        val failed = database.creationDao().observeProjects().first().single()
        assertNotNull(failed)
        assertEquals("FAILED", failed.status)

        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"jobId":"job-accepted","status":"QUEUED"}"""),
        )
        val accepted = repository.retryProject(failed.id)
        assertEquals("job-accepted", accepted.serverJobId)

        val firstBody = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val secondBody = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(
            firstBody.getValue("idempotencyKey").jsonPrimitive.content,
            secondBody.getValue("idempotencyKey").jsonPrimitive.content,
        )
        assertEquals("RECORD", secondBody.getValue("corpus").jsonArray.first().jsonObject
            .getValue("kind").jsonPrimitive.content)
        assertEquals("ESSAY", secondBody.getValue("requestedGenre").jsonPrimitive.content)
        assertEquals("AUTONOMOUS", secondBody.getValue("creationMode").jsonPrimitive.content)
        assertEquals(
            1_500,
            secondBody.getValue("targetLength").jsonObject.getValue("min").jsonPrimitive.content.toInt(),
        )
        assertEquals(
            3_000,
            secondBody.getValue("targetLength").jsonObject.getValue("max").jsonPrimitive.content.toInt(),
        )
    }
}
