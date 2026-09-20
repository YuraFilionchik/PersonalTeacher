package com.example.personallangmaster.core.cost

/**
 * Таблица тарифов Gemini API (в долларах США за 1 миллион токенов).
 */
data class PricingTable(
    val textInputPerMTok: Double = 0.75,
    val textOutputPerMTok: Double = 4.50,
    val audioInputPerMTok: Double = 3.00,
    val audioOutputPerMTok: Double = 12.00,
    val cacheCreatePerMTok: Double = 0.075,
    val cacheStoragePerMTokHour: Double = 0.50,
) {
    companion object {
        const val AUDIO_INPUT_TOKENS_PER_SECOND = 25
        const val AUDIO_OUTPUT_TOKENS_PER_SECOND = 50
    }
}
