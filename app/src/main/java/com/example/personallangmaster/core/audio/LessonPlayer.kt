package com.example.personallangmaster.core.audio

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Воспроизведение записи урока.
 *
 * Нужен ровно для одного сценария: услышать, как ты сам произнёс фразу,
 * по которой тренер сделал замечание. Поэтому здесь есть перемотка к месту
 * реплики и нет ничего лишнего вроде очереди или эквалайзера.
 */
class LessonPlayer {

    private var player: MediaPlayer? = null
    private var currentPath: String? = null

    /** Следит за точкой остановки фрагмента: MediaPlayer сам так не умеет. */
    private val handler = Handler(Looper.getMainLooper())
    private var stopAtMs: Long? = null
    private val stopWatcher = object : Runnable {
        override fun run() {
            val media = player ?: return
            val limit = stopAtMs ?: return
            val position = runCatching { media.currentPosition.toLong() }.getOrNull() ?: return
            if (position >= limit) pause() else handler.postDelayed(this, WATCH_INTERVAL_MS)
        }
    }

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    fun isAvailable(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).let { it.exists() && it.length() > MIN_SIZE_BYTES }

    /**
     * Играет с указанного места. [fromMs] = null — с начала.
     * [untilMs] — где остановиться; null — играть до конца записи.
     */
    fun play(path: String, fromMs: Long? = null, untilMs: Long? = null) {
        handler.removeCallbacks(stopWatcher)
        stopAtMs = untilMs
        runCatching {
            if (currentPath != path) {
                release()
                player = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    setOnCompletionListener { _playing.value = false }
                }
                currentPath = path
            }

            player?.let { media ->
                if (fromMs != null) media.seekTo(fromMs.toInt())
                media.start()
                _playing.value = true
                if (untilMs != null) handler.post(stopWatcher)
            }
        }.onFailure {
            Log.w(TAG, "Не удалось воспроизвести запись: ${it.message}")
            _playing.value = false
        }
    }

    fun pause() {
        handler.removeCallbacks(stopWatcher)
        stopAtMs = null
        runCatching {
            player?.takeIf { it.isPlaying }?.pause()
        }
        _playing.value = false
    }

    fun release() {
        handler.removeCallbacks(stopWatcher)
        stopAtMs = null
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        currentPath = null
        _playing.value = false
    }

    private companion object {
        const val TAG = "LessonPlayer"

        /** Пустой WAV — это только заголовок; такую запись показывать незачем. */
        const val MIN_SIZE_BYTES = 1024L

        const val WATCH_INTERVAL_MS = 100L
    }
}
