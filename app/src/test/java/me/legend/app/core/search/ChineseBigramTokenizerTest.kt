package me.legend.app.core.search

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseBigramTokenizerTest {
    @Test
    fun tokenizesChineseIntoOverlappingBigrams() {
        assertEquals("创业 业失 失败", ChineseBigramTokenizer.tokenize("创业失败"))
    }

    @Test
    fun keepsLatinAndNumericChunks() {
        assertEquals("legendme 2026", ChineseBigramTokenizer.tokenize("LegendMe 2026"))
    }

    @Test
    fun keepsSingleChineseCharacter() {
        assertEquals("我", ChineseBigramTokenizer.tokenize("我"))
    }
}
