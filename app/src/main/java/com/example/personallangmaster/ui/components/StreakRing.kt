package com.example.personallangmaster.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

/**
 * Кольцо прогресса дня со значением ударного режима в центре.
 */
@Composable
fun StreakRing(
    daysDone: Int,
    goal: Int,
    streak: Int,
    modifier: Modifier = Modifier
) {
    val progress = if (goal > 0) (daysDone.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = if (progress >= 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(120.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            
            // Фоновое кольцо
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
            
            // Кольцо прогресса
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = if (streak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = streak.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreakRingPreview() {
    PersonalLangMasterTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StreakRing(daysDone = 5, goal = 10, streak = 14)
                StreakRing(daysDone = 10, goal = 10, streak = 15)
            }
        }
    }
}
