package com.example.personallangmaster.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Разделы верхнего уровня. Каждый ключ должен быть [Serializable] и реализовывать [NavKey],
 * иначе бэкстек не переживёт пересоздание процесса.
 */
sealed interface Route : NavKey {

    /** Главная: приветствие, уровень, быстрый старт урока. */
    @Serializable
    data object Home : Route

    /** Живой урок с ИИ-тренером. */
    @Serializable
    data object Lesson : Route

    /** Практика: словарь, произношение, грамматика, сценарии. */
    @Serializable
    data object Practice : Route

    /** Прогресс: графики, история уроков, расходы. */
    @Serializable
    data object Progress : Route

    /** Настройки приложения и тренера. */
    @Serializable
    data object Settings : Route
}
