package com.example.personallangmaster.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

private data class TopLevelDestination(
    val route: Route,
    val icon: ImageVector,
    val label: String,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Route.Home, Icons.Rounded.Home, "Главная"),
    TopLevelDestination(Route.Lesson, Icons.Rounded.RecordVoiceOver, "Урок"),
    TopLevelDestination(Route.Practice, Icons.Rounded.School, "Практика"),
    TopLevelDestination(Route.Progress, Icons.Rounded.TrendingUp, "Прогресс"),
    TopLevelDestination(Route.Settings, Icons.Rounded.Settings, "Настройки"),
)

/**
 * Корневой каркас приложения: адаптивная навигация (нижняя панель на телефоне,
 * боковой rail на планшете) поверх [NavDisplay].
 */
@Composable
fun PersonalLangMasterApp() {
    val backStack = rememberNavBackStack(Route.Home)
    val currentRoute = backStack.lastOrNull()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                item(
                    selected = currentRoute == destination.route,
                    onClick = {
                        // Разделы верхнего уровня не копятся в стеке: заменяем корень.
                        backStack.clear()
                        backStack.add(destination.route)
                    },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = { backStack.removeAt(backStack.lastIndex) },
            entryProvider = entryProvider {
                entry<Route.Home> { HomeScreen() }
                entry<Route.Lesson> { LessonScreen() }
                entry<Route.Practice> { PracticeScreen() }
                entry<Route.Progress> { ProgressScreen() }
                entry<Route.Settings> { SettingsScreen() }
            },
        )
    }
}
