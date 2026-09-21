package com.example.personallangmaster.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personallangmaster.PersonalLangMasterApplication
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.prefs.AudioRecording
import com.example.personallangmaster.data.prefs.TranscriptRetention
import com.example.personallangmaster.data.repo.StatsRepository
import com.example.personallangmaster.domain.AnalysisOutcome
import com.example.personallangmaster.domain.AnalysisResult
import com.example.personallangmaster.domain.AnalysisRetryPolicy
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Ежедневное напоминание.
 *
 * Молчит, если цель уже выполнена: напоминание о том, что и так сделано,
 * быстро приучает игнорировать уведомления.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PersonalLangMasterApplication)?.container
            ?: return Result.success()

        val settings = container.settingsRepository.current()
        if (!settings.reminderEnabled) return Result.success()

        val today = LocalDate.now()
        // Дни недели в настройках: 1 — понедельник, как в ISO.
        if (today.dayOfWeek.value !in settings.reminderDays) return Result.success()

        val profile = container.profileRepository.current() ?: return Result.success()
        val stat = container.statsRepository.observeToday(profile.id).first()
        val minutes = (stat?.minutesSpoken ?: 0.0).toInt()
        val goal = profile.dailyGoalMinutes

        if (goal > 0 && minutes >= goal) return Result.success()

        val streak = container.statsRepository.observeStreak(profile.id).first()?.current ?: 0
        Notifications.showDailyReminder(
            context = applicationContext,
            minutesLeft = (goal - minutes).coerceAtLeast(1),
            streak = streak,
        )
        return Result.success()
    }
}


/**
 * Разбор урока в фоне.
 *
 * Раньше разбор начинался только при открытии экрана «Разбор урока», и урок,
 * после которого человек просто закрыл приложение, оставался неразобранным:
 * ошибки не попадали в грамматику, слова — в словарь. Теперь урок разбирается
 * сам, а экран лишь показывает готовый результат.
 */
class LessonAnalysisWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PersonalLangMasterApplication)?.container
            ?: return Result.success()

        val lessonId = inputData.getLong(KEY_LESSON_ID, -1L)
        if (lessonId <= 0L) return Result.success()

        val settings = container.settingsRepository.current()
        // Автоматический разбор можно выключить: это платный вызов модели.
        if (!settings.autoAnalyzeLesson) return Result.success()

        val result = container.analyzeLessonUseCase(lessonId)

        return when (AnalysisRetryPolicy.decide(result, runAttemptCount)) {
            AnalysisOutcome.DONE -> {
                val analysis = (result as AnalysisResult.Success).analysis
                if (inputData.getBoolean(KEY_NOTIFY, true)) {
                    val lesson = container.database.lessonDao().getById(lessonId)
                    Notifications.showAnalysisReady(
                        context = applicationContext,
                        lessonId = lessonId,
                        summaryRu = lesson?.summaryRu,
                        mistakes = analysis.mistakes.size,
                        words = analysis.vocab.size,
                    )
                }
                Result.success()
            }

            AnalysisOutcome.ALREADY_DONE -> Result.success()

            AnalysisOutcome.RETRY -> Result.retry()

            // Молча: урок остаётся со статусом COMPLETED, разбор запустится
            // с экрана разбора или следующей ночью.
            AnalysisOutcome.GIVE_UP -> Result.success()
        }
    }

    companion object {
        const val KEY_LESSON_ID = "lessonId"
        const val KEY_NOTIFY = "notify"
    }
}

/** Напоминание о карточках: приходит, только когда их накопилось заметное число. */
class ReviewReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PersonalLangMasterApplication)?.container
            ?: return Result.success()

        val settings = container.settingsRepository.current()
        if (!settings.reviewReminderEnabled) return Result.success()

        val profile = container.profileRepository.current() ?: return Result.success()
        val due = container.vocabRepository.observeDueCount(profile.id).first()

        if (due >= MIN_DUE_CARDS) {
            Notifications.showReviewReminder(applicationContext, due)
        }
        return Result.success()
    }
}

/**
 * Ночное обслуживание: пересчёт серии и чистка старых данных.
 *
 * Серию нужно пересчитывать без участия пользователя, иначе пропущенный день
 * «висит» до следующего запуска приложения и статистика врёт.
 */
class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? PersonalLangMasterApplication)?.container
            ?: return Result.success()

        val profile = container.profileRepository.current() ?: return Result.success()
        val settings = container.settingsRepository.current()

        val stat = container.statsRepository.observeToday(profile.id).first()
        container.statsRepository.refreshStreak(
            profileId = profile.id,
            minutesToday = (stat?.minutesSpoken ?: 0.0).toInt(),
            goalMinutes = profile.dailyGoalMinutes,
        )

        if (settings.transcriptRetention == TranscriptRetention.DAYS) {
            val threshold = System.currentTimeMillis() -
                settings.transcriptRetentionDays * StatsRepository.DAY_MILLIS
            container.database.lessonDao().deleteTurnsOlderThan(threshold)
        }

        if (settings.audioRecording != AudioRecording.NONE) {
            container.lessonRepository.deleteAudioOlderThan(settings.audioRetentionDays)
        }

        // Записи о расходе держим год: этого хватает для годовой статистики.
        container.statsRepository.cleanupUsage(olderThanDays = USAGE_RETENTION_DAYS)

        analyzeBacklog(profile.id)

        return Result.success()
    }

    /**
     * Уроки, разбор которых не прошёл днём — не было сети, кончилась квота,
     * приложение убили сразу после разговора. Берём по чуть-чуть: каждый разбор
     * стоит денег, и лучше растянуть хвост на несколько ночей, чем неожиданно
     * потратить бюджет за одну.
     */
    private suspend fun analyzeBacklog(profileId: Long) {
        val container = (applicationContext as? PersonalLangMasterApplication)?.container ?: return
        if (!container.settingsRepository.current().autoAnalyzeLesson) return

        container.database.lessonDao()
            .getByStatus(profileId, LessonStatus.COMPLETED, limit = MAX_BACKLOG_LESSONS)
            .forEach { lesson ->
                // Тихо: человек уже не помнит этот урок, уведомление ночью ни к чему.
                container.analysisScheduler.schedule(lesson.id, notify = false)
            }
    }
}

private const val MIN_DUE_CARDS = 10
private const val USAGE_RETENTION_DAYS = 365
private const val MAX_BACKLOG_LESSONS = 3
