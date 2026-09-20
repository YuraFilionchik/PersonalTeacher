package com.example.personallangmaster.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sin

class PcmTest {

    private fun generateSineWave(frequencyHz: Double, durationMs: Int, sampleRate: Int, amplitudeRatio: Double = 1.0): ByteArray {
        val numSamples = (durationMs * sampleRate) / 1000
        val pcm = ByteArray(numSamples * 2)
        val maxAmplitude = (32767 * amplitudeRatio).toInt()
        
        for (i in 0 until numSamples) {
            val time = i.toDouble() / sampleRate
            val sampleValue = (sin(2.0 * Math.PI * frequencyHz * time) * maxAmplitude).toInt()
            pcm[i * 2] = (sampleValue and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sampleValue shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun createSilence(durationMs: Int, sampleRate: Int): ByteArray {
        val numSamples = (durationMs * sampleRate) / 1000
        return ByteArray(numSamples * 2)
    }

    @Test
    fun `durationMs returns correct duration`() {
        val sampleRate = 16000
        val pcm100ms = createSilence(100, sampleRate)
        assertEquals(100L, Pcm.durationMs(pcm100ms.size, sampleRate))
    }

    @Test
    fun `bytesForMs returns correct byte count`() {
        val sampleRate = 16000
        assertEquals(3200, Pcm.bytesForMs(100, sampleRate)) // 16000 * 0.1 * 2
    }

    @Test
    fun `rms and dbfs of silence`() {
        val silence = createSilence(100, 16000)
        assertEquals(0.0, Pcm.rms(silence), 0.001)
        assertEquals(-100.0, Pcm.dbfs(silence), 0.001)
        assertEquals(0.0, Pcm.peak(silence), 0.001)
    }

    @Test
    fun `rms and dbfs of full amplitude sine wave`() {
        val sineWave = generateSineWave(440.0, 100, 16000, 1.0)
        // RMS синусоиды с пиком 1.0 = 1/sqrt(2) = ~0.707
        val rms = Pcm.rms(sineWave)
        assertEquals(0.707, rms, 0.01)
        val dbfs = Pcm.dbfs(sineWave)
        assertEquals(-3.0, dbfs, 0.5) // ~-3dBFS для полной синусоиды
        val peak = Pcm.peak(sineWave)
        assertEquals(1.0, peak, 0.01)
    }
    
    @Test
    fun `rms and dbfs of half amplitude sine wave`() {
        val sineWave = generateSineWave(440.0, 100, 16000, 0.5)
        val rms = Pcm.rms(sineWave)
        assertEquals(0.353, rms, 0.01)
        val peak = Pcm.peak(sineWave)
        assertEquals(0.5, peak, 0.01)
    }

    @Test
    fun `chunk splits byte array correctly`() {
        val data = ByteArray(10) { it.toByte() }
        val chunks = Pcm.chunk(data, 4)
        assertEquals(3, chunks.size)
        assertEquals(4, chunks[0].size)
        assertEquals(4, chunks[1].size)
        assertEquals(2, chunks[2].size)
        
        val reconstructed = chunks[0] + chunks[1] + chunks[2]
        assertArrayEquals(data, reconstructed)
    }

    @Test
    fun `applyGain correctly scales and limits values`() {
        val sineWave = generateSineWave(440.0, 10, 16000, 0.5)
        
        // Double gain
        val amplified = Pcm.applyGain(sineWave, 2.0)
        assertEquals(1.0, Pcm.peak(amplified), 0.01)
        
        // Quadruple gain (should clip)
        val clipped = Pcm.applyGain(sineWave, 4.0)
        assertEquals(1.0, Pcm.peak(clipped), 0.01) // Not more than 1.0
        
        // Half gain
        val attenuated = Pcm.applyGain(sineWave, 0.5)
        assertEquals(0.25, Pcm.peak(attenuated), 0.01)
    }

    @Test
    fun `downsampleForWaveform returns correct number of buckets`() {
        val sineWave = generateSineWave(440.0, 100, 16000, 1.0)
        val buckets = Pcm.downsampleForWaveform(sineWave, 50)
        assertEquals(50, buckets.size)
        
        // Peak for a 440hz wave over 2ms bucket (16000hz) will hit the maximum
        for (i in 10 until 40) { // Check middle buckets to avoid edge cases of the wave
             assertEquals(1.0f, buckets[i], 0.1f)
        }
    }
}
