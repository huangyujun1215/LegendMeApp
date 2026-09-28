package me.legend.app.feature.recording

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CloseSessionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecordingRepository,
    private val syncScheduler: UnderstandingSyncScheduler,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(SESSION_ID) ?: return Result.failure()
        if (repository.endSessionAndQueueAnalysis(sessionId)) {
            syncScheduler.schedule()
        }
        return Result.success()
    }

    companion object {
        const val SESSION_ID = "session_id"
    }
}
