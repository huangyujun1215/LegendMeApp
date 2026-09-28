package me.legend.app.core.search

import me.legend.app.core.database.SearchDocumentEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridSearchEngineTest {
    @Test
    fun combinesLexicalResultsWithSemanticOnlyCandidates() {
        val lexicalDocument = document("lexical", "创业失败后的争执")
        val semanticDocument = document("semantic", "那段灰暗日子终于过去")
        val bm25 = listOf(SearchHit("EVENT", "lexical", lexicalDocument.originalText, 4.0))

        val results = HybridSearchEngine().combine(
            documents = listOf(lexicalDocument, semanticDocument),
            bm25Hits = bm25,
            queryVector = floatArrayOf(1f, 0f),
            documentVectors = mapOf(
                lexicalDocument.id to floatArrayOf(0f, 1f),
                semanticDocument.id to floatArrayOf(1f, 0f),
            ),
            limit = 10,
        )

        assertEquals(listOf("lexical", "semantic"), results.map { it.entityId })
        assertTrue(results.single { it.entityId == "semantic" }.score > 0.0)
    }

    @Test
    fun vectorCodecRoundTripsAndCosineRejectsMismatchedDimensions() {
        val vector = floatArrayOf(0.25f, -0.5f, 1f)
        assertArrayEquals(vector, VectorCodec.decode(VectorCodec.encode(vector)), 0f)
        assertEquals(1.0, VectorMath.cosineSimilarity(vector, vector), 0.000_001)
        assertEquals(0.0, VectorMath.cosineSimilarity(vector, floatArrayOf(1f)), 0.0)
    }

    private fun document(id: String, text: String) = SearchDocumentEntity(
        id = "EVENT:$id",
        entityType = "EVENT",
        entityId = id,
        originalText = text,
        tokenizedText = ChineseBigramTokenizer.tokenize(text),
        updatedAtEpochMillis = 0,
    )
}
