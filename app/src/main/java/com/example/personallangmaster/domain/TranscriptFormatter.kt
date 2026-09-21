package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.data.db.entity.TurnEntity

/** Разговор в читаемый текст: для экрана разбора и для отправки в заметки. */
object TranscriptFormatter {

    fun plain(turns: List<TurnEntity>): String = turns
        // Распознавание иногда отдаёт пустую реплику; в тексте от неё остаётся
        // только дырка, поэтому такие выбрасываем.
        .filter { it.text.isNotBlank() }
        .joinToString("\n") { turn ->
            val who = if (turn.speaker == Speaker.USER) "Я" else "Тренер"
            "$who: ${turn.text.trim()}"
        }
}
