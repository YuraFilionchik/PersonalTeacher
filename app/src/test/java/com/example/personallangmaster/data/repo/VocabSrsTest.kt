package com.example.personallangmaster.data.repo

import com.example.personallangmaster.core.srs.ReviewGrade
import com.example.personallangmaster.core.srs.Sm2Scheduler
import com.example.personallangmaster.core.srs.SrsState
import com.example.personallangmaster.data.db.VocabSource
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Мост между карточкой в базе и планировщиком.
 *
 * Ошибка здесь не падает и не видна в интерфейсе — просто слова начинают
 * всплывать не в срок, и заметить это можно лишь через недели.
 */
class VocabSrsTest {

    private val today = 20_000L

    private fun item(
        state: VocabState = VocabState.NEW,
        ease: Double = 2.5,
        interval: Int = 0,
        repetitions: Int = 0,
        lapses: Int = 0,
        due: Long = 20_000L,
    ) = VocabItemEntity(
        id = 1,
        profileId = 1,
        term = "crowded",
        translationRu = "переполненный",
        source = VocabSource.LESSON,
        ease = ease,
        intervalDays = interval,
        repetitions = repetitions,
        lapses = lapses,
        state = state,
        dueAtEpochDay = due,
        createdAt = 0,
    )

    @Test
    fun `перенос в карточку планировщика сохраняет все поля`() {
        val entity = item(
            state = VocabState.REVIEW,
            ease = 2.3,
            interval = 12,
            repetitions = 4,
            lapses = 1,
            due = 20_012L,
        )

        val card = VocabSrs.toCard(entity)

        assertEquals(2.3, card.ease, 0.0001)
        assertEquals(12, card.intervalDays)
        assertEquals(4, card.repetitions)
        assertEquals(1, card.lapses)
        assertEquals(SrsState.REVIEW, card.state)
        assertEquals(20_012L, card.dueAtEpochDay)
    }

    @Test
    fun `обратный перенос не теряет данные самой карточки`() {
        val entity = item(state = VocabState.LEARNING, interval = 3, repetitions = 2)
        val scheduled = Sm2Scheduler.schedule(VocabSrs.toCard(entity), ReviewGrade.GOOD, today)

        val updated = VocabSrs.apply(entity, scheduled)

        // Учебные поля обновились...
        assertEquals(scheduled.intervalDays, updated.intervalDays)
        assertEquals(scheduled.repetitions, updated.repetitions)
        assertEquals(scheduled.dueAtEpochDay, updated.dueAtEpochDay)
        // ...а само слово осталось прежним.
        assertEquals("crowded", updated.term)
        assertEquals("переполненный", updated.translationRu)
        assertEquals(1L, updated.id)
        assertEquals(VocabSource.LESSON, updated.source)
    }

    @Test
    fun `все состояния переводятся в обе стороны`() {
        VocabState.entries.forEach { state ->
            val card = VocabSrs.toCard(item(state = state))
            val back = VocabSrs.apply(item(state = state), card)
            assertEquals("состояние $state должно пережить круг", state, back.state)
        }
    }

    @Test
    fun `успешное повторение отодвигает срок в будущее`() {
        val entity = item(state = VocabState.NEW)

        val first = VocabSrs.apply(
            entity,
            Sm2Scheduler.schedule(VocabSrs.toCard(entity), ReviewGrade.GOOD, today),
        )
        assertTrue("после первого успеха слово не должно ждать сегодня же",
            first.dueAtEpochDay > today)

        val second = VocabSrs.apply(
            first,
            Sm2Scheduler.schedule(VocabSrs.toCard(first), ReviewGrade.GOOD, first.dueAtEpochDay),
        )
        assertTrue("интервал должен расти", second.intervalDays > first.intervalDays)
    }

    @Test
    fun `забытое слово возвращается на сегодня и копит промахи`() {
        val entity = item(state = VocabState.REVIEW, interval = 20, repetitions = 5)

        val updated = VocabSrs.apply(
            entity,
            Sm2Scheduler.schedule(VocabSrs.toCard(entity), ReviewGrade.AGAIN, today),
        )

        assertEquals(today, updated.dueAtEpochDay)
        assertEquals(0, updated.repetitions)
        assertEquals(entity.lapses + 1, updated.lapses)
        assertEquals(VocabState.LEARNING, updated.state)
    }

    @Test
    fun `скрытое слово планировщик не трогает`() {
        val entity = item(state = VocabState.SUSPENDED, interval = 10, due = 19_000L)

        val updated = VocabSrs.apply(
            entity,
            Sm2Scheduler.schedule(VocabSrs.toCard(entity), ReviewGrade.GOOD, today),
        )

        assertEquals(VocabState.SUSPENDED, updated.state)
        assertEquals(19_000L, updated.dueAtEpochDay)
        assertEquals(10, updated.intervalDays)
    }
}
