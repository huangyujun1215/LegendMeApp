package me.legend.app.core.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.legend.app.R

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CREATION_CHANNEL,
                "文学创作",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "作品完成或失败通知"
            },
        )
    }

    fun creationFinished(title: String, successful: Boolean) {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CREATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(if (successful) "作品已经写好" else "创作未能完成")
            .setContentText(title)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(title.hashCode(), notification)
        } catch (_: SecurityException) {
            // Android 13+ may not have notification permission yet. The result remains in the app.
        }
    }

    companion object {
        private const val CREATION_CHANNEL = "creation_results"
    }
}
