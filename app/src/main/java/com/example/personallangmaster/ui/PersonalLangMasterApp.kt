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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.personallangmaster.R
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.ui.lesson.LessonScreen
import com.example.personallangmaster.ui.practice.PracticeScreen
import com.example.personallangmaster.ui.vocab.VocabListScreen
import com.example.personallangmaster.ui.vocab.VocabReviewScreen
import com.example.personallangmaster.ui.review.LessonReviewScreen
import com.example.personallangmaster.ui.settings.AppearanceSettingsScreen
import com.example.personallangmaster.ui.settings.AudioSettingsScreen
import com.example.personallangmaster.ui.settings.BudgetSettingsScreen
import com.example.personallangmaster.ui.settings.DataSettingsScreen
import com.example.personallangmaster.ui.settings.LevelSettingsScreen
import com.example.personallangmaster.ui.settings.MethodSettingsScreen
import com.example.personallangmaster.ui.settings.ModelSettingsScreen
import com.example.personallangmaster.ui.settings.NotificationSettingsScreen
import com.example.personallangmaster.ui.settings.PromptPreviewScreen
import com.example.personallangmaster.ui.settings.SettingsRoute
import com.example.personallangmaster.ui.settings.SettingsScreen
import com.example.personallangmaster.ui.settings.SettingsViewModel
import com.example.personallangmaster.ui.settings.TutorSettingsScreen

private data class TopLevelDestination(
    val route: Route,
    val icon: ImageVector,
    val labelRes: Int,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Route.Home, Icons.Rounded.Home, R.string.nav_home),
    TopLevelDestination(Route.Lesson, Icons.Rounded.RecordVoiceOver, R.string.nav_lesson),
    TopLevelDestination(Route.Practice, Icons.Rounded.School, R.string.nav_practice),
    TopLevelDestination(Route.Progress, Icons.Rounded.TrendingUp, R.string.nav_progress),
    TopLevelDestination(Route.Settings, Icons.Rounded.Settings, R.string.nav_settings),
)

/**
 * Корневой каркас: адаптивная навигация (нижняя панель на телефоне, боковой rail
 * на планшете) поверх [NavDisplay]. Подэкраны настроек кладутся на тот же стек.
 */
@Composable
fun PersonalLangMasterApp() {
    val container = LocalAppContainer.current
    val backStack = rememberNavBackStack(Route.Home)

    // Раздел подсвечивается по корню стека: внутри настроек вкладка остаётся выбранной.
    val rootRoute = backStack.firstOrNull()

    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            container.settingsRepository,
            container.profileRepository,
            container.database,
        )
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelDestinations.forEach { destination ->
                item(
                    selected = rootRoute == destination.route,
                    onClick = {
                        // Разделы верхнего уровня не копятся в стеке: заменяем корень.
                        backStack.clear()
                        backStack.add(destination.route)
                    },
                    icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = goBack,
            entryProvider = entryProvider {
                entry<Route.Home> { HomeScreen() }
                entry<Route.Lesson> {
                    LessonScreen(
                        onOpenReview = { lessonId -> backStack.add(Route.Review(lessonId)) },
                        onOpenSettings = {
                            backStack.clear()
                            backStack.add(Route.Settings)
                            backStack.add(SettingsRoute.Model)
                        }
                    )
                }
                entry<Route.Practice> {
                    PracticeScreen(
                        onOpenVocab = { backStack.add(Route.VocabList) },
                        onStartReview = { backStack.add(Route.VocabReview) },
                    )
                }
                entry<Route.VocabList> {
                    VocabListScreen(
                        onBack = goBack,
                        onStartReview = { backStack.add(Route.VocabReview) },
                    )
                }
                entry<Route.VocabReview> { VocabReviewScreen(onBack = goBack) }
                entry<Route.Progress> { ProgressScreen() }
                entry<Route.Review> { key ->
                    LessonReviewScreen(lessonId = key.lessonId, onBack = goBack)
                }
                entry<Route.Settings> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onOpenSection = { section -> backStack.add(section) },
                    )
                }

                entry<SettingsRoute.Tutor> { TutorSettingsScreen(settingsViewModel, goBack) }
                entry<SettingsRoute.Method> { MethodSettingsScreen(settingsViewModel, goBack) }
                entry<SettingsRoute.Level> { LevelSettingsScreen(settingsViewModel, goBack) }
                entry<SettingsRoute.Audio> { AudioSettingsScreen(settingsViewModel, goBack) }
                entry<SettingsRoute.Model> { ModelSettingsScreen(settingsViewModel, goBack) }
                entry<SettingsRoute.Budget> { BudgetSettingsScreen(settingsViewModel, goBack) }
                entry<SettingsRoute.Data> { DataSettingsScreen(settingsViewModel, goBack) }
                entry<SettingsRoute.Appearance> {
                    AppearanceSettingsScreen(settingsViewModel, goBack)
                }
                entry<SettingsRoute.Notifications> {
                    NotificationSettingsScreen(settingsViewModel, goBack)
                }
                entry<SettingsRoute.PromptPreview> {
                    PromptPreviewScreen(settingsViewModel, goBack)
                }
            },
        )
    }
}
