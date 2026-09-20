package com.example.personallangmaster.ui.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Подэкраны настроек. Вынесены отдельно от разделов верхнего уровня:
 * они кладутся поверх стека и возвращают назад к списку настроек.
 */
sealed interface SettingsRoute : NavKey {

    @Serializable data object Tutor : SettingsRoute
    @Serializable data object Method : SettingsRoute
    @Serializable data object Level : SettingsRoute
    @Serializable data object Audio : SettingsRoute
    @Serializable data object Model : SettingsRoute
    @Serializable data object Budget : SettingsRoute
    @Serializable data object Data : SettingsRoute
    @Serializable data object Appearance : SettingsRoute
    @Serializable data object Notifications : SettingsRoute

    /** Отладочный экран: показывает системный промпт, собранный из текущих настроек. */
    @Serializable data object PromptPreview : SettingsRoute
}
