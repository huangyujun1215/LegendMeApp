package me.legend.app.feature.recording

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.ServerSettingsRepository
import me.legend.app.core.network.UnderstandingJobRequest
import me.legend.app.core.network.UnderstandingMessageRequest
import me.legend.app.feature.knowledge.KnowledgeRepository

@HiltWorker
class UnderstandingSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecordingRepository,
    private val settingsRepository: ServerSettingsRepository,
    private val api: LegendMeApi,
    private val knowledgeRepository: KnowledgeRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (settings.accessToken.isBlank()) return Result.retry()

        val tasks = repository.pendingAnalysis()
        for (task in tasks) {
            try {
                val messages = repository.userMessages(task.sessionId).map { message ->
                    UnderstandingMessageRequest(
                        id = message.id,
                        content = message.content,
                        createdAt = Instant.ofEpochMilli(message.createdAtEpochMillis).toString(),
                    )
                }
                if (messages.isEmpty()) {
                    repository.discardAnalysisForSession(task.sessionId)
                    continue
                }
                val response = api.createUnderstandingJob(
                    settings = settings,
                    input = UnderstandingJobRequest(
                        idempotencyKey = task.idempotencyKey,
                        sourceSessionId = task.sessionId,
                        messages = messages,
                        modelId = settings.selectedModelId,
                        currentKnowledge = knowledgeRepository.snapshotForAgent(),
                    ),
                )
                repository.markAnalysisSubmitted(task.id, response.jobId)
                UnderstandingResultScheduler(applicationContext).schedule(task.id)
            } catch (error: Exception) {
                repository.markAnalysisFailed(task.id, error.message ?: "Unknown sync error")
                return Result.retry()
            }
        }
        return Result.success()
    }
}
