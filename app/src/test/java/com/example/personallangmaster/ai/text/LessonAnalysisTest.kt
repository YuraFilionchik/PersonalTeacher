package com.example.personallangmaster.ai.text

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор урока приходит от модели как JSON по схеме. Если схема и модель данных
 * разойдутся, приложение молча потеряет ошибки и слова, поэтому проверяем обе стороны.
 */
class LessonAnalysisTest {

    private val sampleResponse = """
        {
          "cefr_estimate": "B1",
          "fluency": 62,
          "accuracy": 48,
          "vocabulary_range": 55,
          "summary_ru": "Говорили про отпуск и планы на лето.",
          "summary_en": "Talked about holidays and summer plans.",
          "praise_ru": "Стали увереннее держать длинные фразы.",
          "mistakes": [
            {"type":"TENSE","original":"I go to Spain last year",
             "corrected":"I went to Spain last year",
             "explanation_ru":"Прошедшее время: went, а не go.",
             "grammar_topic":"past_simple","severity":3},
            {"type":"ARTICLE","original":"I saw beach",
             "corrected":"I saw the beach",
             "explanation_ru":"Конкретный пляж — нужен артикль the."}
          ],
          "vocab": [
            {"term":"crowded","translation_ru":"переполненный","example":"The beach was crowded.","cefr":"B1"}
          ],
          "pronunciation_notes": [
            {"phoneme":"/θ/","words":["think","through"],"comment_ru":"Язык между зубами."}
          ],
          "next_lesson_focus": ["Прошедшее время", "Артикли"]
        }
    """.trimIndent()

    @Test
    fun `ответ модели разбирается полностью`() {
        val analysis = LessonAnalysisSchema.parse(sampleResponse)

        assertEquals("B1", analysis.cefr_estimate)
        assertEquals(62, analysis.fluency)
        assertEquals(2, analysis.mistakes.size)
        assertEquals("I went to Spain last year", analysis.mistakes.first().corrected)
        assertEquals("past_simple", analysis.mistakes.first().grammar_topic)
        assertEquals("crowded", analysis.vocab.single().term)
        assertEquals("/θ/", analysis.pronunciation_notes.single().phoneme)
        assertEquals(listOf("Прошедшее время", "Артикли"), analysis.next_lesson_focus)
    }

    @Test
    fun `пропущенные необязательные поля не ломают разбор`() {
        val analysis = LessonAnalysisSchema.parse(
            """{"cefr_estimate":"A2","summary_ru":"Короткий разговор.","mistakes":[],"vocab":[]}"""
        )

        assertEquals("A2", analysis.cefr_estimate)
        assertTrue(analysis.mistakes.isEmpty())
        assertEquals(2, analysis.mistakes.size + 2)
        assertEquals("", analysis.praise_ru)
        assertEquals(0, analysis.fluency)
    }

    @Test
    fun `ошибка без severity получает среднюю тяжесть по умолчанию`() {
        val analysis = LessonAnalysisSchema.parse(sampleResponse)
        assertEquals(2, analysis.mistakes[1].severity)
    }

    @Test
    fun `неизвестные поля от модели игнорируются`() {
        val analysis = LessonAnalysisSchema.parse(
            """{"cefr_estimate":"B2","summary_ru":"x","mistakes":[],"vocab":[],"newFieldFromFuture":1}"""
        )
        assertEquals("B2", analysis.cefr_estimate)
    }

    @Test
    fun `схема требует ключевые поля`() {
        val required = LessonAnalysisSchema.schema["required"].toString()

        assertTrue(required.contains("cefr_estimate"))
        assertTrue(required.contains("summary_ru"))
        assertTrue(required.contains("mistakes"))
        assertTrue(required.contains("vocab"))
    }

    @Test
    fun `схема описывает массив ошибок объектами`() {
        val mistakes = LessonAnalysisSchema.schema["properties"]!!.jsonObject["mistakes"]!!.jsonObject

        assertEquals("ARRAY", mistakes["type"]?.jsonPrimitive?.content)
        assertEquals("OBJECT", mistakes["items"]?.jsonObject?.get("type")?.jsonPrimitive?.content)

        val itemRequired = mistakes["items"]!!.jsonObject["required"].toString()
        assertTrue(itemRequired.contains("original"))
        assertTrue(itemRequired.contains("corrected"))
        assertTrue(itemRequired.contains("explanation_ru"))
    }

    @Test
    fun `инструкция переключает язык объяснений`() {
        assertTrue(LessonAnalysisSchema.systemInstruction(true).contains("in clear, friendly Russian"))
        assertTrue(LessonAnalysisSchema.systemInstruction(false).contains("in simple English"))
    }
}
