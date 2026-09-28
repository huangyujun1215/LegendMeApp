package me.legend.app.feature.creation

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreationMonitorScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun schedule(projectId: String) {
        val request = OneTimeWorkRequestBuilder<CreationMonitorWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putString(CreationMonitorWorker.PROJECT_ID, projectId).build())
            .build()
        workManager.enqueueUniqueWork(
            "creation-monitor-$projectId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(projectId: String) {
        workManager.cancelUniqueWork("creation-monitor-$projectId")
    }
}
