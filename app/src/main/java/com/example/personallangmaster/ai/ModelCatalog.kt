package com.example.personallangmaster.ai

/**
 * Модели Gemini, из которых приложение даёт выбирать.
 *
 * Имена моделей меняются чаще, чем выходят версии приложения, поэтому они живут
 * здесь и в настройках, а не разбросаны константами по коду. Сверено 2026-09-20
 * по ai.google.dev — при следующем крупном этапе проверить заново.
 */
object ModelCatalog {

    data class ModelOption(
        val id: String,
        val titleRu: String,
        val noteRu: String,
    )

    /** Модели для живого голосового урока (Live API, WebSocket). */
    val liveModels = listOf(
        ModelOption(
            id = "gemini-3.8-live",
            titleRu = "Живой голос (по умолчанию)",
            noteRu = "Нативное аудио, минимальная задержка, естественная интонация",
        ),
        ModelOption(
            id = "gemini-3.8-live-extended-thinking",
            titleRu = "Живой голос с размышлением",
            noteRu = "Лучше объясняет сложное, но отвечает медленнее и стоит дороже",
        ),
        ModelOption(
            id = "gemini-3.1-flash-live-preview",
            titleRu = "Запасной (half-cascade)",
            noteRu = "Голос менее живой, зато стабильнее на долгих сессиях",
        ),
    )

    /** Модели для текстовых задач: разбор урока, генерация упражнений, переводы. */
    val textModels = listOf(
        ModelOption(
            id = "gemini-3.8-flash",
            titleRu = "Разбор уроков",
            noteRu = "Структурированный вывод, основной текстовый вызов",
        ),
        ModelOption(
            id = "gemini-3.5-flash-lite",
            titleRu = "Мелкие задачи",
            noteRu = "Самая дешёвая: переводы слов, подсказки, карточки",
        ),
    )

    const val DEFAULT_LIVE_MODEL = "gemini-3.8-live"
    const val DEFAULT_TEXT_MODEL = "gemini-3.8-flash"
    const val DEFAULT_CHEAP_MODEL = "gemini-3.5-flash-lite"

    /** Версия API для WebSocket-эндпоинта Live. */
    const val API_VERSION = "v1beta"
}
