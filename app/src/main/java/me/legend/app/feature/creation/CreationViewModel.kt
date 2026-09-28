package me.legend.app.feature.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.legend.app.core.database.ManuscriptVersionEntity
import me.legend.app.core.database.WorkProjectEntity
import me.legend.app.core.network.CreativeBriefDto

data class CreationUiState(
    val projects: List<WorkProjectEntity> = emptyList(),
    val selectedProject: WorkProjectEntity? = null,
    val versions: List<ManuscriptVersionEntity> = emptyList(),
    val brief: CreativeBriefDto? = null,
    val intentDraft: String = "",
    val feedbackDraft: String = "",
    val revisionDraft: String = "",
    val selectedGenre: LiteraryGenre = LiteraryGenre.Auto,
    val creationMode: CreationMode = CreationMode.COLLABORATIVE,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CreationViewModel @Inject constructor(
    private val repository: CreationRepository,
    private val json: Json,
    private val monitorScheduler: CreationMonitorScheduler,
) : ViewModel() {
    private val selectedProjectId = MutableStateFlow<String?>(null)
    private val intentDraft = MutableStateFlow("")
    private val feedbackDraft = MutableStateFlow("")
    private val revisionDraft = MutableStateFlow("")
    private val selectedGenre = MutableStateFlow(LiteraryGenre.Auto)
    private val creationMode = MutableStateFlow(CreationMode.COLLABORATIVE)
    private val isSubmitting = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val monitors = mutableMapOf<String, Job>()

    private val projects = repository.observeProjects()
    private val versions = selectedProjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeVersions(id)
    }

    private val projectState = combine(projects, selectedProjectId, versions) { projectList, selectedId, versionList ->
        Triple(projectList, selectedId, versionList)
    }

    private val creationPreferences = combine(selectedGenre, creationMode) { genre, mode -> genre to mode }

    private val editingState = combine(
        intentDraft,
        feedbackDraft,
        revisionDraft,
        creationPreferences,
    ) { intent, feedback, revision, (genre, mode) ->
        CreationDraftState(intent, feedback, revision, genre, mode)
    }

    val uiState: StateFlow<CreationUiState> = combine(
        projectState,
        editingState,
        isSubmitting,
        error,
    ) { (projectList, selectedId, versionList), drafts, submitting, currentError ->
        val selected = projectList.firstOrNull { it.id == selectedId }
        CreationUiState(
            projects = projectList,
            selectedProject = selected,
            versions = versionList,
            brief = selected?.creativeBriefJson?.let { runCatching { json.decodeFromString<CreativeBriefDto>(it) }.getOrNull() },
            intentDraft = drafts.intent,
            feedbackDraft = drafts.feedback,
            revisionDraft = drafts.revision,
            selectedGenre = drafts.genre,
            creationMode = drafts.creationMode,
            isSubmitting = submitting,
            error = currentError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreationUiState())

    init {
        viewModelScope.launch {
            runCatching { repository.recoverRemoteProjects() }
            repository.activeProjects().forEach { project -> monitor(project.id) }
        }
    }

    fun setIntent(value: String) {
        intentDraft.value = value
    }

    fun setFeedback(value: String) {
        feedbackDraft.value = value
    }

    fun setRevision(value: String) {
        revisionDraft.value = value
    }

    fun setGenre(value: LiteraryGenre) {
        selectedGenre.value = value
    }

    fun setCreationMode(value: CreationMode) {
        creationMode.value = value
    }

    fun selectStyleSample(index: Int) {
        feedbackDraft.value = "采用第 ${index + 1} 段试写所体现的风格。"
    }

    fun selectProject(id: String) {
        selectedProjectId.value = id
        viewModelScope.launch { repository.project(id)?.let { if (it.serverJobId != null) monitor(id) } }
    }

    fun createProject() {
        val intent = intentDraft.value.trim()
        if (intent.isEmpty() || isSubmitting.value) return
        viewModelScope.launch {
            isSubmitting.value = true
            runCatching { repository.createProject(intent, selectedGenre.value, creationMode.value) }
                .onSuccess { project ->
                    intentDraft.value = ""
                    selectedGenre.value = LiteraryGenre.Auto
                    creationMode.value = CreationMode.COLLABORATIVE
                    selectedProjectId.value = project.id
                    monitor(project.id)
                    monitorScheduler.schedule(project.id)
                }
                .onFailure { error.value = it.message ?: "创建作品失败" }
            isSubmitting.value = false
        }
    }

    private data class CreationDraftState(
        val intent: String,
        val feedback: String,
        val revision: String,
        val genre: LiteraryGenre,
        val creationMode: CreationMode,
    )

    fun confirmBrief(approved: Boolean) {
        val projectId = selectedProjectId.value ?: return
        viewModelScope.launch {
            isSubmitting.value = true
            runCatching { repository.confirmBrief(projectId, approved, feedbackDraft.value.ifBlank { null }) }
                .onSuccess {
                    feedbackDraft.value = ""
                    monitor(projectId)
                    monitorScheduler.schedule(projectId)
                }
                .onFailure { error.value = it.message ?: "提交创作方案失败" }
            isSubmitting.value = false
        }
    }

    fun saveManualEdit(content: String) {
        val state = uiState.value
        val project = state.selectedProject ?: return
        val latest = state.versions.firstOrNull() ?: return
        viewModelScope.launch {
            runCatching { repository.saveManualEdit(project.id, latest.title, content) }
                .onFailure { error.value = it.message ?: "保存修改失败" }
        }
    }

    fun restoreVersion(version: ManuscriptVersionEntity) {
        val projectId = selectedProjectId.value ?: return
        viewModelScope.launch {
            runCatching { repository.restoreVersion(projectId, version) }
                .onFailure { error.value = it.message ?: "恢复版本失败" }
        }
    }

    fun submitFeedback(sentiment: String, note: String?) {
        val projectId = selectedProjectId.value ?: return
        viewModelScope.launch {
            runCatching { repository.addFeedback(projectId, sentiment, note) }
                .onSuccess { feedbackDraft.value = "" }
                .onFailure { error.value = it.message ?: "保存反馈失败" }
        }
    }

    fun requestRevision() {
        val projectId = selectedProjectId.value ?: return
        val instruction = revisionDraft.value.trim()
        if (instruction.isEmpty()) return
        viewModelScope.launch {
            isSubmitting.value = true
            runCatching { repository.requestRevision(projectId, instruction) }
                .onSuccess {
                    revisionDraft.value = ""
                    monitor(projectId)
                    monitorScheduler.schedule(projectId)
                }
                .onFailure { error.value = it.message ?: "提交修改失败" }
            isSubmitting.value = false
        }
    }

    fun cancelProject(projectId: String) {
        if (isSubmitting.value) return
        viewModelScope.launch {
            isSubmitting.value = true
            runCatching { repository.cancelProject(projectId) }
                .onSuccess {
                    monitors.remove(projectId)?.cancel()
                    monitorScheduler.cancel(projectId)
                }
                .onFailure { error.value = it.message ?: "取消任务失败" }
            isSubmitting.value = false
        }
    }

    fun retryProject(projectId: String) {
        if (isSubmitting.value) return
        viewModelScope.launch {
            isSubmitting.value = true
            runCatching { repository.retryProject(projectId) }
                .onSuccess {
                    monitor(projectId)
                    monitorScheduler.schedule(projectId)
                }
                .onFailure { error.value = it.message ?: "重新提交失败" }
            isSubmitting.value = false
        }
    }

    private fun monitor(projectId: String) {
        if (monitors[projectId]?.isActive == true) return
        monitors[projectId] = viewModelScope.launch {
            runCatching { repository.refreshProject(projectId) }
            repository.observeRemoteEvents(projectId)
                .retryWhen { _, attempt ->
                    if (attempt >= 5) false else {
                        delay((attempt + 1) * 1_000)
                        true
                    }
                }
                .catch { failure -> error.value = failure.message ?: "任务进度连接中断" }
                .takeWhile { event ->
                    if (event.type in setOf("JOB_PROGRESS", "JOB_WAITING_FOR_USER", "JOB_COMPLETED", "JOB_FAILED", "JOB_CANCELLED")) {
                        repository.refreshProject(projectId)
                    }
                    event.type !in setOf("JOB_WAITING_FOR_USER", "JOB_COMPLETED", "JOB_FAILED", "JOB_CANCELLED")
                }
                .collect {}
            monitors.remove(projectId)
        }
    }
}
