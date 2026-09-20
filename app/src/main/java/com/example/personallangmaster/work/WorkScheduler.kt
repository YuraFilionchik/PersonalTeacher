package com.example.personallangmaster.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personallangmaster.data.prefs.AppSettings
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Расписание фоновых задач.
 *
 * Пересобирается при старте приложения и после изменения настроек: проще
 * перезаписать расписание целиком, чем отслеживать, что именно поменялось.
 */
object WorkScheduler {

    private const val WORK_DAILY = "daily_reminder"
    private const val WORK_REVIEW = "review_reminder"
    private const val WORK_MAINTENANCE = "nightly_maintenance"

    fun sync(context: Context, settings: AppSettings) {
        val manager = WorkManager.getInstance(context)

        if (settings.reminderEnabled) {
            manager.enqueueUniquePeriodicWork(
                WORK_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DailyReminderWorker>(Duration.ofDays(1))
                    .setInitialDelay(delayUntil(settings.reminderMinuteOfDay))
                    .build(),
            )
        } else {
            manager.cancelUniqueWork(WORK_DAILY)
        }

        if (settings.reviewReminderEnabled) {
            manager.enqueueUniquePeriodicWork(
                WORK_REVIEW,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReviewReminderWorker>(Duration.ofDays(1))
                    .setInitialDelay(delayUntil(REVIEW_HOUR * 60))
                    .build(),
            )
        } else {
            manager.cancelUniqueWork(WORK_REVIEW)
        }

        manager.enqueueUniquePeriodicWork(
            WORK_MAINTENANCE,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(Duration.ofDays(1))
                .setInitialDelay(delayUntil(MAINTENANCE_HOUR * 60))
                // Чистка и пересчёт не должны будить устройство ради себя.
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build(),
        )
    }

    /** Сколько ждать до ближайшего наступления заданного времени суток. */
    private fun delayUntil(minuteOfDay: Int): Duration {
        val now = LocalDateTime.now()
        val target = now.toLocalDate()
            .atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
            .let { if (it.isAfter(now)) it else it.plusDays(1) }

        return Duration.between(now, target)
    }

    private const val REVIEW_HOUR = 19
    private const val MAINTENANCE_HOUR = 3
}
