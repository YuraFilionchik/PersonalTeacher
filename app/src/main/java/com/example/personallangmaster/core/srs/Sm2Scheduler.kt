package com.example.personallangmaster.core.srs

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Планировщик интервальных повторений на основе алгоритма SM-2.
 */
object Sm2Scheduler {
    
    /**
     * Создает новую карточку.
     */
    fun newCard(todayEpochDay: Long): SrsCard {
        return SrsCard(
            ease = 2.5,
            intervalDays = 0,
            repetitions = 0,
            lapses = 0,
            state = SrsState.NEW,
            dueAtEpochDay = todayEpochDay,
            lastReviewedEpochDay = null
        )
    }

    /**
     * Вычисляет следующее состояние карточки на основе оценки.
     */
    fun schedule(card: SrsCard, grade: ReviewGrade, todayEpochDay: Long): SrsCard {
        if (card.state == SrsState.SUSPENDED) {
            return card
        }

        var ease = card.ease
        var interval = card.intervalDays
        var repetitions = card.repetitions
        var lapses = card.lapses
        var state = card.state

        when (grade) {
            ReviewGrade.AGAIN -> {
                repetitions = 0
                lapses += 1
                interval = 0
                ease -= 0.20
                state = SrsState.LEARNING
            }
            ReviewGrade.HARD -> {
                interval = max(1, (interval * 1.2).roundToInt())
                ease -= 0.15
            }
            ReviewGrade.GOOD -> {
                val goodInterval = if (repetitions == 0) 1 else if (repetitions == 1) 6 else (interval * ease).roundToInt()
                interval = goodInterval
                repetitions += 1
            }
            ReviewGrade.EASY -> {
                val goodInterval = if (repetitions == 0) 1 else if (repetitions == 1) 6 else (interval * ease).roundToInt()
                interval = (goodInterval * 1.3).roundToInt()
                ease += 0.15
                repetitions += 1
            }
        }

        // Зажимаем ease в пределы [1.3, 3.0]
        ease = min(3.0, max(1.3, ease))
        
        // Зажимаем интервал в пределы [0, 365]
        interval = min(365, max(0, interval))

        if (grade != ReviewGrade.AGAIN) {
            state = if (interval >= 21) {
                SrsState.MATURE
            } else if (repetitions >= 2) {
                SrsState.REVIEW
            } else {
                SrsState.LEARNING
            }
        }

        return SrsCard(
            ease = ease,
            intervalDays = interval,
            repetitions = repetitions,
            lapses = lapses,
            state = state,
            dueAtEpochDay = todayEpochDay + interval,
            lastReviewedEpochDay = todayEpochDay
        )
    }

    /**
     * Проверяет, настало ли время повторять карточку.
     */
    fun isDue(card: SrsCard, todayEpochDay: Long): Boolean {
        if (card.state == SrsState.SUSPENDED) return false
        return todayEpochDay >= card.dueAtEpochDay
    }
}
