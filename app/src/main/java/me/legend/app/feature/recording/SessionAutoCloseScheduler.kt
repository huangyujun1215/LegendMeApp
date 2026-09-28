package me.legend.app.feature.recording

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionAutoCloseScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun schedule(sessionId: String) {
        val request = OneTimeWorkRequestBuilder<CloseSessionWorker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putString(CloseSessionWorker.SESSION_ID, sessionId).build())
            .build()
        workManager.enqueueUniqueWork(
            workName(sessionId),
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(sessionId: String) {
        workManager.cancelUniqueWork(workName(sessionId))
    }

    private fun workName(sessionId: String) = "close-recording-session-$sessionId"
}
