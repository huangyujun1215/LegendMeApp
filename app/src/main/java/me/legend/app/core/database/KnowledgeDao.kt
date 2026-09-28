package me.legend.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM people WHERE displayName = :displayName LIMIT 1")
    suspend fun personByName(displayName: String): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPerson(person: PersonEntity)

    @Query("SELECT * FROM places WHERE name = :name LIMIT 1")
    suspend fun placeByName(name: String): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlace(place: PlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: LifeEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTheme(theme: ThemeEntity): Long

    @Query("SELECT * FROM themes WHERE name = :name LIMIT 1")
    suspend fun themeByName(name: String): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventPerson(link: EventPersonEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventTheme(link: EventThemeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRelation(relation: PersonRelationEntity)

    @Query("SELECT * FROM person_relations ORDER BY updatedAtEpochMillis DESC")
    fun observeRelations(): Flow<List<PersonRelationEntity>>

    @Query("SELECT * FROM places ORDER BY name")
    fun observePlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM themes ORDER BY name")
    fun observeThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM event_people")
    fun observeEventPeople(): Flow<List<EventPersonEntity>>

    @Query("SELECT * FROM event_themes")
    fun observeEventThemes(): Flow<List<EventThemeEntity>>

    @Query("SELECT * FROM fact_versions WHERE isCurrent = 1 ORDER BY createdAtEpochMillis DESC")
    fun observeCurrentFacts(): Flow<List<FactVersionEntity>>

    @Query("SELECT * FROM person_relations WHERE id = :id")
    suspend fun relation(id: String): PersonRelationEntity?

    @Query("UPDATE person_relations SET relationType = :relationType, description = :description, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updateRelation(id: String, relationType: String, description: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFactVersion(fact: FactVersionEntity)

    @Query("SELECT * FROM facts WHERE subject = :subject AND predicate = :predicate LIMIT 1")
    suspend fun fact(subject: String, predicate: String): FactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFact(fact: FactEntity)

    @Query("UPDATE fact_versions SET isCurrent = 0 WHERE factId = :factId AND isCurrent = 1")
    suspend fun retireCurrentFacts(factId: String)

    @Query("SELECT * FROM fact_versions ORDER BY subject, predicate, createdAtEpochMillis DESC")
    fun observeFactVersions(): Flow<List<FactVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvidenceLink(link: EvidenceLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingConfirmation(item: PendingConfirmationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: KnowledgeCorrectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchDocument(document: SearchDocumentEntity)

    @Query("SELECT * FROM life_events ORDER BY updatedAtEpochMillis DESC")
    fun observeTimeline(): Flow<List<LifeEventEntity>>

    @Query("SELECT * FROM pending_confirmations WHERE status = 'PENDING' ORDER BY createdAtEpochMillis DESC")
    fun observePendingConfirmations(): Flow<List<PendingConfirmationEntity>>

    @Query("UPDATE pending_confirmations SET status = :status WHERE id = :id")
    suspend fun updateConfirmationStatus(id: String, status: String)

    @Query("SELECT * FROM pending_confirmations WHERE id = :id")
    suspend fun pendingConfirmation(id: String): PendingConfirmationEntity?

    @Query("SELECT * FROM search_documents")
    suspend fun searchDocuments(): List<SearchDocumentEntity>

    @Query("SELECT * FROM search_documents WHERE id IN (:ids)")
    suspend fun searchDocumentsByIds(ids: List<String>): List<SearchDocumentEntity>

    @Query("SELECT rowid, documentId, tokenizedText FROM search_documents_fts WHERE tokenizedText MATCH :query LIMIT :limit")
    suspend fun ftsCandidates(query: String, limit: Int): List<SearchDocumentFtsEntity>

    @Query("DELETE FROM search_documents_fts WHERE documentId = :documentId")
    suspend fun deleteFtsDocument(documentId: String)

    @Insert
    suspend fun insertFtsDocument(document: SearchDocumentFtsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchEmbeddings(embeddings: List<SearchEmbeddingEntity>)

    @Query("SELECT * FROM search_embeddings WHERE modelId = :modelId AND documentId IN (:documentIds)")
    suspend fun searchEmbeddings(modelId: String, documentIds: List<String>): List<SearchEmbeddingEntity>

    @Query("DELETE FROM search_embeddings WHERE modelId != :modelId")
    suspend fun deleteEmbeddingsFromOtherModels(modelId: String)

    @Query("SELECT * FROM people ORDER BY displayName")
    suspend fun people(): List<PersonEntity>

    @Query("SELECT * FROM life_events ORDER BY updatedAtEpochMillis DESC")
    suspend fun events(): List<LifeEventEntity>

    @Query("SELECT id FROM life_events WHERE (:fromEpochMillis IS NULL OR COALESCE(occurredAtEndEpochMillis, occurredAtStartEpochMillis) >= :fromEpochMillis) AND (:toEpochMillis IS NULL OR COALESCE(occurredAtStartEpochMillis, occurredAtEndEpochMillis) <= :toEpochMillis)")
    suspend fun eventIdsInRange(fromEpochMillis: Long?, toEpochMillis: Long?): List<String>

    @Query("SELECT DISTINCT eventId FROM event_people WHERE personId IN (:personIds)")
    suspend fun eventIdsForPeople(personIds: List<String>): List<String>

    @Query("SELECT DISTINCT eventId FROM event_themes WHERE themeId IN (:themeIds)")
    suspend fun eventIdsForThemes(themeIds: List<String>): List<String>

    @Query("SELECT * FROM fact_versions WHERE isCurrent = 1 ORDER BY createdAtEpochMillis DESC")
    suspend fun currentFacts(): List<FactVersionEntity>

    @Query("SELECT * FROM themes ORDER BY name")
    suspend fun themes(): List<ThemeEntity>

    @Query("SELECT * FROM knowledge_corrections ORDER BY createdAtEpochMillis DESC")
    suspend fun corrections(): List<KnowledgeCorrectionEntity>

    @Query("SELECT * FROM evidence_links WHERE sourceMessageId = :sourceMessageId")
    suspend fun evidenceForSource(sourceMessageId: String): List<EvidenceLinkEntity>

    @Query("SELECT m.* FROM messages m INNER JOIN evidence_links e ON m.id = e.sourceMessageId WHERE e.targetType = :targetType AND e.targetId = :targetId ORDER BY m.createdAtEpochMillis")
    suspend fun sourceMessages(targetType: String, targetId: String): List<MessageEntity>

    @Query("DELETE FROM evidence_links WHERE sourceMessageId = :sourceMessageId")
    suspend fun deleteEvidenceForSource(sourceMessageId: String)

    @Query("SELECT COUNT(*) FROM evidence_links WHERE targetType = :targetType AND targetId = :targetId")
    suspend fun evidenceCount(targetType: String, targetId: String): Int

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePerson(id: String)

    @Query("SELECT * FROM people ORDER BY displayName")
    fun observePeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun person(id: String): PersonEntity?

    @Query("UPDATE people SET displayName = :displayName, description = :description, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updatePerson(id: String, displayName: String, description: String, updatedAt: Long)

    @Query("DELETE FROM event_people WHERE personId = :personId")
    suspend fun deletePersonLinks(personId: String)

    @Query("SELECT eventId FROM event_people WHERE personId = :personId")
    suspend fun eventIdsForPerson(personId: String): List<String>

    @Query("UPDATE evidence_links SET targetId = :targetId WHERE targetType = 'PERSON' AND targetId = :sourceId")
    suspend fun movePersonEvidence(sourceId: String, targetId: String)

    @Query("DELETE FROM life_events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("SELECT * FROM life_events WHERE id = :id")
    suspend fun event(id: String): LifeEventEntity?

    @Query("UPDATE life_events SET title = :title, occurredAtText = :occurredAtText, placeName = :placeName, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updateEvent(
        id: String,
        title: String,
        occurredAtText: String?,
        placeName: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM event_people WHERE eventId = :eventId")
    suspend fun deleteEventPeople(eventId: String)

    @Query("DELETE FROM event_themes WHERE eventId = :eventId")
    suspend fun deleteEventThemes(eventId: String)

    @Query("DELETE FROM fact_versions WHERE id = :id")
    suspend fun deleteFact(id: String)

    @Query("SELECT * FROM fact_versions WHERE id = :id")
    suspend fun factVersion(id: String): FactVersionEntity?

    @Query("SELECT * FROM fact_versions WHERE factId = :factId ORDER BY createdAtEpochMillis DESC LIMIT 1")
    suspend fun latestFactVersion(factId: String): FactVersionEntity?

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun deleteFactRoot(id: String)

    @Query("UPDATE facts SET currentVersionId = :versionId WHERE id = :id")
    suspend fun setCurrentFactVersion(id: String, versionId: String)

    @Query("UPDATE fact_versions SET isCurrent = 1 WHERE id = :id")
    suspend fun activateFactVersion(id: String)

    @Query("DELETE FROM person_relations WHERE id = :id")
    suspend fun deleteRelation(id: String)

    @Query("DELETE FROM person_relations WHERE fromPersonId = :personId OR toPersonId = :personId")
    suspend fun deleteRelationsForPerson(personId: String)

    @Query("DELETE FROM search_documents WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteSearchDocument(entityType: String, entityId: String)
}
