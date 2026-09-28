package me.legend.app.core.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.serverSettingsDataStore by preferencesDataStore(name = "server_settings")

data class ServerSettings(
    val baseUrl: String = "http://10.0.2.2:3100",
    val accessToken: String = "",
    val selectedModelId: String = "glm-5.3",
    val notificationsEnabled: Boolean = true,
)

@Singleton
class ServerSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseUrlKey = stringPreferencesKey("server_base_url")
    private val accessTokenKey = stringPreferencesKey("device_access_token")
    private val selectedModelKey = stringPreferencesKey("selected_model_id")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")

    val settings: Flow<ServerSettings> = context.serverSettingsDataStore.data.map { values ->
        ServerSettings(
            baseUrl = values[baseUrlKey] ?: "http://10.0.2.2:3100",
            accessToken = values[accessTokenKey] ?: "",
            selectedModelId = values[selectedModelKey] ?: "glm-5.3",
            notificationsEnabled = values[notificationsEnabledKey] ?: true,
        )
    }

    suspend fun current(): ServerSettings = settings.first()

    suspend fun update(baseUrl: String, accessToken: String, selectedModelId: String) {
        context.serverSettingsDataStore.edit { values ->
            values[baseUrlKey] = baseUrl.trimEnd('/')
            values[accessTokenKey] = accessToken
            values[selectedModelKey] = selectedModelId
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.serverSettingsDataStore.edit { values -> values[notificationsEnabledKey] = enabled }
    }
}
