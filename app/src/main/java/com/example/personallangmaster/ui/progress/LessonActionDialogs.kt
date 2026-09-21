package com.example.personallangmaster.ui.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.data.repo.DeleteScope

/**
 * Подтверждение удаления уроков.
 *
 * Ошибки, слова и расходы ссылаются на урок без внешнего ключа, поэтому решение
 * по ним человек принимает здесь. По умолчанию они остаются: удалять историю
 * разговора и терять при этом выученные слова — не то, чего от кнопки ждут.
 */
@Composable
fun DeleteLessonsDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (DeleteScope) -> Unit,
) {
    var withResults by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (count == 1) "Удалить урок?" else "Удалить уроков: $count?")
        },
        text = {
            Column {
                Text("Разговор и его запись пропадут без возможности восстановить.")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withResults, onCheckedChange = { withResults = it })
                    Text("Удалить и результаты урока")
                }
                Text(
                    text = if (withResults) {
                        "Ошибки, слова из этого урока и записи о расходе тоже исчезнут: " +
                            "просядут рекомендации тем и цифры расходов за месяц."
                    } else {
                        "Ошибки останутся в статистике, слова — в словаре, " +
                            "а потраченное — в итоге за месяц."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        if (withResults) DeleteScope.WITH_RESULTS else DeleteScope.KEEP_RESULTS
                    )
                }
            ) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Урок закрывается без разбора — и про отсутствие ошибок и слов говорим честно. */
@Composable
fun MarkSkippedDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Закрыть без разбора?") },
        text = {
            Text(
                "Урок перестанет напоминать о себе. Это только смена состояния: " +
                    "ошибки и слова из него не появятся, оценок за урок не будет."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Закрыть") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ClearRecordingsDialog(
    count: Int,
    sizeLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Очистить все записи?") },
        text = {
            Text(
                "Записей: $count, занимают $sizeLabel. Транскрипты и разборы останутся — " +
                    "пропадёт только возможность переслушать разговор."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Очистить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Одна строка от руки: «говорили про работу», «плохая связь». */
@Composable
fun LessonNoteDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пометка к уроку") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Например: говорили про работу") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
