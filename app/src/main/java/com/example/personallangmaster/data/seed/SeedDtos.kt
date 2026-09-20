package com.example.personallangmaster.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO учебного контента из `app/src/main/assets`. Имена полей повторяют файлы
 * как есть: файлы ведёт отдельная задача по контенту, а код под них подстраивается.
 */

@Serializable
data class ScenarioDto(
    val id: String,
    val category: String,
    val level: String,
    @SerialName("title_ru") val titleRu: String,
    @SerialName("title_en") val titleEn: String,
    @SerialName("description_ru") val descriptionRu: String,
    @SerialName("duration_mins") val durationMins: Int = 5,
    @SerialName("role_tutor") val roleTutor: String,
    @SerialName("role_user") val roleUser: String,
    val goal: String,
    @SerialName("success_criteria") val successCriteria: List<String> = emptyList(),
    @SerialName("opening_line") val openingLine: String,
    @SerialName("vocab_hints") val vocabHints: List<String> = emptyList(),
)

@Serializable
data class GrammarTopicDto(
    val id: String,
    val code: String,
    @SerialName("title_ru") val titleRu: String,
    @SerialName("title_en") val titleEn: String,
    val cefr: String,
    @SerialName("explanation_ru") val explanationRu: String,
    @SerialName("explanation_en") val explanationEn: String,
    val isSeed: Boolean = true,
)

@Serializable
data class MinimalPairDto(
    val id: String,
    val word1: String,
    val word2: String,
    val phoneme1: String,
    val phoneme2: String,
    val translation1: String = "",
    val translation2: String = "",
)

@Serializable
data class PhonemeDto(
    val id: String,
    val ipa: String,
    val type: String,
    @SerialName("hint_ru") val hintRu: String,
)
