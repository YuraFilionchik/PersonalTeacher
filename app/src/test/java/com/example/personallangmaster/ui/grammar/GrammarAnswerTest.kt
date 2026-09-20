package com.example.personallangmaster.ui.grammar

import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.repo.ContentRepository
import com.example.personallangmaster.data.repo.scenarioRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Мелкие правила, от которых зависит, ощущается ли практика справедливой:
 * что считать верным ответом, что считать тем же звуком и какие сценарии
 * предлагать по уровню.
 */
class GrammarAnswerTest {

    // --- Проверка ответа на упражнение ---

    @Test
    fun `регистр и точка в конце не делают ответ неверным`() {
        assertTrue(GrammarViewModel.isCorrect("Went", "went"))
        assertTrue(GrammarViewModel.isCorrect("went.", "went"))
        assertTrue(GrammarViewModel.isCorrect("  WENT  ", "went"))
    }

    @Test
    fun `лишние пробелы внутри ответа схлопываются`() {
        assertTrue(GrammarViewModel.isCorrect("I  have   been", "I have been"))
    }

    @Test
    fun `апостроф остаётся значимым`() {
        assertTrue(GrammarViewModel.isCorrect("haven't", "haven't"))
        assertFalse(GrammarViewModel.isCorrect("have not", "haven't"))
    }

    @Test
    fun `другое слово не засчитывается`() {
        assertFalse(GrammarViewModel.isCorrect("go", "went"))
        assertFalse(GrammarViewModel.isCorrect("", "went"))
        assertFalse(GrammarViewModel.isCorrect("   ", "went"))
    }

    // --- Запись звука ---

    @Test
    fun `звук в разных записях считается одним и тем же`() {
        val expected = "θ"
        assertEquals(expected, ContentRepository.normalizePhoneme("θ"))
        assertEquals(expected, ContentRepository.normalizePhoneme("/θ/"))
        assertEquals(expected, ContentRepository.normalizePhoneme("  /θ/  "))
    }

    @Test
    fun `пустая запись звука остаётся пустой`() {
        assertEquals("", ContentRepository.normalizePhoneme(""))
        assertEquals("", ContentRepository.normalizePhoneme("//"))
        assertEquals("", ContentRepository.normalizePhoneme("   "))
    }

    // --- Подбор сценариев по уровню ---

    @Test
    fun `сценарии предлагаются на ступень вокруг текущего уровня`() {
        val (from, to) = scenarioRange(Cefr.B1)
        assertEquals(Cefr.A2, from)
        assertEquals(Cefr.B2, to)
    }

    @Test
    fun `на краях шкалы диапазон не выходит за границы`() {
        val (lowFrom, lowTo) = scenarioRange(Cefr.A1)
        assertEquals(Cefr.A1, lowFrom)
        assertEquals(Cefr.A2, lowTo)

        val (highFrom, highTo) = scenarioRange(Cefr.C1)
        assertEquals(Cefr.B2, highFrom)
        assertEquals(Cefr.C1, highTo)
    }
}
