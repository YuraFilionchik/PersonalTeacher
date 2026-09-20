package com.example.personallangmaster.core.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/** Что происходит с распознаванием произнесённой фразы. */
sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Failed(val reason: String) : SpeechEvent
}

/**
 * Системное распознавание речи для режима «произнеси слово».
 *
 * Бесплатно и работает офлайн, когда в системе стоит языковой пакет, —
 * именно поэтому проверка произношения в карточках не ходит в Gemini.
 * Сравнение с эталоном грубое: важно, что ученик произнёс узнаваемо,
 * а не совпало ли слово побуквенно.
 */
class SpeechInput(private val context: Context) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(languageTag: String = "en-US"): Flow<SpeechEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(SpeechEvent.Failed("Распознавание речи недоступно на этом устройстве"))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Офлайн-режим: карточки должны работать без сети.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechEvent.Ready)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstResult()?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val text = results.firstResult()
                if (text.isNullOrBlank()) {
                    trySend(SpeechEvent.Failed("Не расслышал"))
                } else {
                    trySend(SpeechEvent.Final(text))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(describe(error)))
                close()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(intent)

        awaitClose {
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.destroy() }
        }
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Проблема с микрофоном"
        SpeechRecognizer.ERROR_CLIENT -> "Распознавание прервано"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Нет сети, а офлайн-пакет английского не установлен"
        SpeechRecognizer.ERROR_NO_MATCH -> "Не расслышал"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание занято, попробуйте ещё раз"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Тишина: ничего не услышал"
        else -> "Ошибка распознавания ($error)"
    }

    companion object {
        /**
         * Грубое сравнение произнесённого с эталоном.
         *
         * Распознаватель часто путает артикли и окончания, поэтому сравниваем
         * по нормализованному виду и допускаем, что эталон прозвучал внутри фразы.
         */
        fun matches(spoken: String, expected: String): Boolean {
            val said = normalize(spoken)
            val target = normalize(expected)
            if (said.isEmpty() || target.isEmpty()) return false
            return said == target || said.contains(target) || target.contains(said)
        }

        private fun normalize(value: String): String = value
            .lowercase(Locale.US)
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
