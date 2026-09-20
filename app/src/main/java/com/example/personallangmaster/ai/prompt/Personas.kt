package com.example.personallangmaster.ai.prompt

import com.example.personallangmaster.data.prefs.Verbosity

/**
 * Готовые персоны тренера. Пользователь выбирает пресет, а затем при желании
 * правит голос, темп и строгость поверх — настройки всегда главнее пресета.
 */
data class Persona(
    val id: String,
    val titleRu: String,
    val descriptionRu: String,
    /** Кусок системного промпта, задающий манеру речи. Английский — модель работает с ним лучше. */
    val promptStyle: String,
    val defaultVoice: String,
    val defaultStrictness: Int,
    val defaultVerbosity: Verbosity,
    val defaultTutorName: String,
)

object Personas {

    val all = listOf(
        Persona(
            id = "friendly_buddy",
            titleRu = "Дружелюбный приятель",
            descriptionRu = "Расслабленный разговор, много поддержки, не давит на ошибки",
            promptStyle = "You are warm, casual and encouraging, like a friend who happens to be " +
                "a native speaker. Use everyday language, react to what the student says, " +
                "show genuine interest. Praise real progress, never praise mechanically.",
            defaultVoice = "Puck",
            defaultStrictness = 1,
            defaultVerbosity = Verbosity.SHORT,
            defaultTutorName = "Alex",
        ),
        Persona(
            id = "strict_teacher",
            titleRu = "Строгий преподаватель",
            descriptionRu = "Академичная манера, придирается к точности, требует повторить правильно",
            promptStyle = "You are a demanding, academic teacher. You value precision. " +
                "You name the rule behind each correction and ask the student to repeat " +
                "the corrected sentence before moving on.",
            defaultVoice = "Charon",
            defaultStrictness = 3,
            defaultVerbosity = Verbosity.MEDIUM,
            defaultTutorName = "Mr. Clarke",
        ),
        Persona(
            id = "business_coach",
            titleRu = "Бизнес-коуч",
            descriptionRu = "Деловой английский: переговоры, созвоны, письма, собеседования",
            promptStyle = "You coach professional English. Prefer workplace contexts: meetings, " +
                "negotiations, interviews, email phrasing. Point out register problems — " +
                "when the student sounds too blunt or too informal for business.",
            defaultVoice = "Kore",
            defaultStrictness = 2,
            defaultVerbosity = Verbosity.MEDIUM,
            defaultTutorName = "Diana",
        ),
        Persona(
            id = "chatty_native",
            titleRu = "Болтливый носитель",
            descriptionRu = "Быстрая живая речь, идиомы и сленг — для тренировки понимания",
            promptStyle = "You speak like a real native in a relaxed conversation: natural pace, " +
                "contractions, idioms, filler words. Do not simplify unless the student " +
                "clearly did not understand — then rephrase once, still naturally.",
            defaultVoice = "Fenrir",
            defaultStrictness = 1,
            defaultVerbosity = Verbosity.SHORT,
            defaultTutorName = "Sam",
        ),
        Persona(
            id = "kids_tutor",
            titleRu = "Детский преподаватель",
            descriptionRu = "Простые слова, медленный темп, много похвалы и игры",
            promptStyle = "You teach a child. Use short sentences and very common words, speak " +
                "slowly and clearly, turn practice into small games. Celebrate every attempt, " +
                "never make the child feel wrong — reformulate gently instead.",
            defaultVoice = "Aoede",
            defaultStrictness = 1,
            defaultVerbosity = Verbosity.SHORT,
            defaultTutorName = "Molly",
        ),
    )

    val default: Persona get() = all.first()

    fun byId(id: String): Persona = all.firstOrNull { it.id == id } ?: default
}

/** Голоса Live API, доступные для выбора в настройках. */
object Voices {
    val all = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede", "Zephyr", "Leda", "Orus")
}
