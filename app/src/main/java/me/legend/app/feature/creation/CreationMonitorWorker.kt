package me.legend.app.feature.creation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.legend.app.core.common.NotificationService
import me.legend.app.core.network.ServerSettingsRepository

@HiltWorker
class CreationMonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: CreationRepository,
    private val settingsRepository: ServerSettingsRepository,
    private val notifications: NotificationService,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val projectId = inputData.getString(PROJECT_ID) ?: return Result.failure()
        return try {
            val project = repository.refreshProject(projectId)
            when (project.status) {
                "COMPLETED" -> {
                    if (settingsRepository.current().notificationsEnabled) {
                        notifications.creationFinished(project.title, successful = true)
                    }
                    Result.success()
                }
                "FAILED", "CANCELLED" -> {
                    if (settingsRepository.current().notificationsEnabled) {
                        notifications.creationFinished(project.title, successful = false)
                    }
                    Result.failure()
                }
                "WAITING_FOR_USER" -> Result.success()
                else -> Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val PROJECT_ID = "project_id"
    }
}
