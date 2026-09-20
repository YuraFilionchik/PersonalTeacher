package com.example.personallangmaster.ai.prompt

import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.LessonMode
import com.example.personallangmaster.data.db.entity.ProfileEntity
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import com.example.personallangmaster.data.prefs.AppSettings
import com.example.personallangmaster.data.prefs.NativeLanguageUse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Промпт — главный продукт приложения, поэтому проверяем не форматирование,
 * а то, что настройки действительно доезжают до инструкции тренеру.
 */
class TutorPromptBuilderTest {

    private val profile = ProfileEntity(
        id = 1,
        name = "Юра",
        createdAt = 0,
        cefrOverall = Cefr.B1,
        cefrSpeaking = Cefr.A2,
        cefrGrammar = Cefr.B1,
        interests = listOf("IT", "Путешествия"),
        goals = "свободно говорить на созвонах",
    )

    @Test
    fun `профиль попадает в промпт`() {
        val prompt = TutorPromptBuilder.build(TutorContext(profile, AppSettings()))

        assertTrue(prompt.contains("Юра"))
        assertTrue(prompt.contains("B1"))
        assertTrue(prompt.contains("IT"))
        assertTrue(prompt.contains("свободно говорить на созвонах"))
    }

    @Test
    fun `нулевая строгость запрещает поправки вслух`() {
        val prompt = TutorPromptBuilder.build(
            TutorContext(profile, AppSettings(strictness = 0))
        )

        assertTrue(prompt.contains("Do not correct the student out loud"))
        assertTrue("даже при нулевой строгости ошибки должны логироваться",
            prompt.contains("log_mistake"))
    }

    @Test
    fun `режим дрилла разрешает перебивать`() {
        val prompt = TutorPromptBuilder.build(
            TutorContext(profile, AppSettings(strictness = 4))
        )

        assertTrue(prompt.contains("Interrupt as soon as you hear a mistake"))
    }

    @Test
    fun `выключенные категории не поправляются`() {
        val settings = AppSettings(
            correctGrammar = false,
            correctVocab = false,
            correctPronunciation = false,
            correctWordOrder = false,
            correctNaturalness = false,
            correctArticles = false,
        )

        val prompt = TutorPromptBuilder.build(TutorContext(profile, settings))

        assertTrue(prompt.contains("Do not correct anything out loud"))
    }

    @Test
    fun `включённая категория перечислена явно`() {
        val settings = AppSettings(
            correctGrammar = true,
            correctVocab = false,
            correctPronunciation = false,
            correctWordOrder = false,
            correctNaturalness = false,
            correctArticles = false,
        )

        val prompt = TutorPromptBuilder.build(TutorContext(profile, settings))

        assertTrue(prompt.contains("Correct only these: grammar."))
    }

    @Test
    fun `сценарий задаёт роли и первую реплику`() {
        val scenario = ScenarioEntity(
            id = "cafe",
            category = "EVERYDAY",
            level = Cefr.A1,
            titleRu = "В кафе",
            titleEn = "At a cafe",
            descriptionRu = "заказать кофе",
            durationMins = 3,
            roleTutor = "barista",
            roleUser = "customer",
            goal = "order a latte",
            openingLine = "Hi there! What can I get you?",
            vocabHints = listOf("to go", "oat milk"),
        )

        val prompt = TutorPromptBuilder.build(
            TutorContext(
                profile = profile,
                settings = AppSettings(),
                mode = LessonMode.SCENARIO,
                scenario = scenario,
            )
        )

        assertTrue(prompt.contains("barista"))
        assertTrue(prompt.contains("Hi there! What can I get you?"))
        assertTrue(prompt.contains("oat milk"))
    }

    @Test
    fun `placement не поправляет ошибки`() {
        val prompt = TutorPromptBuilder.build(
            TutorContext(profile, AppSettings(), mode = LessonMode.PLACEMENT)
        )

        assertTrue(prompt.contains("Do not correct anything during this interview"))
    }

    @Test
    fun `память подставляется только когда она есть`() {
        val empty = TutorPromptBuilder.build(TutorContext(profile, AppSettings()))
        assertFalse(empty.contains("[MEMORY]"))

        val withMemory = TutorPromptBuilder.build(
            TutorContext(
                profile = profile,
                settings = AppSettings(),
                recentSummaries = listOf("Говорили про отпуск"),
                recurringMistakes = listOf("I go yesterday → I went yesterday"),
                weakPhonemes = listOf("/θ/"),
                activeVocabulary = listOf("deadline", "commute"),
            )
        )
        assertTrue(withMemory.contains("[MEMORY]"))
        assertTrue(withMemory.contains("Говорили про отпуск"))
        assertTrue(withMemory.contains("/θ/"))
        assertTrue(withMemory.contains("deadline"))
    }

    @Test
    fun `свой промпт пользователя попадает в конец`() {
        val prompt = TutorPromptBuilder.build(
            TutorContext(profile, AppSettings(customPromptExtra = "Спрашивай про мой проект"))
        )

        assertTrue(prompt.contains("EXTRA INSTRUCTIONS FROM THE STUDENT"))
        assertTrue(prompt.trimEnd().endsWith("Спрашивай про мой проект"))
    }

    @Test
    fun `режим только английского запрещает перевод`() {
        val prompt = TutorPromptBuilder.build(
            TutorContext(
                profile,
                AppSettings(nativeLanguageUse = NativeLanguageUse.ENGLISH_ONLY),
            )
        )

        assertTrue(prompt.contains("Speak English only"))
    }

    @Test
    fun `длина урока превращается в инструкцию о завершении`() {
        val prompt = TutorPromptBuilder.build(
            TutorContext(profile, AppSettings(lessonMinutes = 15))
        )
        assertTrue(prompt.contains("about 15 minutes"))
        assertTrue("тренер должен сворачивать разговор сам, без инструмента",
            prompt.contains("start wrapping up"))

        val unlimited = TutorPromptBuilder.build(
            TutorContext(profile, AppSettings(lessonMinutes = 0))
        )
        assertFalse(unlimited.contains("The lesson is planned"))
    }

    @Test
    fun `промпт не зовёт инструменты, которых нет`() {
        val prompts = com.example.personallangmaster.data.db.LessonMode.entries.map { mode ->
            TutorPromptBuilder.build(TutorContext(profile, AppSettings(), mode = mode))
        }
        val declared = com.example.personallangmaster.ai.live.LiveTools.declarations
            .flatMap { it.functionDeclarations }
            .map { it.name }

        prompts.forEach { prompt ->
            Regex("""\b[a-z]+_[a-z_]+\b""").findAll(prompt)
                .map { it.value }
                .filter { it.endsWith("_lesson") || it.startsWith("save_") || it.startsWith("log_") ||
                    it.startsWith("set_") || it.startsWith("show_") || it.startsWith("suggest_") }
                .forEach { mentioned ->
                    assertTrue("промпт упоминает $mentioned, но такого инструмента нет",
                        declared.contains(mentioned))
                }
        }
    }
}
