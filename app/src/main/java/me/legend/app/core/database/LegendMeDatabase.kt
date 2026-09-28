package me.legend.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationSessionEntity::class,
        MessageEntity::class,
        RecordEventEntity::class,
        PendingAnalysisEntity::class,
        PendingContentDeletionEntity::class,
        PersonEntity::class,
        PlaceEntity::class,
        LifeEventEntity::class,
        ThemeEntity::class,
        EventPersonEntity::class,
        EventThemeEntity::class,
        FactVersionEntity::class,
        FactEntity::class,
        EvidenceLinkEntity::class,
        PendingConfirmationEntity::class,
        SearchDocumentEntity::class,
        WorkProjectEntity::class,
        CreativeMessageEntity::class,
        ManuscriptVersionEntity::class,
        WorkFeedbackEntity::class,
        SearchDocumentFtsEntity::class,
        SearchEmbeddingEntity::class,
        PersonRelationEntity::class,
        KnowledgeCorrectionEntity::class,
        CreativeBriefVersionEntity::class,
        OutlineVersionEntity::class,
    ],
    version = 15,
    exportSchema = true,
)
abstract class LegendMeDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun creationDao(): CreationDao
    abstract fun backupDao(): BackupDao
}
