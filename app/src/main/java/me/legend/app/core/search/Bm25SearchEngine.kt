package me.legend.app.core.search

import kotlin.math.ln
import me.legend.app.core.database.SearchDocumentEntity

data class SearchHit(
    val entityType: String,
    val entityId: String,
    val text: String,
    val score: Double,
)

class Bm25SearchEngine(
    private val k1: Double = 1.2,
    private val b: Double = 0.75,
) {
    fun search(
        query: String,
        documents: List<SearchDocumentEntity>,
        limit: Int = 20,
    ): List<SearchHit> {
        if (documents.isEmpty() || limit <= 0) return emptyList()
        val queryTerms = ChineseBigramTokenizer.tokenize(query)
            .split(' ')
            .filter(String::isNotBlank)
            .distinct()
        if (queryTerms.isEmpty()) return emptyList()

        val tokenized = documents.associateWith { document ->
            document.tokenizedText.split(' ').filter(String::isNotBlank)
        }
        val averageLength = tokenized.values.map { it.size }.average().coerceAtLeast(1.0)
        val documentCount = documents.size.toDouble()
        val documentFrequency = queryTerms.associateWith { term ->
            tokenized.values.count { terms -> term in terms }
        }

        return documents.mapNotNull { document ->
            val terms = tokenized.getValue(document)
            val frequencies = terms.groupingBy { it }.eachCount()
            val score = queryTerms.sumOf { term ->
                val frequency = frequencies[term]?.toDouble() ?: 0.0
                if (frequency == 0.0) return@sumOf 0.0
                val matchingDocuments = documentFrequency.getValue(term).toDouble()
                val inverseDocumentFrequency = ln(
                    1.0 + (documentCount - matchingDocuments + 0.5) / (matchingDocuments + 0.5),
                )
                inverseDocumentFrequency *
                    (frequency * (k1 + 1.0)) /
                    (frequency + k1 * (1.0 - b + b * terms.size / averageLength))
            }
            if (score <= 0.0) null else SearchHit(
                entityType = document.entityType,
                entityId = document.entityId,
                text = document.originalText,
                score = score,
            )
        }.sortedByDescending(SearchHit::score).take(limit)
    }
}
