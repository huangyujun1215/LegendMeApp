package me.legend.app.feature.knowledge

import androidx.room.withTransaction
import java.util.UUID
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.legend.app.core.database.EventPersonEntity
import me.legend.app.core.database.EventThemeEntity
import me.legend.app.core.database.EvidenceLinkEntity
import me.legend.app.core.database.FactVersionEntity
import me.legend.app.core.database.FactEntity
import me.legend.app.core.database.KnowledgeDao
import me.legend.app.core.database.KnowledgeCorrectionEntity
import me.legend.app.core.database.LegendMeDatabase
import me.legend.app.core.database.LifeEventEntity
import me.legend.app.core.database.PendingConfirmationEntity
import me.legend.app.core.database.PersonEntity
import me.legend.app.core.database.PersonRelationEntity
import me.legend.app.core.database.PlaceEntity
import me.legend.app.core.database.SearchDocumentEntity
import me.legend.app.core.database.SearchDocumentFtsEntity
import me.legend.app.core.database.ThemeEntity
import me.legend.app.core.network.EvidenceDto
import me.legend.app.core.network.KnowledgePatchDto
import me.legend.app.core.network.CreativeCorpusItemRequest
import me.legend.app.core.search.ChineseBigramTokenizer
import me.legend.app.core.search.Bm25SearchEngine
import me.legend.app.core.search.SearchHit
import me.legend.app.feature.recording.RecordingMessage

@Singleton
class KnowledgeRepository @Inject constructor(
    private val database: LegendMeDatabase,
    private val dao: KnowledgeDao,
    private val json: Json,
    private val embeddingSearchService: EmbeddingSearchService? = null,
) {
    private val searchEngine = Bm25SearchEngine()

    fun observeTimeline(): Flow<List<LifeEventEntity>> = dao.observeTimeline()

    fun observePendingConfirmations(): Flow<List<PendingConfirmationEntity>> =
        dao.observePendingConfirmations()

    fun observePeople(): Flow<List<PersonEntity>> = dao.observePeople()

    fun observeRelations(): Flow<List<PersonRelationEntity>> = dao.observeRelations()

    fun observePlaces(): Flow<List<PlaceEntity>> = dao.observePlaces()

    fun observeThemes(): Flow<List<ThemeEntity>> = dao.observeThemes()

    fun observeEventPeople(): Flow<List<EventPersonEntity>> = dao.observeEventPeople()

    fun observeEventThemes(): Flow<List<EventThemeEntity>> = dao.observeEventThemes()

    fun observeCurrentFacts(): Flow<List<FactVersionEntity>> = dao.observeCurrentFacts()

    fun observeFactVersions(): Flow<List<FactVersionEntity>> = dao.observeFactVersions()

    suspend fun resolvePendingConfirmation(id: String, accepted: Boolean) {
        if (accepted) {
            val item = dao.pendingConfirmation(id)
            if (item?.kind == "IDENTITY" && item.subjectId != null) {
                val candidates = json.decodeFromString<List<String>>(item.candidateIdsJson)
                val targetId = candidates.firstOrNull()
                if (targetId != null && targetId != item.subjectId) {
                    database.withTransaction {
                        dao.eventIdsForPerson(item.subjectId).forEach { eventId ->
                            dao.insertEventPerson(EventPersonEntity(eventId, targetId))
                        }
                        dao.deletePersonLinks(item.subjectId)
                        dao.movePersonEvidence(item.subjectId, targetId)
                        dao.deletePerson(item.subjectId)
                    }
                }
            }
        }
        dao.updateConfirmationStatus(id, if (accepted) "CONFIRMED" else "REJECTED")
    }

    suspend fun correctEvent(id: String, title: String, occurredAtText: String?, placeName: String?) {
        val current = dao.event(id) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            recordCorrection("EVENT", id, "title", current.title, title, now)
            recordCorrection("EVENT", id, "occurredAtText", current.occurredAtText, occurredAtText, now)
            recordCorrection("EVENT", id, "placeName", current.placeName, placeName, now)
            dao.updateEvent(id, title, occurredAtText, placeName, now)
            val text = "$title ${current.description} ${placeName.orEmpty()}"
            val documentId = "EVENT:$id"
            val tokens = ChineseBigramTokenizer.tokenize(text)
            dao.upsertSearchDocument(SearchDocumentEntity(documentId, "EVENT", id, text, tokens, now))
            dao.deleteFtsDocument(documentId)
            dao.insertFtsDocument(SearchDocumentFtsEntity(documentId = documentId, tokenizedText = tokens))
        }
    }

    suspend fun correctPerson(id: String, displayName: String, description: String) {
        val current = dao.person(id) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            recordCorrection("PERSON", id, "displayName", current.displayName, displayName, now)
            recordCorrection("PERSON", id, "description", current.description, description, now)
            dao.updatePerson(id, displayName, description, now)
        }
    }

    suspend fun correctRelation(id: String, relationType: String, description: String) {
        val current = dao.relation(id) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            recordCorrection("RELATION", id, "relationType", current.relationType, relationType, now)
            recordCorrection("RELATION", id, "description", current.description, description, now)
            dao.updateRelation(id, relationType, description, now)
        }
    }

    suspend fun search(query: String, limit: Int = 20): List<SearchHit> {
        return search(query, KnowledgeSearchFilter(), limit)
    }

    suspend fun search(query: String, entityTypes: Set<String>, limit: Int = 20): List<SearchHit> =
        search(query, KnowledgeSearchFilter(entityTypes = entityTypes), limit)

    suspend fun search(query: String, filter: KnowledgeSearchFilter, limit: Int = 20): List<SearchHit> {
        val tokenizedQuery = ChineseBigramTokenizer.tokenize(query)
        if (tokenizedQuery.isBlank() || limit <= 0) return emptyList()
        val candidateIds = dao.ftsCandidates(tokenizedQuery, limit * 5).map { it.documentId }
        var allowedEventIds: Set<String>? = null
        if (filter.fromEpochMillis != null || filter.toEpochMillis != null) {
            allowedEventIds = dao.eventIdsInRange(filter.fromEpochMillis, filter.toEpochMillis).toSet()
        }
        if (filter.personIds.isNotEmpty()) {
            val ids = dao.eventIdsForPeople(filter.personIds.toList()).toSet()
            allowedEventIds = allowedEventIds?.intersect(ids) ?: ids
        }
        if (filter.themeIds.isNotEmpty()) {
            val ids = dao.eventIdsForThemes(filter.themeIds.toList()).toSet()
            allowedEventIds = allowedEventIds?.intersect(ids) ?: ids
        }
        fun allowed(document: SearchDocumentEntity): Boolean =
            (filter.entityTypes.isEmpty() || document.entityType in filter.entityTypes) &&
                (document.entityType != "EVENT" || allowedEventIds == null || document.entityId in allowedEventIds)

        val candidates = if (candidateIds.isEmpty()) emptyList()
        else dao.searchDocumentsByIds(candidateIds).filter(::allowed)
        val bm25Hits = searchEngine.search(query, candidates, limit)
            .distinctBy { it.entityType to it.entityId }
        val embeddingSearch = embeddingSearchService ?: return bm25Hits
        val semanticCandidates = dao.searchDocuments().filter(::allowed)
        return runCatching {
            embeddingSearch.search(query, semanticCandidates, bm25Hits, limit)
        }.getOrNull() ?: bm25Hits
    }

    suspend fun sourceMessages(targetType: String, targetId: String): List<RecordingMessage> =
        dao.sourceMessages(targetType, targetId).map {
            RecordingMessage(it.id, it.role, it.content, it.createdAtEpochMillis)
        }

    suspend fun snapshotForAgent(): kotlinx.serialization.json.JsonObject {
        val people = dao.people()
        val events = dao.events().take(200)
        val facts = dao.currentFacts().take(300)
        val themes = dao.themes()
        val corrections = dao.corrections().take(200)
        return buildJsonObject {
        putJsonArray("people") {
            people.forEach { person ->
                add(buildJsonObject {
                    put("id", person.id)
                    put("displayName", person.displayName)
                    put("aliases", json.parseToJsonElement(person.aliasesJson))
                    put("description", person.description)
                })
            }
        }
        putJsonArray("events") {
            events.forEach { event ->
                add(buildJsonObject {
                    put("id", event.id)
                    put("title", event.title)
                    put("description", event.description)
                    event.occurredAtText?.let { put("occurredAtText", it) }
                    event.occurredAtStartEpochMillis?.let { put("occurredAtStartEpochMillis", it) }
                    event.occurredAtEndEpochMillis?.let { put("occurredAtEndEpochMillis", it) }
                    event.placeName?.let { put("placeName", it) }
                })
            }
        }
        putJsonArray("currentFacts") {
            facts.forEach { fact ->
                add(buildJsonObject {
                    put("id", fact.id)
                    put("subject", fact.subject)
                    put("predicate", fact.predicate)
                    put("value", fact.value)
                })
            }
        }
        putJsonArray("themes") {
            themes.forEach { add(JsonPrimitive(it.name)) }
        }
        putJsonArray("userCorrections") {
            corrections.forEach { correction ->
                add(buildJsonObject {
                    put("targetType", correction.targetType)
                    put("targetId", correction.targetId)
                    put("fieldName", correction.fieldName)
                    correction.newValue?.let { put("authoritativeValue", it) }
                })
            }
        }
        }
    }

    suspend fun corpusForCreation(): List<CreativeCorpusItemRequest> {
        val people = dao.people().map { person ->
            CreativeCorpusItemRequest(
                id = "person:${person.id}",
                kind = "PERSON",
                content = "${person.displayName}：${person.description}",
            )
        }
        val events = dao.events().map { event ->
            CreativeCorpusItemRequest(
                id = "event:${event.id}",
                kind = "EVENT",
                content = listOfNotNull(event.occurredAtText, event.title, event.description, event.placeName)
                    .joinToString(" "),
            )
        }
        val facts = dao.currentFacts().map { fact ->
            CreativeCorpusItemRequest(
                id = "fact:${fact.id}",
                kind = "FACT",
                content = "${fact.subject} ${fact.predicate}：${fact.value}",
            )
        }
        val themes = dao.themes().map { theme ->
            CreativeCorpusItemRequest(
                id = "theme:${theme.id}",
                kind = "THEME",
                content = theme.name,
            )
        }
        return people + events + facts + themes
    }

    suspend fun applyPatch(jobId: String, patch: KnowledgePatchDto, now: Long = System.currentTimeMillis()) {
        require(patch.schemaVersion == "1.0") { "Unsupported knowledge patch ${patch.schemaVersion}" }
        database.withTransaction {
            val personIds = mutableMapOf<String, String>()
            patch.people.forEach { person ->
                val identityNeedsConfirmation = patch.pendingConfirmations.any {
                    it.kind == "IDENTITY" && it.subjectTemporaryId == person.temporaryId
                }
                val existing = if (person.evidence.confidence >= 0.85 && !identityNeedsConfirmation) {
                    dao.personByName(person.displayName)
                } else {
                    null
                }
                val personId = existing?.id ?: UUID.randomUUID().toString()
                personIds[person.temporaryId] = personId
                dao.upsertPerson(
                    PersonEntity(
                        id = personId,
                        displayName = person.displayName,
                        aliasesJson = json.encodeToString(person.aliases),
                        description = person.description,
                        updatedAtEpochMillis = now,
                    ),
                )
                insertEvidence("PERSON", personId, person.evidence)
                upsertSearchDocument(
                    id = "PERSON:$personId",
                    entityType = "PERSON",
                    entityId = personId,
                    text = "${person.displayName} ${person.aliases.joinToString(" ")} ${person.description}",
                    now = now,
                )
            }

            val themeIds = mutableMapOf<String, String>()
            patch.themes.forEach { themeName ->
                val existing = dao.themeByName(themeName)
                val themeId = existing?.id ?: UUID.randomUUID().toString()
                themeIds[themeName] = themeId
                dao.insertTheme(ThemeEntity(themeId, themeName))
                upsertSearchDocument("THEME:$themeId", "THEME", themeId, themeName, now)
            }

            patch.events.forEach { event ->
                val eventId = UUID.randomUUID().toString()
                event.placeName?.let { placeName ->
                    if (dao.placeByName(placeName) == null) {
                        dao.insertPlace(PlaceEntity(UUID.randomUUID().toString(), placeName))
                    }
                }
                dao.upsertEvent(
                    LifeEventEntity(
                        id = eventId,
                        title = event.title,
                        description = event.description,
                        occurredAtText = event.occurredAtText,
                        occurredAtStartEpochMillis = event.occurredAtStart?.let(::parseEpochMillis),
                        occurredAtEndEpochMillis = event.occurredAtEnd?.let(::parseEpochMillis),
                        placeName = event.placeName,
                        sourceJobId = jobId,
                        updatedAtEpochMillis = now,
                    ),
                )
                event.participantTemporaryIds.mapNotNull(personIds::get).forEach { personId ->
                    dao.insertEventPerson(EventPersonEntity(eventId, personId))
                }
                event.themeNames.forEach { themeName ->
                    val themeId = themeIds[themeName] ?: UUID.randomUUID().toString().also {
                        dao.insertTheme(ThemeEntity(it, themeName))
                        themeIds[themeName] = it
                    }
                    dao.insertEventTheme(EventThemeEntity(eventId, themeId))
                }
                insertEvidence("EVENT", eventId, event.evidence)
                upsertSearchDocument(
                    "EVENT:$eventId",
                    "EVENT",
                    eventId,
                    "${event.title} ${event.description}",
                    now,
                )
            }

            patch.facts.forEach { fact ->
                val stableFact = dao.fact(fact.subject, fact.predicate)
                    ?: FactEntity(
                        id = UUID.randomUUID().toString(),
                        subject = fact.subject,
                        predicate = fact.predicate,
                        currentVersionId = null,
                    )
                dao.retireCurrentFacts(stableFact.id)
                val factVersionId = UUID.randomUUID().toString()
                dao.insertFactVersion(
                    FactVersionEntity(
                        id = factVersionId,
                        factId = stableFact.id,
                        subject = fact.subject,
                        predicate = fact.predicate,
                        value = fact.value,
                        validAtText = fact.validAtText,
                        supersedesText = fact.supersedes,
                        provenance = fact.evidence.provenance,
                        confidence = fact.evidence.confidence,
                        sourceJobId = jobId,
                        isCurrent = true,
                        createdAtEpochMillis = now,
                    ),
                )
                dao.upsertFact(stableFact.copy(currentVersionId = factVersionId))
                insertEvidence("FACT", factVersionId, fact.evidence)
                upsertSearchDocument(
                    "FACT:$factVersionId",
                    "FACT",
                    factVersionId,
                    "${fact.subject} ${fact.predicate} ${fact.value}",
                    now,
                )
            }

            patch.relations.forEach { relation ->
                val fromId = personIds[relation.fromPersonTemporaryId] ?: return@forEach
                val toId = personIds[relation.toPersonTemporaryId] ?: return@forEach
                val relationId = UUID.randomUUID().toString()
                dao.upsertRelation(
                    PersonRelationEntity(
                        id = relationId,
                        fromPersonId = fromId,
                        toPersonId = toId,
                        relationType = relation.relationType,
                        description = relation.description,
                        confidence = relation.evidence.confidence,
                        sourceJobId = jobId,
                        updatedAtEpochMillis = now,
                    ),
                )
                insertEvidence("RELATION", relationId, relation.evidence)
            }

            patch.pendingConfirmations.forEach { item ->
                val subjectId = item.subjectTemporaryId?.let(personIds::get)
                val candidateIds = item.candidateIds.map { personIds[it] ?: it }
                dao.insertPendingConfirmation(
                    PendingConfirmationEntity(
                        id = UUID.randomUUID().toString(),
                        kind = item.kind,
                        question = item.question,
                        subjectId = subjectId,
                        candidateIdsJson = json.encodeToString(candidateIds),
                        sourceJobId = jobId,
                        createdAtEpochMillis = now,
                    ),
                )
            }

            upsertSearchDocument("SUMMARY:$jobId", "SUMMARY", jobId, patch.summary, now, patch.searchText)
            val summarySources = buildSet {
                patch.people.flatMapTo(this) { it.evidence.sourceMessageIds }
                patch.events.flatMapTo(this) { it.evidence.sourceMessageIds }
                patch.facts.flatMapTo(this) { it.evidence.sourceMessageIds }
                patch.relations.flatMapTo(this) { it.evidence.sourceMessageIds }
            }
            summarySources.forEach { sourceMessageId ->
                dao.insertEvidenceLink(
                    EvidenceLinkEntity(
                        id = UUID.randomUUID().toString(),
                        targetType = "SUMMARY",
                        targetId = jobId,
                        sourceMessageId = sourceMessageId,
                        confidence = 1.0,
                        provenance = "AGENT_INFERENCE",
                    ),
                )
            }
        }
    }

    private suspend fun insertEvidence(targetType: String, targetId: String, evidence: EvidenceDto) {
        evidence.sourceMessageIds.forEach { sourceMessageId ->
            dao.insertEvidenceLink(
                EvidenceLinkEntity(
                    id = UUID.randomUUID().toString(),
                    targetType = targetType,
                    targetId = targetId,
                    sourceMessageId = sourceMessageId,
                    confidence = evidence.confidence,
                    provenance = evidence.provenance,
                ),
            )
        }
    }

    private suspend fun recordCorrection(
        targetType: String,
        targetId: String,
        fieldName: String,
        oldValue: String?,
        newValue: String?,
        now: Long,
    ) {
        if (oldValue == newValue) return
        dao.insertCorrection(
            KnowledgeCorrectionEntity(
                id = UUID.randomUUID().toString(),
                targetType = targetType,
                targetId = targetId,
                fieldName = fieldName,
                oldValue = oldValue,
                newValue = newValue,
                createdAtEpochMillis = now,
            ),
        )
    }

    private suspend fun upsertSearchDocument(
        id: String,
        entityType: String,
        entityId: String,
        text: String,
        now: Long,
        textForTokens: String = text,
    ) {
        val tokens = ChineseBigramTokenizer.tokenize(textForTokens)
        dao.upsertSearchDocument(SearchDocumentEntity(id, entityType, entityId, text, tokens, now))
        dao.deleteFtsDocument(id)
        dao.insertFtsDocument(SearchDocumentFtsEntity(documentId = id, tokenizedText = tokens))
    }
}

data class KnowledgeSearchFilter(
    val entityTypes: Set<String> = emptySet(),
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
    val personIds: Set<String> = emptySet(),
    val themeIds: Set<String> = emptySet(),
)

private fun parseEpochMillis(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
