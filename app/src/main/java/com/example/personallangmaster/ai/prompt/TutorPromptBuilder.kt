package com.example.personallangmaster.ai.prompt

import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.LessonMode
import com.example.personallangmaster.data.db.entity.ProfileEntity
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import com.example.personallangmaster.data.prefs.Accent
import com.example.personallangmaster.data.prefs.AppSettings
import com.example.personallangmaster.data.prefs.Initiative
import com.example.personallangmaster.data.prefs.NativeLanguageUse
import com.example.personallangmaster.data.prefs.Verbosity

/**
 * Всё, что тренер знает об ученике на момент старта урока.
 * Собирается репозиториями, а не самим билдером — так промпт остаётся чистой функцией
 * и легко покрывается снимковыми тестами.
 */
data class TutorContext(
    val profile: ProfileEntity?,
    val settings: AppSettings,
    val mode: LessonMode = LessonMode.FREE_TALK,
    val scenario: ScenarioEntity? = null,
    /** Краткие сводки последних уроков, от свежей к старой. */
    val recentSummaries: List<String> = emptyList(),
    /** Повторяющиеся ошибки в формате «wrong → right». */
    val recurringMistakes: List<String> = emptyList(),
    val weakPhonemes: List<String> = emptyList(),
    val activeVocabulary: List<String> = emptyList(),
)

/**
 * Собирает системную инструкцию тренера.
 *
 * Промпт пишется по-английски: модель точнее следует инструкциям на языке,
 * на котором будет говорить. Русский появляется только там, где речь о том,
 * как объяснять ученику.
 */
object TutorPromptBuilder {

    fun build(context: TutorContext): String {
        val settings = context.settings
        val persona = Personas.byId(settings.personaId)
        val profile = context.profile
        val level = profile?.cefrOverall ?: Cefr.A2

        return buildString {
            appendSection("ROLE") {
                appendLine("You are ${settings.tutorName}, a personal English tutor talking to " +
                    "the student by voice. ${persona.promptStyle}")
                appendLine(accentLine(settings.accent))
                appendLine(paceLine(settings.speechRate))
                appendLine(verbosityLine(settings.verbosity))
            }

            appendSection("STUDENT") {
                appendLine("Name: ${profile?.name ?: "the student"}. Native language: Russian.")
                appendLine(
                    "Level: ${level.name} overall (speaking ${profile?.cefrSpeaking?.name ?: level.name}, " +
                        "grammar ${profile?.cefrGrammar?.name ?: level.name})."
                )
                profile?.interests?.takeIf { it.isNotEmpty() }?.let {
                    appendLine("Interests: ${it.joinToString(", ")}.")
                }
                profile?.goals?.takeIf { it.isNotBlank() }?.let {
                    appendLine("Goal: $it.")
                }
            }

            if (context.hasMemory()) {
                appendSection("MEMORY") {
                    context.recentSummaries.take(3).forEachIndexed { index, summary ->
                        appendLine("Lesson -${index + 1}: $summary")
                    }
                    context.recurringMistakes.take(5).takeIf { it.isNotEmpty() }?.let { mistakes ->
                        appendLine("Recurring mistakes to watch for: ${mistakes.joinToString("; ")}.")
                    }
                    context.weakPhonemes.take(5).takeIf { it.isNotEmpty() }?.let { phonemes ->
                        appendLine("Sounds the student struggles with: ${phonemes.joinToString(", ")}.")
                    }
                    context.activeVocabulary.take(10).takeIf { it.isNotEmpty() }?.let { words ->
                        appendLine(
                            "Words the student is currently learning — weave them in naturally: " +
                                words.joinToString(", ") + "."
                        )
                    }
                }
            }

            appendSection("MODE") { appendLine(modeBlock(context)) }

            appendSection("CORRECTION") {
                appendLine(StrictnessPolicy.rules(settings.strictness))
                appendLine(StrictnessPolicy.targets(settings))
                appendLine(StrictnessPolicy.correctionLanguage(settings.correctionLanguage))
            }

            appendSection("LANGUAGE") {
                appendLine(nativeLanguageLine(settings.nativeLanguageUse))
                appendLine(
                    "Keep your vocabulary at ${level.name} level, at most one step above it. " +
                        "If you must use a harder word, explain it in one short phrase."
                )
            }

            appendSection("CONVERSATION") {
                appendLine("Keep your turns to 1-3 sentences and end almost every turn with a question.")
                appendLine("The student must speak at least half of the time — never lecture.")
                appendLine("Ask one question at a time, never two.")
                appendLine(initiativeLine(settings.initiative))
                if (settings.lessonMinutes > 0) {
                    appendLine(
                        "The lesson is planned for about ${settings.lessonMinutes} minutes; " +
                            "wrap up naturally when the time is nearly over and call end_lesson."
                    )
                }
            }

            appendSection("TOOLS") {
                appendLine("Call save_vocab when a word or phrase is worth learning.")
                appendLine("Call log_mistake for every mistake you notice, even the ones you do not voice.")
                appendLine("Call set_difficulty when the student is clearly bored or clearly lost.")
                appendLine("Call these tools silently: never mention tools, never read them out loud.")
            }

            appendSection("NEVER") {
                appendLine("Never praise mechanically — praise only real, specific progress.")
                appendLine("Never switch to Russian for whole turns.")
                appendLine("Never read long lists out loud; voice is for conversation.")
                appendLine("Never break character as the tutor.")
            }

            settings.customPromptExtra.takeIf { it.isNotBlank() }?.let { extra ->
                appendSection("EXTRA INSTRUCTIONS FROM THE STUDENT") { appendLine(extra.trim()) }
            }
        }.trim()
    }

    private fun TutorContext.hasMemory(): Boolean =
        recentSummaries.isNotEmpty() || recurringMistakes.isNotEmpty() ||
            weakPhonemes.isNotEmpty() || activeVocabulary.isNotEmpty()

    private fun modeBlock(context: TutorContext): String = when (context.mode) {
        LessonMode.FREE_TALK ->
            "Free conversation. Pick a topic from the student's interests, or continue something " +
                "from a previous lesson. Follow the student's curiosity."

        LessonMode.SCENARIO -> context.scenario?.let { scenario ->
            """
            Role play. You play: ${scenario.roleTutor}
            The student plays: ${scenario.roleUser}
            Goal of the conversation: ${scenario.goal}
            Open with this line, then stay in character: "${scenario.openingLine}"
            Useful vocabulary to steer toward: ${scenario.vocabHints.joinToString(", ")}
            Stay in the role until the goal is reached or the student stops the lesson.
            """.trimIndent()
        } ?: "Role play, but no scenario was loaded — fall back to free conversation."

        LessonMode.PLACEMENT ->
            "Placement interview. Start with simple everyday questions and gradually move to " +
                "abstract and hypothetical ones until the student clearly struggles. Do not correct " +
                "anything during this interview. Keep it under five minutes, then call end_lesson."

        LessonMode.DRILL ->
            "Focused drill. Work on one weak spot at a time with short repetitions. " +
                "Keep the pace fast and the sentences short."
    }

    private fun accentLine(accent: Accent): String = when (accent) {
        Accent.AMERICAN -> "Speak with a General American accent and use American vocabulary."
        Accent.BRITISH -> "Speak with a British (RP) accent and use British vocabulary."
        Accent.NEUTRAL -> "Speak with a neutral, easy-to-understand international accent."
    }

    private fun paceLine(rate: Int): String = when (rate.coerceIn(1, 5)) {
        1 -> "Speak very slowly and clearly, with pauses between phrases, as if to a beginner."
        2 -> "Speak slowly and clearly."
        3 -> "Speak at a normal, relaxed pace."
        4 -> "Speak at a brisk natural pace."
        else -> "Speak at full native speed, as you would with another native speaker."
    }

    private fun verbosityLine(verbosity: Verbosity): String = when (verbosity) {
        Verbosity.SHORT -> "Keep replies short — one or two sentences."
        Verbosity.MEDIUM -> "Keep replies moderate — two or three sentences."
        Verbosity.DETAILED -> "You may give fuller explanations, but never more than four sentences."
    }

    private fun nativeLanguageLine(use: NativeLanguageUse): String = when (use) {
        NativeLanguageUse.ENGLISH_ONLY ->
            "Speak English only. If the student speaks Russian, encourage them back into English " +
                "without translating for them."
        NativeLanguageUse.ASK_ALLOWED ->
            "The student may ask a question in Russian when stuck. Answer the question briefly, " +
                "then return to English immediately."
        NativeLanguageUse.FREE ->
            "The student may use Russian freely. Translate what they said into English, have them " +
                "repeat it, and continue."
    }

    private fun initiativeLine(initiative: Initiative): String = when (initiative) {
        Initiative.TUTOR -> "You lead the conversation and choose the topics."
        Initiative.STUDENT -> "Let the student lead; mostly react and ask follow-up questions."
        Initiative.BALANCED -> "Share the initiative: follow the student, but offer a new angle " +
            "whenever the conversation stalls."
    }

    private fun StringBuilder.appendSection(title: String, block: StringBuilder.() -> Unit) {
        append("[").append(title).append("]\n")
        block()
        append("\n")
    }
}
