package com.example.personallangmaster.data.repo

import com.example.personallangmaster.core.progress.DailyGoal
import com.example.personallangmaster.core.progress.StreakCalculator
import com.example.personallangmaster.data.db.dao.ProfileDao
import com.example.personallangmaster.data.db.dao.StatsDao
import com.example.personallangmaster.data.db.dao.UsageByKind
import com.example.personallangmaster.data.db.entity.DailyStatEntity
import com.example.personallangmaster.data.db.entity.StreakEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Прогресс и расходы: дневная сводка, серия дней и потраченные деньги.
 *
 * Дневная строка обновляется по ходу занятий, а не пересчитывается из истории:
 * так экран прогресса открывается мгновенно и работает без сети.
 */
class StatsRepository(
    private val statsDao: StatsDao,
    private val profileDao: ProfileDao,
) {

    fun observeRange(profileId: Long, fromEpochDay: Long): Flow<List<DailyStatEntity>> =
        statsDao.observeRange(profileId, fromEpochDay)

    fun observeToday(profileId: Long): Flow<DailyStatEntity?> =
        statsDao.observeDailyStat(profileId, today())

    fun observeStreak(profileId: Long): Flow<StreakEntity?> = profileDao.observeStreak(profileId)

    fun observeCostSince(profileId: Long, since: Long): Flow<Double> =
        statsDao.observeCostSince(profileId, since)

    fun observeUsageByKind(profileId: Long, since: Long): Flow<List<UsageByKind>> =
        statsDao.observeUsageByKind(profileId, since)

    suspend fun spentSince(profileId: Long, since: Long): Double =
        statsDao.costSince(profileId, since)

    /**
     * Записывает итог урока в дневную сводку и пересчитывает серию дней.
     *
     * Серия считается по выполненной цели, а не по факту «открыл приложение»:
     * иначе стрик перестаёт что-либо значить.
     */
    suspend fun recordLesson(
        profileId: Long,
        minutes: Double,
        costUsd: Double,
        mistakesFixed: Int = 0,
        wordsLearned: Int = 0,
        goalMinutes: Int,
    ) {
        val day = today()
        val current = statsDao.dailyStat(profileId, day)
        val updatedMinutes = (current?.minutesSpoken ?: 0.0) + minutes

        statsDao.upsertDailyStat(
            DailyStatEntity(
                profileId = profileId,
                epochDay = day,
                minutesSpoken = updatedMinutes,
                lessonsCount = (current?.lessonsCount ?: 0) + 1,
                wordsLearned = (current?.wordsLearned ?: 0) + wordsLearned,
                reviewsDone = current?.reviewsDone ?: 0,
                mistakesFixed = (current?.mistakesFixed ?: 0) + mistakesFixed,
                costUsd = (current?.costUsd ?: 0.0) + costUsd,
                goalMet = DailyGoal.isGoalAchieved(updatedMinutes.toInt(), goalMinutes),
            )
        )

        refreshStreak(profileId, updatedMinutes.toInt(), goalMinutes)
    }

    /** Повторения карточек тоже идут в дневную сводку, но серию не двигают. */
    suspend fun recordReviews(profileId: Long, count: Int) {
        if (count <= 0) return
        val day = today()
        val current = statsDao.dailyStat(profileId, day)

        statsDao.upsertDailyStat(
            DailyStatEntity(
                profileId = profileId,
                epochDay = day,
                minutesSpoken = current?.minutesSpoken ?: 0.0,
                lessonsCount = current?.lessonsCount ?: 0,
                wordsLearned = current?.wordsLearned ?: 0,
                reviewsDone = (current?.reviewsDone ?: 0) + count,
                mistakesFixed = current?.mistakesFixed ?: 0,
                costUsd = current?.costUsd ?: 0.0,
                goalMet = current?.goalMet ?: false,
            )
        )
    }

    /** Пересчёт серии: вызывается после урока и в полночь фоновой задачей. */
    suspend fun refreshStreak(profileId: Long, minutesToday: Int, goalMinutes: Int) {
        val streak = profileDao.observeStreakOnce(profileId)
        val achieved = DailyGoal.isGoalAchieved(minutesToday, goalMinutes)

        val result = StreakCalculator.calculate(
            lastActiveDate = streak?.lastActiveEpochDay?.let(LocalDate::ofEpochDay),
            today = LocalDate.now(),
            currentStreak = streak?.current ?: 0,
            longestStreak = streak?.longest ?: 0,
            freezesLeft = streak?.freezesLeft ?: DEFAULT_FREEZES,
            achievedGoal = achieved,
        )

        profileDao.upsertStreak(
            StreakEntity(
                profileId = profileId,
                current = result.currentStreak,
                longest = result.longestStreak,
                lastActiveEpochDay = result.lastActiveDate?.toEpochDay(),
                freezesLeft = result.freezesLeft,
            )
        )
    }

    /** Чистка старых записей о расходе — по сроку хранения из настроек. */
    suspend fun cleanupUsage(olderThanDays: Int) {
        if (olderThanDays <= 0) return
        val threshold = System.currentTimeMillis() - olderThanDays * DAY_MILLIS
        statsDao.deleteUsageOlderThan(threshold)
    }

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val DEFAULT_FREEZES = 2

        fun today(zone: ZoneId = ZoneId.systemDefault()): Long = LocalDate.now(zone).toEpochDay()

        /** Начало текущих суток в миллисекундах — граница дневного лимита. */
        fun startOfToday(zone: ZoneId = ZoneId.systemDefault()): Long =
            LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        /** Начало текущего месяца — граница месячного лимита. */
        fun startOfMonth(zone: ZoneId = ZoneId.systemDefault()): Long =
            LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
