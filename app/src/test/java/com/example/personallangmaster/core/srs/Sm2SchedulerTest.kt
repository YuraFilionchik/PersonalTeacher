package com.example.personallangmaster.core.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class Sm2SchedulerTest {

    @Test
    fun `new card has correct defaults`() {
        val today = 100L
        val card = Sm2Scheduler.newCard(today)
        assertEquals(2.5, card.ease, 0.001)
        assertEquals(0, card.intervalDays)
        assertEquals(0, card.repetitions)
        assertEquals(0, card.lapses)
        assertEquals(SrsState.NEW, card.state)
        assertEquals(today, card.dueAtEpochDay)
        assertEquals(null, card.lastReviewedEpochDay)
    }

    @Test
    fun `isDue returns correct value based on days`() {
        val today = 100L
        val card = Sm2Scheduler.newCard(today)
        assertTrue(Sm2Scheduler.isDue(card, today))
        assertTrue(Sm2Scheduler.isDue(card, today + 1))
        assertFalse(Sm2Scheduler.isDue(card, today - 1))
    }

    @Test
    fun `isDue returns false for SUSPENDED cards`() {
        val today = 100L
        val card = Sm2Scheduler.newCard(today).copy(state = SrsState.SUSPENDED, dueAtEpochDay = today - 10)
        assertFalse(Sm2Scheduler.isDue(card, today))
    }

    @Test
    fun `schedule suspended card does nothing`() {
        val today = 100L
        val card = Sm2Scheduler.newCard(today).copy(state = SrsState.SUSPENDED)
        val result = Sm2Scheduler.schedule(card, ReviewGrade.GOOD, today)
        assertEquals(card, result)
    }

    @Test
    fun `good progression through NEW, LEARNING, REVIEW, MATURE`() {
        var today = 100L
        var card = Sm2Scheduler.newCard(today)
        
        // Rep 1 (0 -> 1)
        card = Sm2Scheduler.schedule(card, ReviewGrade.GOOD, today)
        assertEquals(1, card.intervalDays)
        assertEquals(1, card.repetitions)
        assertEquals(SrsState.LEARNING, card.state)
        
        // Rep 2 (1 -> 2)
        today += card.intervalDays
        card = Sm2Scheduler.schedule(card, ReviewGrade.GOOD, today)
        assertEquals(6, card.intervalDays)
        assertEquals(2, card.repetitions)
        assertEquals(SrsState.REVIEW, card.state)
        
        // Rep 3 (2 -> 3)
        today += card.intervalDays
        card = Sm2Scheduler.schedule(card, ReviewGrade.GOOD, today)
        assertEquals((6 * 2.5).toInt(), card.intervalDays) // 15
        assertEquals(3, card.repetitions)
        assertEquals(SrsState.REVIEW, card.state)

        // Rep 4 (3 -> 4)
        today += card.intervalDays
        card = Sm2Scheduler.schedule(card, ReviewGrade.GOOD, today)
        assertEquals((15 * 2.5).roundToInt(), card.intervalDays) // 37.5 -> 38
        assertEquals(4, card.repetitions)
        assertEquals(SrsState.MATURE, card.state) // 38 >= 21
    }

    @Test
    fun `again resets repetitions and interval, increments lapses, decreases ease`() {
        var today = 100L
        var card = Sm2Scheduler.newCard(today)
        card = Sm2Scheduler.schedule(card, ReviewGrade.GOOD, today) // interval 1
        card = Sm2Scheduler.schedule(card, ReviewGrade.GOOD, today + 1) // interval 6, REVIEW
        
        // Fail
        val failed = Sm2Scheduler.schedule(card, ReviewGrade.AGAIN, today + 7)
        assertEquals(0, failed.repetitions)
        assertEquals(0, failed.intervalDays)
        assertEquals(1, failed.lapses)
        assertEquals(2.3, failed.ease, 0.001) // 2.5 - 0.2
        assertEquals(SrsState.LEARNING, failed.state)
    }

    @Test
    fun `hard increases interval by 1-2 and decreases ease`() {
        val today = 100L
        var card = Sm2Scheduler.newCard(today)
        card = card.copy(intervalDays = 10, repetitions = 2, state = SrsState.REVIEW)
        
        val hardCard = Sm2Scheduler.schedule(card, ReviewGrade.HARD, today)
        assertEquals(12, hardCard.intervalDays) // 10 * 1.2
        assertEquals(2.35, hardCard.ease, 0.001) // 2.5 - 0.15
        assertEquals(2, hardCard.repetitions) // unchanged
    }

    @Test
    fun `easy increases ease and gives 1-3x good interval`() {
        val today = 100L
        var card = Sm2Scheduler.newCard(today)
        card = card.copy(intervalDays = 10, repetitions = 2, state = SrsState.REVIEW)
        
        val easyCard = Sm2Scheduler.schedule(card, ReviewGrade.EASY, today)
        assertEquals(2.65, easyCard.ease, 0.001) // 2.5 + 0.15
        val expectedGoodInterval = (10 * 2.5).toInt() // 25
        val expectedEasyInterval = (25 * 1.3).toInt() // 32.5 -> 33
        assertEquals(33, easyCard.intervalDays)
        assertEquals(3, easyCard.repetitions)
    }

    @Test
    fun `ease is clamped to minimum 1-3`() {
        val today = 100L
        var card = Sm2Scheduler.newCard(today).copy(ease = 1.4)
        card = Sm2Scheduler.schedule(card, ReviewGrade.AGAIN, today)
        assertEquals(1.3, card.ease, 0.001) // 1.4 - 0.2 = 1.2 clamped to 1.3
    }

    @Test
    fun `ease is clamped to maximum 3-0`() {
        val today = 100L
        var card = Sm2Scheduler.newCard(today).copy(ease = 2.9)
        card = Sm2Scheduler.schedule(card, ReviewGrade.EASY, today)
        assertEquals(3.0, card.ease, 0.001) // 2.9 + 0.15 = 3.05 clamped to 3.0
    }

    @Test
    fun `interval is clamped to 365 days`() {
        val today = 100L
        var card = Sm2Scheduler.newCard(today).copy(intervalDays = 300, repetitions = 5, ease = 2.5)
        card = Sm2Scheduler.schedule(card, ReviewGrade.GOOD, today)
        assertEquals(365, card.intervalDays) // 300 * 2.5 = 750 clamped to 365
    }

    @Test
    fun `hard grade guarantees at least 1 day interval increase`() {
        val today = 100L
        var card = Sm2Scheduler.newCard(today).copy(intervalDays = 0)
        card = Sm2Scheduler.schedule(card, ReviewGrade.HARD, today)
        assertEquals(1, card.intervalDays) // max(1, 0 * 1.2)
    }
}