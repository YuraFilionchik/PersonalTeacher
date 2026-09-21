package com.example.personallangmaster

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personallangmaster.data.prefs.AppSettings
import com.example.personallangmaster.data.prefs.ThemeMode
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.ui.PersonalLangMasterApp
import com.example.personallangmaster.ui.Route
import com.example.personallangmaster.ui.onboarding.OnboardingScreen
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme
import com.example.personallangmaster.work.Notifications
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingRouteState = MutableStateFlow<Route?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val container = (application as PersonalLangMasterApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            val pendingRoute by pendingRouteState.collectAsStateWithLifecycle()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            CompositionLocalProvider(LocalAppContainer provides container) {
                PersonalLangMasterTheme(
                    darkTheme = darkTheme,
                    dynamicColor = settings.dynamicColor,
                ) {
                    // Пока онбординг не пройден, приложение показывает только его.
                    if (settings.onboardingCompleted) {
                        PersonalLangMasterApp(
                            pendingRoute = pendingRoute,
                            onRouteHandled = { pendingRouteState.value = null }
                        )
                    } else {
                        OnboardingScreen(onFinished = { /* переключит сам поток настроек */ })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        pendingRouteState.value = when (intent?.action) {
            Notifications.ACTION_START_REVIEW -> Route.VocabReview

            Notifications.ACTION_OPEN_LESSON_REVIEW -> {
                val lessonId = intent.getLongExtra(Notifications.EXTRA_LESSON_ID, -1L)
                if (lessonId > 0L) Route.Review(lessonId) else null
            }

            else -> null
        }
    }
}
