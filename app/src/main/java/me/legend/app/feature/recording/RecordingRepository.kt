package me.legend.app.feature.recording

import androidx.room.withTransaction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.legend.app.core.database.ConversationSessionEntity
import me.legend.app.core.database.LegendMeDatabase
import me.legend.app.core.database.MessageEntity
import me.legend.app.core.database.PendingAnalysisEntity
import me.legend.app.core.database.PendingContentDeletionEntity
import me.legend.app.core.database.RecordEventEntity
import me.legend.app.core.database.RecordingDao
import me.legend.app.core.database.KnowledgeDao

@Singleton
class RecordingRepository @Inject constructor(
    private val database: LegendMeDatabase,
    private val dao: RecordingDao,
    private val knowledgeDao: KnowledgeDao,
) {
    suspend fun getOrCreateActiveSession(now: Long = System.currentTimeMillis()): String =
        database.withTransaction {
            dao.activeSession()?.id ?: UUID.randomUUID().toString().also { id ->
                dao.insertSession(ConversationSessionEntity(id = id, createdAtEpochMillis = now))
            }
        }

    fun observeMessages(sessionId: String): Flow<List<RecordingMessage>> =
        dao.observeMessages(sessionId).map { messages ->
            messages.map { message ->
                RecordingMessage(
                    id = message.id,
                    role = message.role,
                    content = message.content,
                    createdAtEpochMillis = message.createdAtEpochMillis,
                )
            }
        }

    suspend fun appendUserMessage(
        sessionId: String,
        content: String,
        mode: RecordingMode,
        now: Long = System.currentTimeMillis(),
    ) {
        val normalized = content.trim()
        require(normalized.isNotEmpty()) { "Message cannot be blank" }
        database.withTransaction {
            val messageId = UUID.randomUUID().toString()
            dao.insertMessage(
                MessageEntity(
                    id = messageId,
                    sessionId = sessionId,
                    role = "USER",
                    mode = mode.name,
                    content = normalized,
                    createdAtEpochMillis = now,
                ),
            )
            dao.insertRecordEvent(
                RecordEventEntity(
                    id = UUID.randomUUID().toString(),
                    messageId = messageId,
                    type = "APPEND",
                    createdAtEpochMillis = now,
                ),
            )
        }
    }

    suspend fun endSessionAndQueueAnalysis(
        sessionId: String,
        now: Long = System.currentTimeMillis(),
    ): Boolean = database.withTransaction {
        val session = dao.session(sessionId) ?: return@withTransaction false
        if (session.endedAtEpochMillis != null) return@withTransaction false
        if (dao.userMessageCount(sessionId) == 0) return@withTransaction false
        if (dao.endSession(sessionId, now) != 1) return@withTransaction false
        dao.insertPendingAnalysis(
            PendingAnalysisEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                idempotencyKey = "session-$sessionId-v1",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        true
    }

    suspend fun pendingAnalysis(limit: Int = 5): List<PendingAnalysisEntity> =
        dao.pendingAnalysis(limit)

    fun observeOutstandingAnalysisCount(): Flow<Int> = dao.observeOutstandingAnalysisCount()

    suspend fun analysis(id: String): PendingAnalysisEntity? = dao.analysis(id)

    suspend fun discardAnalysisForSession(sessionId: String) = dao.deleteAnalysisForSession(sessionId)

    suspend fun pendingContentDeletions(limit: Int = 20): List<PendingContentDeletionEntity> =
        dao.pendingContentDeletions(limit)

    suspend fun completeContentDeletion(contentId: String) = dao.completeContentDeletion(contentId)

    suspend fun failContentDeletion(contentId: String, error: String) =
        dao.failContentDeletion(contentId, error)

    suspend fun userMessages(sessionId: String): List<MessageEntity> = dao.userMessages(sessionId)

    suspend fun messages(sessionId: String): List<MessageEntity> = dao.messages(sessionId)

    fun observeAllUserMessages(): Flow<List<RecordingMessage>> =
        dao.observeAllUserMessages().map { messages ->
            messages.map { message ->
                RecordingMessage(
                    id = message.id,
                    role = message.role,
                    content = message.content,
                    createdAtEpochMillis = message.createdAtEpochMillis,
                )
            }
        }

    suspend fun appendCompanionMessage(
        sessionId: String,
        content: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val normalized = content.trim()
        if (normalized.isEmpty()) return
        dao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "COMPANION",
                mode = RecordingMode.CONVERSE.name,
                content = normalized,
                createdAtEpochMillis = now,
            ),
        )
    }

    suspend fun markAnalysisSubmitted(taskId: String, serverJobId: String) {
        dao.updateAnalysis(
            id = taskId,
            status = "SUBMITTED",
            serverJobId = serverJobId,
            error = null,
            attemptIncrement = 1,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun markAnalysisFailed(taskId: String, error: String) {
        dao.updateAnalysis(
            id = taskId,
            status = "FAILED",
            serverJobId = null,
            error = error,
            attemptIncrement = 1,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun markAnalysisCompleted(taskId: String, serverJobId: String) {
        dao.updateAnalysis(
            id = taskId,
            status = "COMPLETED",
            serverJobId = serverJobId,
            error = null,
            attemptIncrement = 0,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun permanentlyDeleteMessage(messageId: String) {
        database.withTransaction {
            val message = dao.message(messageId) ?: return@withTransaction
            require(message.role == "USER") { "只能删除用户原始记录" }
            val analysis = dao.analysisForSession(message.sessionId)
            val affected = knowledgeDao.evidenceForSource(messageId)
            knowledgeDao.deleteEvidenceForSource(messageId)
            affected.distinctBy { it.targetType to it.targetId }.forEach { evidence ->
                if (knowledgeDao.evidenceCount(evidence.targetType, evidence.targetId) == 0) {
                    when (evidence.targetType) {
                        "PERSON" -> {
                            knowledgeDao.deletePersonLinks(evidence.targetId)
                            knowledgeDao.deleteRelationsForPerson(evidence.targetId)
                            knowledgeDao.deletePerson(evidence.targetId)
                        }
                        "EVENT" -> {
                            knowledgeDao.deleteEventPeople(evidence.targetId)
                            knowledgeDao.deleteEventThemes(evidence.targetId)
                            knowledgeDao.deleteSearchDocument("EVENT", evidence.targetId)
                            knowledgeDao.deleteFtsDocument("EVENT:${evidence.targetId}")
                            knowledgeDao.deleteEvent(evidence.targetId)
                        }
                        "FACT" -> {
                            val factVersion = knowledgeDao.factVersion(evidence.targetId)
                            knowledgeDao.deleteFact(evidence.targetId)
                            factVersion?.let { deletedVersion ->
                                val latest = knowledgeDao.latestFactVersion(deletedVersion.factId)
                                if (latest == null) {
                                    knowledgeDao.deleteFactRoot(deletedVersion.factId)
                                } else {
                                    knowledgeDao.activateFactVersion(latest.id)
                                    knowledgeDao.setCurrentFactVersion(deletedVersion.factId, latest.id)
                                }
                            }
                        }
                        "RELATION" -> knowledgeDao.deleteRelation(evidence.targetId)
                        "SUMMARY" -> {
                            knowledgeDao.deleteSearchDocument("SUMMARY", evidence.targetId)
                            knowledgeDao.deleteFtsDocument("SUMMARY:${evidence.targetId}")
                        }
                    }
                }
            }
            dao.deleteRecordEvents(messageId)
            dao.deleteMessage(messageId)
            if (analysis != null && analysis.status != "COMPLETED") {
                dao.deleteAnalysisForSession(message.sessionId)
                if (dao.userMessageCount(message.sessionId) > 0) {
                    val now = System.currentTimeMillis()
                    dao.insertPendingAnalysis(
                        PendingAnalysisEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = message.sessionId,
                            idempotencyKey = "session-${message.sessionId}-after-delete-$now",
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                        ),
                    )
                }
            }
            dao.insertPendingContentDeletion(
                PendingContentDeletionEntity(
                    contentId = messageId,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            dao.insertRecordEvent(
                RecordEventEntity(
                    id = UUID.randomUUID().toString(),
                    messageId = messageId,
                    type = "TOMBSTONE",
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }
}
