package me.legend.app.feature.timeline

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.legend.app.core.database.EventPersonEntity
import me.legend.app.core.database.EventThemeEntity
import me.legend.app.core.database.FactVersionEntity
import me.legend.app.core.database.LifeEventEntity
import me.legend.app.core.database.PersonEntity
import me.legend.app.core.database.PersonRelationEntity
import me.legend.app.core.database.PlaceEntity
import me.legend.app.core.database.ThemeEntity

private enum class GraphNodeKind(val label: String) {
    PERSON("人物"),
    EVENT("事件"),
    PLACE("地点"),
    THEME("主题"),
    FACT("事实"),
}

private data class KnowledgeGraphNode(
    val id: String,
    val label: String,
    val description: String,
    val kind: GraphNodeKind,
)

private data class KnowledgeGraphEdge(val fromId: String, val toId: String, val label: String)

@Composable
fun KnowledgeGraph(
    people: List<PersonEntity>,
    events: List<LifeEventEntity>,
    places: List<PlaceEntity>,
    themes: List<ThemeEntity>,
    facts: List<FactVersionEntity>,
    relations: List<PersonRelationEntity>,
    eventPeople: List<EventPersonEntity>,
    eventThemes: List<EventThemeEntity>,
    modifier: Modifier = Modifier,
) {
    val graph = remember(people, events, places, themes, facts, relations, eventPeople, eventThemes) {
        buildGraph(people, events, places, themes, facts, relations, eventPeople, eventThemes)
    }
    val positions = remember(graph.first.map { it.id }) {
        KnowledgeGraphLayout.arrange(graph.first.map { it.id }).associateBy { it.id }
    }
    var scaleFactor by remember { mutableFloatStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    val selectedNode = graph.first.firstOrNull { it.id == selectedNodeId }
    val colors = mapOf(
        GraphNodeKind.PERSON to MaterialTheme.colorScheme.primaryContainer,
        GraphNodeKind.EVENT to MaterialTheme.colorScheme.secondaryContainer,
        GraphNodeKind.PLACE to MaterialTheme.colorScheme.tertiaryContainer,
        GraphNodeKind.THEME to MaterialTheme.colorScheme.surface,
        GraphNodeKind.FACT to MaterialTheme.colorScheme.errorContainer,
    )
    val selectedColor = MaterialTheme.colorScheme.primary
    val edgeColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurface

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("人生知识图谱", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "双指缩放、拖动浏览，点按节点查看信息。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = {
                    scaleFactor = 1f
                    translation = Offset.Zero
                }) { Text("重置视图") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GraphNodeKind.entries.forEach { kind ->
                    Text(kind.label, color = colors.getValue(kind), style = MaterialTheme.typography.labelSmall)
                }
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics {
                        contentDescription = "人生知识图谱，共 ${graph.first.size} 个节点、${graph.second.size} 条连接"
                    }
                    .pointerInput(positions) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scaleFactor = (scaleFactor * zoom).coerceIn(0.7f, 3f)
                            translation += pan
                        }
                    }
                    .pointerInput(positions, scaleFactor, translation) {
                        detectTapGestures { tap ->
                            val graphPoint = (tap - translation) / scaleFactor
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = minOf(size.width, size.height) * 0.46f
                            selectedNodeId = positions.values.minByOrNull { node ->
                                val position = center + Offset(node.x * radius, node.y * radius)
                                (position - graphPoint).getDistance()
                            }?.takeIf { node ->
                                val position = center + Offset(node.x * radius, node.y * radius)
                                (position - graphPoint).getDistance() <= 34.dp.toPx()
                            }?.id
                        }
                    },
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val graphRadius = minOf(size.width, size.height) * 0.46f
                val nodeRadius = 27.dp.toPx()
                val textPaint = Paint().apply {
                    color = labelColor.toArgbInt()
                    textAlign = Paint.Align.CENTER
                    textSize = 11.dp.toPx()
                    isAntiAlias = true
                }
                translate(translation.x, translation.y) {
                    scale(scaleFactor, pivot = Offset.Zero) {
                        graph.second.forEach { edge ->
                            val from = positions[edge.fromId] ?: return@forEach
                            val to = positions[edge.toId] ?: return@forEach
                            val start = center + Offset(from.x * graphRadius, from.y * graphRadius)
                            val end = center + Offset(to.x * graphRadius, to.y * graphRadius)
                            drawLine(edgeColor, start, end, strokeWidth = 1.5.dp.toPx())
                        }
                        graph.first.forEach { node ->
                            val layout = positions[node.id] ?: return@forEach
                            val position = center + Offset(layout.x * graphRadius, layout.y * graphRadius)
                            drawCircle(
                                color = if (node.id == selectedNodeId) selectedColor else colors.getValue(node.kind),
                                radius = nodeRadius,
                                center = position,
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                node.label.take(6),
                                position.x,
                                position.y + textPaint.textSize / 3f,
                                textPaint,
                            )
                        }
                    }
                }
            }
            selectedNode?.let { node ->
                Text("${node.kind.label} · ${node.label}", style = MaterialTheme.typography.titleMedium)
                if (node.description.isNotBlank()) Text(node.description)
                graph.second.filter { it.fromId == node.id || it.toId == node.id }.forEach { edge ->
                    val otherId = if (edge.fromId == node.id) edge.toId else edge.fromId
                    val other = graph.first.firstOrNull { it.id == otherId }?.label ?: "未知节点"
                    Text("${edge.label} · $other", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun buildGraph(
    people: List<PersonEntity>,
    events: List<LifeEventEntity>,
    places: List<PlaceEntity>,
    themes: List<ThemeEntity>,
    facts: List<FactVersionEntity>,
    relations: List<PersonRelationEntity>,
    eventPeople: List<EventPersonEntity>,
    eventThemes: List<EventThemeEntity>,
): Pair<List<KnowledgeGraphNode>, List<KnowledgeGraphEdge>> {
    val nodes = buildList {
        people.forEach { add(KnowledgeGraphNode("PERSON:${it.id}", it.displayName, it.description, GraphNodeKind.PERSON)) }
        events.forEach { add(KnowledgeGraphNode("EVENT:${it.id}", it.title, it.description, GraphNodeKind.EVENT)) }
        places.forEach { add(KnowledgeGraphNode("PLACE:${it.id}", it.name, "地点", GraphNodeKind.PLACE)) }
        themes.forEach { add(KnowledgeGraphNode("THEME:${it.id}", it.name, "人生主题", GraphNodeKind.THEME)) }
        facts.forEach {
            add(KnowledgeGraphNode("FACT:${it.id}", it.predicate, "${it.subject}：${it.value}", GraphNodeKind.FACT))
        }
    }
    val placeByName = places.associateBy { it.name }
    val personByName = people.associateBy { it.displayName }
    val edges = buildList {
        relations.forEach {
            add(KnowledgeGraphEdge("PERSON:${it.fromPersonId}", "PERSON:${it.toPersonId}", it.relationType))
        }
        eventPeople.forEach { add(KnowledgeGraphEdge("EVENT:${it.eventId}", "PERSON:${it.personId}", "参与")) }
        eventThemes.forEach { add(KnowledgeGraphEdge("EVENT:${it.eventId}", "THEME:${it.themeId}", "主题")) }
        events.forEach { event ->
            event.placeName?.let(placeByName::get)?.let { place ->
                add(KnowledgeGraphEdge("EVENT:${event.id}", "PLACE:${place.id}", "发生于"))
            }
        }
        facts.forEach { fact ->
            personByName[fact.subject]?.let { person ->
                add(KnowledgeGraphEdge("PERSON:${person.id}", "FACT:${fact.id}", fact.predicate))
            }
        }
    }
    val nodeIds = nodes.mapTo(mutableSetOf()) { it.id }
    return nodes to edges.filter { it.fromId in nodeIds && it.toId in nodeIds }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
