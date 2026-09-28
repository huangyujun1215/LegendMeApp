package me.legend.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CreationDao {
    @Query("SELECT * FROM work_projects ORDER BY updatedAtEpochMillis DESC")
    fun observeProjects(): Flow<List<WorkProjectEntity>>

    @Query("SELECT * FROM work_projects WHERE id = :id")
    suspend fun project(id: String): WorkProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: WorkProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: CreativeMessageEntity)

    @Query("SELECT * FROM creative_messages WHERE projectId = :projectId ORDER BY createdAtEpochMillis ASC")
    fun observeMessages(projectId: String): Flow<List<CreativeMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: ManuscriptVersionEntity)

    @Query("SELECT * FROM manuscript_versions WHERE projectId = :projectId ORDER BY versionNumber DESC")
    fun observeVersions(projectId: String): Flow<List<ManuscriptVersionEntity>>

    @Query("SELECT * FROM manuscript_versions ORDER BY createdAtEpochMillis DESC")
    fun observeAllVersions(): Flow<List<ManuscriptVersionEntity>>

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM manuscript_versions WHERE projectId = :projectId")
    suspend fun maxVersion(projectId: String): Int

    @Query("SELECT * FROM manuscript_versions WHERE id = :id")
    suspend fun version(id: String): ManuscriptVersionEntity?

    @Query("SELECT * FROM manuscript_versions WHERE projectId = :projectId ORDER BY versionNumber DESC LIMIT 1")
    suspend fun latestVersion(projectId: String): ManuscriptVersionEntity?

    @Query("SELECT * FROM work_projects WHERE status IN ('QUEUED', 'RUNNING', 'WAITING_FOR_USER')")
    suspend fun activeProjects(): List<WorkProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: WorkFeedbackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBriefVersion(version: CreativeBriefVersionEntity): Long

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM creative_brief_versions WHERE projectId = :projectId")
    suspend fun maxBriefVersion(projectId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutlineVersion(version: OutlineVersionEntity): Long

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM outline_versions WHERE projectId = :projectId")
    suspend fun maxOutlineVersion(projectId: String): Int
}
