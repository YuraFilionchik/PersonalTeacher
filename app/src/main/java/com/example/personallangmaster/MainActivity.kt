package com.example.personallangmaster

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
import com.example.personallangmaster.ui.onboarding.OnboardingScreen
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PersonalLangMasterApplication).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

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
                        PersonalLangMasterApp()
                    } else {
                        OnboardingScreen(onFinished = { /* переключит сам поток настроек */ })
                    }
                }
            }
        }
    }
}
