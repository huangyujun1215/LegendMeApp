package me.legend.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LegendMeDatabase =
        Room.databaseBuilder(context, LegendMeDatabase::class.java, "legendme.db")
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .addMigrations(MIGRATION_9_10)
            .addMigrations(MIGRATION_10_11)
            .addMigrations(MIGRATION_11_12)
            .addMigrations(MIGRATION_12_13)
            .addMigrations(MIGRATION_13_14)
            .addMigrations(MIGRATION_14_15)
            .build()

    @Provides
    fun provideRecordingDao(database: LegendMeDatabase): RecordingDao = database.recordingDao()

    @Provides
    fun provideKnowledgeDao(database: LegendMeDatabase): KnowledgeDao = database.knowledgeDao()

    @Provides
    fun provideCreationDao(database: LegendMeDatabase): CreationDao = database.creationDao()

    @Provides
    fun provideBackupDao(database: LegendMeDatabase): BackupDao = database.backupDao()

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `people` (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `aliasesJson` TEXT NOT NULL, `description` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_people_displayName` ON `people` (`displayName`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `life_events` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `occurredAtText` TEXT, `placeName` TEXT, `sourceJobId` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_life_events_occurredAtText` ON `life_events` (`occurredAtText`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `themes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_themes_name` ON `themes` (`name`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `event_people` (`eventId` TEXT NOT NULL, `personId` TEXT NOT NULL, PRIMARY KEY(`eventId`, `personId`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_people_personId` ON `event_people` (`personId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `event_themes` (`eventId` TEXT NOT NULL, `themeId` TEXT NOT NULL, PRIMARY KEY(`eventId`, `themeId`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_themes_themeId` ON `event_themes` (`themeId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `fact_versions` (`id` TEXT NOT NULL, `subject` TEXT NOT NULL, `predicate` TEXT NOT NULL, `value` TEXT NOT NULL, `validAtText` TEXT, `supersedesText` TEXT, `provenance` TEXT NOT NULL, `confidence` REAL NOT NULL, `sourceJobId` TEXT NOT NULL, `isCurrent` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fact_versions_subject` ON `fact_versions` (`subject`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fact_versions_isCurrent` ON `fact_versions` (`isCurrent`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `evidence_links` (`id` TEXT NOT NULL, `targetType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `sourceMessageId` TEXT NOT NULL, `confidence` REAL NOT NULL, `provenance` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_links_targetId` ON `evidence_links` (`targetId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_evidence_links_sourceMessageId` ON `evidence_links` (`sourceMessageId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `pending_confirmations` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `question` TEXT NOT NULL, `candidateIdsJson` TEXT NOT NULL, `sourceJobId` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_confirmations_status` ON `pending_confirmations` (`status`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `search_documents` (`id` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `originalText` TEXT NOT NULL, `tokenizedText` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_documents_entityId` ON `search_documents` (`entityId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_documents_entityType` ON `search_documents` (`entityType`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `work_projects` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `intent` TEXT NOT NULL, `status` TEXT NOT NULL, `serverJobId` TEXT, `creativeBriefJson` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_projects_status` ON `work_projects` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_projects_updatedAtEpochMillis` ON `work_projects` (`updatedAtEpochMillis`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `creative_messages` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_messages_projectId` ON `creative_messages` (`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_messages_createdAtEpochMillis` ON `creative_messages` (`createdAtEpochMillis`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `manuscript_versions` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `versionNumber` INTEGER NOT NULL, `title` TEXT NOT NULL, `synopsis` TEXT NOT NULL, `content` TEXT NOT NULL, `origin` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_manuscript_versions_projectId` ON `manuscript_versions` (`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_manuscript_versions_versionNumber` ON `manuscript_versions` (`versionNumber`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `work_feedback` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `sentiment` TEXT NOT NULL, `note` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_feedback_projectId` ON `work_feedback` (`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_feedback_createdAtEpochMillis` ON `work_feedback` (`createdAtEpochMillis`)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `search_documents_fts` USING FTS4(`documentId` TEXT NOT NULL, `tokenizedText` TEXT NOT NULL)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `pending_confirmations` ADD COLUMN `subjectId` TEXT")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `person_relations` (`id` TEXT NOT NULL, `fromPersonId` TEXT NOT NULL, `toPersonId` TEXT NOT NULL, `relationType` TEXT NOT NULL, `description` TEXT NOT NULL, `confidence` REAL NOT NULL, `sourceJobId` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_person_relations_fromPersonId` ON `person_relations` (`fromPersonId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_person_relations_toPersonId` ON `person_relations` (`toPersonId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `knowledge_corrections` (`id` TEXT NOT NULL, `targetType` TEXT NOT NULL, `targetId` TEXT NOT NULL, `fieldName` TEXT NOT NULL, `oldValue` TEXT, `newValue` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_corrections_targetType` ON `knowledge_corrections` (`targetType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_corrections_targetId` ON `knowledge_corrections` (`targetId`)")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `places` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_places_name` ON `places` (`name`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `facts` (`id` TEXT NOT NULL, `subject` TEXT NOT NULL, `predicate` TEXT NOT NULL, `currentVersionId` TEXT, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_facts_subject_predicate` ON `facts` (`subject`, `predicate`)")
            db.execSQL("ALTER TABLE `fact_versions` ADD COLUMN `factId` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `work_projects` ADD COLUMN `stage` TEXT NOT NULL DEFAULT 'PREPARING'")
            db.execSQL("ALTER TABLE `work_projects` ADD COLUMN `progress` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `creative_brief_versions` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `versionNumber` INTEGER NOT NULL, `contentJson` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_brief_versions_projectId` ON `creative_brief_versions` (`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_creative_brief_versions_versionNumber` ON `creative_brief_versions` (`versionNumber`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `outline_versions` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `briefVersionId` TEXT NOT NULL, `versionNumber` INTEGER NOT NULL, `contentJson` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_outline_versions_projectId` ON `outline_versions` (`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_outline_versions_versionNumber` ON `outline_versions` (`versionNumber`)")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `life_events` ADD COLUMN `occurredAtStartEpochMillis` INTEGER")
            db.execSQL("ALTER TABLE `life_events` ADD COLUMN `occurredAtEndEpochMillis` INTEGER")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `search_embeddings` (`documentId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `dimensions` INTEGER NOT NULL, `vectorBlob` BLOB NOT NULL, `contentHash` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`documentId`), FOREIGN KEY(`documentId`) REFERENCES `search_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_embeddings_modelId` ON `search_embeddings` (`modelId`)")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_content_deletions` (`contentId` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`contentId`))",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `pending_content_deletions` (`contentId`, `createdAtEpochMillis`, `attemptCount`, `lastError`) SELECT `messageId`, `createdAtEpochMillis`, 0, NULL FROM `record_events` WHERE `type` = 'TOMBSTONE'",
            )
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `work_projects` ADD COLUMN `genre` TEXT")
            db.execSQL("ALTER TABLE `work_projects` ADD COLUMN `targetMinLength` INTEGER")
            db.execSQL("ALTER TABLE `work_projects` ADD COLUMN `targetMaxLength` INTEGER")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `work_projects` ADD COLUMN `creationMode` TEXT NOT NULL DEFAULT 'COLLABORATIVE'",
            )
        }
    }
}
