package me.legend.app.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "people", indices = [Index("displayName")])
data class PersonEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val aliasesJson: String,
    val description: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "places", indices = [Index(value = ["name"], unique = true)])
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "life_events", indices = [Index("occurredAtText")])
data class LifeEventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val occurredAtText: String?,
    val occurredAtStartEpochMillis: Long?,
    val occurredAtEndEpochMillis: Long?,
    val placeName: String?,
    val sourceJobId: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "themes", indices = [Index(value = ["name"], unique = true)])
data class ThemeEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "event_people",
    primaryKeys = ["eventId", "personId"],
    indices = [Index("personId")],
)
data class EventPersonEntity(val eventId: String, val personId: String)

@Entity(
    tableName = "event_themes",
    primaryKeys = ["eventId", "themeId"],
    indices = [Index("themeId")],
)
data class EventThemeEntity(val eventId: String, val themeId: String)

@Entity(
    tableName = "person_relations",
    indices = [Index("fromPersonId"), Index("toPersonId")],
)
data class PersonRelationEntity(
    @PrimaryKey val id: String,
    val fromPersonId: String,
    val toPersonId: String,
    val relationType: String,
    val description: String,
    val confidence: Double,
    val sourceJobId: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "fact_versions", indices = [Index("subject"), Index("isCurrent")])
data class FactVersionEntity(
    @PrimaryKey val id: String,
    val factId: String,
    val subject: String,
    val predicate: String,
    val value: String,
    val validAtText: String?,
    val supersedesText: String?,
    val provenance: String,
    val confidence: Double,
    val sourceJobId: String,
    val isCurrent: Boolean,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "facts",
    indices = [Index(value = ["subject", "predicate"], unique = true)],
)
data class FactEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val predicate: String,
    val currentVersionId: String?,
)

@Entity(tableName = "evidence_links", indices = [Index("targetId"), Index("sourceMessageId")])
data class EvidenceLinkEntity(
    @PrimaryKey val id: String,
    val targetType: String,
    val targetId: String,
    val sourceMessageId: String,
    val confidence: Double,
    val provenance: String,
)

@Entity(tableName = "pending_confirmations", indices = [Index("status")])
data class PendingConfirmationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val question: String,
    val subjectId: String?,
    val candidateIdsJson: String,
    val sourceJobId: String,
    val status: String = "PENDING",
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "search_documents", indices = [Index("entityId"), Index("entityType")])
data class SearchDocumentEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val originalText: String,
    val tokenizedText: String,
    val updatedAtEpochMillis: Long,
)

@Fts4
@Entity(tableName = "search_documents_fts")
data class SearchDocumentFtsEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Int = 0,
    val documentId: String,
    val tokenizedText: String,
)

@Entity(
    tableName = "search_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = SearchDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("modelId")],
)
data class SearchEmbeddingEntity(
    @PrimaryKey val documentId: String,
    val modelId: String,
    val dimensions: Int,
    val vectorBlob: ByteArray,
    val contentHash: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "knowledge_corrections", indices = [Index("targetType"), Index("targetId")])
data class KnowledgeCorrectionEntity(
    @PrimaryKey val id: String,
    val targetType: String,
    val targetId: String,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val createdAtEpochMillis: Long,
)
