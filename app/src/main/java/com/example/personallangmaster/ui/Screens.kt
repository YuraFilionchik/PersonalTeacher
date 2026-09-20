package com.example.personallangmaster.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Временная заглушка экрана: заменяется реальным содержимым на своём этапе плана. */
@Composable
private fun PlaceholderScreen(title: String) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
fun HomeScreen() = PlaceholderScreen("Главная")

@Composable
fun LessonScreen() = PlaceholderScreen("Урок")

@Composable
fun PracticeScreen() = PlaceholderScreen("Практика")

@Composable
fun ProgressScreen() = PlaceholderScreen("Прогресс")

@Composable
fun SettingsScreen() = PlaceholderScreen("Настройки")
