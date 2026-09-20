package com.example.personallangmaster.core.audio

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Запись урока в WAV.
 *
 * Пишем обе стороны в один моно-файл на 16 кГц в порядке поступления: разговор
 * идёт по очереди, поэтому склейка звучит как настоящая беседа, а один файл
 * гораздо удобнее переслушивать, чем две дорожки. Речь тренера приходит на
 * 24 кГц и приводится к 16 кГц — терять качество не жалко, это архив для себя,
 * зато файл втрое меньше.
 */
class AudioFileStore(private val context: Context) {

    private var file: File? = null
    private var stream: java.io.BufferedOutputStream? = null
    private var bytesWritten = 0L
    private val lock = Any()

    val isRecording: Boolean get() = stream != null

    /** Позиция в записи — по ней карточка ошибки находит нужное место в уроке. */
    val positionMs: Long
        get() = synchronized(lock) { Pcm.durationMs(bytesWritten.toInt(), SAMPLE_RATE) }

    fun start(lessonId: Long): File? = synchronized(lock) {
        if (stream != null) return file

        return runCatching {
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            val target = File(directory, "lesson_$lessonId.wav")

            val output = java.io.BufferedOutputStream(target.outputStream())
            // Заголовок пишем заглушкой: реальные размеры известны только в конце.
            output.write(ByteArray(HEADER_SIZE))

            file = target
            stream = output
            bytesWritten = 0
            target
        }.onFailure { Log.w(TAG, "Не удалось начать запись: ${it.message}") }.getOrNull()
    }

    fun writeUser(pcm16k: ByteArray): Unit = write(pcm16k)

    fun writeTutor(pcm24k: ByteArray): Unit = write(downsample24to16(pcm24k))

    private fun write(pcm: ByteArray): Unit = synchronized(lock) {
        val output = stream ?: return
        runCatching {
            output.write(pcm)
            bytesWritten += pcm.size
        }.onFailure { Log.w(TAG, "Ошибка записи: ${it.message}") }
        return
    }

    /** Закрывает файл и проставляет размеры в заголовке. Возвращает путь или null. */
    fun finish(): String? = synchronized(lock) {
        val output = stream ?: return null
        val target = file

        runCatching {
            output.flush()
            output.close()
        }
        stream = null

        if (target == null || bytesWritten == 0L) {
            target?.delete()
            file = null
            return null
        }

        runCatching { writeHeader(target, bytesWritten) }
            .onFailure { Log.w(TAG, "Не удалось дописать заголовок: ${it.message}") }

        val path = target.absolutePath
        file = null
        return path
    }

    fun cancel() = synchronized(lock) {
        runCatching { stream?.close() }
        stream = null
        file?.delete()
        file = null
        bytesWritten = 0
    }

    /**
     * 24 кГц → 16 кГц: на каждые три исходных отсчёта приходится два итоговых.
     * Линейная интерполяция звучит заметно чище простого выбрасывания отсчётов.
     */
    private fun downsample24to16(pcm: ByteArray): ByteArray {
        val inputSamples = pcm.size / 2
        if (inputSamples < 2) return ByteArray(0)

        val outputSamples = (inputSamples * 2) / 3
        val result = ByteArray(outputSamples * 2)
        val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val output = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        for (index in 0 until outputSamples) {
            val position = index * 1.5
            val left = position.toInt()
            val right = (left + 1).coerceAtMost(inputSamples - 1)
            val weight = position - left

            val value = input.get(left) * (1 - weight) + input.get(right) * weight
            output.put(index, value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        return result
    }

    private fun writeHeader(target: File, dataSize: Long) {
        RandomAccessFile(target, "rw").use { raf ->
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8

            header.put("RIFF".toByteArray())
            header.putInt((36 + dataSize).toInt())
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)                                  // PCM
            header.putShort(CHANNELS.toShort())
            header.putInt(SAMPLE_RATE)
            header.putInt(byteRate)
            header.putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())
            header.putShort(BITS_PER_SAMPLE.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize.toInt())

            raf.seek(0)
            raf.write(header.array())
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
        private const val HEADER_SIZE = 44
        private const val DIRECTORY = "lessons"
        private const val TAG = "AudioFileStore"

        /** Сколько места занимает запись: примерно 1.9 МБ на минуту. */
        fun approximateSizePerMinuteBytes(): Long =
            SAMPLE_RATE.toLong() * CHANNELS * BITS_PER_SAMPLE / 8 * 60
    }
}
