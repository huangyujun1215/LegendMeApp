package me.legend.app.feature.recording

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.legend.app.core.database.LegendMeDatabase
import me.legend.app.core.database.SearchEmbeddingEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.serialization.json.Json
import me.legend.app.core.network.EvidenceDto
import me.legend.app.core.network.KnowledgePatchDto
import me.legend.app.core.network.LifeEventPatchDto
import me.legend.app.feature.knowledge.KnowledgeRepository
import me.legend.app.core.search.VectorCodec

@RunWith(RobolectricTestRunner::class)
class RecordingRepositoryTest {
    private lateinit var database: LegendMeDatabase
    private lateinit var repository: RecordingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LegendMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecordingRepository(database, database.recordingDao(), database.knowledgeDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storesOfflineMessageAndQueuesClosedSessionExactlyOnce() = runTest {
        val sessionId = repository.getOrCreateActiveSession(now = 100)
        repository.appendUserMessage(sessionId, " 今天见到了老朋友。 ", RecordingMode.LISTEN, now = 200)
        repository.appendCompanionMessage(sessionId, "我会安静地记住。", now = 210)

        val messages = repository.observeMessages(sessionId).first()
        assertEquals(2, messages.size)
        assertEquals("今天见到了老朋友。", repository.userMessages(sessionId).single().content)

        assertTrue(repository.endSessionAndQueueAnalysis(sessionId, now = 300))
        assertEquals(false, repository.endSessionAndQueueAnalysis(sessionId, now = 400))
        val pending = repository.pendingAnalysis()
        assertEquals(1, pending.size)
        assertEquals(sessionId, pending.single().sessionId)
        assertNotNull(pending.single().idempotencyKey)
    }

    @Test
    fun sealsMultipleSessionsIntoIndependentAnalysisTasks() = runTest {
        repeat(3) { index ->
            val sessionId = repository.getOrCreateActiveSession(now = index * 1_000L + 100L)
            repository.appendUserMessage(
                sessionId,
                "第 ${index + 1} 段人生记录",
                RecordingMode.LISTEN,
                now = index * 1_000L + 200L,
            )
            assertTrue(repository.endSessionAndQueueAnalysis(sessionId, now = index * 1_000L + 300L))
        }

        val tasks = repository.pendingAnalysis(limit = 10)
        assertEquals(3, tasks.size)
        assertEquals(3, tasks.map { it.sessionId }.toSet().size)
        assertEquals(3, tasks.map { it.idempotencyKey }.toSet().size)
    }

    @Test
    fun permanentlyDeletesRawMessageAndSoleDerivedEvent() = runTest {
        val sessionId = repository.getOrCreateActiveSession(now = 100)
        repository.appendUserMessage(sessionId, "需要被删除的记录", RecordingMode.LISTEN, now = 200)
        val message = repository.messages(sessionId).single()
        val evidence = EvidenceDto(listOf(message.id), 1.0, "USER_FACT")
        val knowledge = KnowledgeRepository(database, database.knowledgeDao(), Json)
        knowledge.applyPatch(
            "job-delete",
            KnowledgePatchDto(
                schemaVersion = "1.0",
                summary = "需要被删除的记录",
                people = emptyList(),
                events = listOf(
                    LifeEventPatchDto(
                        temporaryId = "event-delete",
                        title = "待删除事件",
                        description = "只由这一条记录产生",
                        evidence = evidence,
                    ),
                ),
                facts = emptyList(),
                relations = emptyList(),
                themes = emptyList(),
                pendingConfirmations = emptyList(),
                searchText = "待删除 事件",
            ),
            now = 300,
        )
        val searchDocument = database.knowledgeDao().searchDocuments().first { it.entityType == "EVENT" }
        database.knowledgeDao().upsertSearchEmbeddings(
            listOf(
                SearchEmbeddingEntity(
                    documentId = searchDocument.id,
                    modelId = "embed-test",
                    dimensions = 2,
                    vectorBlob = VectorCodec.encode(floatArrayOf(1f, 0f)),
                    contentHash = "hash",
                    updatedAtEpochMillis = 300,
                ),
            ),
        )

        repository.permanentlyDeleteMessage(message.id)

        assertTrue(repository.messages(sessionId).isEmpty())
        assertEquals(message.id, repository.pendingContentDeletions().single().contentId)
        assertTrue(database.knowledgeDao().events().isEmpty())
        assertTrue(database.knowledgeDao().searchDocuments().isEmpty())
        assertTrue(
            database.knowledgeDao().searchEmbeddings("embed-test", listOf(searchDocument.id)).isEmpty(),
        )
    }

    @Test
    fun keepsDerivedEventUntilItsLastSourceIsDeleted() = runTest {
        val sessionId = repository.getOrCreateActiveSession(now = 100)
        repository.appendUserMessage(sessionId, "第一次提到雨夜重逢", RecordingMode.LISTEN, now = 200)
        repository.appendUserMessage(sessionId, "补充：地点是在杭州", RecordingMode.LISTEN, now = 210)
        val messages = repository.messages(sessionId)
        val evidence = EvidenceDto(messages.map { it.id }, 1.0, "USER_FACT")
        val knowledge = KnowledgeRepository(database, database.knowledgeDao(), Json)
        knowledge.applyPatch(
            "job-multi-source",
            KnowledgePatchDto(
                schemaVersion = "1.0",
                summary = "雨夜重逢",
                people = emptyList(),
                events = listOf(
                    LifeEventPatchDto(
                        temporaryId = "event-multi-source",
                        title = "雨夜重逢",
                        description = "在杭州再次见面",
                        evidence = evidence,
                    ),
                ),
                facts = emptyList(),
                relations = emptyList(),
                themes = emptyList(),
                pendingConfirmations = emptyList(),
                searchText = "杭州 雨夜 重逢",
            ),
            now = 300,
        )
        val event = database.knowledgeDao().events().single()

        repository.permanentlyDeleteMessage(messages.first().id)
        assertEquals(event.id, database.knowledgeDao().events().single().id)
        assertEquals(1, knowledge.sourceMessages("EVENT", event.id).size)

        repository.permanentlyDeleteMessage(messages.last().id)
        assertTrue(database.knowledgeDao().events().isEmpty())
        assertTrue(database.knowledgeDao().searchDocuments().isEmpty())
    }

    @Test
    fun rebuildsOrRemovesOutstandingAnalysisAfterSourceDeletion() = runTest {
        val sessionId = repository.getOrCreateActiveSession(now = 100)
        repository.appendUserMessage(sessionId, "保留的记录", RecordingMode.LISTEN, now = 200)
        repository.appendUserMessage(sessionId, "删除的记录", RecordingMode.LISTEN, now = 210)
        assertTrue(repository.endSessionAndQueueAnalysis(sessionId, now = 300))
        val originalTask = repository.pendingAnalysis().single()
        repository.markAnalysisSubmitted(originalTask.id, "server-job-before-delete")

        val messages = repository.messages(sessionId)
        repository.permanentlyDeleteMessage(messages.last().id)

        val replacement = repository.pendingAnalysis().single()
        assertEquals(sessionId, replacement.sessionId)
        assertTrue(replacement.id != originalTask.id)
        assertTrue(replacement.idempotencyKey.contains("after-delete"))
        assertEquals("PENDING", replacement.status)

        repository.permanentlyDeleteMessage(messages.first().id)
        assertTrue(repository.pendingAnalysis().isEmpty())
        assertEquals(null, database.recordingDao().analysisForSession(sessionId))
        assertEquals(2, repository.pendingContentDeletions().size)
    }
}
