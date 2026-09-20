package com.example.personallangmaster.core.cost

import org.junit.Assert.assertEquals
import org.junit.Test

class CostCalculatorTest {

    @Test
    fun `tokens calculation for audio input and output`() {
        assertEquals(1500L, CostCalculator.audioInputTokens(60.0))
        assertEquals(3000L, CostCalculator.audioOutputTokens(60.0))
        assertEquals(0L, CostCalculator.audioInputTokens(0.0))
        assertEquals(0L, CostCalculator.audioInputTokens(-5.0))
    }

    @Test
    fun `costUsd returns correct amount`() {
        // 1M tokens at $3.00 = $3.00
        assertEquals(3.00, CostCalculator.costUsd(1_000_000L, 3.00), 0.0001)
        // 1000 tokens at $3.00 = $0.003
        assertEquals(0.003, CostCalculator.costUsd(1_000L, 3.00), 0.0001)
        // 0 tokens = $0.00
        assertEquals(0.0, CostCalculator.costUsd(0L, 3.00), 0.0001)
    }

    @Test
    fun `estimateLessonCostUsd matches docs for 1 minute`() {
        // 1 min voice-only: ~ $0.0204 (≈ $0.021)
        val cost1Min = CostCalculator.estimateLessonCostUsd(1.0)
        assertEquals(0.021, cost1Min, 0.002) // within 5%
    }

    @Test
    fun `estimateLessonCostUsd matches docs for 10 minutes`() {
        // 10 mins: ~ $0.204 (≈ $0.21)
        val cost10Min = CostCalculator.estimateLessonCostUsd(10.0)
        assertEquals(0.204, cost10Min, 0.01) // within 5%
    }

    @Test
    fun `estimateLessonCostUsd matches docs for 1 hour`() {
        // 60 mins: ~ $1.226 (≈ $1.26)
        val cost60Min = CostCalculator.estimateLessonCostUsd(60.0)
        assertEquals(1.23, cost60Min, 0.05) // within 5%
    }

    @Test
    fun `estimateLessonCostUsd handles zero and edge cases`() {
        assertEquals(0.0, CostCalculator.estimateLessonCostUsd(0.0), 0.0001)
        assertEquals(0.0, CostCalculator.estimateLessonCostUsd(-10.0), 0.0001)
    }

    @Test
    fun `estimateLessonCostUsd respects custom pricing table`() {
        val customTable = PricingTable(
            audioInputPerMTok = 6.00, // doubled
            audioOutputPerMTok = 24.00, // doubled
            textInputPerMTok = 1.50 // doubled
        )
        val defaultCost = CostCalculator.estimateLessonCostUsd(10.0)
        val customCost = CostCalculator.estimateLessonCostUsd(10.0, table = customTable)
        assertEquals(defaultCost * 2.0, customCost, 0.001)
    }

    @Test
    fun `formatUsd formatting`() {
        assertEquals("$0.00", CostCalculator.formatUsd(0.0))
        assertEquals("<$0.01", CostCalculator.formatUsd(0.004))
        assertEquals("$0.21", CostCalculator.formatUsd(0.208))
        assertEquals("$1.26", CostCalculator.formatUsd(1.26))
    }

    @Test
    fun `formatUsdRub formatting`() {
        assertEquals("$0.21 (~19 ₽)", CostCalculator.formatUsdRub(0.21, 90.0))
        assertEquals("$0.00 (~0 ₽)", CostCalculator.formatUsdRub(0.0, 90.0))
        assertEquals("<$0.01 (~0 ₽)", CostCalculator.formatUsdRub(0.004, 90.0))
    }
}
