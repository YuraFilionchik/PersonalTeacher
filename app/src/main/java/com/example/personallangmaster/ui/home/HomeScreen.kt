package com.example.personallangmaster.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.ui.components.LevelBadge
import com.example.personallangmaster.ui.components.StreakRing

/**
 * Главная: что делать прямо сейчас.
 *
 * Экран отвечает на один вопрос — «чем заняться сегодня», — поэтому здесь
 * только цель дня и два-три ближайших действия, а вся статистика живёт в «Прогрессе».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartLesson: () -> Unit,
    onReviewVocab: () -> Unit,
    onOpenGrammar: () -> Unit,
    onOpenReview: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            container.profileRepository,
            container.statsRepository,
            container.vocabRepository,
            container.contentRepository,
            container.database.lessonDao(),
            container.settingsRepository,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.name.isBlank()) "Привет!" else "Привет, ${state.name}",
                    )
                },
                actions = {
                    LevelBadge(cefr = state.level.name, modifier = Modifier.padding(end = 16.dp))
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StreakRing(
                        daysDone = state.minutesToday,
                        goal = state.goalMinutes,
                        streak = state.streak,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (state.goalMet) {
                                "Цель на сегодня выполнена"
                            } else {
                                "Цель: ${state.minutesToday} из ${state.goalMinutes} мин"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = when {
                                state.streak == 0 -> "Серия начнётся с первого выполненного дня"
                                state.streak == 1 -> "Первый день серии"
                                else -> "Серия: ${state.streak} дн. подряд"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.budgetLeftUsd?.let {
                            Text(
                                text = "Бюджет на сегодня: ${state.budgetLeftLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onStartLesson,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text("  Начать урок")
            }

            Spacer(Modifier.height(8.dp))

            if (state.dueCards > 0) {
                ActionCard(
                    title = "Повторить слова",
                    subtitle = "Карточек на сегодня: ${state.dueCards}",
                    icon = Icons.Rounded.MenuBook,
                    highlighted = true,
                    onClick = onReviewVocab,
                )
            }

            state.recommendedTopic?.let { topic ->
                ActionCard(
                    title = "Подтянуть тему",
                    subtitle = topic.titleRu,
                    icon = Icons.Rounded.Rule,
                    onClick = onOpenGrammar,
                )
            }

            state.lastLesson?.let { lesson ->
                ActionCard(
                    title = "Разбор прошлого урока",
                    subtitle = lesson.summaryRu?.take(80) ?: "Урок ещё не разобран",
                    icon = Icons.Rounded.RecordVoiceOver,
                    onClick = { onOpenReview(lesson.id) },
                )
            }

            if (state.dueCards == 0 && state.recommendedTopic == null && state.lastLesson == null) {
                Text(
                    text = "После первого урока здесь появятся слова на повторение и темы, " +
                        "которые стоит подтянуть.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
