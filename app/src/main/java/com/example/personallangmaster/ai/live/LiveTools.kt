package com.example.personallangmaster.ai.live

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Функции, которые тренер вызывает прямо во время урока.
 *
 * Ответ на вызов должен быть мгновенным — иначе рвётся темп речи, — поэтому
 * обработчики только пишут в базу и сразу возвращают `{"ok": true}`.
 */
object LiveTools {

    const val SAVE_VOCAB = "save_vocab"
    const val LOG_MISTAKE = "log_mistake"
    const val SET_DIFFICULTY = "set_difficulty"
    const val SHOW_CARD = "show_card"
    const val SUGGEST_DRILL = "suggest_drill"

    /** Объявления инструментов для `setup.tools`. */
    val declarations: List<Tool> = listOf(
        Tool(
            functionDeclarations = listOf(
                FunctionDeclaration(
                    name = SAVE_VOCAB,
                    description = "Save a word or phrase the student should learn. " +
                        "Call it silently whenever something is worth remembering.",
                    parameters = objectSchema(
                        required = listOf("term", "translation_ru"),
                    ) {
                        stringProperty("term", "The English word or phrase")
                        stringProperty("translation_ru", "Russian translation")
                        stringProperty("example", "A short example sentence in English")
                        stringProperty("why", "Why this is worth learning for this student")
                    },
                ),
                FunctionDeclaration(
                    name = LOG_MISTAKE,
                    description = "Log a mistake the student made. Call it for every mistake " +
                        "you notice, including the ones you decided not to voice.",
                    parameters = objectSchema(
                        required = listOf("type", "original", "corrected"),
                    ) {
                        stringProperty(
                            "type",
                            "One of: GRAMMAR, VOCAB, PRONUNCIATION, WORD_ORDER, ARTICLE, " +
                                "TENSE, PREPOSITION, STYLE",
                        )
                        stringProperty("original", "What the student actually said")
                        stringProperty("corrected", "The correct version")
                        stringProperty("explanation", "One short sentence in Russian")
                        stringProperty("grammar_topic", "Grammar topic code, if applicable")
                        stringProperty("phoneme", "IPA symbol, for pronunciation mistakes")
                        integerProperty("severity", "1 minor, 2 noticeable, 3 breaks understanding")
                    },
                ),
                FunctionDeclaration(
                    name = SET_DIFFICULTY,
                    description = "Signal that the lesson is too easy or too hard for the student.",
                    parameters = objectSchema(required = listOf("direction")) {
                        stringProperty("direction", "UP or DOWN")
                        stringProperty("reason", "Short reason in Russian")
                    },
                ),
                FunctionDeclaration(
                    name = SHOW_CARD,
                    description = "Show a small card on the student's screen: a word, a phrase " +
                        "or a rule they should see while you keep talking.",
                    parameters = objectSchema(required = listOf("text_en")) {
                        stringProperty("kind", "WORD, PHRASE or RULE")
                        stringProperty("text_en", "English text for the card")
                        stringProperty("text_ru", "Russian translation or explanation")
                    },
                ),
                FunctionDeclaration(
                    name = SUGGEST_DRILL,
                    description = "Suggest a drill for after the lesson: a sound or a grammar topic.",
                    parameters = objectSchema(required = listOf("target")) {
                        stringProperty("target", "IPA phoneme or grammar topic code")
                        stringProperty("reason", "Short reason in Russian")
                    },
                ),
            )
        )
    )

    /** Стандартный ответ: тренеру не нужно ничего, кроме подтверждения. */
    fun ok(): JsonObject = buildJsonObject { put("ok", true) }

    /** Безопасное чтение строкового аргумента вызова. */
    fun FunctionCall.string(key: String): String? =
        args?.get(key)?.jsonPrimitive?.contentOrNullSafe()

    fun FunctionCall.int(key: String): Int? =
        args?.get(key)?.jsonPrimitive?.contentOrNullSafe()?.toIntOrNull()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

    // --- Сборка схемы параметров ---

    private class SchemaBuilder {
        val properties = mutableListOf<Pair<String, Pair<String, String>>>()

        fun stringProperty(name: String, description: String) {
            properties += name to ("STRING" to description)
        }

        fun integerProperty(name: String, description: String) {
            properties += name to ("INTEGER" to description)
        }
    }

    private fun objectSchema(
        required: List<String>,
        block: SchemaBuilder.() -> Unit,
    ): JsonObject {
        val builder = SchemaBuilder().apply(block)
        return buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                builder.properties.forEach { (name, spec) ->
                    putJsonObject(name) {
                        put("type", spec.first)
                        put("description", spec.second)
                    }
                }
            }
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            }
        }
    }
}
