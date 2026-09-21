package com.example.personallangmaster.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.R
import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.UsageKind
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.ui.components.StatTile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Прогресс: минуты по дням, словарь, повторяющиеся ошибки и расходы.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(onOpenReview: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.factory(
            container.profileRepository,
            container.statsRepository,
            container.vocabRepository,
            container.database.lessonDao(),
            container.lessonHistoryRepository,
            container.appScope,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_progress)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Минуты разговора за неделю")
            MinutesChart(bars = state.days, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(
                    title = "За неделю",
                    value = "${state.minutesWeek} мин",
                    caption = "уроков: ${state.lessonsWeek}",
                    icon = Icons.Rounded.Schedule,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "Словарь",
                    value = "${state.vocabByState.values.sum()}",
                    caption = "слов всего",
                    icon = Icons.Rounded.MenuBook,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionTitle("Словарь по состояниям")
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.vocabByState
                    .filterValues { it > 0 }
                    .forEach { (vocabState, count) ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = ProgressViewModel.vocabStateTitle(vocabState),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                if (state.vocabByState.values.sum() == 0) {
                    Text(
                        text = "Слова появятся после первого урока",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.topMistakes.isNotEmpty()) {
                SectionTitle("Что повторяется в ошибках")
                state.topMistakes.forEach { mistake ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = ProgressViewModel.mistakeTitle(mistake.type),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "${mistake.total}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            SectionTitle("Расходы")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(
                    title = "Сегодня",
                    value = CostCalculator.formatUsd(state.spentTodayUsd),
                    caption = null,
                    icon = Icons.Rounded.Payments,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "За месяц",
                    value = CostCalculator.formatUsd(state.spentMonthUsd),
                    caption = null,
                    icon = Icons.Rounded.Payments,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.usageBreakdown.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                state.usageBreakdown.forEach { usage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = usageTitle(usage.kind),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${usage.tokens} ток. · ${CostCalculator.formatUsd(usage.costUsd)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Календарь уроков")
            LessonCalendarGrid(
                month = state.month,
                days = state.calendar,
                selectedDay = state.selectedDay,
                canGoForward = state.canGoForward,
                onShiftMonth = viewModel::shiftMonth,
                onToday = viewModel::goToToday,
                onSelectDay = viewModel::selectDay,
            )

            SectionTitle(
                state.selectedDay?.let { day -> "Уроки ${formatDay(day)}" }
                    ?: "Уроки за месяц (${state.lessonsTotal})"
            )

            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LessonFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = filter == state.filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = { Text(ProgressViewModel.filterTitle(filter)) },
                    )
                }
            }

            if (state.recentLessons.isEmpty()) {
                Text(
                    text = if (state.lessonsTotal == 0) {
                        "В этом месяце уроков пока нет"
                    } else {
                        "Таких уроков здесь нет"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            state.recentLessons.forEach { lesson ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onOpenReview(lesson.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatDate(lesson.startedAt),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${lesson.durationSec / 60} мин · " +
                                    CostCalculator.formatUsd(lesson.costUsd),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Состояние видно сразу: неразобранный урок — это
                        // предложение открыть его и разобрать, а не потеря.
                        Text(
                            text = ProgressViewModel.lessonStatusTitle(lesson.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (lesson.status) {
                                LessonStatus.ANALYZED -> MaterialTheme.colorScheme.primary
                                LessonStatus.FAILED -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.tertiary
                            },
                        )

                        lesson.summaryRu?.let { summary ->
                            Text(
                                text = summary.take(100),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

private fun usageTitle(kind: UsageKind): String = when (kind) {
    UsageKind.LIVE_AUDIO_IN -> "Голос: ваша речь"
    UsageKind.LIVE_AUDIO_OUT -> "Голос: речь тренера"
    UsageKind.TEXT_IN -> "Текст: запросы"
    UsageKind.TEXT_OUT -> "Текст: разборы и упражнения"
}

private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMMM, HH:mm"))

private fun formatDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM"))
