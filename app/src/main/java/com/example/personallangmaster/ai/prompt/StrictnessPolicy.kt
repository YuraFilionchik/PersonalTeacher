package com.example.personallangmaster.ai.prompt

import com.example.personallangmaster.data.prefs.AppSettings
import com.example.personallangmaster.data.prefs.CorrectionLanguage

/**
 * Превращает ползунок строгости 0..4 в конкретные правила поведения тренера.
 *
 * Это самая чувствительная часть промпта: от неё зависит, ощущается ли разговор
 * живым или превращается в разбор полётов. Формулировки намеренно императивные —
 * модели лучше следуют прямым запретам, чем описаниям стиля.
 */
object StrictnessPolicy {

    const val MIN = 0
    const val MAX = 4

    /** Короткое название уровня для интерфейса. */
    fun titleKeySuffix(level: Int): Int = level.coerceIn(MIN, MAX)

    fun rules(level: Int): String = when (level.coerceIn(MIN, MAX)) {
        0 -> """
            Do not correct the student out loud at all, whatever mistakes you hear.
            Keep the conversation flowing and simply model correct English yourself.
            Still call log_mistake silently for every mistake you notice.
        """.trimIndent()

        1 -> """
            Correct only through recasts: naturally repeat the student's idea in correct English
            as part of your own reply, without pointing at the mistake and without meta-commentary.
            Never interrupt. Never say "you should say". Call log_mistake for what you noticed.
        """.trimIndent()

        2 -> """
            After the student finishes a turn, give exactly ONE correction — the mistake that
            matters most for being understood — in a single short sentence, then immediately
            continue the conversation with your reply and a question.
            Never stack corrections. Never interrupt mid-sentence.
        """.trimIndent()

        3 -> """
            Correct every noticeable mistake after the student's turn, grouped into at most three
            short points. Ask the student to repeat the corrected sentence once before you move on.
            Still do not interrupt mid-sentence.
        """.trimIndent()

        else -> """
            This is drill mode. Interrupt as soon as you hear a mistake, including pronunciation.
            Say the correct form, have the student repeat it, and only accept an accurate repetition
            before continuing. Be demanding but never mocking.
        """.trimIndent()
    }

    /** Какие категории ошибок вообще разрешено поправлять. */
    fun targets(settings: AppSettings): String {
        val enabled = buildList {
            if (settings.correctGrammar) add("grammar")
            if (settings.correctVocab) add("word choice")
            if (settings.correctPronunciation) add("pronunciation")
            if (settings.correctWordOrder) add("word order")
            if (settings.correctNaturalness) add("unnatural phrasing")
            if (settings.correctArticles) add("articles and prepositions")
        }
        return if (enabled.isEmpty()) {
            "Do not correct anything out loud — the student turned all correction types off."
        } else {
            "Correct only these: ${enabled.joinToString(", ")}. Ignore everything else, " +
                "even if it is wrong."
        }
    }

    /** На каком языке звучат поправки. */
    fun correctionLanguage(language: CorrectionLanguage): String = when (language) {
        CorrectionLanguage.EN -> "Give corrections in English only."
        CorrectionLanguage.RU -> "Give the correction itself in English, but explain why in Russian."
        CorrectionLanguage.MIXED ->
            "Give the corrected phrase in English; if the reason is not obvious, add one short " +
                "clause in Russian. Keep Russian to a minimum."
    }
}
