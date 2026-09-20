package com.example.personallangmaster.core.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    @Test
    fun `no previous activity, goal not achieved`() {
        val today = LocalDate.of(2023, 10, 1)
        val result = StreakCalculator.calculate(
            lastActiveDate = null,
            today = today,
            currentStreak = 0,
            longestStreak = 0,
            freezesLeft = 2,
            achievedGoal = false
        )
        assertEquals(0, result.currentStreak)
        assertEquals(0, result.longestStreak)
        assertEquals(2, result.freezesLeft)
        assertNull(result.lastActiveDate)
    }

    @Test
    fun `no previous activity, goal achieved starts streak`() {
        val today = LocalDate.of(2023, 10, 1)
        val result = StreakCalculator.calculate(
            lastActiveDate = null,
            today = today,
            currentStreak = 0,
            longestStreak = 0,
            freezesLeft = 2,
            achievedGoal = true
        )
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.longestStreak)
        assertEquals(today, result.lastActiveDate)
    }

    @Test
    fun `goal achieved consecutive day increments streak`() {
        val yesterday = LocalDate.of(2023, 10, 1)
        val today = LocalDate.of(2023, 10, 2)
        val result = StreakCalculator.calculate(
            lastActiveDate = yesterday,
            today = today,
            currentStreak = 5,
            longestStreak = 5,
            freezesLeft = 2,
            achievedGoal = true
        )
        assertEquals(6, result.currentStreak)
        assertEquals(6, result.longestStreak)
        assertEquals(today, result.lastActiveDate)
    }

    @Test
    fun `not achieving goal consecutive day keeps current streak waiting`() {
        val yesterday = LocalDate.of(2023, 10, 1)
        val today = LocalDate.of(2023, 10, 2)
        val result = StreakCalculator.calculate(
            lastActiveDate = yesterday,
            today = today,
            currentStreak = 5,
            longestStreak = 5,
            freezesLeft = 2,
            achievedGoal = false
        )
        assertEquals(5, result.currentStreak) // Remains 5 until midnight passes
        assertEquals(yesterday, result.lastActiveDate) // Last active is still yesterday
    }

    @Test
    fun `one day gap uses one freeze`() {
        val lastActive = LocalDate.of(2023, 10, 1)
        val today = LocalDate.of(2023, 10, 3) // Missed the 2nd
        val result = StreakCalculator.calculate(
            lastActiveDate = lastActive,
            today = today,
            currentStreak = 5,
            longestStreak = 5,
            freezesLeft = 2,
            achievedGoal = true
        )
        assertEquals(6, result.currentStreak) // 5 + today = 6 (missed day is frozen)
        assertEquals(1, result.freezesLeft) // Used 1 freeze
        assertEquals(today, result.lastActiveDate)
    }

    @Test
    fun `two day gap uses two freezes`() {
        val lastActive = LocalDate.of(2023, 10, 1)
        val today = LocalDate.of(2023, 10, 4) // Missed 2nd and 3rd
        val result = StreakCalculator.calculate(
            lastActiveDate = lastActive,
            today = today,
            currentStreak = 5,
            longestStreak = 5,
            freezesLeft = 2,
            achievedGoal = true
        )
        assertEquals(6, result.currentStreak)
        assertEquals(0, result.freezesLeft) // Used both freezes
    }

    @Test
    fun `gap without enough freezes breaks streak`() {
        val lastActive = LocalDate.of(2023, 10, 1)
        val today = LocalDate.of(2023, 10, 4) // Missed 2 days
        val result = StreakCalculator.calculate(
            lastActiveDate = lastActive,
            today = today,
            currentStreak = 5,
            longestStreak = 5,
            freezesLeft = 1, // Only 1 freeze available
            achievedGoal = true
        )
        assertEquals(1, result.currentStreak) // Streak reset and starts over today
        assertEquals(5, result.longestStreak) // Longest remains 5
        assertEquals(1, result.freezesLeft) // Freezes NOT consumed if streak breaks anyway
    }
}
