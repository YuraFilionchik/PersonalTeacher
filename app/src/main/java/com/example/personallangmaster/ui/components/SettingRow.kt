package com.example.personallangmaster.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

/**
 * Базовая строка настройки.
 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailing()
        }
    }
}

/**
 * Строка настройки с тумблером (Switch).
 */
@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null // Обрабатывается кликом по строке
            )
        }
    )
}

/**
 * Строка настройки с ползунком (Slider). Занимает две строки.
 */
@Composable
fun SettingSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(top = 8.dp, start = if (icon != null) 40.dp else 0.dp)
        )
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsRowsPreview() {
    PersonalLangMasterTheme {
        Surface {
            Column {
                SettingRow(
                    title = "Голос тренера",
                    subtitle = "Puck (американский)",
                    icon = Icons.Rounded.RecordVoiceOver
                )
                HorizontalDivider()
                SettingSwitchRow(
                    title = "Автоматический разбор",
                    subtitle = "Генерировать разбор сразу после урока",
                    checked = true,
                    onCheckedChange = {}
                )
                HorizontalDivider()
                SettingSliderRow(
                    title = "Строгость исправлений",
                    subtitle = "Баланс: поправляет основные ошибки",
                    value = 2f,
                    valueRange = 0f..4f,
                    steps = 3,
                    onValueChange = {}
                )
            }
        }
    }
}
