package com.example.personallangmaster.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

enum class Trend { UP, FLAT, DOWN }

/**
 * Компактный бейдж уровня CEFR с индикатором тренда.
 */
@Composable
fun LevelBadge(
    cefr: String,
    trend: Trend = Trend.FLAT,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = cefr,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            
            val icon = when (trend) {
                Trend.UP -> Icons.Rounded.ArrowUpward
                Trend.FLAT -> Icons.Rounded.Remove
                Trend.DOWN -> Icons.Rounded.ArrowDownward
            }
            
            val iconTint = when (trend) {
                Trend.UP -> MaterialTheme.colorScheme.primary
                Trend.FLAT -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                Trend.DOWN -> MaterialTheme.colorScheme.error
            }

            Icon(
                imageVector = icon,
                contentDescription = trend.name,
                modifier = Modifier.size(14.dp),
                tint = iconTint
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LevelBadgePreview() {
    PersonalLangMasterTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LevelBadge(cefr = "A2", trend = Trend.DOWN)
                LevelBadge(cefr = "B1", trend = Trend.FLAT)
                LevelBadge(cefr = "B2", trend = Trend.UP)
            }
        }
    }
}
