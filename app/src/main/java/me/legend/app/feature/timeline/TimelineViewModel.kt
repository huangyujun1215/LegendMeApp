package me.legend.app.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import me.legend.app.core.database.LifeEventEntity
import me.legend.app.core.database.PendingConfirmationEntity
import me.legend.app.core.database.PersonEntity
import me.legend.app.core.database.PersonRelationEntity
import me.legend.app.core.database.FactVersionEntity
import me.legend.app.core.database.EventPersonEntity
import me.legend.app.core.database.EventThemeEntity
import me.legend.app.core.database.PlaceEntity
import me.legend.app.core.database.ThemeEntity
import me.legend.app.feature.knowledge.KnowledgeRepository
import me.legend.app.core.search.SearchHit
import me.legend.app.feature.recording.RecordingMessage
import me.legend.app.feature.recording.RecordingRepository
import me.legend.app.feature.recording.UnderstandingSyncScheduler
import me.legend.app.feature.recording.ContentPurgeScheduler

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TimelineViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    private val recordingRepository: RecordingRepository,
    private val contentPurgeScheduler: ContentPurgeScheduler,
    private val syncScheduler: UnderstandingSyncScheduler,
) : ViewModel() {
    val events: StateFlow<List<LifeEventEntity>> = repository.observeTimeline().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val searchQuery = MutableStateFlow("")
    val searchType = MutableStateFlow("ALL")
    val searchResults: StateFlow<List<SearchHit>> = combine(searchQuery, searchType) { query, type -> query to type }
        .debounce(250)
        .mapLatest { (query, type) ->
            if (query.isBlank()) emptyList()
            else repository.search(query, if (type == "ALL") emptySet() else setOf(type))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingConfirmations: StateFlow<List<PendingConfirmationEntity>> =
        repository.observePendingConfirmations().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    val people: StateFlow<List<PersonEntity>> = repository.observePeople().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val relations: StateFlow<List<PersonRelationEntity>> = repository.observeRelations().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val places: StateFlow<List<PlaceEntity>> = repository.observePlaces().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val themes: StateFlow<List<ThemeEntity>> = repository.observeThemes().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val eventPeople: StateFlow<List<EventPersonEntity>> = repository.observeEventPeople().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val eventThemes: StateFlow<List<EventThemeEntity>> = repository.observeEventThemes().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val currentFacts: StateFlow<List<FactVersionEntity>> = repository.observeCurrentFacts().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val factVersions: StateFlow<List<FactVersionEntity>> = repository.observeFactVersions().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val sourceMessages = MutableStateFlow<List<RecordingMessage>>(emptyList())
    val allUserMessages: StateFlow<List<RecordingMessage>> =
        recordingRepository.observeAllUserMessages().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )
    val deletionError = MutableStateFlow<String?>(null)

    fun resolveConfirmation(id: String, accepted: Boolean) {
        viewModelScope.launch { repository.resolvePendingConfirmation(id, accepted) }
    }

    fun setSearchQuery(value: String) {
        searchQuery.value = value
    }

    fun setSearchType(value: String) {
        searchType.value = value
    }

    fun correctEvent(id: String, title: String, occurredAtText: String?, placeName: String?) {
        viewModelScope.launch { repository.correctEvent(id, title, occurredAtText, placeName) }
    }

    fun correctPerson(id: String, displayName: String, description: String) {
        viewModelScope.launch { repository.correctPerson(id, displayName, description) }
    }

    fun correctRelation(id: String, relationType: String, description: String) {
        viewModelScope.launch { repository.correctRelation(id, relationType, description) }
    }

    fun loadSources(targetType: String, targetId: String) {
        viewModelScope.launch {
            sourceMessages.value = repository.sourceMessages(targetType, targetId)
        }
    }

    fun clearSources() {
        sourceMessages.value = emptyList()
    }

    fun permanentlyDeleteMessage(messageId: String) {
        viewModelScope.launch {
            val localResult = runCatching { recordingRepository.permanentlyDeleteMessage(messageId) }
            if (localResult.isFailure) {
                deletionError.value = "删除失败：${localResult.exceptionOrNull()?.message}"
                return@launch
            }
            sourceMessages.value = sourceMessages.value.filterNot { it.id == messageId }
            // A search result is an in-memory copy of derived text. Clear the query immediately so
            // deleted content cannot remain visible until the user happens to search again.
            searchQuery.value = ""
            contentPurgeScheduler.schedule()
            syncScheduler.schedule()
        }
    }

    fun clearDeletionError() {
        deletionError.value = null
    }
}
