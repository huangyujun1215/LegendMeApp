package me.legend.app.feature.recording

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.legend.app.core.network.KnowledgePatchDto
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.ServerSettingsRepository
import me.legend.app.feature.knowledge.KnowledgeRepository

@HiltWorker
class UnderstandingResultWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recordingRepository: RecordingRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val settingsRepository: ServerSettingsRepository,
    private val api: LegendMeApi,
    private val json: Json,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val localTaskId = inputData.getString(LOCAL_TASK_ID) ?: return Result.failure()
        val task = recordingRepository.analysis(localTaskId) ?: return Result.success()
        val serverJobId = task.serverJobId ?: return Result.retry()
        val settings = settingsRepository.current()
        if (task.status == "COMPLETED") {
            return runCatching { api.acknowledgeJob(settings, serverJobId) }
                .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
        }
        return try {
            val job = api.job(settings, serverJobId)
            when (job.status) {
                "COMPLETED" -> {
                    val output = job.output ?: return Result.failure()
                    val patch = json.decodeFromJsonElement<KnowledgePatchDto>(output)
                    knowledgeRepository.applyPatch(serverJobId, patch)
                    recordingRepository.markAnalysisCompleted(localTaskId, serverJobId)
                    runCatching { api.acknowledgeJob(settings, serverJobId) }
                        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
                }
                "FAILED", "CANCELLED" -> {
                    recordingRepository.markAnalysisFailed(localTaskId, job.errorMessage ?: job.status)
                    Result.failure()
                }
                else -> Result.retry()
            }
        } catch (error: Exception) {
            recordingRepository.markAnalysisFailed(localTaskId, error.message ?: "Result sync failed")
            Result.retry()
        }
    }

    companion object {
        const val LOCAL_TASK_ID = "local_task_id"
    }
}
