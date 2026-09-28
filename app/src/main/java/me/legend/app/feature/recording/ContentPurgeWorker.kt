package me.legend.app.feature.recording

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.ServerSettingsRepository

@HiltWorker
class ContentPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecordingRepository,
    private val settingsRepository: ServerSettingsRepository,
    private val api: LegendMeApi,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (settings.accessToken.isBlank()) return Result.retry()

        for (deletion in repository.pendingContentDeletions()) {
            try {
                api.purgeContent(settings, deletion.contentId)
                repository.completeContentDeletion(deletion.contentId)
            } catch (error: Exception) {
                repository.failContentDeletion(
                    deletion.contentId,
                    error.message ?: "Server content purge failed",
                )
                return Result.retry()
            }
        }
        return Result.success()
    }
}
