package me.legend.app.feature.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.legend.app.core.database.LifeEventEntity
import me.legend.app.core.database.PersonEntity
import me.legend.app.core.database.PersonRelationEntity
import me.legend.app.feature.recording.RecordingMessage

@Composable
fun TimelineScreen(viewModel: TimelineViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val pending by viewModel.pendingConfirmations.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchType by viewModel.searchType.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val relations by viewModel.relations.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val themes by viewModel.themes.collectAsStateWithLifecycle()
    val eventPeople by viewModel.eventPeople.collectAsStateWithLifecycle()
    val eventThemes by viewModel.eventThemes.collectAsStateWithLifecycle()
    val currentFacts by viewModel.currentFacts.collectAsStateWithLifecycle()
    val factVersions by viewModel.factVersions.collectAsStateWithLifecycle()
    val sourceMessages by viewModel.sourceMessages.collectAsStateWithLifecycle()
    val allUserMessages by viewModel.allUserMessages.collectAsStateWithLifecycle()
    val deletionError by viewModel.deletionError.collectAsStateWithLifecycle()
    val personNames = people.associate { it.id to it.displayName }
    var editingEvent by remember { mutableStateOf<LifeEventEntity?>(null) }
    var editingPerson by remember { mutableStateOf<PersonEntity?>(null) }
    var editingRelation by remember { mutableStateOf<PersonRelationEntity?>(null) }
    var showRawRecords by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RecordingMessage?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("人生时间线", style = MaterialTheme.typography.headlineMedium) }
        item {
            TextButton(onClick = { showRawRecords = true }) {
                Text("管理原始记录（${allUserMessages.size}）")
            }
        }
        deletionError?.let { message ->
            item {
                TextButton(onClick = viewModel::clearDeletionError) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索人生片段") },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to "全部", "EVENT" to "事件", "PERSON" to "人物", "FACT" to "事实").forEach { (type, label) ->
                    FilterChip(
                        selected = searchType == type,
                        onClick = { viewModel.setSearchType(type) },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (searchQuery.isNotBlank()) {
            items(searchResults, key = { "search:${it.entityType}:${it.entityId}" }) { hit ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(hit.text, modifier = Modifier.padding(12.dp))
                }
            }
        } else {
            if (pending.isNotEmpty()) {
                item {
                    Text("${pending.size} 项需要确认", style = MaterialTheme.typography.titleMedium)
                }
                items(pending.take(3), key = { "confirmation:${it.id}" }) { confirmation ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(confirmation.question)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.resolveConfirmation(confirmation.id, true) }) {
                                    Text("确认")
                                }
                                TextButton(onClick = { viewModel.resolveConfirmation(confirmation.id, false) }) {
                                    Text("不是")
                                }
                            }
                        }
                    }
                }
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        "完成后台理解后，事件会按时间出现在这里。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(events, key = { "event:${it.id}" }) { event ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(event.occurredAtText ?: "时间待确认", color = MaterialTheme.colorScheme.primary)
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            Text(event.description, modifier = Modifier.padding(top = 6.dp))
                            event.placeName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { editingEvent = event }) { Text("纠正") }
                                TextButton(onClick = { viewModel.loadSources("EVENT", event.id) }) {
                                    Text("查看原始依据")
                                }
                            }
                        }
                    }
                }
            }
            if (people.isNotEmpty()) {
                item { Text("人物", style = MaterialTheme.typography.titleLarge) }
                items(people, key = { "person:${it.id}" }) { person ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(person.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(person.description)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.loadSources("PERSON", person.id) }) {
                                    Text("查看原始依据")
                                }
                                TextButton(onClick = { editingPerson = person }) { Text("纠正") }
                            }
                        }
                    }
                }
            }
            if (people.isNotEmpty() || events.isNotEmpty() || themes.isNotEmpty() || currentFacts.isNotEmpty()) {
                item {
                    KnowledgeGraph(
                        people = people,
                        events = events,
                        places = places,
                        themes = themes,
                        facts = currentFacts,
                        relations = relations,
                        eventPeople = eventPeople,
                        eventThemes = eventThemes,
                    )
                }
            }
            if (relations.isNotEmpty()) {
                items(relations, key = { "relation:${it.id}" }) { relation ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(personNames[relation.fromPersonId] ?: "未知人物")
                                Text("— ${relation.relationType} →", color = MaterialTheme.colorScheme.primary)
                                Text(personNames[relation.toPersonId] ?: "未知人物")
                            }
                            Text(relation.description)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.loadSources("RELATION", relation.id) }) {
                                    Text("查看原始依据")
                                }
                                TextButton(onClick = { editingRelation = relation }) { Text("纠正") }
                            }
                        }
                    }
                }
            }
            if (factVersions.isNotEmpty()) {
                item { Text("事实版本", style = MaterialTheme.typography.titleLarge) }
                items(factVersions, key = { "fact:${it.id}" }) { fact ->
                    Column(Modifier.padding(12.dp)) {
                        Text("${if (fact.isCurrent) "当前" else "历史"} · ${fact.subject} ${fact.predicate}：${fact.value}")
                        TextButton(onClick = { viewModel.loadSources("FACT", fact.id) }) {
                            Text("查看原始依据")
                        }
                    }
                }
            }
        }
    }
    editingEvent?.let { event ->
        EventCorrectionDialog(
            event = event,
            onDismiss = { editingEvent = null },
            onConfirm = { title, time, place ->
                viewModel.correctEvent(event.id, title, time, place)
                editingEvent = null
            },
        )
    }
    editingPerson?.let { person ->
        PersonCorrectionDialog(
            person = person,
            onDismiss = { editingPerson = null },
            onConfirm = { name, description ->
                viewModel.correctPerson(person.id, name, description)
                editingPerson = null
            },
        )
    }
    editingRelation?.let { relation ->
        RelationCorrectionDialog(
            relation = relation,
            onDismiss = { editingRelation = null },
            onConfirm = { type, description ->
                viewModel.correctRelation(relation.id, type, description)
                editingRelation = null
            },
        )
    }
    if (sourceMessages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::clearSources,
            title = { Text("原始记录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sourceMessages.forEach { Text(it.content) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::clearSources) { Text("关闭") } },
        )
    }
    if (showRawRecords) {
        AlertDialog(
            onDismissRequest = { showRawRecords = false },
            title = { Text("原始记录") },
            text = {
                if (allUserMessages.isEmpty()) {
                    Text("还没有可管理的原始记录。")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(allUserMessages, key = { "raw:${it.id}" }) { message ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(message.content)
                                    TextButton(onClick = { pendingDelete = message }) {
                                        Text("删除")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRawRecords = false }) { Text("关闭") }
            },
        )
    }
    pendingDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("永久删除这条记录？") },
            text = { Text("原文及只由它产生的知识将被清除，且不会进入后续备份，无法恢复。") },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = null
                    viewModel.permanentlyDeleteMessage(message.id)
                }) { Text("永久删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RelationCorrectionDialog(
    relation: PersonRelationEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var type by remember(relation.id) { mutableStateOf(relation.relationType) }
    var description by remember(relation.id) { mutableStateOf(relation.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("纠正人物关系") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(type, { type = it }, label = { Text("关系") })
                OutlinedTextField(description, { description = it }, label = { Text("说明") })
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(type, description) }, enabled = type.isNotBlank()) {
                Text("保存纠正")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun EventCorrectionDialog(
    event: LifeEventEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?) -> Unit,
) {
    var title by remember(event.id) { mutableStateOf(event.title) }
    var time by remember(event.id) { mutableStateOf(event.occurredAtText.orEmpty()) }
    var place by remember(event.id) { mutableStateOf(event.placeName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("纠正人生事件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("标题") })
                OutlinedTextField(time, { time = it }, label = { Text("时间") })
                OutlinedTextField(place, { place = it }, label = { Text("地点") })
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(title, time.ifBlank { null }, place.ifBlank { null }) }) {
                Text("保存纠正")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PersonCorrectionDialog(
    person: PersonEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember(person.id) { mutableStateOf(person.displayName) }
    var description by remember(person.id) { mutableStateOf(person.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("纠正人物信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("姓名") })
                OutlinedTextField(description, { description = it }, label = { Text("关系与描述") })
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, description) }, enabled = name.isNotBlank()) {
                Text("保存纠正")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
