package me.legend.app.feature.timeline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.sin

data class GraphNodePosition(
    val id: String,
    val x: Float,
    val y: Float,
)

object KnowledgeGraphLayout {
    fun arrange(ids: List<String>): List<GraphNodePosition> {
        if (ids.isEmpty()) return emptyList()
        if (ids.size == 1) return listOf(GraphNodePosition(ids.single(), 0f, 0f))

        val ordered = ids.distinct().sorted()
        val goldenAngle = PI * (3 - sqrt(5.0))
        return ordered.mapIndexed { index, id ->
            val progress = if (ordered.size == 1) 0.0 else sqrt(index.toDouble() / (ordered.size - 1))
            val radius = 0.12 + progress * 0.78
            val angle = -PI / 2 + index * goldenAngle
            GraphNodePosition(
                id = id,
                x = (cos(angle) * radius).toFloat(),
                y = (sin(angle) * radius).toFloat(),
            )
        }
    }
}
