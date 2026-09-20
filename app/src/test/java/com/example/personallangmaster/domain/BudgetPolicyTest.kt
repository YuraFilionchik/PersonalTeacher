package com.example.personallangmaster.domain

import com.example.personallangmaster.data.prefs.LimitBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Правила про деньги: ошибка здесь либо молча тратит лишнее, либо не даёт
 * заниматься. Поэтому проверяем все сочетания лимитов и поведения.
 */
class BudgetPolicyTest {

    // --- Перед началом урока ---

    @Test
    fun `в пределах лимитов урок разрешён без предупреждений`() {
        val decision = BudgetPolicy.beforeLesson(
            spentTodayUsd = 0.10,
            spentMonthUsd = 2.0,
            dailyLimitUsd = 0.5,
            monthlyLimitUsd = 10.0,
            behavior = LimitBehavior.SOFT_STOP,
        )

        assertEquals(BudgetAction.ALLOW, decision.action)
        assertNull(decision.reason)
    }

    @Test
    fun `исчерпанный дневной лимит блокирует урок`() {
        val decision = BudgetPolicy.beforeLesson(
            spentTodayUsd = 0.5,
            spentMonthUsd = 1.0,
            dailyLimitUsd = 0.5,
            monthlyLimitUsd = 10.0,
            behavior = LimitBehavior.BLOCK,
        )

        assertEquals(BudgetAction.BLOCK, decision.action)
        assertNotNull(decision.reason)
    }

    @Test
    fun `месячный лимит блокирует, даже если дневной не тронут`() {
        val decision = BudgetPolicy.beforeLesson(
            spentTodayUsd = 0.0,
            spentMonthUsd = 10.0,
            dailyLimitUsd = 0.5,
            monthlyLimitUsd = 10.0,
            behavior = LimitBehavior.SOFT_STOP,
        )

        assertEquals(BudgetAction.BLOCK, decision.action)
        assertEquals(true, decision.reason?.contains("Месячный"))
    }

    @Test
    fun `режим предупреждения не запрещает заниматься`() {
        val decision = BudgetPolicy.beforeLesson(
            spentTodayUsd = 5.0,
            spentMonthUsd = 50.0,
            dailyLimitUsd = 0.5,
            monthlyLimitUsd = 10.0,
            behavior = LimitBehavior.WARN,
        )

        assertEquals(BudgetAction.WARN, decision.action)
        assertNotNull(decision.reason)
    }

    @Test
    fun `нулевой лимит означает отсутствие лимита`() {
        val decision = BudgetPolicy.beforeLesson(
            spentTodayUsd = 100.0,
            spentMonthUsd = 100.0,
            dailyLimitUsd = 0.0,
            monthlyLimitUsd = 0.0,
            behavior = LimitBehavior.BLOCK,
        )

        assertEquals(BudgetAction.ALLOW, decision.action)
    }

    // --- По ходу урока ---

    @Test
    fun `до восьмидесяти процентов лимита урок идёт молча`() {
        val decision = BudgetPolicy.duringLesson(
            sessionCostUsd = 0.39,
            dailyLimitUsd = 0.5,
            behavior = LimitBehavior.SOFT_STOP,
        )

        assertEquals(BudgetAction.ALLOW, decision.action)
    }

    @Test
    fun `на восьмидесяти процентах приходит предупреждение, но урок продолжается`() {
        val decision = BudgetPolicy.duringLesson(
            sessionCostUsd = 0.40,
            dailyLimitUsd = 0.5,
            behavior = LimitBehavior.SOFT_STOP,
        )

        assertEquals(BudgetAction.WARN, decision.action)
        assertNotNull(decision.reason)
    }

    @Test
    fun `достигнутый лимит завершает урок при мягком завершении`() {
        val decision = BudgetPolicy.duringLesson(
            sessionCostUsd = 0.5,
            dailyLimitUsd = 0.5,
            behavior = LimitBehavior.SOFT_STOP,
        )

        assertEquals(BudgetAction.BLOCK, decision.action)
    }

    @Test
    fun `в режиме предупреждения урок не обрывается даже на лимите`() {
        val decision = BudgetPolicy.duringLesson(
            sessionCostUsd = 1.0,
            dailyLimitUsd = 0.5,
            behavior = LimitBehavior.WARN,
        )

        assertEquals(BudgetAction.WARN, decision.action)
    }

    @Test
    fun `без лимита урок не прерывается никогда`() {
        val decision = BudgetPolicy.duringLesson(
            sessionCostUsd = 42.0,
            dailyLimitUsd = 0.0,
            behavior = LimitBehavior.BLOCK,
        )

        assertEquals(BudgetAction.ALLOW, decision.action)
    }
}
