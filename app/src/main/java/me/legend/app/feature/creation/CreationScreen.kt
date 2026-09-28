package me.legend.app.feature.creation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CreationScreen(viewModel: CreationViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("文学创作", style = MaterialTheme.typography.headlineMedium) }
        item {
            OutlinedTextField(
                value = state.intentDraft,
                onValueChange = viewModel::setIntent,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("你想写一个怎样的故事？") },
                minLines = 3,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("体裁", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiteraryGenre.Options.forEach { genre ->
                        FilterChip(
                            selected = state.selectedGenre == genre,
                            onClick = { viewModel.setGenre(genre) },
                            label = { Text(genre.label) },
                        )
                    }
                }
                Text(
                    if (state.selectedGenre == LiteraryGenre.Auto) {
                        "不确定也没关系，Agent 会从你的描述中判断。"
                    } else {
                        "目标篇幅：${state.selectedGenre.targetMinLength}–${state.selectedGenre.targetMaxLength} 字"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("创作方式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CreationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.creationMode == mode,
                            onClick = { viewModel.setCreationMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text(
                    state.creationMode.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Button(
                onClick = viewModel::createProject,
                enabled = state.intentDraft.isNotBlank() && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) "正在提交……" else "开始创作")
            }
        }
        state.error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (state.projects.isNotEmpty()) {
            item { Text("作品", style = MaterialTheme.typography.titleLarge) }
        }
        items(state.projects, key = { it.id }) { project ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectProject(project.id) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(project.title, style = MaterialTheme.typography.titleMedium)
                    project.genre?.let { genre ->
                        val range = if (project.targetMinLength != null && project.targetMaxLength != null) {
                            " · ${project.targetMinLength}–${project.targetMaxLength} 字"
                        } else ""
                        Text("${genreLabel(genre)}$range", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        CreationMode.entries.firstOrNull { it.name == project.creationMode }?.label
                            ?: project.creationMode,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stageLabel(project.stage), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (project.status in setOf("QUEUED", "RUNNING")) {
                        LinearProgressIndicator(
                            progress = { project.progress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                    if (project.status in setOf("PREPARING", "QUEUED", "RUNNING", "WAITING_FOR_USER")) {
                        TextButton(
                            onClick = { viewModel.cancelProject(project.id) },
                            enabled = !state.isSubmitting,
                        ) { Text("取消任务") }
                    }
                    if (project.status == "FAILED" && project.serverJobId == null) {
                        TextButton(
                            onClick = { viewModel.retryProject(project.id) },
                            enabled = !state.isSubmitting,
                        ) { Text("重试提交") }
                    }
                }
            }
        }
        state.brief?.let { brief ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("创作方案", style = MaterialTheme.typography.titleLarge)
                        Text(brief.premise)
                        Text("${brief.genre} · ${brief.perspective}")
                        Text("目标篇幅：${brief.targetLength.min}–${brief.targetLength.max} 字")
                        brief.structure.sortedBy { it.order }.forEach { section ->
                            Text("${section.order}. ${section.title} — ${section.purpose}")
                        }
                        Text("风格试写", style = MaterialTheme.typography.titleMedium)
                        brief.styleSamples.forEachIndexed { index, sample ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(sample)
                                    TextButton(onClick = { viewModel.selectStyleSample(index) }) {
                                        Text("选择这一种")
                                    }
                                }
                            }
                        }
                        brief.questions.forEach { Text("需要确认：$it") }
                        OutlinedTextField(
                            value = state.feedbackDraft,
                            onValueChange = viewModel::setFeedback,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("补充或修改意见") },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { viewModel.confirmBrief(false) },
                                enabled = state.selectedProject?.status == "WAITING_FOR_USER" && !state.isSubmitting,
                            ) { Text("调整方案") }
                            Button(
                                onClick = { viewModel.confirmBrief(true) },
                                enabled = state.selectedProject?.status == "WAITING_FOR_USER" && !state.isSubmitting,
                            ) { Text("确认并写作") }
                        }
                    }
                }
            }
        }
        state.versions.firstOrNull()?.let { version ->
            item {
                var editedContent by remember(version.id) { mutableStateOf(version.content) }
                LaunchedEffect(version.id) { editedContent = version.content }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(version.title, style = MaterialTheme.typography.headlineSmall)
                    Text(version.synopsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 520.dp),
                        minLines = 12,
                        label = { Text("正文 · 版本 ${version.versionNumber}") },
                    )
                    Button(
                        onClick = { viewModel.saveManualEdit(editedContent) },
                        enabled = editedContent.trim() != version.content.trim(),
                    ) { Text("保存为新版本") }
                    OutlinedTextField(
                        value = state.revisionDraft,
                        onValueChange = viewModel::setRevision,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("告诉 Agent 你希望怎样修改") },
                    )
                    Button(
                        onClick = viewModel::requestRevision,
                        enabled = state.revisionDraft.isNotBlank() && !state.isSubmitting,
                    ) { Text("让 Agent 修改") }
                    Text("这篇作品带给你的感受", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("触动", "温暖", "悲伤", "释然", "无感").forEach { sentiment ->
                            TextButton(onClick = { viewModel.submitFeedback(sentiment, state.feedbackDraft) }) {
                                Text(sentiment)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.feedbackDraft,
                        onValueChange = viewModel::setFeedback,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("补充感受（可选）") },
                    )
                }
            }
        }
        if (state.versions.size > 1) {
            item { Text("历史版本", style = MaterialTheme.typography.titleMedium) }
            items(state.versions.drop(1), key = { it.id }) { version ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("版本 ${version.versionNumber} · ${version.origin}")
                        TextButton(onClick = { viewModel.restoreVersion(version) }) { Text("恢复") }
                    }
                }
            }
        }
    }
}

private fun genreLabel(value: String): String =
    LiteraryGenre.Options.firstOrNull { it.wireValue == value }?.label ?: value

private fun stageLabel(stage: String): String = when (stage) {
    "QUEUED", "PREPARING" -> "准备中"
    "RETRIEVING" -> "整理素材"
    "PLANNING" -> "设计叙事"
    "WAITING_FOR_USER" -> "等待确认"
    "WRITING" -> "正在写作"
    "REVIEWING", "VALIDATING" -> "文学审校"
    "COMPLETED" -> "已完成"
    "FAILED" -> "创作失败"
    "CANCELLED" -> "已取消"
    else -> stage
}
