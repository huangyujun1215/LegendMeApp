package me.legend.app.feature.creation

import java.time.Instant
import java.util.UUID
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.legend.app.core.database.CreationDao
import me.legend.app.core.database.CreativeBriefVersionEntity
import me.legend.app.core.database.CreativeMessageEntity
import me.legend.app.core.database.ManuscriptVersionEntity
import me.legend.app.core.database.OutlineVersionEntity
import me.legend.app.core.database.RecordingDao
import me.legend.app.core.database.WorkProjectEntity
import me.legend.app.core.database.WorkFeedbackEntity
import me.legend.app.core.network.CreationJobRequest
import me.legend.app.core.network.CreativeBriefDto
import me.legend.app.core.network.CreativeCorpusItemRequest
import me.legend.app.core.network.CreativeResultDto
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.JobEventDto
import me.legend.app.core.network.ServerSettingsRepository
import me.legend.app.core.network.TargetLengthRequest
import me.legend.app.feature.knowledge.KnowledgeRepository

@Singleton
class CreationRepository @Inject constructor(
    private val creationDao: CreationDao,
    private val recordingDao: RecordingDao,
    private val settingsRepository: ServerSettingsRepository,
    private val api: LegendMeApi,
    private val json: Json,
    private val knowledgeRepository: KnowledgeRepository,
) {
    fun observeProjects(): Flow<List<WorkProjectEntity>> = creationDao.observeProjects()

    fun observeVersions(projectId: String): Flow<List<ManuscriptVersionEntity>> =
        creationDao.observeVersions(projectId)

    suspend fun activeProjects(): List<WorkProjectEntity> = creationDao.activeProjects()

    suspend fun recoverRemoteProjects(): List<WorkProjectEntity> {
        val settings = settingsRepository.current()
        if (settings.accessToken.isBlank()) return emptyList()
        return api.jobs(settings, "CREATION").jobs.mapNotNull { remote ->
            val projectId = remote.projectId ?: return@mapNotNull null
            val existing = creationDao.project(projectId)
            val recovered = (existing ?: WorkProjectEntity(
                id = projectId,
                title = remote.intent?.take(24) ?: "恢复的作品",
                intent = remote.intent.orEmpty(),
                status = remote.status,
                creationMode = remote.creationMode,
                genre = remote.requestedGenre,
                targetMinLength = remote.targetLength?.min,
                targetMaxLength = remote.targetLength?.max,
                createdAtEpochMillis = Instant.parse(remote.createdAt).toEpochMilli(),
                updatedAtEpochMillis = Instant.parse(remote.updatedAt).toEpochMilli(),
            )).copy(
                status = remote.status,
                stage = remote.stage,
                progress = remote.progress,
                serverJobId = remote.id,
                updatedAtEpochMillis = Instant.parse(remote.updatedAt).toEpochMilli(),
            )
            creationDao.upsertProject(recovered)
            if (remote.status in setOf("WAITING_FOR_USER", "COMPLETED")) refreshProject(projectId) else recovered
        }
    }

    suspend fun project(projectId: String): WorkProjectEntity? = creationDao.project(projectId)

    suspend fun observeRemoteEvents(projectId: String): Flow<JobEventDto> {
        val project = requireNotNull(creationDao.project(projectId))
        val jobId = requireNotNull(project.serverJobId)
        return api.observeJobEvents(settingsRepository.current(), jobId)
    }

    suspend fun createProject(
        intent: String,
        genre: LiteraryGenre = LiteraryGenre.Auto,
        creationMode: CreationMode = CreationMode.COLLABORATIVE,
    ): WorkProjectEntity {
        val normalized = intent.trim()
        require(normalized.isNotEmpty()) { "创作意图不能为空" }
        val now = System.currentTimeMillis()
        val project = WorkProjectEntity(
            id = UUID.randomUUID().toString(),
            title = normalized.take(24),
            intent = normalized,
            status = "PREPARING",
            creationMode = creationMode.name,
            genre = genre.wireValue,
            targetMinLength = genre.targetMinLength,
            targetMaxLength = genre.targetMaxLength,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        creationDao.upsertProject(project)
        creationDao.insertMessage(
            CreativeMessageEntity(
                id = UUID.randomUUID().toString(),
                projectId = project.id,
                role = "USER",
                content = normalized,
                createdAtEpochMillis = now,
            ),
        )

        return try {
            submitInitialProject(project)
        } catch (error: Exception) {
            project.copy(status = "FAILED", updatedAtEpochMillis = System.currentTimeMillis())
                .also { creationDao.upsertProject(it) }
            throw error
        }
    }

    /** Retries the exact logical upload using the project's stable idempotency key. */
    suspend fun retryProject(projectId: String): WorkProjectEntity {
        val project = requireNotNull(creationDao.project(projectId))
        require(project.serverJobId == null) { "任务已由服务端接收" }
        require(project.status in setOf("FAILED", "PREPARING")) { "当前作品无需重新提交" }
        return try {
            submitInitialProject(project.copy(status = "PREPARING", stage = "PREPARING"))
        } catch (error: Exception) {
            project.copy(status = "FAILED", updatedAtEpochMillis = System.currentTimeMillis())
                .also { creationDao.upsertProject(it) }
            throw error
        }
    }

    suspend fun refreshProject(projectId: String): WorkProjectEntity {
        val project = requireNotNull(creationDao.project(projectId))
        val jobId = requireNotNull(project.serverJobId)
        val settings = settingsRepository.current()
        val snapshot = api.job(settings, jobId)
        var updated = project.copy(
            status = snapshot.status,
            stage = snapshot.stage,
            progress = snapshot.progress,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        when (snapshot.status) {
            "WAITING_FOR_USER" -> {
                val output = snapshot.output ?: error("Creative brief is missing")
                val brief = json.decodeFromJsonElement<CreativeBriefDto>(output)
                val briefVersionId = "job:$jobId:brief"
                if (creationDao.insertBriefVersion(
                        CreativeBriefVersionEntity(
                            id = briefVersionId,
                            projectId = projectId,
                            versionNumber = creationDao.maxBriefVersion(projectId) + 1,
                            contentJson = json.encodeToString(brief),
                            createdAtEpochMillis = System.currentTimeMillis(),
                        ),
                    ) != -1L
                ) {
                    creationDao.insertOutlineVersion(
                        OutlineVersionEntity(
                            id = "job:$jobId:outline",
                            projectId = projectId,
                            briefVersionId = briefVersionId,
                            versionNumber = creationDao.maxOutlineVersion(projectId) + 1,
                            contentJson = json.encodeToString(brief.structure),
                            createdAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                }
                updated = updated.copy(
                    title = brief.titleOptions.firstOrNull() ?: updated.title,
                    creativeBriefJson = json.encodeToString(brief),
                    genre = brief.genre,
                    targetMinLength = brief.targetLength.min,
                    targetMaxLength = brief.targetLength.max,
                )
            }
            "COMPLETED" -> {
                val output = snapshot.output ?: error("Creative result is missing")
                val result = json.decodeFromJsonElement<CreativeResultDto>(output)
                if (creationDao.version("job:$jobId") == null) {
                    val nextVersion = creationDao.maxVersion(projectId) + 1
                    creationDao.insertVersion(
                        ManuscriptVersionEntity(
                            id = "job:$jobId",
                            projectId = projectId,
                            versionNumber = nextVersion,
                            title = result.title,
                            synopsis = result.synopsis,
                            content = result.manuscript,
                            origin = "AGENT",
                            createdAtEpochMillis = System.currentTimeMillis(),
                        ),
                    )
                }
                val generatedBrief = result.creativeBrief
                if (generatedBrief != null) {
                    persistBrief(projectId, jobId, generatedBrief)
                }
                updated = updated.copy(
                    title = result.title,
                    creativeBriefJson = generatedBrief?.let { json.encodeToString(it) }
                        ?: updated.creativeBriefJson,
                    genre = generatedBrief?.genre ?: updated.genre,
                    targetMinLength = generatedBrief?.targetLength?.min ?: updated.targetMinLength,
                    targetMaxLength = generatedBrief?.targetLength?.max ?: updated.targetMaxLength,
                )
            }
        }
        creationDao.upsertProject(updated)
        return updated
    }

    suspend fun confirmBrief(projectId: String, approved: Boolean, feedback: String? = null) {
        val project = requireNotNull(creationDao.project(projectId))
        val jobId = requireNotNull(project.serverJobId)
        val settings = settingsRepository.current()
        val response = api.confirmCreativeBrief(settings, jobId, approved, feedback)
        creationDao.upsertProject(
            project.copy(
                status = response.status,
                stage = response.stage ?: "QUEUED",
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun cancelProject(projectId: String): WorkProjectEntity {
        val project = requireNotNull(creationDao.project(projectId))
        require(project.status in setOf("PREPARING", "QUEUED", "RUNNING", "WAITING_FOR_USER")) {
            "当前任务无法取消"
        }
        val jobId = requireNotNull(project.serverJobId) { "作品尚未提交到服务端" }
        api.cancelJob(settingsRepository.current(), jobId)
        return project.copy(
            status = "CANCELLED",
            stage = "CANCELLED",
            updatedAtEpochMillis = System.currentTimeMillis(),
        ).also { creationDao.upsertProject(it) }
    }

    suspend fun saveManualEdit(projectId: String, title: String, content: String) {
        val normalized = content.trim()
        require(normalized.isNotEmpty()) { "作品正文不能为空" }
        val nextVersion = creationDao.maxVersion(projectId) + 1
        creationDao.insertVersion(
            ManuscriptVersionEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                versionNumber = nextVersion,
                title = title.ifBlank { "未命名作品" },
                synopsis = "用户直接编辑",
                content = normalized,
                origin = "USER_EDIT",
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun restoreVersion(projectId: String, version: ManuscriptVersionEntity) {
        val nextVersion = creationDao.maxVersion(projectId) + 1
        creationDao.insertVersion(
            version.copy(
                id = UUID.randomUUID().toString(),
                versionNumber = nextVersion,
                origin = "RESTORE",
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun addFeedback(projectId: String, sentiment: String, note: String?) {
        creationDao.insertFeedback(
            WorkFeedbackEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                sentiment = sentiment,
                note = note?.trim()?.ifEmpty { null },
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun requestRevision(projectId: String, instruction: String): WorkProjectEntity {
        val project = requireNotNull(creationDao.project(projectId))
        val latest = creationDao.latestVersion(projectId)
            ?: error("当前作品还没有可修改的正文")
        val briefJson = project.creativeBriefJson ?: error("当前作品缺少创作方案")
        val settings = settingsRepository.current()
        val records = recordingDao.allUserMessages()
        val now = System.currentTimeMillis()
        creationDao.insertMessage(
            CreativeMessageEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                role = "USER",
                content = instruction,
                createdAtEpochMillis = now,
            ),
        )
        val corpus = records.map { record ->
            CreativeCorpusItemRequest(
                id = record.id,
                kind = "RECORD",
                content = record.content,
                occurredAt = Instant.ofEpochMilli(record.createdAtEpochMillis).toString(),
            )
        } + knowledgeRepository.corpusForCreation()
        val request = CreationJobRequest(
            idempotencyKey = "project-$projectId-revision-${UUID.randomUUID()}",
            projectId = projectId,
            intent = "请根据修改意见修订已有作品：$instruction",
            corpusVersion = corpusVersion(corpus),
            corpus = corpus,
            creationMode = project.creationMode,
            targetLength = project.targetLengthOrNull(),
            modelId = settings.selectedModelId,
            confirmedBrief = json.parseToJsonElement(briefJson),
            previousManuscript = latest.content,
        )
        val response = api.createCreationJob(settings, request)
        return project.copy(
            status = response.status,
            stage = response.stage ?: "QUEUED",
            progress = 0,
            serverJobId = response.jobId,
            updatedAtEpochMillis = now,
        ).also { creationDao.upsertProject(it) }
    }

    private fun corpusVersion(corpus: List<CreativeCorpusItemRequest>): String {
        val canonical = corpus.sortedBy { it.id }.joinToString("\n") { "${it.id}\u0000${it.content}" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
        return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private suspend fun submitInitialProject(project: WorkProjectEntity): WorkProjectEntity {
        val settings = settingsRepository.current()
        require(settings.accessToken.isNotBlank()) { "请先在设置中配置服务端访问令牌" }
        val records = recordingDao.allUserMessages()
        require(records.isNotEmpty()) { "请先记录一些人生素材" }
        val corpus = records.map { record ->
            CreativeCorpusItemRequest(
                id = record.id,
                kind = "RECORD",
                content = record.content,
                occurredAt = Instant.ofEpochMilli(record.createdAtEpochMillis).toString(),
            )
        } + knowledgeRepository.corpusForCreation()
        val response = api.createCreationJob(
            settings,
            CreationJobRequest(
                idempotencyKey = "project-${project.id}-brief-v1",
                projectId = project.id,
                intent = project.intent,
                corpusVersion = corpusVersion(corpus),
                corpus = corpus,
                creationMode = project.creationMode,
                requestedGenre = project.genre,
                targetLength = project.targetLengthOrNull(),
                modelId = settings.selectedModelId,
            ),
        )
        return project.copy(
            status = response.status,
            stage = response.stage ?: "QUEUED",
            progress = 0,
            serverJobId = response.jobId,
            updatedAtEpochMillis = System.currentTimeMillis(),
        ).also { creationDao.upsertProject(it) }
    }

    private fun WorkProjectEntity.targetLengthOrNull(): TargetLengthRequest? {
        val minimum = targetMinLength ?: return null
        val maximum = targetMaxLength ?: return null
        return TargetLengthRequest(minimum, maximum)
    }

    private suspend fun persistBrief(projectId: String, jobId: String, brief: CreativeBriefDto) {
        val briefVersionId = "job:$jobId:brief"
        if (creationDao.insertBriefVersion(
                CreativeBriefVersionEntity(
                    id = briefVersionId,
                    projectId = projectId,
                    versionNumber = creationDao.maxBriefVersion(projectId) + 1,
                    contentJson = json.encodeToString(brief),
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            ) != -1L
        ) {
            creationDao.insertOutlineVersion(
                OutlineVersionEntity(
                    id = "job:$jobId:outline",
                    projectId = projectId,
                    briefVersionId = briefVersionId,
                    versionNumber = creationDao.maxOutlineVersion(projectId) + 1,
                    contentJson = json.encodeToString(brief.structure),
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }
}
