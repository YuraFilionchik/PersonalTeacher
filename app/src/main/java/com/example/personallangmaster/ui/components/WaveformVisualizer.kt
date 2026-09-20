package com.example.personallangmaster.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

/**
 * Визуализатор звуковой волны. 
 * Отрисовывает симметричные столбики от центра по вертикали.
 * 
 * @param amplitudes Массив амплитуд (от 0.0 до 1.0) для каждого столбика.
 */
@Composable
fun WaveformVisualizer(
    amplitudes: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barWidth: Float = 12f,
    gapWidth: Float = 8f
) {
    // Анимируем изменения амплитуд для плавности
    val animatedAmplitudes = amplitudes.map { target ->
        animateFloatAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 100),
            label = "amp"
        ).value
    }

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        val totalWidth = animatedAmplitudes.size * (barWidth + gapWidth) - gapWidth
        val startX = (size.width - totalWidth) / 2f

        animatedAmplitudes.forEachIndexed { index, amplitude ->
            val x = startX + index * (barWidth + gapWidth)
            // Минимальная высота 4f, чтобы всегда была видна линия
            val barHeight = maxOf(4f, size.height * amplitude)
            val y = centerY - (barHeight / 2f)

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WaveformVisualizerPreview() {
    PersonalLangMasterTheme {
        Surface {
            WaveformVisualizer(
                amplitudes = floatArrayOf(0.1f, 0.4f, 0.8f, 1.0f, 0.6f, 0.3f, 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(16.dp)
            )
        }
    }
}
