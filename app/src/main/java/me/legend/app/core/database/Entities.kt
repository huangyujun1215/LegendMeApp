package me.legend.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("createdAtEpochMillis")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val mode: String,
    val content: String,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "record_events",
    indices = [Index("messageId"), Index("referencedMessageId")],
)
data class RecordEventEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val type: String,
    val referencedMessageId: String? = null,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "pending_analysis",
    indices = [Index(value = ["sessionId"], unique = true), Index("status")],
)
data class PendingAnalysisEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val idempotencyKey: String,
    val status: String = "PENDING",
    val serverJobId: String? = null,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "pending_content_deletions")
data class PendingContentDeletionEntity(
    @PrimaryKey val contentId: String,
    val createdAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)
