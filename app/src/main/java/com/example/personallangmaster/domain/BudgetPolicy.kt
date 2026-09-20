package com.example.personallangmaster.domain

import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.data.prefs.LimitBehavior

/** Что делать с уроком при текущих расходах. */
enum class BudgetAction { ALLOW, WARN, BLOCK }

data class BudgetDecision(
    val action: BudgetAction,
    val reason: String? = null,
)

/**
 * Решения по бюджету: пускать ли в урок и когда его прерывать.
 *
 * Вынесено из экрана отдельно, потому что это правило про деньги: ошибка здесь
 * либо молча тратит лишнее, либо не даёт заниматься. Чистая функция — её видно
 * в тестах целиком.
 */
object BudgetPolicy {

    /** Доля лимита, после которой стоит предупредить, но ещё не мешать. */
    const val WARN_SHARE = 0.8

    /**
     * Проверка перед стартом урока.
     *
     * В режиме «только предупредить» решение остаётся за человеком: приложение
     * личное, и запрещать себе заниматься оно не должно.
     */
    fun beforeLesson(
        spentTodayUsd: Double,
        spentMonthUsd: Double,
        dailyLimitUsd: Double,
        monthlyLimitUsd: Double,
        behavior: LimitBehavior,
    ): BudgetDecision {
        val dayExceeded = dailyLimitUsd > 0 && spentTodayUsd >= dailyLimitUsd
        val monthExceeded = monthlyLimitUsd > 0 && spentMonthUsd >= monthlyLimitUsd

        if (!dayExceeded && !monthExceeded) return BudgetDecision(BudgetAction.ALLOW)

        val reason = if (dayExceeded) {
            "Дневной лимит исчерпан: потрачено ${CostCalculator.formatUsd(spentTodayUsd)}"
        } else {
            "Месячный лимит исчерпан: потрачено ${CostCalculator.formatUsd(spentMonthUsd)}"
        }

        return if (behavior == LimitBehavior.WARN) {
            BudgetDecision(BudgetAction.WARN, reason)
        } else {
            BudgetDecision(BudgetAction.BLOCK, reason)
        }
    }

    /**
     * Проверка по ходу урока: [sessionCostUsd] — сколько стоит уже этот разговор.
     */
    fun duringLesson(
        sessionCostUsd: Double,
        dailyLimitUsd: Double,
        behavior: LimitBehavior,
    ): BudgetDecision {
        if (dailyLimitUsd <= 0) return BudgetDecision(BudgetAction.ALLOW)

        return when {
            sessionCostUsd >= dailyLimitUsd -> {
                if (behavior == LimitBehavior.WARN) {
                    BudgetDecision(BudgetAction.WARN, "Лимит на сегодня превышен")
                } else {
                    BudgetDecision(
                        BudgetAction.BLOCK,
                        "Лимит на сегодня исчерпан — завершаем урок",
                    )
                }
            }

            sessionCostUsd >= dailyLimitUsd * WARN_SHARE -> BudgetDecision(
                BudgetAction.WARN,
                "Израсходовано ${CostCalculator.formatUsd(sessionCostUsd)} из лимита",
            )

            else -> BudgetDecision(BudgetAction.ALLOW)
        }
    }
}
