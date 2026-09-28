package me.legend.app.core.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import me.legend.app.core.database.SearchDocumentEntity

object VectorCodec {
    fun encode(vector: FloatArray): ByteArray =
        ByteBuffer.allocate(vector.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { vector.forEach(::putFloat) }
            .array()

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) { "Invalid vector byte length" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }
}

object VectorMath {
    fun cosineSimilarity(left: FloatArray, right: FloatArray): Double {
        if (left.isEmpty() || left.size != right.size) return 0.0
        var dot = 0.0
        var leftMagnitude = 0.0
        var rightMagnitude = 0.0
        left.indices.forEach { index ->
            val leftValue = left[index].toDouble()
            val rightValue = right[index].toDouble()
            dot += leftValue * rightValue
            leftMagnitude += leftValue * leftValue
            rightMagnitude += rightValue * rightValue
        }
        if (leftMagnitude == 0.0 || rightMagnitude == 0.0) return 0.0
        return (dot / (sqrt(leftMagnitude) * sqrt(rightMagnitude))).coerceIn(-1.0, 1.0)
    }
}

class HybridSearchEngine(
    private val bm25Weight: Double = 0.65,
    private val vectorWeight: Double = 0.35,
) {
    fun combine(
        documents: List<SearchDocumentEntity>,
        bm25Hits: List<SearchHit>,
        queryVector: FloatArray,
        documentVectors: Map<String, FloatArray>,
        limit: Int,
    ): List<SearchHit> {
        if (limit <= 0) return emptyList()
        val bm25ByEntity = bm25Hits.associateBy { it.entityType to it.entityId }
        val maxBm25 = bm25Hits.maxOfOrNull(SearchHit::score)?.coerceAtLeast(0.0) ?: 0.0
        return documents.mapNotNull { document ->
            val bm25 = bm25ByEntity[document.entityType to document.entityId]?.score ?: 0.0
            val vector = documentVectors[document.id]
            val semantic = vector?.let { VectorMath.cosineSimilarity(queryVector, it).coerceAtLeast(0.0) } ?: 0.0
            val lexical = if (maxBm25 > 0.0) bm25 / maxBm25 else 0.0
            val score = bm25Weight * lexical + vectorWeight * semantic
            if (score <= 0.0) null else SearchHit(
                entityType = document.entityType,
                entityId = document.entityId,
                text = document.originalText,
                score = score,
            )
        }.sortedByDescending(SearchHit::score)
            .distinctBy { it.entityType to it.entityId }
            .take(limit)
    }
}
