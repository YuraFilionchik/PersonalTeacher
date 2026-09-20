package com.example.personallangmaster.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Системная озвучка английского — для карточек словаря и тренажёра произношения.
 *
 * Здесь намеренно не используется голос Gemini: повторения должны работать
 * в метро без сети и не стоить ни цента. Живой голос остаётся для разговора.
 */
class TtsController(context: Context) {

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale.US)
                val missing = result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                _ready.value = !missing
                if (missing) Log.w(TAG, "Английский голос не установлен в системе")
            } else {
                Log.w(TAG, "TTS недоступен: статус $status")
            }
        }
    }

    /**
     * Произносит текст. [rate] — множитель скорости: 0.6 для медленного повтора,
     * которым ученик разбирает сложное слово по слогам.
     */
    fun speak(text: String, rate: Float = 1.0f) {
        val tts = engine ?: return
        if (text.isBlank()) return
        tts.setSpeechRate(rate.coerceIn(0.4f, 2.0f))
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun stop() {
        engine?.stop()
    }

    fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        _ready.value = false
    }

    private companion object {
        const val TAG = "TtsController"
    }
}
