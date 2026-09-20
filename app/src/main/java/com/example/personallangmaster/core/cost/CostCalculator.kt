package com.example.personallangmaster.core.cost

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Калькулятор стоимости использования Gemini API для урока и отдельных вызовов.
 */
object CostCalculator {

    /** Рассчитывает токенов аудио-входа за указанное количество секунд. */
    fun audioInputTokens(seconds: Double): Long {
        if (seconds <= 0.0) return 0L
        return (seconds * PricingTable.AUDIO_INPUT_TOKENS_PER_SECOND).roundToLong()
    }

    /** Рассчитывает токенов аудио-выхода за указанное количество секунд. */
    fun audioOutputTokens(seconds: Double): Long {
        if (seconds <= 0.0) return 0L
        return (seconds * PricingTable.AUDIO_OUTPUT_TOKENS_PER_SECOND).roundToLong()
    }

    /** Рассчитывает стоимость в USD за количество токенов по тарифу perMTok. */
    fun costUsd(tokens: Long, perMTok: Double): Double {
        if (tokens <= 0L || perMTok <= 0.0) return 0.0
        return (tokens.toDouble() / 1_000_000.0) * perMTok
    }

    /**
     * Оценивает полную стоимость урока в долларах США.
     * @param minutes длительность урока в минутах.
     * @param userSpeakShare доля времени речи ученика (от 0.0 до 1.0, по умолчанию 0.5).
     * @param systemPromptTokens количество токенов системного промпта за минуту.
     * @param table таблица с тарифами.
     */
    fun estimateLessonCostUsd(
        minutes: Double,
        userSpeakShare: Double = 0.5,
        systemPromptTokens: Long = 250,
        table: PricingTable = PricingTable()
    ): Double {
        if (minutes <= 0.0) return 0.0
        val totalSeconds = minutes * 60.0
        val clampedShare = userSpeakShare.coerceIn(0.0, 1.0)
        
        val userSpeakSeconds = totalSeconds * clampedShare
        val tutorSpeakSeconds = totalSeconds * (1.0 - clampedShare)

        val inAudioTok = audioInputTokens(userSpeakSeconds)
        val outAudioTok = audioOutputTokens(tutorSpeakSeconds)
        val promptTok = (systemPromptTokens.toDouble() * minutes).roundToLong()

        val audioInCost = costUsd(inAudioTok, table.audioInputPerMTok)
        val audioOutCost = costUsd(outAudioTok, table.audioOutputPerMTok)
        val promptCost = costUsd(promptTok, table.textInputPerMTok)

        return audioInCost + audioOutCost + promptCost
    }

    /** Форматирует сумму в долларах США: "$0.21" или "<$0.01" при малой сумме. */
    fun formatUsd(amount: Double): String {
        if (amount <= 0.0) return "$0.00"
        if (amount < 0.01) return "<$0.01"
        return String.format(Locale.US, "$%.2f", amount)
    }

    /** Форматирует сумму в долларах с эквивалентом в рублях по курсу: "$0.21 (~19 ₽)". */
    fun formatUsdRub(amount: Double, rate: Double): String {
        val usdStr = formatUsd(amount)
        if (amount <= 0.0 || rate <= 0.0) return "$usdStr (~0 ₽)"
        val rubAmount = (amount * rate).roundToLong()
        return "$usdStr (~$rubAmount ₽)"
    }
}
