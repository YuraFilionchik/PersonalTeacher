package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.prefs.ProgressionPace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Уровень — самая заметная для ученика цифра в приложении, и дёргать её нельзя.
 * Тесты фиксируют осторожность: рост только по серии, падение ещё медленнее.
 */
class LevelProgressionTest {

    @Test
    fun `одна удачная оценка не повышает уровень`() {
        val next = LevelProgression.next(
            current = Cefr.A2,
            estimates = listOf(Cefr.B1, Cefr.A2, Cefr.A2),
            pace = ProgressionPace.NORMAL,
        )
        assertNull(next)
    }

    @Test
    fun `три оценки подряд выше текущего повышают уровень на один шаг`() {
        val next = LevelProgression.next(
            current = Cefr.A2,
            estimates = listOf(Cefr.B1, Cefr.B1, Cefr.B2, Cefr.A2),
            pace = ProgressionPace.NORMAL,
        )
        assertEquals(Cefr.B1, next)
    }

    @Test
    fun `уровень не перепрыгивает через ступень`() {
        val next = LevelProgression.next(
            current = Cefr.A1,
            estimates = listOf(Cefr.C1, Cefr.C1, Cefr.C1),
            pace = ProgressionPace.NORMAL,
        )
        assertEquals("рост всегда на одну ступень", Cefr.A2, next)
    }

    @Test
    fun `агрессивный темп повышает быстрее`() {
        val estimates = listOf(Cefr.B1, Cefr.B1, Cefr.A2)

        assertEquals(Cefr.B1, LevelProgression.next(Cefr.A2, estimates, ProgressionPace.AGGRESSIVE))
        assertNull(LevelProgression.next(Cefr.A2, estimates, ProgressionPace.NORMAL))
    }

    @Test
    fun `консервативный темп требует четырёх оценок`() {
        val three = listOf(Cefr.B1, Cefr.B1, Cefr.B1)
        val four = listOf(Cefr.B1, Cefr.B1, Cefr.B1, Cefr.B1)

        assertNull(LevelProgression.next(Cefr.A2, three, ProgressionPace.CONSERVATIVE))
        assertEquals(Cefr.B1, LevelProgression.next(Cefr.A2, four, ProgressionPace.CONSERVATIVE))
    }

    @Test
    fun `понижение требует на одну оценку больше, чем повышение`() {
        val three = listOf(Cefr.A2, Cefr.A2, Cefr.A2)
        val four = listOf(Cefr.A2, Cefr.A2, Cefr.A2, Cefr.A2)

        assertNull("трёх слабых уроков мало для понижения",
            LevelProgression.next(Cefr.B1, three, ProgressionPace.NORMAL))
        assertEquals(Cefr.A2, LevelProgression.next(Cefr.B1, four, ProgressionPace.NORMAL))
    }

    @Test
    fun `смешанные оценки ничего не меняют`() {
        val next = LevelProgression.next(
            current = Cefr.B1,
            estimates = listOf(Cefr.B2, Cefr.A2, Cefr.B2, Cefr.B1),
            pace = ProgressionPace.NORMAL,
        )
        assertNull(next)
    }

    @Test
    fun `пустая история не меняет уровень`() {
        assertNull(LevelProgression.next(Cefr.A2, emptyList(), ProgressionPace.NORMAL))
    }

    @Test
    fun `на краях шкалы уровень не выходит за границы`() {
        val top = listOf(Cefr.C1, Cefr.C1, Cefr.C1, Cefr.C1)
        assertNull("выше C1 в шкале ничего нет",
            LevelProgression.next(Cefr.C1, top, ProgressionPace.NORMAL))

        val bottom = listOf(Cefr.A1, Cefr.A1, Cefr.A1, Cefr.A1, Cefr.A1)
        assertNull("ниже A1 опускаться некуда",
            LevelProgression.next(Cefr.A1, bottom, ProgressionPace.NORMAL))
    }
}
