package me.legend.app.core.search

object ChineseBigramTokenizer {
    fun tokenize(input: String): String {
        val chunks = input.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
        return chunks.flatMap { chunk ->
            if (chunk.any(::isHanCharacter)) {
                val characters = chunk.toList()
                if (characters.size == 1) characters.map(Char::toString)
                else characters.windowed(2).map { it.joinToString("") }
            } else {
                listOf(chunk)
            }
        }.joinToString(" ")
    }

    private fun isHanCharacter(value: Char): Boolean =
        Character.UnicodeScript.of(value.code) == Character.UnicodeScript.HAN
}
