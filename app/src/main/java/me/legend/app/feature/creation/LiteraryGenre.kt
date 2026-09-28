package me.legend.app.feature.creation

data class LiteraryGenre(
    val wireValue: String?,
    val label: String,
    val targetMinLength: Int?,
    val targetMaxLength: Int?,
) {
    companion object {
        val Auto = LiteraryGenre(null, "自动判断", null, null)
        val Options = listOf(
            Auto,
            LiteraryGenre("SHORT_STORY", "短篇小说", 3_000, 5_000),
            LiteraryGenre("ESSAY", "散文", 1_500, 3_000),
            LiteraryGenre("POETRY", "诗歌", 100, 800),
            LiteraryGenre("MEMOIR", "人物传记", 3_000, 5_000),
            LiteraryGenre("SCRIPT", "剧本", 3_000, 5_000),
        )
    }
}

enum class CreationMode(val label: String, val description: String) {
    COLLABORATIVE("协作创作", "确认创作方案后再开始正文。"),
    AUTONOMOUS("托管创作", "Agent 自行确定方案并完成作品。"),
}
