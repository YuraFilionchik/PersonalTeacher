package com.example.personallangmaster.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Воспроизведение речи тренера: PCM 16 бит, моно, 24 кГц.
 *
 * Чанки складываются в очередь и играются подряд. Ключевая деталь — [flush]:
 * когда ученик перебивает, буфер нужно сбросить мгновенно, иначе тренер ещё
 * несколько секунд договаривает фразу, которую уже никто не слушает.
 */
class AudioPlayer(
    private val sampleRate: Int = SAMPLE_RATE,
) {

    /**
     * Куда выводить речь тренера.
     *
     * MEDIA звучит заметно громче: он идёт по медиа-потоку, громкость которого
     * пользователь и крутит кнопками. VOICE_COMMUNICATION нужен только в hands-free,
     * где микрофон открыт постоянно и аппаратное эхоподавление обязано слышать,
     * что именно проигрывается, — иначе тренер перебивает сам себя.
     */
    enum class Output { MEDIA, VOICE_COMMUNICATION }

    private var track: AudioTrack? = null
    private var scope: CoroutineScope? = null
    private var pump: Job? = null

    private var queue = newQueue()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Текущий уровень речи тренера в dBFS — для визуализации волны. */
    private val _levelDbfs = MutableStateFlow(SILENCE_DB)
    val levelDbfs: StateFlow<Double> = _levelDbfs.asStateFlow()

    fun start(volume: Float = 1.0f, output: Output = Output.MEDIA) {
        if (track != null) return

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioTrack недоступен: размер буфера $minBuffer")
            return
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        when (output) {
                            Output.MEDIA -> AudioAttributes.USAGE_MEDIA
                            Output.VOICE_COMMUNICATION -> AudioAttributes.USAGE_VOICE_COMMUNICATION
                        }
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * BUFFER_FACTOR)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.setVolume(volume.coerceIn(0f, 1f))
        audioTrack.play()
        track = audioTrack

        queue = newQueue()
        val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = playerScope
        pump = playerScope.launch {
            while (isActive) {
                val chunk = queue.receive()
                val current = track ?: break
                _isPlaying.value = true
                _levelDbfs.value = Pcm.dbfs(chunk)
                var offset = 0
                while (offset < chunk.size && isActive) {
                    val written = current.write(chunk, offset, chunk.size - offset)
                    if (written <= 0) break
                    offset += written
                }
                if (queue.isEmpty) {
                    _isPlaying.value = false
                    _levelDbfs.value = SILENCE_DB
                }
            }
        }
    }

    /** Ставит чанк в очередь воспроизведения. */
    fun enqueue(pcm: ByteArray) {
        if (pcm.isEmpty()) return
        queue.trySend(pcm)
    }

    /**
     * Мгновенно прекращает воспроизведение и выбрасывает всё, что не успело прозвучать.
     * Вызывается при перебивании и при `interrupted` от сервера.
     */
    fun flush() {
        val current = track ?: return
        // Пересоздаём очередь: так гарантированно уходят уже принятые чанки.
        queue.close()
        queue = newQueue()
        runCatching {
            current.pause()
            current.flush()
            current.play()
        }
        _isPlaying.value = false
        _levelDbfs.value = SILENCE_DB
    }

    fun setVolume(volume: Float) {
        track?.setVolume(volume.coerceIn(0f, 1f))
    }

    fun stop() {
        pump?.cancel()
        pump = null
        scope?.cancel()
        scope = null
        queue.close()
        runCatching {
            track?.pause()
            track?.flush()
            track?.stop()
            track?.release()
        }
        track = null
        _isPlaying.value = false
        _levelDbfs.value = SILENCE_DB
    }

    private fun newQueue(): Channel<ByteArray> = Channel(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    companion object {
        const val SAMPLE_RATE = 24_000
        private const val BUFFER_FACTOR = 4
        private const val QUEUE_CAPACITY = 256
        private const val SILENCE_DB = -100.0
        private const val TAG = "AudioPlayer"
    }
}
