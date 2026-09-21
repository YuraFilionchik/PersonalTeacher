package com.example.personallangmaster.domain

/** Что фоновая задача делает с результатом разбора. */
enum class AnalysisOutcome {
    /** Разбор прошёл — можно показать уведомление. */
    DONE,

    /** Урок уже был разобран: задача сделана, но сообщать не о чем. */
    ALREADY_DONE,

    /** Помешало временное: сеть, квота, сбой сервера. Повторим позже. */
    RETRY,

    /** Повторять бессмысленно или попытки закончились. */
    GIVE_UP,
}

/**
 * Когда повторять разбор урока.
 *
 * Вынесено из воркера отдельно, потому что цена ошибки здесь несимметрична:
 * лишний повтор — это лишний платный вызов модели, а отказ от повтора —
 * навсегда потерянный разбор урока.
 */
object AnalysisRetryPolicy {

    /** Больше трёх попыток не имеет смысла: до ночного доразбора всё равно недалеко. */
    const val MAX_ATTEMPTS = 3

    /** @param attempt номер уже сделанной попытки, как его считает WorkManager (с нуля). */
    fun decide(result: AnalysisResult, attempt: Int): AnalysisOutcome = when (result) {
        is AnalysisResult.Success -> AnalysisOutcome.DONE
        AnalysisResult.AlreadyAnalyzed -> AnalysisOutcome.ALREADY_DONE
        is AnalysisResult.Failure ->
            if (result.retriable && attempt < MAX_ATTEMPTS - 1) {
                AnalysisOutcome.RETRY
            } else {
                AnalysisOutcome.GIVE_UP
            }
    }
}
