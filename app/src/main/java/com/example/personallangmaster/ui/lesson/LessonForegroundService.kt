package com.example.personallangmaster.ui.lesson

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.personallangmaster.MainActivity
import com.example.personallangmaster.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Держит урок живым, пока экран погашен.
 *
 * Android с 9-й версии обрывает запись микрофона у фонового приложения, поэтому
 * без переднего сервиса урок прерывался бы каждый раз, когда телефон кладут на стол.
 * Сама сессия живёт в [LessonViewModel] — сервис только удерживает процесс
 * и показывает уведомление с таймером и кнопкой «Завершить».
 *
 * Сервис гаснет сам, как только [lessonRunning] падает в false: так уведомление
 * не переживает урок, даже если экран урока к этому моменту уже закрыт.
 */
class LessonForegroundService : Service() {

    private var scope: CoroutineScope? = null
    private var ticker: Job? = null
    private var startedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        // Урока нет — нечего и показывать. Сюда же попадает нажатие «Завершить»:
        // сервис гасим сразу, не дожидаясь, пока модель урока отработает запрос.
        if (intent?.action == ACTION_STOP || !lessonRunning.value) {
            if (intent?.action == ACTION_STOP) {
                stopRequests.tryEmit(Unit)
            } else {
                // Запущен через startForegroundService: без startForeground система
                // уронит приложение, даже если сервис тут же гаснет.
                startInForeground(buildNotification(0))
            }
            shutDown()
            return START_NOT_STICKY
        }

        // Повторный старт при уже идущем уроке — не новый урок: таймер не сбрасываем
        // и второй не запускаем, иначе старый продолжал бы обновлять уведомление вечно.
        if (ticker?.isActive != true) startedAt = System.currentTimeMillis()
        startInForeground(buildNotification(elapsedSeconds()))

        if (ticker?.isActive != true) {
            // Главный поток: тикер и остановка идут строго по очереди, и тикер
            // не может опубликовать уведомление сразу после того, как его сняли.
            val serviceScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)
                .also { scope = it }
            ticker = serviceScope.launch {
                while (isActive && lessonRunning.value) {
                    delay(1_000)
                    if (!lessonRunning.value) break
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(elapsedSeconds()))
                }
            }
            serviceScope.launch {
                lessonRunning.first { !it }
                shutDown()
            }
        }

        // Процесс убит — урок вместе с ним: воскрешать пустое уведомление незачем.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope?.cancel()
        scope = null
        notificationManager().cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun shutDown() {
        ticker?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager().cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun elapsedSeconds(): Int =
        ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(elapsedSeconds: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LessonForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val elapsed = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.nav_lesson))
            .setContentText("Идёт урок · $elapsed")
            .setContentIntent(openIntent)
            .addAction(0, "Завершить", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Урок",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Показывает, что идёт живой урок"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "lesson"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.example.personallangmaster.STOP_LESSON"

        /** Нажатие «Завершить» в уведомлении: модель урока слушает этот поток. */
        private val stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val stopRequestFlow = stopRequests.asSharedFlow()

        /**
         * Идёт ли урок. Выставляет модель урока — она знает это точно и
         * сбрасывает флаг даже тогда, когда её очищают вместе с экраном.
         */
        val lessonRunning = MutableStateFlow(false)

        fun start(context: Context) {
            lessonRunning.value = true
            val intent = Intent(context, LessonForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            lessonRunning.value = false
            context.stopService(Intent(context, LessonForegroundService::class.java))
        }
    }
}
