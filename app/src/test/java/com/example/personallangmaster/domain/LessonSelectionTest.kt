package com.example.personallangmaster.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Режим выбора нескольких уроков в истории. */
class LessonSelectionTest {

    @Test
    fun `по умолчанию режим выключен и никто не выбран`() {
        val selection = LessonSelection()

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun `долгое нажатие включает режим и сразу выбирает урок`() {
        val selection = LessonSelection().start(7)

        assertTrue(selection.active)
        assertEquals(setOf(7L), selection.ids)
    }

    @Test
    fun `повторный тап снимает выбор с урока`() {
        val selection = LessonSelection().start(7).toggle(8).toggle(8)

        assertEquals(setOf(7L), selection.ids)
    }

    @Test
    fun `когда снят последний выбор, режим выключается сам`() {
        val selection = LessonSelection().start(7).toggle(7)

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun `удалённые уроки перестают быть выбранными`() {
        val selection = LessonSelection().start(7).toggle(8).retain(setOf(8L, 9L))

        assertTrue(selection.active)
        assertEquals(setOf(8L), selection.ids)
    }

    @Test
    fun `когда исчезли все выбранные уроки, режим выключается`() {
        val selection = LessonSelection().start(7).retain(setOf(9L))

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }

    @Test
    fun `сброс выключает режим целиком`() {
        val selection = LessonSelection().start(7).toggle(8).clear()

        assertFalse(selection.active)
        assertEquals(0, selection.count)
    }
}
