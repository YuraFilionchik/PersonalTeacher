package com.example.personallangmaster.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personallangmaster.R
import com.example.personallangmaster.data.prefs.AudioRecording
import com.example.personallangmaster.data.prefs.SubtitleMode
import com.example.personallangmaster.data.prefs.ThemeMode
import com.example.personallangmaster.data.prefs.TranscriptRetention
import com.example.personallangmaster.data.prefs.WaveStyle

@Composable
fun DataSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    SettingsScaffold(stringResource(R.string.settings_section_data), onBack) {
        SettingsGroup("Транскрипты") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_data_transcripts_title),
                subtitle = transcriptRetentionTitle(settings),
                options = TranscriptRetention.entries,
                selected = settings.transcriptRetention,
                optionLabel = ::transcriptRetentionOption,
                onSelect = { value ->
                    viewModel.update { setTranscriptRetention(value, settings.transcriptRetentionDays) }
                },
            )
            if (settings.transcriptRetention == TranscriptRetention.DAYS) {
                SettingsSliderRow(
                    title = "Срок хранения",
                    valueLabel = "${settings.transcriptRetentionDays} дн.",
                    value = settings.transcriptRetentionDays.toFloat(),
                    range = 7f..365f,
                    steps = 11,
                    onValueChange = { value ->
                        viewModel.update {
                            setTranscriptRetention(TranscriptRetention.DAYS, value.toInt())
                        }
                    },
                )
            }
        }

        SettingsDivider()
        SettingsGroup("Аудиозаписи") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_data_audio_recording_title),
                subtitle = "По умолчанию записи не ведутся",
                options = AudioRecording.entries,
                selected = settings.audioRecording,
                optionLabel = ::audioRecordingTitle,
                onSelect = { value ->
                    viewModel.update { setAudioRecording(value, settings.audioRetentionDays) }
                },
            )
            if (settings.audioRecording != AudioRecording.NONE) {
                SettingsSliderRow(
                    title = "Удалять записи старше",
                    valueLabel = "${settings.audioRetentionDays} дн.",
                    value = settings.audioRetentionDays.toFloat(),
                    range = 1f..90f,
                    steps = 10,
                    onValueChange = { value ->
                        viewModel.update { setAudioRecording(settings.audioRecording, value.toInt()) }
                    },
                )
                SettingsNote(
                    "Записи хранятся только на телефоне. Час урока занимает примерно 30 МБ."
                )
            }
        }

        SettingsDivider()
        SettingsGroup("Перенос и сброс") {
            SettingsRow(
                title = stringResource(R.string.settings_data_export_title),
                subtitle = "Появится вместе с модулем резервных копий",
                enabled = false,
            )
            SettingsRow(
                title = stringResource(R.string.settings_data_import_title),
                subtitle = "Появится вместе с модулем резервных копий",
                enabled = false,
            )
            SettingsRow(
                title = stringResource(R.string.settings_data_clear_title),
                subtitle = "Профиль, история, словарь и настройки",
                onClick = { confirmClear = true },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.dialog_clear_all_title)) },
            text = { Text(stringResource(R.string.dialog_clear_all_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData { confirmClear = false }
                    }
                ) { Text(stringResource(R.string.dialog_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
fun AppearanceSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_section_appearance), onBack) {
        SettingsGroup("Тема") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_appearance_theme_title),
                options = ThemeMode.entries,
                selected = settings.themeMode,
                optionLabel = ::themeModeTitle,
                onSelect = { value -> viewModel.update { setThemeMode(value) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_appearance_dynamic_color_title),
                subtitle = "Брать цвета из обоев системы",
                checked = settings.dynamicColor,
                onCheckedChange = { value -> viewModel.update { setDynamicColor(value) } },
            )
        }

        SettingsDivider()
        SettingsGroup("Экран урока") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_appearance_subtitles_title),
                options = SubtitleMode.entries,
                selected = settings.subtitleMode,
                optionLabel = ::subtitleModeTitle,
                onSelect = { value -> viewModel.update { setSubtitleMode(value) } },
            )
            SettingsSliderRow(
                title = "Размер субтитров",
                valueLabel = "${(settings.subtitleFontScale * 100).toInt()}%",
                value = settings.subtitleFontScale,
                range = 0.8f..2.0f,
                steps = 5,
                onValueChange = { value -> viewModel.update { setSubtitleFontScale(value) } },
            )
            SettingsChoiceRow(
                title = "Визуализация голоса",
                options = WaveStyle.entries,
                selected = settings.waveStyle,
                optionLabel = ::waveStyleTitle,
                onSelect = { value -> viewModel.update { setWaveStyle(value) } },
            )
            SettingsSwitchRow(
                title = "Крупные элементы",
                subtitle = "Больше кнопки и отступы — для детей и для тех, кому так удобнее",
                checked = settings.largeElements,
                onCheckedChange = { value -> viewModel.update { setLargeElements(value) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotificationSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_section_notifications), onBack) {
        SettingsGroup("Ежедневное напоминание") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_daily_title),
                subtitle = "Напомнить, если цель дня ещё не выполнена",
                checked = settings.reminderEnabled,
                onCheckedChange = { enabled -> viewModel.update { setReminder(enabled) } },
            )
            if (settings.reminderEnabled) {
                SettingsSliderRow(
                    title = "Время",
                    valueLabel = "%02d:%02d".format(
                        settings.reminderMinuteOfDay / 60,
                        settings.reminderMinuteOfDay % 60,
                    ),
                    value = settings.reminderMinuteOfDay.toFloat(),
                    range = 0f..(24 * 60 - 15).toFloat(),
                    steps = 95,
                    onValueChange = { value ->
                        val rounded = (value / 15).toInt() * 15
                        viewModel.update { setReminder(true, minuteOfDay = rounded) }
                    },
                )
                Text(
                    "Дни недели",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    (1..7).forEach { day ->
                        val selected = day in settings.reminderDays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val updated = if (selected) {
                                    settings.reminderDays - day
                                } else {
                                    settings.reminderDays + day
                                }
                                if (updated.isNotEmpty()) {
                                    viewModel.update { setReminder(true, days = updated) }
                                }
                            },
                            label = { Text(weekdayShort(day)) },
                        )
                    }
                }
            }
        }

        SettingsDivider()
        SettingsGroup("Повторения") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_notifications_review_title),
                subtitle = "Когда накопились карточки словаря",
                checked = settings.reviewReminderEnabled,
                onCheckedChange = { value -> viewModel.update { setReviewReminder(value) } },
            )
        }

        SettingsNote(
            "Уведомления начнут приходить, когда будет готов модуль напоминаний — " +
                "настройки здесь уже сохраняются."
        )
    }
}
