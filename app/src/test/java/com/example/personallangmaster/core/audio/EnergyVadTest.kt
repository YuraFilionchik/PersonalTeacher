package com.example.personallangmaster.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class EnergyVadTest {

    private fun generateSineWave(durationMs: Int, amplitudeRatio: Double): ByteArray {
        val sampleRate = 16000
        val frequencyHz = 440.0
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

    private fun createSilence(durationMs: Int): ByteArray {
        val sampleRate = 16000
        val numSamples = (durationMs * sampleRate) / 1000
        return ByteArray(numSamples * 2)
    }

    @Test
    fun `silence does not trigger speech start`() {
        val vad = EnergyVad()
        val silenceFrame = createSilence(150)
        
        val events = vad.process(silenceFrame)
        
        assertFalse(vad.isSpeaking)
        assertTrue(events.none { it is EnergyVad.Event.SpeechStart })
        assertTrue(events.any { it is EnergyVad.Event.Level }) // Level should always be emitted
    }

    @Test
    fun `loud noise triggers exactly one speech start`() {
        val vad = EnergyVad()
        // Генерируем 150мс громкого звука (больше minSpeechMs 120ms)
        val loudFrame1 = generateSineWave(150, 1.0) 
        val loudFrame2 = generateSineWave(100, 1.0)

        val events1 = vad.process(loudFrame1)
        assertTrue(vad.isSpeaking)
        assertEquals(1, events1.count { it is EnergyVad.Event.SpeechStart })

        val events2 = vad.process(loudFrame2)
        assertTrue(vad.isSpeaking)
        // Второе громкое сообщение не должно генерировать SpeechStart снова
        assertEquals(0, events2.count { it is EnergyVad.Event.SpeechStart })
    }

    @Test
    fun `short noise is ignored`() {
        val vad = EnergyVad(minSpeechMs = 120)
        // Шум 50мс (короче minSpeechMs)
        val shortNoise = generateSineWave(50, 1.0)
        
        val events = vad.process(shortNoise)
        
        assertFalse(vad.isSpeaking)
        assertTrue(events.none { it is EnergyVad.Event.SpeechStart })
    }

    @Test
    fun `pause shorter than hangover does not end speech`() {
        val vad = EnergyVad(silenceHangoverMs = 800)
        val loudFrame = generateSineWave(150, 1.0)
        val shortPause = createSilence(300) // 300ms < 800ms
        
        vad.process(loudFrame)
        assertTrue(vad.isSpeaking)
        
        val events = vad.process(shortPause)
        assertTrue(vad.isSpeaking)
        assertTrue(events.none { it is EnergyVad.Event.SpeechEnd })
    }

    @Test
    fun `pause longer than hangover ends speech`() {
        val vad = EnergyVad(silenceHangoverMs = 800)
        val loudFrame = generateSineWave(150, 1.0)
        val longPause = createSilence(900) // 900ms > 800ms
        
        vad.process(loudFrame)
        assertTrue(vad.isSpeaking)
        
        val events = vad.process(longPause)
        assertFalse(vad.isSpeaking)
        assertEquals(1, events.count { it is EnergyVad.Event.SpeechEnd })
    }
    
    @Test
    fun `reset clears state`() {
        val vad = EnergyVad()
        val loudFrame = generateSineWave(150, 1.0)
        
        vad.process(loudFrame)
        assertTrue(vad.isSpeaking)
        
        vad.reset()
        assertFalse(vad.isSpeaking)
    }
}