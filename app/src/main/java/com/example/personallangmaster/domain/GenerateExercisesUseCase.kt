package com.example.personallangmaster.domain

import com.example.personallangmaster.ai.text.GeminiTextClient
import com.example.personallangmaster.ai.text.TextResult
import com.example.personallangmaster.data.db.ExerciseKind
import com.example.personallangmaster.data.db.entity.ExerciseEntity
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
private data class GeneratedExercises(val exercises: List<GeneratedExercise> = emptyList())

@Serializable
private data class GeneratedExercise(
    val kind: String = "FILL_GAP",
    val prompt: String = "",
    val options: List<String> = emptyList(),
    val answer: String = "",
    val explanation_ru: String = "",
)

sealed interface ExercisesResult {
    data class Success(val created: Int) : ExercisesResult
    data class Failure(val reason: String) : ExercisesResult
}

/**
 * Готовит упражнения по теме на материале собственных ошибок ученика.
 *
 * Генерируем пачкой и заранее: тогда практика открывается мгновенно и работает
 * без сети, а не ждёт модель на каждом задании.
 */
class GenerateExercisesUseCase(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val contentRepository: ContentRepository,
    private val textClient: GeminiTextClient = GeminiTextClient(),
) {

    suspend operator fun invoke(topicId: String, count: Int = DEFAULT_COUNT): ExercisesResult {
        val profile = profileRepository.current()
            ?: return ExercisesResult.Failure("Профиль не найден")
        val topic = contentRepository.topic(topicId)
            ?: return ExercisesResult.Failure("Тема не найдена")

        val settings = settingsRepository.current()
        val apiKey = settingsRepository.apiKey()
            ?: return ExercisesResult.Failure("Ключ Gemini не задан")

        val mistakes = contentRepository.mistakesForTopic(profile.id, topicId, limit = 10)

        val prompt = buildString {
            appendLine("Topic: ${topic.titleEn} (${topic.code}), level ${topic.cefr.name}.")
            appendLine("Student level: ${profile.cefrOverall.name}, native language Russian.")
            if (mistakes.isNotEmpty()) {
                appendLine()
                appendLine("The student actually made these mistakes:")
                mistakes.forEach { mistake ->
                    appendLine("- said \"${mistake.original}\", correct: \"${mistake.corrected}\"")
                }
                appendLine()
                appendLine(
                    "Build the exercises around these very mistakes: reuse the student's own " +
                        "sentences and situations, changed just enough not to be a copy."
                )
            } else {
                appendLine()
                appendLine("The student has no logged mistakes on this topic yet — build typical ones.")
            }
            appendLine()
            appendLine("Create exactly $count exercises.")
        }

        val result = textClient.generateStructured(
            apiKey = apiKey,
            model = settings.textModelId,
            prompt = prompt,
            systemInstruction = SYSTEM_INSTRUCTION,
            schema = schema,
            parse = { raw -> json.decodeFromString<GeneratedExercises>(raw) },
        )

        return when (result) {
            is TextResult.Failure -> ExercisesResult.Failure(result.reason)
            is TextResult.Success -> {
                val now = System.currentTimeMillis()
                val entities = result.value.exercises
                    .filter { it.prompt.isNotBlank() && it.answer.isNotBlank() }
                    .mapNotNull { generated ->
                        val kind = ExerciseKind.entries
                            .firstOrNull { it.name.equals(generated.kind, ignoreCase = true) }
                            ?: ExerciseKind.FILL_GAP

                        // Вариант ответа обязан быть среди опций, иначе задание невыполнимо.
                        val options = if (kind == ExerciseKind.CHOICE) {
                            val list = (generated.options + generated.answer).distinct()
                            if (list.size < 2) return@mapNotNull null else list.shuffled()
                        } else {
                            emptyList()
                        }

                        ExerciseEntity(
                            profileId = profile.id,
                            topicId = topicId,
                            kind = kind,
                            promptText = generated.prompt.trim(),
                            options = options,
                            answer = generated.answer.trim(),
                            explanationRu = generated.explanation_ru.takeIf { it.isNotBlank() },
                            generatedFromMistakeId = mistakes.firstOrNull()?.id,
                            createdAt = now,
                        )
                    }

                if (entities.isEmpty()) {
                    ExercisesResult.Failure("Модель не вернула ни одного упражнения")
                } else {
                    contentRepository.saveExercises(entities)
                    ExercisesResult.Success(entities.size)
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_COUNT = 8

        val json = Json { ignoreUnknownKeys = true }

        const val SYSTEM_INSTRUCTION =
            "You write short grammar exercises for a Russian-speaking learner of English. " +
                "Every exercise must be solvable in one line. Use FILL_GAP with a single ___ " +
                "gap, CHOICE with three or four options, or TRANSLATE from Russian to English. " +
                "The answer field holds exactly what the student must type or pick — no extra " +
                "words. Explanations are one short sentence in Russian."

        val schema: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("exercises") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("kind") {
                                put("type", "STRING")
                                put("description", "FILL_GAP, CHOICE or TRANSLATE")
                            }
                            putJsonObject("prompt") {
                                put("type", "STRING")
                                put(
                                    "description",
                                    "The sentence with ___ for FILL_GAP, the question for " +
                                        "CHOICE, or the Russian sentence for TRANSLATE",
                                )
                            }
                            putJsonObject("options") {
                                put("type", "ARRAY")
                                put("description", "Answer options, only for CHOICE")
                                putJsonObject("items") { put("type", "STRING") }
                            }
                            putJsonObject("answer") {
                                put("type", "STRING")
                                put("description", "Exactly what counts as correct")
                            }
                            putJsonObject("explanation_ru") {
                                put("type", "STRING")
                                put("description", "One short sentence in Russian explaining why")
                            }
                        }
                        putJsonArray("required") { add("kind"); add("prompt"); add("answer") }
                    }
                }
            }
            putJsonArray("required") { add("exercises") }
        }
    }
}
