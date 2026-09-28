package me.legend.app.feature.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RecordingRoute(viewModel: RecordingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecordingScreen(
        state = state,
        onModeChange = viewModel::setMode,
        onDraftChange = viewModel::setDraft,
        onSend = viewModel::send,
        onEndSession = viewModel::endSession,
        onDeleteMessage = viewModel::deleteMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    state: RecordingUiState,
    onModeChange: (RecordingMode) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onEndSession: () -> Unit,
    onDeleteMessage: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("LegendMe", style = MaterialTheme.typography.headlineMedium)
            Text(
                "把此刻留下，故事会在以后生长。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.outstandingAnalysisCount > 0) {
                Text(
                    "${state.outstandingAnalysisCount} 段记录正在等待理解",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RecordingMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, RecordingMode.entries.size),
                    ) {
                        Text(if (mode == RecordingMode.LISTEN) "倾听" else "交流")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            state.suggestedPrompt,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(text = message.content, style = MaterialTheme.typography.bodyLarge)
                            if (message.role == "USER") {
                                TextButton(onClick = { pendingDelete = message.id }) { Text("删除") }
                            }
                        }
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            if (state.isCompanionReplying) {
                Text(
                    "正在回应……",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            TextField(
                value = state.draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("写下你想说的……") },
                minLines = 3,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onEndSession, enabled = !state.isEndingSession) {
                    Text("结束本次记录")
                }
                Button(onClick = onSend, enabled = state.draft.isNotBlank()) {
                    Text("记下")
                }
            }
        }
    }
    pendingDelete?.let { messageId ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("永久删除这条记录？") },
            text = { Text("原文及只由它产生的知识将被清除，无法恢复。") },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = null
                    onDeleteMessage(messageId)
                }) { Text("永久删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}
