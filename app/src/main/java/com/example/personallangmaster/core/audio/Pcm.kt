package com.example.personallangmaster.core.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Утилиты для работы с аудио-данными в формате PCM 16-bit, Mono, Little-Endian.
 */
object Pcm {

    /** Возвращает среднеквадратичное значение (RMS) нормализованное от 0.0 до 1.0 */
    fun rms(pcm: ByteArray): Double {
        if (pcm.isEmpty()) return 0.0
        var sumSquare = 0.0
        val sampleCount = pcm.size / 2
        for (i in pcm.indices step 2) {
            if (i + 1 >= pcm.size) break
            val sample = getSample(pcm, i)
            val normalized = sample / 32768.0
            sumSquare += normalized * normalized
        }
        return sqrt(sumSquare / sampleCount)
    }

    /** Возвращает громкость в децибелах полной шкалы (dBFS). От -100.0 (тишина) до 0.0 (максимум) */
    fun dbfs(pcm: ByteArray): Double {
        val rmsValue = rms(pcm)
        if (rmsValue <= 0.00001) return -100.0
        return max(-100.0, 20.0 * log10(rmsValue))
    }

    /** Возвращает максимальное абсолютное значение амплитуды (пик) нормализованное от 0.0 до 1.0 */
    fun peak(pcm: ByteArray): Double {
        if (pcm.isEmpty()) return 0.0
        var maxAbs = 0
        for (i in pcm.indices step 2) {
            if (i + 1 >= pcm.size) break
            val sample = abs(getSample(pcm, i).toInt())
            if (sample > maxAbs) {
                maxAbs = sample
            }
        }
        return min(1.0, maxAbs / 32768.0)
    }

    /** Вычисляет длительность в миллисекундах по количеству байт и частоте дискретизации */
    fun durationMs(byteCount: Int, sampleRate: Int): Long {
        if (sampleRate <= 0) return 0L
        val sampleCount = byteCount / 2
        return (sampleCount.toLong() * 1000L) / sampleRate.toLong()
    }

    /** Вычисляет количество байт (кратное 2) для заданной длительности в миллисекундах */
    fun bytesForMs(ms: Int, sampleRate: Int): Int {
        if (sampleRate <= 0 || ms <= 0) return 0
        val sampleCount = (ms.toLong() * sampleRate.toLong()) / 1000L
        return (sampleCount * 2L).toInt()
    }

    /** Разбивает массив байт на куски заданного размера. Последний кусок может быть меньше chunkBytes. */
    fun chunk(pcm: ByteArray, chunkBytes: Int): List<ByteArray> {
        if (chunkBytes <= 0 || pcm.isEmpty()) return emptyList()
        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < pcm.size) {
            val length = min(chunkBytes, pcm.size - offset)
            val chunk = ByteArray(length)
            System.arraycopy(pcm, offset, chunk, 0, length)
            result.add(chunk)
            offset += length
        }
        return result
    }

    /**
     * Применяет усиление/ослабление к сигналу с защитой от клиппинга.
     * Значения ограничиваются диапазоном [-32768, 32767].
     */
    fun applyGain(pcm: ByteArray, gain: Double): ByteArray {
        if (pcm.isEmpty() || gain == 1.0) return pcm.clone()
        val result = ByteArray(pcm.size)
        for (i in pcm.indices step 2) {
            if (i + 1 >= pcm.size) {
                if (i < pcm.size) result[i] = pcm[i]
                break
            }
            val sample = getSample(pcm, i)
            var amplified = (sample * gain).toInt()
            amplified = min(32767, max(-32768, amplified))
            result[i] = (amplified and 0xFF).toByte()
            result[i + 1] = ((amplified shr 8) and 0xFF).toByte()
        }
        return result
    }

    /** Конвертирует PCM 16-bit в массив Float нормализованных от -1.0 до 1.0 */
    fun toFloatSamples(pcm: ByteArray): FloatArray {
        val sampleCount = pcm.size / 2
        val result = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val sample = getSample(pcm, i * 2)
            result[i] = sample / 32768f
        }
        return result
    }

    /**
     * Возвращает массив амплитуд (пиковых значений) для отрисовки волны,
     * разделяя аудио на заданное количество корзин (buckets).
     */
    fun downsampleForWaveform(pcm: ByteArray, buckets: Int): FloatArray {
        if (buckets <= 0) return FloatArray(0)
        val result = FloatArray(buckets)
        if (pcm.isEmpty()) return result

        val sampleCount = pcm.size / 2
        
        if (sampleCount == 0) return result
        
        val samplesPerBucket = max(1.0, sampleCount.toDouble() / buckets)

        for (b in 0 until buckets) {
            val startSample = (b * samplesPerBucket).toInt()
            val endSample = min(sampleCount, ((b + 1) * samplesPerBucket).toInt())
            
            var maxAbs = 0
            for (i in startSample until endSample) {
                val sample = abs(getSample(pcm, i * 2).toInt())
                if (sample > maxAbs) {
                    maxAbs = sample
                }
            }
            result[b] = min(1.0f, maxAbs / 32768f)
        }
        return result
    }

    /** Вспомогательная функция для чтения 16-битного семпла (little-endian) по индексу байта */
    private fun getSample(pcm: ByteArray, byteIndex: Int): Short {
        val lo = pcm[byteIndex].toInt() and 0xFF
        val hi = pcm[byteIndex + 1].toInt()
        return ((hi shl 8) or lo).toShort()
    }
}
