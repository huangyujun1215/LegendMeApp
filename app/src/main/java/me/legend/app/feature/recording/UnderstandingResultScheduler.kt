package me.legend.app.feature.recording

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class UnderstandingResultScheduler(private val context: Context) {
    fun schedule(localTaskId: String) {
        val request = OneTimeWorkRequestBuilder<UnderstandingResultWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setInputData(Data.Builder().putString(UnderstandingResultWorker.LOCAL_TASK_ID, localTaskId).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "understanding-result-$localTaskId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
