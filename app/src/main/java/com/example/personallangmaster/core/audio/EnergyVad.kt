package com.example.personallangmaster.core.audio

/**
 * Энергетический Voice Activity Detector (VAD).
 * Определяет наличие речи на основе порога громкости сигнала в dBFS и гистерезиса времени.
 */
class EnergyVad(
    val sampleRate: Int = 16000,
    val startThresholdDb: Double = -38.0,
    val stopThresholdDb: Double = -45.0,
    val minSpeechMs: Int = 120,
    val silenceHangoverMs: Int = 800,
) {
    sealed interface Event {
        object SpeechStart : Event
        object SpeechEnd : Event
        data class Level(val dbfs: Double) : Event
    }

    var isSpeaking: Boolean = false
        private set

    private var speechFramesTimeMs = 0L
    private var silenceFramesTimeMs = 0L

    /**
     * Обрабатывает кадр аудио и возвращает список событий, которые произошли.
     */
    fun process(frame: ByteArray): List<Event> {
        val events = mutableListOf<Event>()
        if (frame.isEmpty()) return events

        val dbfs = Pcm.dbfs(frame)
        events.add(Event.Level(dbfs))
        
        val frameDurationMs = Pcm.durationMs(frame.size, sampleRate)

        if (dbfs >= startThresholdDb) {
            // Сигнал громкий - может быть началом или продолжением речи
            silenceFramesTimeMs = 0L // Сбрасываем счетчик тишины
            if (!isSpeaking) {
                speechFramesTimeMs += frameDurationMs
                if (speechFramesTimeMs >= minSpeechMs) {
                    isSpeaking = true
                    events.add(Event.SpeechStart)
                }
            }
        } else if (dbfs <= stopThresholdDb) {
            // Сигнал тихий - может быть тишина или продолжение паузы
            speechFramesTimeMs = 0L // Сбрасываем счетчик громкости
            if (isSpeaking) {
                silenceFramesTimeMs += frameDurationMs
                if (silenceFramesTimeMs >= silenceHangoverMs) {
                    isSpeaking = false
                    events.add(Event.SpeechEnd)
                }
            }
        } else {
            // Сигнал в "серой зоне" между startThresholdDb и stopThresholdDb
            // Ничего не меняем, сохраняем текущее состояние. 
            // Но счетчики времени не сбрасываем, чтобы резкие всплески или провалы не ломали логику.
            if (isSpeaking) {
                silenceFramesTimeMs += frameDurationMs
                 if (silenceFramesTimeMs >= silenceHangoverMs) {
                    isSpeaking = false
                    events.add(Event.SpeechEnd)
                }
            } else {
                speechFramesTimeMs += frameDurationMs
                if (speechFramesTimeMs >= minSpeechMs) {
                    isSpeaking = true
                    events.add(Event.SpeechStart)
                }
            }
        }

        return events
    }

    /**
     * Сбрасывает состояние VAD
     */
    fun reset() {
        isSpeaking = false
        speechFramesTimeMs = 0L
        silenceFramesTimeMs = 0L
    }
}
