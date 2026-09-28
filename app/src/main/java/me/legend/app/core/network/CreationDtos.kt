package me.legend.app.core.network

import kotlinx.serialization.Serializable

@Serializable
data class CreativeBriefDto(
    val schemaVersion: String,
    val titleOptions: List<String>,
    val premise: String,
    val genre: String,
    val targetLength: TargetLengthRequest = TargetLengthRequest(3000, 5000),
    val perspective: String,
    val tone: List<String>,
    val inferredStyle: List<String>,
    val styleSamples: List<String> = emptyList(),
    val structure: List<CreativeSectionDto>,
    val questions: List<String>,
    val readyToWrite: Boolean,
)

@Serializable
data class CreativeSectionDto(
    val order: Int,
    val title: String,
    val purpose: String,
)

@Serializable
data class CreativeResultDto(
    val schemaVersion: String,
    val title: String,
    val synopsis: String,
    val manuscript: String,
    val wordCount: Int,
    val inferredStyle: List<String>,
    val creativeBrief: CreativeBriefDto? = null,
)
