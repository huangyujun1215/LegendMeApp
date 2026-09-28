package me.legend.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.ModelInfo
import me.legend.app.core.network.ServerSettings
import me.legend.app.core.network.ServerSettingsRepository

data class ConnectionState(
    val isChecking: Boolean = false,
    val message: String? = null,
    val successful: Boolean = false,
)

data class DiagnosticsState(
    val summary: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ServerSettingsRepository,
    private val api: LegendMeApi,
    private val backupService: BackupService,
) : ViewModel() {
    val settings: StateFlow<ServerSettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ServerSettings(),
    )
    val connection = MutableStateFlow(ConnectionState())
    val availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val backupMessage = MutableStateFlow<String?>(null)
    val diagnostics = MutableStateFlow(DiagnosticsState())

    fun save(baseUrl: String, token: String, modelId: String) {
        viewModelScope.launch {
            repository.update(baseUrl, token, modelId)
            connection.value = ConnectionState(message = "设置已保存", successful = true)
        }
    }

    fun test(baseUrl: String, token: String, modelId: String) {
        viewModelScope.launch {
            connection.value = ConnectionState(isChecking = true)
            runCatching {
                val candidate = ServerSettings(baseUrl.trimEnd('/'), token, modelId)
                val health = api.health(candidate)
                val models = api.models(candidate)
                check(health.status == "ok")
                models.models
            }.onSuccess { models ->
                availableModels.value = models
                connection.value = ConnectionState(
                    message = "连接成功，可用模型 ${models.size} 个",
                    successful = true,
                )
            }.onFailure { error ->
                connection.value = ConnectionState(message = error.message ?: "连接失败")
            }
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupService.exportTo(uri) }
                .onSuccess { backupMessage.value = "备份已导出" }
                .onFailure { backupMessage.value = "备份失败：${it.message}" }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupService.restoreFrom(uri) }
                .onSuccess { backupMessage.value = "备份已恢复" }
                .onFailure { backupMessage.value = "恢复失败：${it.message}" }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun loadDiagnostics() {
        viewModelScope.launch {
            diagnostics.value = DiagnosticsState(isLoading = true)
            runCatching {
                api.diagnostics(repository.current())
            }.onSuccess { result ->
                diagnostics.value = DiagnosticsState(
                    summary = "任务 ${result.jobs.total} 次 · Token ${result.usage.totalTokens} · Agent ${result.usage.turns} 轮 · 工具 ${result.usage.toolCalls} 次",
                )
            }.onFailure {
                diagnostics.value = DiagnosticsState(summary = "诊断信息获取失败：${it.message}")
            }
        }
    }
}
