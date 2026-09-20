package com.example.personallangmaster.domain

import com.example.personallangmaster.ai.text.GeminiTextClient
import com.example.personallangmaster.ai.text.TextResult
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import com.example.personallangmaster.data.prefs.SettingsRepository
import com.example.personallangmaster.data.repo.ContentRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
private data class GeneratedScenario(
    val title_ru: String = "",
    val title_en: String = "",
    val description_ru: String = "",
    val level: String = "A2",
    val category: String = "CUSTOM",
    val duration_mins: Int = 5,
    val role_tutor: String = "",
    val role_user: String = "",
    val goal: String = "",
    val opening_line: String = "",
    val vocab_hints: List<String> = emptyList(),
    val success_criteria: List<String> = emptyList(),
)

sealed interface ScenarioResult {
    data class Success(val scenario: ScenarioEntity) : ScenarioResult
    data class Failure(val reason: String) : ScenarioResult
}

/**
 * Свой сценарий из одной фразы ученика.
 *
 * Пользователь описывает ситуацию по-русски своими словами, а модель достраивает
 * роли, цель и первую реплику — заполнять восемь полей руками никто не станет.
 */
class GenerateScenarioUseCase(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val contentRepository: ContentRepository,
    private val textClient: GeminiTextClient = GeminiTextClient(),
) {

    suspend operator fun invoke(description: String): ScenarioResult {
        if (description.isBlank()) return ScenarioResult.Failure("Опишите ситуацию")

        val settings = settingsRepository.current()
        val apiKey = settingsRepository.apiKey()
            ?: return ScenarioResult.Failure("Ключ Gemini не задан")
        val level = profileRepository.current()?.cefrOverall ?: Cefr.A2

        val prompt = buildString {
            appendLine("The student described a situation they want to practise, in Russian:")
            appendLine("\"$description\"")
            appendLine()
            appendLine("Student level: ${level.name}.")
            appendLine(
                "Build a role play for a voice lesson: who the tutor plays, who the student " +
                    "plays, a concrete goal the student must reach by talking, and the tutor's " +
                    "opening line in English."
            )
        }

        val result = textClient.generateStructured(
            apiKey = apiKey,
            model = settings.cheapModelId,
            prompt = prompt,
            systemInstruction = SYSTEM_INSTRUCTION,
            schema = schema,
            parse = { raw -> json.decodeFromString<GeneratedScenario>(raw) },
        )

        return when (result) {
            is TextResult.Failure -> ScenarioResult.Failure(result.reason)
            is TextResult.Success -> {
                val generated = result.value
                if (generated.title_ru.isBlank() || generated.opening_line.isBlank()) {
                    return ScenarioResult.Failure("Модель вернула пустой сценарий")
                }

                val scenario = ScenarioEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    category = generated.category.ifBlank { "CUSTOM" },
                    level = Cefr.from(generated.level, level),
                    titleRu = generated.title_ru,
                    titleEn = generated.title_en.ifBlank { generated.title_ru },
                    descriptionRu = generated.description_ru.ifBlank { description },
                    durationMins = generated.duration_mins.coerceIn(2, 30),
                    roleTutor = generated.role_tutor,
                    roleUser = generated.role_user,
                    goal = generated.goal,
                    successCriteria = generated.success_criteria,
                    openingLine = generated.opening_line,
                    vocabHints = generated.vocab_hints,
                    isCustom = true,
                )
                contentRepository.saveScenario(scenario)
                ScenarioResult.Success(scenario)
            }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        const val SYSTEM_INSTRUCTION =
            "You design short role plays for spoken English practice. Keep them realistic and " +
                "achievable in a few minutes of conversation. The goal must be something the " +
                "student accomplishes by speaking, not by knowing facts. Russian fields are " +
                "written in Russian, English fields in English."

        val schema: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                text("title_ru", "Short Russian title, two or three words")
                text("title_en", "Short English title")
                text("description_ru", "One sentence in Russian describing the situation")
                text("level", "A1, A2, B1, B2 or C1")
                text("category", "EVERYDAY, TRAVEL, WORK, SOCIAL or CUSTOM")
                putJsonObject("duration_mins") {
                    put("type", "INTEGER")
                    put("description", "Expected length of the conversation in minutes")
                }
                text("role_tutor", "Who the tutor plays and how they behave, in English")
                text("role_user", "Who the student plays, in English")
                text("goal", "What the student must achieve by talking, in English")
                text("opening_line", "The tutor's very first line, in English")
                list("vocab_hints", "Useful words and phrases in English")
                list("success_criteria", "Two or three checks in Russian that the goal was reached")
            }
            putJsonArray("required") {
                add("title_ru"); add("role_tutor"); add("role_user")
                add("goal"); add("opening_line")
            }
        }

        fun kotlinx.serialization.json.JsonObjectBuilder.text(name: String, description: String) =
            putJsonObject(name) {
                put("type", "STRING")
                put("description", description)
            }

        fun kotlinx.serialization.json.JsonObjectBuilder.list(name: String, description: String) =
            putJsonObject(name) {
                put("type", "ARRAY")
                put("description", description)
                putJsonObject("items") { put("type", "STRING") }
            }
    }
}
