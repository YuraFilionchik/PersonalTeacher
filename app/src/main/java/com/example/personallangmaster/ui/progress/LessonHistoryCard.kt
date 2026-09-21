package com.example.personallangmaster.ui.progress

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.entity.LessonEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Урок в истории под календарём: сводка, пометка и меню действий. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LessonHistoryCard(
    lesson: LessonEntity,
    selectionActive: Boolean,
    selected: Boolean,
    recordingSize: String?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onOpenTranscript: () -> Unit,
    onShareTranscript: () -> Unit,
    onEditNote: () -> Unit,
    onMarkSkipped: () -> Unit,
    onDeleteRecording: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionActive) {
                // Сам чекбокс не кликается: нажатие по карточке и так переключает выбор,
                // а две точки нажатия с разным поведением сбивают с толку.
                Checkbox(checked = selected, onCheckedChange = null)
            }

            Column(Modifier.weight(1f).padding(start = if (selectionActive) 8.dp else 0.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDateTime(lesson.startedAt),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${lesson.durationSec / 60} мин · " +
                            CostCalculator.formatUsd(lesson.costUsd),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Состояние видно сразу: неразобранный урок — это предложение
                // открыть его и разобрать, а не потеря.
                Text(
                    text = ProgressViewModel.lessonStatusTitle(lesson.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (lesson.status) {
                        LessonStatus.ANALYZED -> MaterialTheme.colorScheme.primary
                        LessonStatus.FAILED, LessonStatus.SKIPPED ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                )

                lesson.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                lesson.summaryRu?.let { summary ->
                    Text(
                        text = summary.take(100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // В режиме выбора меню одного урока только мешает: действия наверху.
            if (!selectionActive) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Действия с уроком")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Транскрипт") },
                            onClick = { menuOpen = false; onOpenTranscript() },
                        )
                        DropdownMenuItem(
                            text = { Text("Поделиться транскриптом") },
                            onClick = { menuOpen = false; onShareTranscript() },
                        )
                        DropdownMenuItem(
                            text = { Text("Пометка") },
                            onClick = { menuOpen = false; onEditNote() },
                        )
                        if (lesson.status != LessonStatus.ANALYZED &&
                            lesson.status != LessonStatus.SKIPPED
                        ) {
                            DropdownMenuItem(
                                text = { Text("Закрыть без разбора") },
                                onClick = { menuOpen = false; onMarkSkipped() },
                            )
                        }
                        if (recordingSize != null) {
                            DropdownMenuItem(
                                text = { Text("Удалить запись") },
                                trailingIcon = {
                                    Text(
                                        text = recordingSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = { menuOpen = false; onDeleteRecording() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Удалить урок") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDateTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMMM, HH:mm"))
