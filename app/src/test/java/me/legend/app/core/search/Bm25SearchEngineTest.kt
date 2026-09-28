package me.legend.app.core.search

import me.legend.app.core.database.SearchDocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bm25SearchEngineTest {
    private val engine = Bm25SearchEngine()

    @Test
    fun ranksTheDocumentWithMoreMatchingTermsFirst() {
        val documents = listOf(
            document("1", "创业失败以后，我和朋友多年没有联系"),
            document("2", "今天和朋友在望京重逢，谈起创业失败"),
            document("3", "今天早餐喝了咖啡"),
        )

        val results = engine.search("创业失败 重逢", documents)

        assertEquals("2", results.first().entityId)
        assertTrue(results.none { it.entityId == "3" })
    }

    @Test
    fun returnsEmptyForBlankQuery() {
        assertTrue(engine.search("  ", listOf(document("1", "一些文字"))).isEmpty())
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
