package com.example.personallangmaster.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.personallangmaster.MainActivity
import com.example.personallangmaster.R

/**
 * Напоминания приложения.
 *
 * Канал один и тихий: напоминание о занятии не должно выглядеть срочнее,
 * чем сообщение от человека.
 */
object Notifications {

    const val CHANNEL_REMINDERS = "reminders"

    const val ACTION_START_REVIEW = "com.example.personallangmaster.action.START_REVIEW"

    private const val ID_DAILY = 101
    private const val ID_REVIEW = 102

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Напоминания",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Напоминания о занятии и о карточках на повторение"
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun showDailyReminder(context: Context, minutesLeft: Int, streak: Int) {
        val text = when {
            streak > 1 -> "Серия $streak дн. — осталось $minutesLeft мин до цели"
            else -> "Осталось $minutesLeft мин до цели на сегодня"
        }
        show(context, ID_DAILY, "Время поговорить", text)
    }

    fun showReviewReminder(context: Context, dueCards: Int) {
        show(
            context,
            ID_REVIEW,
            "Карточки ждут",
            "Слов на повторение: $dueCards — это пять минут",
            action = ACTION_START_REVIEW
        )
    }

    private fun show(context: Context, id: Int, title: String, text: String, action: String? = null) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            if (action != null) {
                this.action = action
            }
        }

        val intent = PendingIntent.getActivity(
            context,
            id,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Без разрешения уведомление просто не показываем — молча, без падений. */
    fun canNotify(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
}
