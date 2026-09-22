package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.data.db.entity.TurnEntity

/** Разговор в читаемый текст: для экрана разбора и для отправки в заметки. */
object TranscriptFormatter {

    /**
     * Реплики без пустых: распознавание иногда отдаёт пустую реплику, и от неё
     * остаётся только дырка — что в тексте, что на экране. Правило одно на оба
     * места, чтобы счётчик реплик и отправленный текст не расходились.
     */
    fun nonBlank(turns: List<TurnEntity>): List<TurnEntity> = turns.filter { it.text.isNotBlank() }

    fun plain(turns: List<TurnEntity>): String = nonBlank(turns)
        .joinToString("\n") { turn ->
            val who = if (turn.speaker == Speaker.USER) "Я" else "Тренер"
            "$who: ${turn.text.trim()}"
        }
}
