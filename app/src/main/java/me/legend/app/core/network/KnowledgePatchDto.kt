package me.legend.app.core.network

import kotlinx.serialization.Serializable

@Serializable
data class EvidenceDto(
    val sourceMessageIds: List<String>,
    val confidence: Double,
    val provenance: String,
)

@Serializable
data class PersonPatchDto(
    val temporaryId: String,
    val displayName: String,
    val aliases: List<String> = emptyList(),
    val description: String = "",
    val evidence: EvidenceDto,
)

@Serializable
data class LifeEventPatchDto(
    val temporaryId: String,
    val title: String,
    val description: String,
    val occurredAtText: String? = null,
    val occurredAtStart: String? = null,
    val occurredAtEnd: String? = null,
    val participantTemporaryIds: List<String> = emptyList(),
    val placeName: String? = null,
    val themeNames: List<String> = emptyList(),
    val evidence: EvidenceDto,
)

@Serializable
data class FactVersionPatchDto(
    val subject: String,
    val predicate: String,
    val value: String,
    val validAtText: String? = null,
    val supersedes: String? = null,
    val evidence: EvidenceDto,
)

@Serializable
data class RelationPatchDto(
    val fromPersonTemporaryId: String,
    val toPersonTemporaryId: String,
    val relationType: String,
    val description: String = "",
    val evidence: EvidenceDto,
)

@Serializable
data class PendingConfirmationDto(
    val kind: String,
    val question: String,
    val subjectTemporaryId: String? = null,
    val candidateIds: List<String> = emptyList(),
    val evidence: EvidenceDto,
)

@Serializable
data class KnowledgePatchDto(
    val schemaVersion: String,
    val summary: String,
    val people: List<PersonPatchDto>,
    val events: List<LifeEventPatchDto>,
    val facts: List<FactVersionPatchDto>,
    val relations: List<RelationPatchDto> = emptyList(),
    val themes: List<String>,
    val pendingConfirmations: List<PendingConfirmationDto>,
    val searchText: String,
)
