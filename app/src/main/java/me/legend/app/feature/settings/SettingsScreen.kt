package me.legend.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val persisted by viewModel.settings.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restoreBackup)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setNotificationsEnabled(granted) }
    var baseUrl by remember { mutableStateOf(persisted.baseUrl) }
    var token by remember { mutableStateOf(persisted.accessToken) }
    var modelId by remember { mutableStateOf(persisted.selectedModelId) }

    LaunchedEffect(persisted) {
        baseUrl = persisted.baseUrl
        token = persisted.accessToken
        modelId = persisted.selectedModelId
    }

    SettingsScreen(
        baseUrl = baseUrl,
        token = token,
        modelId = modelId,
        connection = connection,
        backupMessage = backupMessage,
        notificationsEnabled = persisted.notificationsEnabled,
        availableModels = availableModels.map { it.id to it.displayName },
        onBaseUrlChange = { baseUrl = it },
        onTokenChange = { token = it },
        onModelIdChange = { modelId = it },
        onSave = { viewModel.save(baseUrl, token, modelId) },
        onTest = { viewModel.test(baseUrl, token, modelId) },
        onExport = { exportLauncher.launch("legendme-${LocalDate.now()}.zip") },
        onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
        onNotificationsChanged = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= 33) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.setNotificationsEnabled(enabled)
            }
        },
        diagnostics = diagnostics,
        onLoadDiagnostics = viewModel::loadDiagnostics,
    )
}

@Composable
private fun SettingsScreen(
    baseUrl: String,
    token: String,
    modelId: String,
    connection: ConnectionState,
    backupMessage: String?,
    notificationsEnabled: Boolean,
    availableModels: List<Pair<String, String>>,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    diagnostics: DiagnosticsState,
    onLoadDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Text("本地 Agent 服务", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务地址") },
            supportingText = { Text("模拟器默认使用 http://10.0.2.2:3100") },
            singleLine = true,
        )
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("设备访问令牌") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        ModelSelector(
            modelId = modelId,
            availableModels = availableModels,
            onModelIdChange = onModelIdChange,
        )
        connection.message?.let { message ->
            Text(
                message,
                color = if (connection.successful) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("保存")
        }
        TextButton(
            onClick = onTest,
            enabled = !connection.isChecking && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (connection.isChecking) "正在连接……" else "测试连接")
        }
        Text("数据", style = MaterialTheme.typography.titleMedium)
        Text(
            "备份包含原始记录和作品，不包含可重新生成的知识索引。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport) { Text("导出备份") }
            TextButton(onClick = onImport) { Text("恢复备份") }
        }
        backupMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("创作完成通知")
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = onNotificationsChanged,
            )
        }
        Text("开发诊断", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onLoadDiagnostics, enabled = !diagnostics.isLoading) {
            Text(if (diagnostics.isLoading) "正在读取……" else "刷新运行指标")
        }
        diagnostics.summary?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(
    modelId: String,
    availableModels: List<Pair<String, String>>,
    onModelIdChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = modelId,
            onValueChange = onModelIdChange,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            label = { Text("模型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableModels.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text("$name · $id") },
                    onClick = {
                        onModelIdChange(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
