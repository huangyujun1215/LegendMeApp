package me.legend.app.feature.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeGraphLayoutTest {
    @Test
    fun producesStableUniquePositionsAcrossMultipleRings() {
        val ids = (1..25).map { "person-$it" }
        val first = KnowledgeGraphLayout.arrange(ids)
        val second = KnowledgeGraphLayout.arrange(ids.reversed())

        assertEquals(25, first.size)
        assertEquals(first, second)
        assertEquals(25, first.map { it.x to it.y }.toSet().size)
        assertTrue(first.all { it.x in -1f..1f && it.y in -1f..1f })
    }

    @Test
    fun centersASinglePersonAndHandlesEmptyInput() {
        assertTrue(KnowledgeGraphLayout.arrange(emptyList()).isEmpty())
        assertEquals(GraphNodePosition("only", 0f, 0f), KnowledgeGraphLayout.arrange(listOf("only")).single())
    }
}
