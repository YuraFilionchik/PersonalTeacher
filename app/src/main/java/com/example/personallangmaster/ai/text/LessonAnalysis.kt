package com.example.personallangmaster.ai.text

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Разбор урока: то, ради чего сохраняется транскрипт.
 *
 * Структура намеренно совпадает с таблицами базы — результат раскладывается
 * по словарю, грамматике и фонетике без промежуточных преобразований.
 */
@Serializable
data class LessonAnalysis(
    val cefr_estimate: String? = null,
    val fluency: Int = 0,
    val accuracy: Int = 0,
    val vocabulary_range: Int = 0,
    val summary_ru: String = "",
    val summary_en: String = "",
    val praise_ru: String = "",
    val mistakes: List<AnalysisMistake> = emptyList(),
    val vocab: List<AnalysisVocab> = emptyList(),
    val pronunciation_notes: List<AnalysisPhoneme> = emptyList(),
    val next_lesson_focus: List<String> = emptyList(),
)

@Serializable
data class AnalysisMistake(
    val type: String = "GRAMMAR",
    val original: String = "",
    val corrected: String = "",
    val explanation_ru: String = "",
    val grammar_topic: String? = null,
    val severity: Int = 2,
)

@Serializable
data class AnalysisVocab(
    val term: String = "",
    val translation_ru: String = "",
    val example: String? = null,
    val cefr: String? = null,
)

@Serializable
data class AnalysisPhoneme(
    val phoneme: String = "",
    val words: List<String> = emptyList(),
    val comment_ru: String = "",
)

object LessonAnalysisSchema {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): LessonAnalysis = json.decodeFromString(raw)

    /** Схема структурированного вывода: модель обязана вернуть ровно эти поля. */
    val schema: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            string("cefr_estimate", "CEFR level of this conversation: A1, A2, B1, B2 or C1")
            integer("fluency", "How fluent the speech was, 0-100")
            integer("accuracy", "How grammatically accurate the speech was, 0-100")
            integer("vocabulary_range", "How rich the vocabulary was, 0-100")
            string("summary_ru", "Two or three sentences in Russian about what happened in the lesson")
            string("summary_en", "One or two sentences in English about the topics covered")
            string("praise_ru", "One specific thing in Russian the student did better than before")

            putJsonObject("mistakes") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        string(
                            "type",
                            "GRAMMAR, VOCAB, PRONUNCIATION, WORD_ORDER, ARTICLE, TENSE, " +
                                "PREPOSITION or STYLE",
                        )
                        string("original", "Exactly what the student said")
                        string("corrected", "The correct version")
                        string("explanation_ru", "One or two sentences in Russian explaining why")
                        string("grammar_topic", "Short snake_case code of the grammar topic")
                        integer("severity", "1 minor, 2 noticeable, 3 breaks understanding")
                    }
                    putJsonArray("required") {
                        add("type"); add("original"); add("corrected"); add("explanation_ru")
                    }
                }
            }

            putJsonObject("vocab") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        string("term", "English word or phrase worth learning")
                        string("translation_ru", "Russian translation")
                        string("example", "Short example sentence, ideally from the lesson itself")
                        string("cefr", "A1, A2, B1, B2 or C1")
                    }
                    putJsonArray("required") { add("term"); add("translation_ru") }
                }
            }

            putJsonObject("pronunciation_notes") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        string("phoneme", "IPA symbol, for example /θ/")
                        putJsonObject("words") {
                            put("type", "ARRAY")
                            putJsonObject("items") { put("type", "STRING") }
                        }
                        string("comment_ru", "Short articulation hint in Russian")
                    }
                    putJsonArray("required") { add("phoneme"); add("comment_ru") }
                }
            }

            putJsonObject("next_lesson_focus") {
                put("type", "ARRAY")
                putJsonObject("items") { put("type", "STRING") }
            }
        }
        putJsonArray("required") {
            add("cefr_estimate"); add("summary_ru"); add("mistakes"); add("vocab")
        }
    }

    /** Системная инструкция для разбора: она же задаёт тон обратной связи. */
    fun systemInstruction(explanationInRussian: Boolean): String = buildString {
        appendLine(
            "You are an experienced English teacher reviewing a lesson transcript of a " +
                "Russian-speaking student."
        )
        appendLine("Analyse only what the student actually said. Never invent mistakes.")
        appendLine("Judge pronunciation only when the transcript clearly shows a confusion.")
        appendLine(
            "Pick at most 8 mistakes: the ones that matter most for being understood, " +
                "and the ones that repeat."
        )
        appendLine("Pick 5 to 10 words or phrases that are worth adding to active vocabulary.")
        appendLine(
            if (explanationInRussian) {
                "Write every explanation in clear, friendly Russian, as a teacher speaking to a student."
            } else {
                "Write explanations in simple English."
            }
        )
        appendLine("Be honest but never discouraging: always find one real thing that improved.")
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.string(
        name: String,
        description: String,
    ) = putJsonObject(name) {
        put("type", "STRING")
        put("description", description)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.integer(
        name: String,
        description: String,
    ) = putJsonObject(name) {
        put("type", "INTEGER")
        put("description", description)
    }
}
