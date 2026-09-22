package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.data.db.entity.TurnEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Транскрипт, который человек читает на экране и отправляет себе в заметки. */
class TranscriptFormatterTest {

    private fun turn(index: Int, speaker: Speaker, text: String) =
        TurnEntity(lessonId = 1, index = index, speaker = speaker, text = text)

    @Test
    fun `реплики подписаны по-русски и идут по порядку`() {
        val text = TranscriptFormatter.plain(
            listOf(
                turn(0, Speaker.TUTOR, "How was your day?"),
                turn(1, Speaker.USER, "It was fine."),
            )
        )

        assertEquals("Тренер: How was your day?\nЯ: It was fine.", text)
    }

    @Test
    fun `пустой транскрипт даёт пустую строку, а не мусор`() {
        assertEquals("", TranscriptFormatter.plain(emptyList()))
    }

    @Test
    fun `пустые реплики не оставляют пустых строк`() {
        val text = TranscriptFormatter.plain(
            listOf(
                turn(0, Speaker.USER, "Hello"),
                turn(1, Speaker.TUTOR, "   "),
                turn(2, Speaker.USER, "Bye"),
            )
        )

        assertEquals("Я: Hello\nЯ: Bye", text)
    }
}
