package me.legend.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.legend.app.core.database.ConversationSessionEntity
import me.legend.app.core.database.LegendMeDatabase
import me.legend.app.core.database.ManuscriptVersionEntity
import me.legend.app.core.database.MessageEntity
import me.legend.app.core.database.WorkProjectEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the same ZIP export/import path as Settings, but restores into an independent empty
 * database. This catches backup formats that can be written but cannot bootstrap a fresh install.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private lateinit var context: Context
    private lateinit var source: LegendMeDatabase
    private lateinit var restored: LegendMeDatabase
    private lateinit var backupFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        source = Room.inMemoryDatabaseBuilder(context, LegendMeDatabase::class.java).build()
        restored = Room.inMemoryDatabaseBuilder(context, LegendMeDatabase::class.java).build()
        backupFile = File(context.cacheDir, "backup-round-trip-${System.nanoTime()}.zip")
    }

    @After
    fun tearDown() {
        source.close()
        restored.close()
        backupFile.delete()
    }

    @Test
    fun exportThenRestoreIntoEmptyDatabasePreservesRawRecordsAndLatestWork() = runBlocking {
        val sourceDao = source.backupDao()
        sourceDao.restoreSessions(
            listOf(ConversationSessionEntity("session-1", 100L, 300L)),
        )
        sourceDao.restoreMessages(
            listOf(
                MessageEntity("message-1", "session-1", "USER", "LISTEN", "那年我离开故乡", 110L),
                MessageEntity("message-2", "session-1", "COMPANION", "CONVERSE", "我听见了", 120L),
            ),
        )
        sourceDao.restoreProjects(
            listOf(
                WorkProjectEntity(
                    id = "project-1",
                    title = "渡口",
                    intent = "写一次迟到的告别",
                    status = "COMPLETED",
                    createdAtEpochMillis = 200L,
                    updatedAtEpochMillis = 260L,
                ),
            ),
        )
        sourceDao.restoreManuscripts(
            listOf(
                ManuscriptVersionEntity(
                    "version-1", "project-1", 1, "渡口", "初稿", "旧稿", "AGENT", 240L,
                ),
                ManuscriptVersionEntity(
                    "version-2", "project-1", 2, "渡口", "终稿", "最终正文", "USER_EDIT", 260L,
                ),
            ),
        )

        val uri = Uri.fromFile(backupFile)
        BackupService(source, sourceDao, context, Json).exportTo(uri)
        assertTrue(backupFile.isFile)
        assertTrue(backupFile.length() > 0L)

        BackupService(restored, restored.backupDao(), context, Json).restoreFrom(uri)

        val restoredDao = restored.backupDao()
        assertEquals(sourceDao.sessions(), restoredDao.sessions())
        assertEquals(sourceDao.messages(), restoredDao.messages())
        assertEquals(1, restoredDao.projects().size)
        assertEquals("RESTORED", restoredDao.projects().single().status)
        assertEquals("最终正文", restoredDao.manuscriptVersions().single().content)
        assertEquals(2, restoredDao.manuscriptVersions().single().versionNumber)
        assertEquals("RESTORED_BACKUP", restoredDao.manuscriptVersions().single().origin)
    }
}
