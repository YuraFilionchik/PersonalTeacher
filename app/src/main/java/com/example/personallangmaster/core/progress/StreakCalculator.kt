package com.example.personallangmaster.core.progress

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Логика вычисления ударного режима (стрика).
 */
object StreakCalculator {

    /**
     * Вычисляет обновленный стрик на основе дат активностей.
     * Засчитывает стрик, если активность была сегодня или вчера.
     * Допускает использование "заморозок", чтобы не потерять стрик при пропусках.
     *
     * @param lastActiveDate дата последней активности до текущего дня
     * @param today текущая локальная дата
     * @param currentStreak текущий счетчик стрика
     * @param longestStreak максимальный исторический стрик
     * @param freezesLeft количество оставшихся заморозок (в месяц)
     * @param achievedGoal сегодня выполнена норма для стрика
     */
    fun calculate(
        lastActiveDate: LocalDate?,
        today: LocalDate,
        currentStreak: Int,
        longestStreak: Int,
        freezesLeft: Int,
        achievedGoal: Boolean
    ): StreakResult {
        if (lastActiveDate == null) {
            val newStreak = if (achievedGoal) 1 else 0
            return StreakResult(newStreak, newStreak, freezesLeft, if (achievedGoal) today else null)
        }

        val daysBetween = ChronoUnit.DAYS.between(lastActiveDate, today).toInt()

        if (daysBetween == 0) {
            // Сегодня уже была активность, стрик не меняется
            return StreakResult(currentStreak, longestStreak, freezesLeft, lastActiveDate)
        }

        if (daysBetween == 1) {
            // Активность была вчера. Если сегодня достигли цели — увеличиваем.
            val newStreak = if (achievedGoal) currentStreak + 1 else currentStreak
            val newLongest = max(longestStreak, newStreak)
            return StreakResult(
                currentStreak = newStreak,
                longestStreak = newLongest,
                freezesLeft = freezesLeft,
                lastActiveDate = if (achievedGoal) today else lastActiveDate
            )
        }

        // Прошло больше одного дня. Проверяем заморозки.
        // Нам нужно покрыть дни пропуска: daysBetween - 1 (вчера и раньше)
        // Но заморозки списываются только если мы пытаемся сегодня восстановить стрик,
        // или если просто открыли приложение и узнаем статус.
        // Для упрощения: если мы пропустили N дней, нужно N заморозок.
        val missedDays = daysBetween - 1

        if (freezesLeft >= missedDays && currentStreak > 0) {
            // Спасаем стрик заморозками
            val remainingFreezes = freezesLeft - missedDays
            val newStreak = if (achievedGoal) currentStreak + 1 else currentStreak
            val newLongest = max(longestStreak, newStreak)
            
            return StreakResult(
                currentStreak = newStreak,
                longestStreak = newLongest,
                freezesLeft = remainingFreezes,
                lastActiveDate = if (achievedGoal) today else lastActiveDate
            )
        }

        // Заморозок не хватило, стрик потерян
        val newStreak = if (achievedGoal) 1 else 0
        val newLongest = max(longestStreak, newStreak)
        
        return StreakResult(
            currentStreak = newStreak,
            longestStreak = newLongest,
            freezesLeft = freezesLeft, // Заморозки не тратим, если стрик все равно сгорел
            lastActiveDate = if (achievedGoal) today else lastActiveDate
        )
    }

    /**
     * Конвертирует Epoch Millis в локальную дату для расчета стрика.
     */
    fun epochMillisToLocalDate(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
    }
}

data class StreakResult(
    val currentStreak: Int,
    val longestStreak: Int,
    val freezesLeft: Int,
    val lastActiveDate: LocalDate?
)
