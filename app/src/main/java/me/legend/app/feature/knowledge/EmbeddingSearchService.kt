package me.legend.app.feature.knowledge

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import me.legend.app.core.database.KnowledgeDao
import me.legend.app.core.database.SearchDocumentEntity
import me.legend.app.core.database.SearchEmbeddingEntity
import me.legend.app.core.network.EmbeddingCapabilitiesResponse
import me.legend.app.core.network.EmbeddingInputRequest
import me.legend.app.core.network.LegendMeApi
import me.legend.app.core.network.ServerSettings
import me.legend.app.core.network.ServerSettingsRepository
import me.legend.app.core.search.HybridSearchEngine
import me.legend.app.core.search.SearchHit
import me.legend.app.core.search.VectorCodec

@Singleton
class EmbeddingSearchService @Inject constructor(
    private val dao: KnowledgeDao,
    private val api: LegendMeApi,
    private val settingsRepository: ServerSettingsRepository,
) {
    private val hybridSearchEngine = HybridSearchEngine()
    private var cachedCapabilities: CachedCapabilities? = null

    suspend fun search(
        query: String,
        documents: List<SearchDocumentEntity>,
        bm25Hits: List<SearchHit>,
        limit: Int,
    ): List<SearchHit>? {
        if (documents.isEmpty()) return bm25Hits
        val settings = settingsRepository.current()
        if (settings.accessToken.isBlank()) return null
        val capabilities = capabilities(settings) ?: return null
        if (!capabilities.enabled || capabilities.modelId == null) return null
        val modelId = capabilities.modelId
        val candidates = documents.take(MAX_VECTOR_DOCUMENTS)
        val queryResponse = api.embeddings(
            settings,
            listOf(EmbeddingInputRequest(QUERY_ID, query)),
            modelId,
        )
        val queryVector = queryResponse.embeddings.singleOrNull { it.id == QUERY_ID }
            ?.vector
            ?.toFloatArray()
            ?: return null
        if (queryVector.isEmpty()) return null

        dao.deleteEmbeddingsFromOtherModels(modelId)
        val cached = dao.searchEmbeddings(modelId, candidates.map { it.id }).associateBy { it.documentId }
        val stale = candidates.filter { document ->
            val embedding = cached[document.id]
            embedding == null ||
                embedding.contentHash != contentHash(document.originalText) ||
                embedding.dimensions != queryVector.size
        }
        stale.chunked(capabilities.maxBatchSize.coerceIn(1, 64)).forEach { batch ->
            val response = api.embeddings(
                settings,
                batch.map { EmbeddingInputRequest(it.id, it.originalText) },
                modelId,
            )
            require(response.dimensions == queryVector.size) { "Embedding dimensions changed" }
            val documentsById = batch.associateBy { it.id }
            val entities = response.embeddings.map { item ->
                val document = requireNotNull(documentsById[item.id]) { "Unknown embedding document" }
                val vector = item.vector.toFloatArray()
                require(vector.size == response.dimensions) { "Invalid embedding dimensions" }
                SearchEmbeddingEntity(
                    documentId = item.id,
                    modelId = modelId,
                    dimensions = vector.size,
                    vectorBlob = VectorCodec.encode(vector),
                    contentHash = contentHash(document.originalText),
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            }
            require(entities.size == batch.size) { "Embedding response is incomplete" }
            dao.upsertSearchEmbeddings(entities)
        }

        val vectors = dao.searchEmbeddings(modelId, candidates.map { it.id }).associate { entity ->
            entity.documentId to VectorCodec.decode(entity.vectorBlob)
        }
        return hybridSearchEngine.combine(candidates, bm25Hits, queryVector, vectors, limit)
    }

    private suspend fun capabilities(settings: ServerSettings): EmbeddingCapabilitiesResponse? {
        val now = System.currentTimeMillis()
        val key = "${settings.baseUrl}:${contentHash(settings.accessToken)}"
        cachedCapabilities?.takeIf { it.key == key && now - it.loadedAt < CAPABILITY_TTL_MILLIS }
            ?.let { return it.value }
        return runCatching { api.embeddingCapabilities(settings) }.getOrNull()?.also {
            cachedCapabilities = CachedCapabilities(key, now, it)
        }
    }

    private fun contentHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class CachedCapabilities(
        val key: String,
        val loadedAt: Long,
        val value: EmbeddingCapabilitiesResponse,
    )

    companion object {
        private const val QUERY_ID = "__legendme_query__"
        private const val MAX_VECTOR_DOCUMENTS = 1_000
        private const val CAPABILITY_TTL_MILLIS = 60_000L
    }
}
