package com.example.personallangmaster.ui.progress

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.R
import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.data.db.UsageKind
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.domain.LessonHousekeeping
import com.example.personallangmaster.ui.components.StatTile
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Прогресс: минуты по дням, словарь, повторяющиеся ошибки и расходы.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    onOpenReview: (Long) -> Unit,
    onOpenTranscript: (Long) -> Unit,
) {
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
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    var deleteRequest by remember { mutableStateOf<List<Long>?>(null) }
    var skipRequest by remember { mutableStateOf<Long?>(null) }
    var clearRecordingsRequest by remember { mutableStateOf(false) }

    // Сообщение и отмена живут здесь: ViewModel не должна знать про Snackbar.
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = message.text,
            actionLabel = if (message.undoable) "Отменить" else null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.consumeMessage()
    }

    LaunchedEffect(state.shareText) {
        val text = state.shareText ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PersonalLangMaster: транскрипт урока")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться транскриптом"))
        viewModel.consumeShare()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_progress)) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.selectedDay?.let { day -> "Уроки ${formatDay(day)}" }
                        ?: "Уроки за месяц (${state.lessonsTotal})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                var listMenuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { listMenuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Действия со списком")
                    }
                    DropdownMenu(
                        expanded = listMenuOpen,
                        onDismissRequest = { listMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Удалить все несостоявшиеся (${state.failedCount})") },
                            enabled = state.failedCount > 0,
                            onClick = {
                                listMenuOpen = false
                                deleteRequest = viewModel.failedLessonIds()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Очистить все записи · " +
                                        LessonHousekeeping.formatSize(state.recordingBytes)
                                )
                            },
                            enabled = state.recordingLessons > 0,
                            onClick = {
                                listMenuOpen = false
                                clearRecordingsRequest = true
                            },
                        )
                    }
                }
            }

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

            if (state.selection.active) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Выбрано: ${state.selection.count}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        TextButton(onClick = { deleteRequest = state.selection.ids.toList() }) {
                            Text("Удалить")
                        }
                        TextButton(onClick = viewModel::clearSelection) { Text("Снять") }
                    }
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
                LessonHistoryCard(
                    lesson = lesson,
                    selectionActive = state.selection.active,
                    selected = lesson.id in state.selection.ids,
                    recordingSize = state.recordingSizes[lesson.id]
                        ?.let(LessonHousekeeping::formatSize),
                    onOpen = {
                        if (state.selection.active) viewModel.toggleSelection(lesson.id)
                        else onOpenReview(lesson.id)
                    },
                    onLongPress = { viewModel.startSelection(lesson.id) },
                    onOpenTranscript = { onOpenTranscript(lesson.id) },
                    onShareTranscript = { viewModel.requestShare(lesson.id) },
                    onEditNote = { viewModel.openNote(lesson.id) },
                    onMarkSkipped = { skipRequest = lesson.id },
                    onDeleteRecording = { viewModel.deleteRecordings(listOf(lesson.id)) },
                    onDelete = { deleteRequest = listOf(lesson.id) },
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    deleteRequest?.let { ids ->
        DeleteLessonsDialog(
            count = ids.size,
            onDismiss = { deleteRequest = null },
            onConfirm = { scope ->
                viewModel.deleteLessons(ids, scope)
                deleteRequest = null
            },
        )
    }

    skipRequest?.let { lessonId ->
        MarkSkippedDialog(
            onDismiss = { skipRequest = null },
            onConfirm = {
                viewModel.markSkipped(lessonId)
                skipRequest = null
            },
        )
    }

    if (clearRecordingsRequest) {
        ClearRecordingsDialog(
            count = state.recordingLessons,
            sizeLabel = LessonHousekeeping.formatSize(state.recordingBytes),
            onDismiss = { clearRecordingsRequest = false },
            onConfirm = {
                viewModel.deleteRecordings(viewModel.recordingLessonIds())
                clearRecordingsRequest = false
            },
        )
    }

    state.note?.let { request ->
        LessonNoteDialog(
            initial = request.text,
            onDismiss = viewModel::dismissNote,
            onSave = { text -> viewModel.saveNote(request.lessonId, text) },
        )
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

private fun formatDay(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMMM"))
