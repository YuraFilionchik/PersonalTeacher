package com.example.personallangmaster.core.audio

import android.media.MediaPlayer
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

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    fun isAvailable(path: String?): Boolean =
        !path.isNullOrBlank() && File(path).let { it.exists() && it.length() > MIN_SIZE_BYTES }

    /** Играет с указанного места. [fromMs] = null — с начала. */
    fun play(path: String, fromMs: Long? = null) {
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
            }
        }.onFailure {
            Log.w(TAG, "Не удалось воспроизвести запись: ${it.message}")
            _playing.value = false
        }
    }

    fun pause() {
        runCatching {
            player?.takeIf { it.isPlaying }?.pause()
        }
        _playing.value = false
    }

    fun release() {
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
    }
}
