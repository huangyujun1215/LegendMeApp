package me.legend.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.legend.app.core.database.BackupDao
import me.legend.app.core.database.ConversationSessionEntity
import me.legend.app.core.database.LegendMeDatabase
import me.legend.app.core.database.ManuscriptVersionEntity
import me.legend.app.core.database.MessageEntity
import me.legend.app.core.database.RecordEventEntity
import me.legend.app.core.database.WorkProjectEntity

@Serializable
private data class BackupManifest(val schemaVersion: Int = 1, val exportedAtEpochMillis: Long)

@Serializable
private data class BackupSession(
    val id: String,
    val createdAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
)

@Serializable
private data class BackupMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val mode: String,
    val content: String,
    val createdAtEpochMillis: Long,
)

@Serializable
private data class BackupRecords(
    val sessions: List<BackupSession>,
    val messages: List<BackupMessage>,
)

@Serializable
private data class BackupWork(
    val projectId: String,
    val title: String,
    val intent: String,
    val creationMode: String = "COLLABORATIVE",
    val genre: String? = null,
    val targetMinLength: Int? = null,
    val targetMaxLength: Int? = null,
    val versionId: String,
    val versionNumber: Int,
    val synopsis: String,
    val content: String,
    val createdAtEpochMillis: Long,
)

@Singleton
class BackupService @Inject constructor(
    private val database: LegendMeDatabase,
    private val dao: BackupDao,
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val sessions = dao.sessions().map {
            BackupSession(it.id, it.createdAtEpochMillis, it.endedAtEpochMillis)
        }
        val messages = dao.messages().map {
            BackupMessage(it.id, it.sessionId, it.role, it.mode, it.content, it.createdAtEpochMillis)
        }
        val projects = dao.projects()
        val latestWorks = dao.manuscriptVersions()
            .groupBy { it.projectId }
            .mapNotNull { (projectId, versions) ->
                val project = projects.firstOrNull { it.id == projectId } ?: return@mapNotNull null
                val version = versions.maxByOrNull { it.versionNumber } ?: return@mapNotNull null
                BackupWork(
                    projectId,
                    project.title,
                    project.intent,
                    project.creationMode,
                    project.genre,
                    project.targetMinLength,
                    project.targetMaxLength,
                    version.id,
                    version.versionNumber,
                    version.synopsis,
                    version.content,
                    version.createdAtEpochMillis,
                )
            }

        val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
            "无法打开备份文件"
        }
        output.use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.writeText(
                    "manifest.json",
                    json.encodeToString(BackupManifest(exportedAtEpochMillis = System.currentTimeMillis())),
                )
                zip.writeText("records.json", json.encodeToString(BackupRecords(sessions, messages)))
                zip.writeText("works/works.json", json.encodeToString(latestWorks))
                latestWorks.forEach { work ->
                    zip.writeText("works/${safeFileName(work.title)}.md", "# ${work.title}\n\n${work.content}\n")
                }
            }
        }
    }

    suspend fun restoreFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val entries = mutableMapOf<String, ByteArray>()
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开备份文件" }
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                var totalBytes = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || entry.name.contains("..")) continue
                    val bytes = zip.readBytes()
                    totalBytes += bytes.size
                    require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "备份文件过大" }
                    entries[entry.name] = bytes
                }
            }
        }
        val manifest = entries["manifest.json"] ?: error("缺少 manifest.json")
        val parsedManifest = json.decodeFromString<BackupManifest>(manifest.decodeToString())
        require(parsedManifest.schemaVersion == 1) { "不支持的备份版本" }
        val records = entries["records.json"]?.decodeToString()?.let {
            json.decodeFromString<BackupRecords>(it)
        } ?: error("缺少 records.json")
        val works = entries["works/works.json"]?.decodeToString()?.let {
            json.decodeFromString<List<BackupWork>>(it)
        }.orEmpty()

        database.withTransaction {
            dao.restoreSessions(records.sessions.map {
                ConversationSessionEntity(it.id, it.createdAtEpochMillis, it.endedAtEpochMillis)
            })
            dao.restoreMessages(records.messages.map {
                MessageEntity(it.id, it.sessionId, it.role, it.mode, it.content, it.createdAtEpochMillis)
            })
            dao.restoreRecordEvents(records.messages.filter { it.role == "USER" }.map {
                RecordEventEntity(
                    id = UUID.randomUUID().toString(),
                    messageId = it.id,
                    type = "APPEND",
                    createdAtEpochMillis = it.createdAtEpochMillis,
                )
            })
            dao.restoreProjects(works.map {
                WorkProjectEntity(
                    id = it.projectId,
                    title = it.title,
                    intent = it.intent,
                    status = "RESTORED",
                    creationMode = it.creationMode,
                    genre = it.genre,
                    targetMinLength = it.targetMinLength,
                    targetMaxLength = it.targetMaxLength,
                    createdAtEpochMillis = it.createdAtEpochMillis,
                    updatedAtEpochMillis = it.createdAtEpochMillis,
                )
            })
            dao.restoreManuscripts(works.map {
                ManuscriptVersionEntity(
                    id = it.versionId,
                    projectId = it.projectId,
                    versionNumber = it.versionNumber,
                    title = it.title,
                    synopsis = it.synopsis,
                    content = it.content,
                    origin = "RESTORED_BACKUP",
                    createdAtEpochMillis = it.createdAtEpochMillis,
                )
            })
        }
    }

    private fun ZipOutputStream.writeText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.encodeToByteArray())
        closeEntry()
    }

    private fun safeFileName(title: String): String =
        title.replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(80).ifBlank { "untitled" }

    companion object {
        private const val MAX_UNCOMPRESSED_BYTES = 50L * 1024L * 1024L
    }
}
