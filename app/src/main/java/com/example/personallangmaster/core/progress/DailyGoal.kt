package com.example.personallangmaster.core.progress

/**
 * Логика вычисления достижения ежедневной цели.
 */
object DailyGoal {

    /**
     * Проверяет, достигнута ли цель дня.
     * @param spokenMinutes сколько минут наговорено за день
     * @param goalMinutes сколько минут установлена цель (если 0 - цели нет, всегда true)
     */
    fun isGoalAchieved(spokenMinutes: Int, goalMinutes: Int): Boolean {
        if (goalMinutes <= 0) return true
        return spokenMinutes >= goalMinutes
    }
}
