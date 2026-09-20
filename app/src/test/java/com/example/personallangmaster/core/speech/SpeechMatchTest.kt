package com.example.personallangmaster.core.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Сверка произнесённого с эталоном.
 *
 * Распознаватель почти никогда не отдаёт слово ровно так, как оно записано в
 * карточке: добавляет артикли, меняет регистр, ставит точку. Придираться к этому
 * нельзя — проверяется произношение, а не диктант.
 */
class SpeechMatchTest {

    @Test
    fun `точное совпадение засчитывается`() {
        assertTrue(SpeechInput.matches("crowded", "crowded"))
    }

    @Test
    fun `регистр и знаки препинания не мешают`() {
        assertTrue(SpeechInput.matches("Crowded.", "crowded"))
        assertTrue(SpeechInput.matches("  CROWDED!  ", "crowded"))
    }

    @Test
    fun `эталон внутри распознанной фразы засчитывается`() {
        assertTrue(SpeechInput.matches("the beach was crowded", "crowded"))
        assertTrue(SpeechInput.matches("I said take off", "take off"))
    }

    @Test
    fun `лишние пробелы схлопываются`() {
        assertTrue(SpeechInput.matches("take    off", "take off"))
    }

    @Test
    fun `другое слово не засчитывается`() {
        assertFalse(SpeechInput.matches("crowd", "deadline"))
        assertFalse(SpeechInput.matches("sheep", "beach"))
    }

    @Test
    fun `пустой ответ не засчитывается`() {
        assertFalse(SpeechInput.matches("", "crowded"))
        assertFalse(SpeechInput.matches("   ", "crowded"))
        assertFalse(SpeechInput.matches("crowded", ""))
    }

    @Test
    fun `короткое слово внутри длинного эталона тоже принимается`() {
        // Ученик произнёс только ключевое слово из фразы — это всё ещё попытка.
        assertTrue(SpeechInput.matches("deadline", "meet the deadline"))
    }
}
