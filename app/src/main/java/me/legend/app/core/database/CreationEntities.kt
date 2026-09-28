package me.legend.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "work_projects", indices = [Index("status"), Index("updatedAtEpochMillis")])
data class WorkProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val intent: String,
    val status: String,
    val stage: String = "PREPARING",
    val progress: Int = 0,
    val serverJobId: String? = null,
    val creativeBriefJson: String? = null,
    val creationMode: String = "COLLABORATIVE",
    val genre: String? = null,
    val targetMinLength: Int? = null,
    val targetMaxLength: Int? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "creative_messages", indices = [Index("projectId"), Index("createdAtEpochMillis")])
data class CreativeMessageEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val role: String,
    val content: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "manuscript_versions", indices = [Index("projectId"), Index("versionNumber")])
data class ManuscriptVersionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val versionNumber: Int,
    val title: String,
    val synopsis: String,
    val content: String,
    val origin: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "work_feedback", indices = [Index("projectId"), Index("createdAtEpochMillis")])
data class WorkFeedbackEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sentiment: String,
    val note: String?,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "creative_brief_versions", indices = [Index("projectId"), Index("versionNumber")])
data class CreativeBriefVersionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val versionNumber: Int,
    val contentJson: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "outline_versions", indices = [Index("projectId"), Index("versionNumber")])
data class OutlineVersionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val briefVersionId: String,
    val versionNumber: Int,
    val contentJson: String,
    val createdAtEpochMillis: Long,
)
