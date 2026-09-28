package me.legend.app.core.network

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

@Serializable
data class UnderstandingMessageRequest(
    val id: String,
    val content: String,
    val createdAt: String,
)

@Serializable
data class UnderstandingJobRequest(
    val idempotencyKey: String,
    val sourceSessionId: String,
    val messages: List<UnderstandingMessageRequest>,
    val modelId: String,
    val currentKnowledge: JsonElement? = null,
)

@Serializable
data class CreateJobResponse(
    val jobId: String,
    val status: String,
    val stage: String? = null,
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
)

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String,
    val provider: String,
    val capabilities: List<String>,
    val contextWindow: Int,
    val maxOutputTokens: Int,
)

@Serializable
data class ModelsResponse(val models: List<ModelInfo>)

@Serializable
data class EmbeddingCapabilitiesResponse(
    val enabled: Boolean,
    val modelId: String? = null,
    val maxBatchSize: Int = 32,
    val models: List<EmbeddingModelInfo> = emptyList(),
)

@Serializable
data class EmbeddingModelInfo(
    val id: String,
    val displayName: String,
    val provider: String,
    val maxBatchSize: Int,
)

@Serializable
data class EmbeddingInputRequest(val id: String, val text: String)

@Serializable
data class EmbeddingRequest(
    val inputs: List<EmbeddingInputRequest>,
    val modelId: String? = null,
)

@Serializable
data class EmbeddingVectorResponse(val id: String, val vector: List<Float>)

@Serializable
data class EmbeddingResponse(
    val model: String,
    val dimensions: Int,
    val embeddings: List<EmbeddingVectorResponse>,
)

@Serializable
data class CompanionMessageRequest(
    val role: String,
    val content: String,
    val createdAt: String,
)

@Serializable
data class CompanionRequest(
    val sessionId: String,
    val mode: String = "CONVERSE",
    val messages: List<CompanionMessageRequest>,
    val relevantKnowledge: List<String> = emptyList(),
    val modelId: String,
)

@Serializable
data class CompanionResponse(
    val content: String,
    val modelId: String,
    val promptVersion: String,
)

@Serializable
data class JobSnapshotResponse(
    val id: String,
    val type: String,
    val status: String,
    val stage: String,
    val progress: Int,
    val errorMessage: String? = null,
    val output: JsonElement? = null,
)

@Serializable
data class CreativeCorpusItemRequest(
    val id: String,
    val kind: String,
    val content: String,
    val occurredAt: String? = null,
)

@Serializable
data class TargetLengthRequest(val min: Int, val max: Int)

@Serializable
data class CreationJobRequest(
    val idempotencyKey: String,
    val projectId: String,
    val intent: String,
    val corpusVersion: String,
    val corpus: List<CreativeCorpusItemRequest>,
    val creationMode: String = "COLLABORATIVE",
    val requestedGenre: String? = null,
    val targetLength: TargetLengthRequest? = null,
    val modelId: String,
    val confirmedBrief: JsonElement? = null,
    val previousManuscript: String? = null,
)

@Serializable
data class ConfirmBriefRequest(val approved: Boolean, val feedback: String? = null)

@Serializable
data class JobEventDto(val id: Long, val type: String, val data: JsonElement)

@Serializable
data class DiagnosticsJobsDto(
    val total: Int,
    val byStatus: Map<String, Int>,
    val byType: Map<String, Int>,
    val attempts: Int,
)

@Serializable
data class DiagnosticsUsageDto(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val estimatedCost: Double,
    val durationMs: Long,
    val turns: Int,
    val toolCalls: Int,
)

@Serializable
data class DiagnosticsSummaryResponse(
    val jobs: DiagnosticsJobsDto,
    val usage: DiagnosticsUsageDto,
)

@Serializable
data class RemoteJobSummary(
    val id: String,
    val type: String,
    val status: String,
    val stage: String,
    val progress: Int,
    val projectId: String? = null,
    val intent: String? = null,
    val creationMode: String = "COLLABORATIVE",
    val requestedGenre: String? = null,
    val targetLength: TargetLengthRequest? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class JobsResponse(val jobs: List<RemoteJobSummary>)

@Singleton
class LegendMeApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun health(settings: ServerSettings): HealthResponse =
        get(settings, "/api/health", authenticated = false)

    suspend fun models(settings: ServerSettings): ModelsResponse =
        get(settings, "/api/models", authenticated = true)

    suspend fun embeddingCapabilities(settings: ServerSettings): EmbeddingCapabilitiesResponse =
        get(settings, "/api/embeddings/capabilities", authenticated = true)

    suspend fun embeddings(
        settings: ServerSettings,
        inputs: List<EmbeddingInputRequest>,
        modelId: String,
    ): EmbeddingResponse = post(
        settings,
        "/api/embeddings",
        EmbeddingRequest(inputs = inputs, modelId = modelId),
    )

    suspend fun diagnostics(settings: ServerSettings): DiagnosticsSummaryResponse =
        get(settings, "/api/diagnostics/summary", authenticated = true)

    suspend fun jobs(settings: ServerSettings, type: String): JobsResponse =
        get(settings, "/api/jobs?type=$type", authenticated = true)

    suspend fun createUnderstandingJob(
        settings: ServerSettings,
        input: UnderstandingJobRequest,
    ): CreateJobResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(input).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + "/api/understanding/jobs")
            .header("Authorization", "Bearer ${settings.accessToken}")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Server returned ${response.code}: $payload")
            json.decodeFromString<CreateJobResponse>(payload)
        }
    }

    suspend fun companion(
        settings: ServerSettings,
        input: CompanionRequest,
    ): CompanionResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(input).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + "/api/companion/respond")
            .header("Authorization", "Bearer ${settings.accessToken}")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Server returned ${response.code}: $payload")
            json.decodeFromString<CompanionResponse>(payload)
        }
    }

    suspend fun job(settings: ServerSettings, jobId: String): JobSnapshotResponse =
        get(settings, "/api/jobs/$jobId", authenticated = true)

    suspend fun acknowledgeJob(settings: ServerSettings, jobId: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(settings.baseUrl.trimEnd('/') + "/api/jobs/$jobId/ack")
                .header("Authorization", "Bearer ${settings.accessToken}")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server returned ${response.code} while acknowledging job")
                }
            }
        }
    }

    suspend fun cancelJob(settings: ServerSettings, jobId: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(settings.baseUrl.trimEnd('/') + "/api/jobs/$jobId/cancel")
                .header("Authorization", "Bearer ${settings.accessToken}")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server returned ${response.code} while cancelling job")
                }
            }
        }
    }

    suspend fun purgeContent(settings: ServerSettings, contentId: String) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(settings.baseUrl.trimEnd('/') + "/api/content/$contentId")
                .header("Authorization", "Bearer ${settings.accessToken}")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server returned ${response.code} while deleting content")
                }
            }
        }
    }

    suspend fun createCreationJob(
        settings: ServerSettings,
        input: CreationJobRequest,
    ): CreateJobResponse = post(settings, "/api/creation/jobs", input)

    suspend fun confirmCreativeBrief(
        settings: ServerSettings,
        jobId: String,
        approved: Boolean,
        feedback: String? = null,
    ): CreateJobResponse = post(
        settings,
        "/api/creation/jobs/$jobId/confirm",
        ConfirmBriefRequest(approved, feedback),
    )

    fun observeJobEvents(
        settings: ServerSettings,
        jobId: String,
        lastEventId: Long = 0,
    ): Flow<JobEventDto> = callbackFlow {
        val request = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + "/api/jobs/$jobId/events")
            .header("Authorization", "Bearer ${settings.accessToken}")
            .apply { if (lastEventId > 0) header("Last-Event-ID", lastEventId.toString()) }
            .build()
        val eventSource = EventSources.createFactory(client).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
                    val eventId = id?.toLongOrNull() ?: return
                    trySend(JobEventDto(eventId, type ?: "message", json.parseToJsonElement(data)))
                }

                override fun onFailure(source: EventSource, throwable: Throwable?, response: Response?) {
                    close(throwable ?: IOException("SSE closed with HTTP ${response?.code}"))
                }

                override fun onClosed(source: EventSource) {
                    close()
                }
            },
        )
        awaitClose { eventSource.cancel() }
    }

    private suspend inline fun <reified T> get(
        settings: ServerSettings,
        path: String,
        authenticated: Boolean,
    ): T = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(settings.baseUrl.trimEnd('/') + path).get()
        if (authenticated) builder.header("Authorization", "Bearer ${settings.accessToken}")
        client.newCall(builder.build()).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Server returned ${response.code}: $payload")
            json.decodeFromString<T>(payload)
        }
    }

    private suspend inline fun <reified RequestType, reified ResponseType> post(
        settings: ServerSettings,
        path: String,
        input: RequestType,
    ): ResponseType = withContext(Dispatchers.IO) {
        val body = json.encodeToString(input).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer ${settings.accessToken}")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Server returned ${response.code}: $payload")
            json.decodeFromString<ResponseType>(payload)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
