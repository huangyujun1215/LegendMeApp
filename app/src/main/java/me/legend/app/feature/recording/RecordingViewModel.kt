package me.legend.app.feature.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.legend.app.core.network.CompanionMessageRequest
import me.legend.app.core.network.CompanionRequest
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.ServerSettingsRepository
import me.legend.app.feature.knowledge.KnowledgeRepository

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModel @Inject constructor(
    private val repository: RecordingRepository,
    private val scheduler: SessionAutoCloseScheduler,
    private val syncScheduler: UnderstandingSyncScheduler,
    private val contentPurgeScheduler: ContentPurgeScheduler,
    private val settingsRepository: ServerSettingsRepository,
    private val api: LegendMeApi,
    private val knowledgeRepository: KnowledgeRepository,
) : ViewModel() {
    private val sessionId = MutableStateFlow<String?>(null)
    private val mode = MutableStateFlow(RecordingMode.LISTEN)
    private val draft = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)
    private val ending = MutableStateFlow(false)
    private val companionReplying = MutableStateFlow(false)
    private val suggestedPrompt = MutableStateFlow("此刻，有什么值得被记住？")

    private val messages = sessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
    }
    private val outstandingAnalysisCount = repository.observeOutstandingAnalysisCount()

    private data class SessionState(
        val sessionId: String?,
        val mode: RecordingMode,
        val draft: String,
        val messages: List<RecordingMessage>,
    )

    private val sessionState = combine(sessionId, mode, draft, messages) { id, selectedMode, text, items ->
        SessionState(id, selectedMode, text, items)
    }

    private val activityState = combine(
        ending,
        companionReplying,
        outstandingAnalysisCount,
        suggestedPrompt,
    ) { isEnding, isReplying, outstandingCount, prompt ->
        listOf(isEnding, isReplying, outstandingCount, prompt)
    }

    val uiState = combine(
        sessionState,
        activityState,
        error,
    ) { session, activity, currentError ->
        RecordingUiState(
            sessionId = session.sessionId,
            mode = session.mode,
            draft = session.draft,
            messages = session.messages,
            isEndingSession = activity[0] as Boolean,
            isCompanionReplying = activity[1] as Boolean,
            outstandingAnalysisCount = activity[2] as Int,
            suggestedPrompt = activity[3] as String,
            error = currentError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingUiState())

    init {
        viewModelScope.launch {
            sessionId.value = repository.getOrCreateActiveSession()
            // Re-enqueue durable PENDING/FAILED work after process restarts or connectivity recovery.
            syncScheduler.schedule()
            contentPurgeScheduler.schedule()
            loadSuggestedPrompt()
        }
    }

    fun setMode(value: RecordingMode) {
        mode.value = value
    }

    fun setDraft(value: String) {
        draft.value = value
    }

    fun send() {
        val id = sessionId.value ?: return
        val content = draft.value.trim()
        if (content.isEmpty()) return
        draft.value = ""
        viewModelScope.launch {
            val selectedMode = mode.value
            runCatching { repository.appendUserMessage(id, content, selectedMode) }
                .onSuccess {
                    scheduler.schedule(id)
                    if (selectedMode == RecordingMode.CONVERSE) requestCompanionReply(id)
                }
                .onFailure {
                    draft.value = content
                    error.value = it.message ?: "保存失败"
                }
        }
    }

    private fun requestCompanionReply(sessionId: String) {
        viewModelScope.launch {
            companionReplying.value = true
            runCatching {
                val settings = settingsRepository.current()
                require(settings.accessToken.isNotBlank()) { "请先在设置中配置服务端访问令牌" }
                val recentMessages = repository.messages(sessionId).takeLast(30).map { message ->
                    CompanionMessageRequest(
                        role = message.role,
                        content = message.content,
                        createdAt = Instant.ofEpochMilli(message.createdAtEpochMillis).toString(),
                    )
                }
                val latestUserText = recentMessages.lastOrNull { it.role == "USER" }?.content.orEmpty()
                val relevantKnowledge = knowledgeRepository.search(latestUserText, limit = 8).map { it.text }
                api.companion(
                    settings,
                    CompanionRequest(
                        sessionId = sessionId,
                        messages = recentMessages,
                        relevantKnowledge = relevantKnowledge,
                        modelId = settings.selectedModelId,
                    ),
                )
            }.onSuccess { response ->
                repository.appendCompanionMessage(sessionId, response.content)
            }.onFailure { failure ->
                error.value = "内容已保存，但陪伴回复失败：${failure.message ?: "未知错误"}"
            }
            companionReplying.value = false
        }
    }

    private suspend fun loadSuggestedPrompt() {
        val id = sessionId.value ?: return
        val settings = settingsRepository.current()
        if (settings.accessToken.isBlank()) return
        val knowledge = knowledgeRepository.snapshotForAgent().toString()
        if (knowledge.length < 40) return
        runCatching {
            api.companion(
                settings,
                CompanionRequest(
                    sessionId = "prompt:$id",
                    messages = listOf(
                        CompanionMessageRequest(
                            role = "USER",
                            content = "请根据已有材料生成一个温和、具体、不过度触碰创伤的单句记录引导。只返回问题。",
                            createdAt = Instant.now().toString(),
                        ),
                    ),
                    relevantKnowledge = listOf(knowledge),
                    modelId = settings.selectedModelId,
                ),
            )
        }.onSuccess { suggestedPrompt.value = it.content.trim().ifEmpty { suggestedPrompt.value } }
    }

    fun endSession() {
        val id = sessionId.value ?: return
        viewModelScope.launch {
            ending.value = true
            runCatching { repository.endSessionAndQueueAnalysis(id) }
                .onSuccess {
                    scheduler.cancel(id)
                    syncScheduler.schedule()
                    sessionId.value = repository.getOrCreateActiveSession()
                }
                .onFailure { error.value = it.message ?: "结束记录失败" }
            ending.value = false
        }
    }

    fun clearError() {
        error.value = null
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val localResult = runCatching { repository.permanentlyDeleteMessage(messageId) }
            if (localResult.isFailure) {
                error.value = "删除失败：${localResult.exceptionOrNull()?.message}"
                return@launch
            }
            contentPurgeScheduler.schedule()
            syncScheduler.schedule()
        }
    }
}
