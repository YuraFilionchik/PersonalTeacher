package com.example.personallangmaster.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Один столбик: день и сколько в этот день говорили. */
data class DayBar(
    val label: String,
    val minutes: Double,
    val isToday: Boolean,
)

/**
 * Минуты разговора по дням.
 *
 * Одна серия, один цвет и одна шкала — сравнивать нужно дни между собой,
 * а не минуты с деньгами: для расходов есть свои плитки. Подписаны только
 * лучший день и сегодня, иначе цифры забивают саму форму.
 */
@Composable
fun MinutesChart(
    bars: List<DayBar>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 140.dp,
) {
    // Неделя без единого урока — это не график из нулей, а просто пустая неделя.
    if (bars.isEmpty() || bars.all { it.minutes <= 0.0 }) {
        Text(
            text = "Пока нет уроков — график появится после первого разговора",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp),
        )
        return
    }

    val maxMinutes = bars.maxOf { it.minutes }.coerceAtLeast(MIN_SCALE)
    val barColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.tertiary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 16.dp)
        ) {
            val slot = size.width / bars.size
            val barWidth = (slot - BAR_GAP_PX).coerceAtLeast(MIN_BAR_WIDTH_PX)
            val baselineY = size.height

            // Базовая линия намеренно тусклая: она опора, а не данные.
            drawLine(
                color = baselineColor,
                start = Offset(0f, baselineY),
                end = Offset(size.width, baselineY),
                strokeWidth = BASELINE_STROKE_PX,
            )

            bars.forEachIndexed { index, bar ->
                val fraction = (bar.minutes / maxMinutes).toFloat().coerceIn(0f, 1f)
                val barHeight = (size.height * fraction).coerceAtLeast(
                    if (bar.minutes > 0) MIN_VISIBLE_HEIGHT_PX else EMPTY_HEIGHT_PX
                )
                val left = index * slot + (slot - barWidth) / 2f

                drawRoundRect(
                    color = when {
                        bar.minutes <= 0 -> emptyColor
                        bar.isToday -> todayColor
                        else -> barColor
                    },
                    topLeft = Offset(left, baselineY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(CORNER_PX, CORNER_PX),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.isToday) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Прямая подпись только к лучшему дню — она и есть вывод графика.
        val best = bars.maxByOrNull { it.minutes }
        if (best != null && best.minutes > 0) {
            Text(
                text = "Лучший день: ${best.label}, ${best.minutes.toInt()} мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
            )
        }
    }
}

private const val BAR_GAP_PX = 10f
private const val MIN_BAR_WIDTH_PX = 6f
private const val BASELINE_STROKE_PX = 2f
private const val CORNER_PX = 8f
private const val MIN_VISIBLE_HEIGHT_PX = 6f
private const val EMPTY_HEIGHT_PX = 2f
private const val MIN_SCALE = 5.0
