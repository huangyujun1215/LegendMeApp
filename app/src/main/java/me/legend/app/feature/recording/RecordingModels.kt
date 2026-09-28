package me.legend.app.feature.recording

enum class RecordingMode {
    LISTEN,
    CONVERSE,
}

data class RecordingMessage(
    val id: String,
    val role: String,
    val content: String,
    val createdAtEpochMillis: Long,
)

data class RecordingUiState(
    val sessionId: String? = null,
    val mode: RecordingMode = RecordingMode.LISTEN,
    val messages: List<RecordingMessage> = emptyList(),
    val draft: String = "",
    val isEndingSession: Boolean = false,
    val isCompanionReplying: Boolean = false,
    val outstandingAnalysisCount: Int = 0,
    val suggestedPrompt: String = "此刻，有什么值得被记住？",
    val error: String? = null,
)
