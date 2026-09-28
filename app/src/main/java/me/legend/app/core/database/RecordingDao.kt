package me.legend.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM conversation_sessions WHERE endedAtEpochMillis IS NULL ORDER BY createdAtEpochMillis DESC LIMIT 1")
    suspend fun activeSession(): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: ConversationSessionEntity)

    @Query("UPDATE conversation_sessions SET endedAtEpochMillis = :endedAt WHERE id = :sessionId AND endedAtEpochMillis IS NULL")
    suspend fun endSession(sessionId: String, endedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecordEvent(event: RecordEventEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingAnalysis(task: PendingAnalysisEntity): Long

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAtEpochMillis ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND role = 'USER' ORDER BY createdAtEpochMillis ASC")
    suspend fun userMessages(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAtEpochMillis ASC")
    suspend fun messages(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE role = 'USER' ORDER BY createdAtEpochMillis ASC")
    suspend fun allUserMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE role = 'USER' ORDER BY createdAtEpochMillis DESC")
    fun observeAllUserMessages(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId AND role = 'USER'")
    suspend fun userMessageCount(sessionId: String): Int

    @Query("SELECT * FROM conversation_sessions WHERE id = :sessionId")
    suspend fun session(sessionId: String): ConversationSessionEntity?

    @Query("SELECT * FROM pending_analysis WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun pendingAnalysis(limit: Int): List<PendingAnalysisEntity>

    @Query("SELECT COUNT(*) FROM pending_analysis WHERE status IN ('PENDING', 'FAILED', 'SUBMITTED')")
    fun observeOutstandingAnalysisCount(): Flow<Int>

    @Query("SELECT * FROM pending_analysis WHERE id = :id")
    suspend fun analysis(id: String): PendingAnalysisEntity?

    @Query("SELECT * FROM pending_analysis WHERE sessionId = :sessionId LIMIT 1")
    suspend fun analysisForSession(sessionId: String): PendingAnalysisEntity?

    @Query("DELETE FROM pending_analysis WHERE sessionId = :sessionId")
    suspend fun deleteAnalysisForSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingContentDeletion(deletion: PendingContentDeletionEntity)

    @Query("SELECT * FROM pending_content_deletions ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    suspend fun pendingContentDeletions(limit: Int): List<PendingContentDeletionEntity>

    @Query("DELETE FROM pending_content_deletions WHERE contentId = :contentId")
    suspend fun completeContentDeletion(contentId: String)

    @Query("UPDATE pending_content_deletions SET attemptCount = attemptCount + 1, lastError = :error WHERE contentId = :contentId")
    suspend fun failContentDeletion(contentId: String, error: String)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: String): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM record_events WHERE messageId = :messageId")
    suspend fun deleteRecordEvents(messageId: String)

    @Query("UPDATE pending_analysis SET status = :status, serverJobId = :serverJobId, lastError = :error, attemptCount = attemptCount + :attemptIncrement, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updateAnalysis(
        id: String,
        status: String,
        serverJobId: String?,
        error: String?,
        attemptIncrement: Int,
        updatedAt: Long,
    )
}
