package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.prefs.ProgressionPace

/**
 * Решает, пора ли менять уровень ученика.
 *
 * Один удачный разговор ничего не доказывает: уровень растёт, только когда
 * несколько оценок подряд оказались выше текущего. Понижение требует ещё
 * большей уверенности — ошибочно сбросить уровень обиднее, чем пару уроков
 * позаниматься на чуть более сложном материале.
 */
object LevelProgression {

    /** Сколько подряд оценок нужно для повышения. */
    fun required(pace: ProgressionPace): Int = when (pace) {
        ProgressionPace.AGGRESSIVE -> 2
        ProgressionPace.NORMAL -> 3
        ProgressionPace.CONSERVATIVE -> 4
    }

    /**
     * @param estimates оценки последних уроков, от свежей к старой.
     * @return новый уровень или null, если менять нечего.
     */
    fun next(current: Cefr, estimates: List<Cefr>, pace: ProgressionPace): Cefr? {
        val needed = required(pace)
        if (estimates.size < needed) return null

        val promoteWindow = estimates.take(needed)
        if (promoteWindow.all { it.ordinal > current.ordinal }) {
            return Cefr.entries.getOrNull(current.ordinal + 1)
        }

        // Понижение — на одну оценку строже, чем повышение.
        val demoteWindow = estimates.take(needed + 1)
        if (demoteWindow.size > needed && demoteWindow.all { it.ordinal < current.ordinal }) {
            return Cefr.entries.getOrNull(current.ordinal - 1)
        }

        return null
    }
}
