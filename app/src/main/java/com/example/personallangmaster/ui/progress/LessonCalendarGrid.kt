package com.example.personallangmaster.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Месяц уроков: число дня и точки по числу уроков — залитая точка значит
 * разобранный урок. Пустой день остаётся нейтральным: пропуск не провал.
 */
@Composable
fun LessonCalendarGrid(
    month: YearMonth,
    days: List<CalendarDay>,
    selectedDay: LocalDate?,
    canGoForward: Boolean,
    onShiftMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onShiftMonth(-1) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Предыдущий месяц")
            }
            Text(
                text = ProgressViewModel.monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (month != YearMonth.now()) {
                TextButton(onClick = onToday) { Text("Сегодня") }
            }
            IconButton(onClick = { onShiftMonth(1) }, enabled = canGoForward) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Следующий месяц")
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            DayOfWeek.entries.forEach { dayOfWeek ->
                Text(
                    text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
                        .replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        selected = day.date == selectedDay,
                        onClick = { onSelectDay(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickable = day.inMonth && day.total > 0
    val shape = MaterialTheme.shapes.small

    Column(
        modifier = modifier
            .height(46.dp)
            .padding(horizontal = 2.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .then(
                if (day.isToday && !selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                !day.inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )

        Spacer(Modifier.height(3.dp))
        LessonDots(total = day.total, settled = day.settled)
    }
}

/** Точки по урокам дня: залитая — решённый (разобран или закрыт без разбора), контурная — ждёт разбора. */
@Composable
private fun LessonDots(total: Int, settled: Int) {
    if (total == 0) {
        Spacer(Modifier.height(6.dp))
        return
    }

    val shown = minOf(total, MAX_DOTS)
    val shownSettled = minOf(settled, shown)

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(shown) { index ->
            val filled = index < shownSettled
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .then(
                        if (filled) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, CircleShape)
                        }
                    )
            )
        }
        if (total > MAX_DOTS) {
            Text(
                text = "+${total - MAX_DOTS}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_DOTS = 3
