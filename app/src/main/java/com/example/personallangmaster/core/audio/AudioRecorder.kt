package com.example.personallangmaster.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlin.concurrent.thread

/**
 * Захват микрофона в формате, который ждёт Live API: PCM 16 бит, моно, 16 кГц.
 *
 * Отдаёт чанки по 100 мс — такой размер держит задержку незаметной и не создаёт
 * лишнего трафика. Источник VOICE_COMMUNICATION включает системное эхоподавление:
 * без него тренер слышит сам себя через динамик и постоянно «перебивается».
 */
class AudioRecorder(
    private val sampleRate: Int = SAMPLE_RATE,
    private val chunkMs: Int = CHUNK_MS,
) {

    private var record: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    @Volatile
    private var running = false

    /**
     * Поток аудио-чанков. Запись идёт, пока подписчик слушает поток;
     * отмена подписки освобождает микрофон.
     *
     * Вызывающий обязан убедиться, что разрешение RECORD_AUDIO выдано.
     */
    @SuppressLint("MissingPermission")
    fun chunks(applyEffects: Boolean = true): Flow<ByteArray> = callbackFlow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            close(IllegalStateException("Микрофон недоступен: размер буфера $minBuffer"))
            return@callbackFlow
        }

        val chunkBytes = Pcm.bytesForMs(chunkMs, sampleRate)
        val bufferSize = maxOf(minBuffer, chunkBytes * BUFFER_CHUNKS)

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (error: Exception) {
            close(error)
            return@callbackFlow
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            close(IllegalStateException("AudioRecord не инициализирован"))
            return@callbackFlow
        }

        record = audioRecord
        if (applyEffects) attachEffects(audioRecord.audioSessionId)

        running = true
        audioRecord.startRecording()

        val worker = thread(name = "plm-audio-capture") {
            val buffer = ByteArray(chunkBytes)
            while (running) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    trySend(buffer.copyOf(read))
                } else if (read < 0) {
                    Log.w(TAG, "Ошибка чтения микрофона: $read")
                    break
                }
            }
        }

        awaitClose {
            running = false
            runCatching { worker.join(500) }
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            releaseEffects()
            record = null
        }
    }.flowOn(Dispatchers.IO)

    private fun attachEffects(sessionId: Int) {
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = runCatching {
                NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }.getOrNull()
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = runCatching {
                AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }.getOrNull()
        }
    }

    private fun releaseEffects() {
        runCatching { noiseSuppressor?.release() }
        runCatching { echoCanceler?.release() }
        noiseSuppressor = null
        echoCanceler = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_MS = 100
        private const val BUFFER_CHUNKS = 4
        private const val TAG = "AudioRecorder"
    }
}
