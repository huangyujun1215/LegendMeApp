package me.legend.app.feature.knowledge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.legend.app.core.database.LegendMeDatabase
import me.legend.app.core.network.EvidenceDto
import me.legend.app.core.network.FactVersionPatchDto
import me.legend.app.core.network.KnowledgePatchDto
import me.legend.app.core.network.LifeEventPatchDto
import me.legend.app.core.network.PendingConfirmationDto
import me.legend.app.core.network.PersonPatchDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KnowledgeRepositoryTest {
    private lateinit var database: LegendMeDatabase
    private lateinit var repository: KnowledgeRepository
    private val evidence = EvidenceDto(listOf("message-1"), 0.95, "USER_FACT")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LegendMeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KnowledgeRepository(database, database.knowledgeDao(), Json)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun appliesKnowledgePatchAndFindsChineseEventWithBm25() = runTest {
        repository.applyPatch("job-1", patch("过去认为朋友放弃了"), now = 100)

        val events = repository.observeTimeline().first()
        assertEquals("望京重逢", events.single().title)
        assertEquals("EVENT", repository.search("望京重逢").first().entityType)
    }

    @Test
    fun retainsFactHistoryAndMakesNewestVersionCurrent() = runTest {
        repository.applyPatch("job-1", patch("过去认为朋友放弃了"), now = 100)
        repository.applyPatch("job-2", patch("现在认为自己太固执"), now = 200)

        val facts = database.knowledgeDao().currentFacts()
        assertEquals(1, facts.size)
        assertEquals("现在认为自己太固执", facts.single().value)
        val all = repository.observeFactVersions().first()
        assertEquals(2, all.size)
        assertEquals(1, all.count { it.isCurrent })
    }

    @Test
    fun keepsAmbiguousPersonSeparateUntilUserConfirmsMerge() = runTest {
        repository.applyPatch("job-1", patch("过去认为朋友放弃了"), now = 100)
        val wangChuan = database.knowledgeDao().people().single()
        val ambiguous = patch("现在认为自己太固执").copy(
            people = listOf(PersonPatchDto("xiaowang", "小王", emptyList(), "可能是旧友", evidence.copy(confidence = 0.55))),
            events = emptyList(),
            facts = emptyList(),
            pendingConfirmations = listOf(
                PendingConfirmationDto(
                    kind = "IDENTITY",
                    question = "小王是否就是王川？",
                    subjectTemporaryId = "xiaowang",
                    candidateIds = listOf(wangChuan.id),
                    evidence = evidence.copy(confidence = 0.55),
                ),
            ),
        )
        repository.applyPatch("job-2", ambiguous, now = 200)
        assertEquals(2, database.knowledgeDao().people().size)

        val confirmation = repository.observePendingConfirmations().first().single()
        repository.resolvePendingConfirmation(confirmation.id, accepted = true)

        assertEquals(1, database.knowledgeDao().people().size)
        assertTrue(repository.observePendingConfirmations().first().isEmpty())
    }

    @Test
    fun correctedEventIsImmediatelySearchableAndRetainsCorrectionHistory() = runTest {
        repository.applyPatch("job-1", patch("过去认为朋友放弃了"), now = 100)
        val event = repository.observeTimeline().first().single()

        repository.correctEvent(event.id, "杭州雨夜重逢", "多年以后", "杭州")

        assertEquals(event.id, repository.search("杭州雨夜").first().entityId)
        val corrections = database.knowledgeDao().corrections()
        assertTrue(corrections.any { it.targetId == event.id && it.fieldName == "title" })
        assertTrue(corrections.any { it.targetId == event.id && it.fieldName == "placeName" })
    }

    private fun patch(value: String) = KnowledgePatchDto(
        schemaVersion = "1.0",
        summary = "林舟和王川在望京重逢",
        people = listOf(PersonPatchDto("wang", "王川", listOf("小王"), "大学同学", evidence)),
        events = listOf(
            LifeEventPatchDto(
                temporaryId = "reunion",
                title = "望京重逢",
                description = "林舟和王川重新谈起创业失败。",
                occurredAtText = "十年后",
                participantTemporaryIds = listOf("wang"),
                placeName = "望京",
                themeNames = listOf("和解"),
                evidence = evidence,
            ),
        ),
        facts = listOf(
            FactVersionPatchDto("林舟", "对创业失败的理解", value, "现在", null, evidence),
        ),
        relations = emptyList(),
        themes = listOf("和解"),
        pendingConfirmations = emptyList<PendingConfirmationDto>(),
        searchText = "望京 重逢 创业 失败 和解",
    )
}
