package com.example.personallangmaster.core.progress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyGoalTest {

    @Test
    fun `goal achieved when minutes exceed goal`() {
        assertTrue(DailyGoal.isGoalAchieved(15, 10))
    }

    @Test
    fun `goal achieved when minutes equal goal`() {
        assertTrue(DailyGoal.isGoalAchieved(10, 10))
    }

    @Test
    fun `goal not achieved when minutes below goal`() {
        assertFalse(DailyGoal.isGoalAchieved(5, 10))
    }

    @Test
    fun `goal always achieved if goal is 0 or negative`() {
        assertTrue(DailyGoal.isGoalAchieved(0, 0))
        assertTrue(DailyGoal.isGoalAchieved(5, 0))
        assertTrue(DailyGoal.isGoalAchieved(0, -5))
    }
}
