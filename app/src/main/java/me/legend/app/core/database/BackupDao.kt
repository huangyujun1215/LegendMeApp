package me.legend.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BackupDao {
    @Query("SELECT * FROM conversation_sessions ORDER BY createdAtEpochMillis")
    suspend fun sessions(): List<ConversationSessionEntity>

    @Query("SELECT * FROM messages ORDER BY createdAtEpochMillis")
    suspend fun messages(): List<MessageEntity>

    @Query("SELECT * FROM work_projects ORDER BY createdAtEpochMillis")
    suspend fun projects(): List<WorkProjectEntity>

    @Query("SELECT * FROM manuscript_versions ORDER BY projectId, versionNumber")
    suspend fun manuscriptVersions(): List<ManuscriptVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSessions(items: List<ConversationSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMessages(items: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreRecordEvents(items: List<RecordEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreProjects(items: List<WorkProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreManuscripts(items: List<ManuscriptVersionEntity>)
}
