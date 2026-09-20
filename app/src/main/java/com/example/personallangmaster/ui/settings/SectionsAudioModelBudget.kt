package com.example.personallangmaster.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personallangmaster.R
import com.example.personallangmaster.ai.ModelCatalog
import com.example.personallangmaster.ai.text.KeyCheckResult
import com.example.personallangmaster.data.prefs.AudioOutput
import com.example.personallangmaster.data.prefs.LimitBehavior
import com.example.personallangmaster.data.prefs.MicMode

@Composable
fun AudioSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_section_audio), onBack) {
        SettingsGroup("Микрофон") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_audio_mic_mode_title),
                subtitle = stringResource(R.string.settings_audio_mic_mode_subtitle),
                options = MicMode.entries,
                selected = settings.micMode,
                optionLabel = ::micModeTitle,
                optionDescription = ::micModeHint,
                onSelect = { value -> viewModel.update { setMicMode(value) } },
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_audio_vad_sensitivity_title),
                valueLabel = "${settings.vadThresholdDb.toInt()} dB",
                value = settings.vadThresholdDb.toFloat(),
                range = -60f..-20f,
                onValueChange = { value -> viewModel.update { setVadThreshold(value.toDouble()) } },
            )
            SettingsNote(
                "Чем выше порог, тем меньше приложение реагирует на фоновый шум — но тихую речь " +
                    "тоже может не услышать."
            )
            SettingsNote(silenceHangoverHint(settings.micMode))
            SettingsSliderRow(
                title = "Пауза до конца реплики",
                valueLabel = "${settings.silenceHangoverMs} мс",
                value = settings.silenceHangoverMs.toFloat(),
                range = 200f..3000f,
                steps = 13,
                onValueChange = { value -> viewModel.update { setSilenceHangover(value.toInt()) } },
            )
        }

        SettingsDivider()
        SettingsGroup("Разговор") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_audio_barge_in_title),
                subtitle = stringResource(R.string.settings_audio_barge_in_subtitle),
                checked = settings.bargeInEnabled,
                onCheckedChange = { value -> viewModel.update { setBargeIn(value) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_audio_noise_suppression_title),
                subtitle = "Шумоподавление и эхоподавление на стороне устройства",
                checked = settings.noiseSuppression,
                onCheckedChange = { value -> viewModel.update { setNoiseSuppression(value) } },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_audio_output_device_title),
                options = AudioOutput.entries,
                selected = settings.audioOutput,
                optionLabel = ::audioOutputTitle,
                onSelect = { value -> viewModel.update { setAudioOutput(value) } },
            )
            SettingsSliderRow(
                title = "Громкость тренера",
                valueLabel = "${(settings.tutorVolume * 100).toInt()}%",
                value = settings.tutorVolume,
                range = 0f..1f,
                onValueChange = { value -> viewModel.update { setTutorVolume(value) } },
            )
        }
    }
}

@Composable
fun ModelSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keyCheck by viewModel.keyCheck.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf("") }

    SettingsScaffold(stringResource(R.string.settings_section_model), onBack) {
        SettingsGroup(stringResource(R.string.settings_model_api_key_title)) {
            SettingsNote(
                if (settings.hasApiKey) {
                    "Ключ сохранён на этом устройстве в зашифрованном виде. " +
                        "Введите новый, чтобы заменить его."
                } else {
                    "Ключ ещё не задан. Получите его бесплатно в Google AI Studio " +
                        "(aistudio.google.com), раздел «Get API key»."
                }
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(stringResource(R.string.settings_model_api_key_title)) },
                singleLine = true,
                visualTransformation = remember { PasswordVisualTransformation() },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        viewModel.saveApiKey(keyInput)
                        keyInput = ""
                    },
                    enabled = keyInput.isNotBlank(),
                ) { Text("Сохранить") }

                OutlinedButton(
                    onClick = viewModel::checkApiKey,
                    enabled = settings.hasApiKey && keyCheck !is KeyCheckState.Running,
                ) { Text(stringResource(R.string.settings_model_test_key)) }
            }
            keyCheckMessage(keyCheck)?.let { SettingsNote(it) }
        }

        SettingsDivider()
        SettingsGroup("Модели") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_model_live_model_title),
                subtitle = "Модель живого голосового урока",
                options = ModelCatalog.liveModels,
                selected = ModelCatalog.liveModels.firstOrNull { it.id == settings.liveModelId }
                    ?: ModelCatalog.liveModels.first(),
                optionLabel = { it.titleRu },
                optionDescription = { it.noteRu },
                onSelect = { option -> viewModel.update { setLiveModel(option.id) } },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_model_text_model_title),
                subtitle = "Разбор урока и генерация упражнений",
                options = ModelCatalog.textModels,
                selected = ModelCatalog.textModels.firstOrNull { it.id == settings.textModelId }
                    ?: ModelCatalog.textModels.first(),
                optionLabel = { it.titleRu },
                optionDescription = { it.noteRu },
                onSelect = { option -> viewModel.update { setTextModel(option.id) } },
            )
            SettingsSliderRow(
                title = "Температура",
                valueLabel = "%.1f".format(settings.temperature),
                value = settings.temperature,
                range = 0f..2f,
                steps = 19,
                onValueChange = { value -> viewModel.update { setTemperature(value) } },
            )
        }

        SettingsDivider()
        SettingsGroup("Сессия") {
            SettingsSwitchRow(
                title = "Транскрипция речи",
                subtitle = "Нужна для субтитров и разбора урока. Без неё тренер просто говорит.",
                checked = settings.transcriptionEnabled,
                onCheckedChange = { value -> viewModel.update { setTranscription(value) } },
            )
            SettingsSwitchRow(
                title = "Сжатие контекста",
                subtitle = "Позволяет вести долгий урок без обрыва сессии",
                checked = settings.contextCompression,
                onCheckedChange = { value -> viewModel.update { setContextCompression(value) } },
            )
            SettingsSwitchRow(
                title = "Возобновление сессии",
                subtitle = "После обрыва связи урок продолжается с того же места",
                checked = settings.sessionResumption,
                onCheckedChange = { value -> viewModel.update { setSessionResumption(value) } },
            )
        }
    }
}

private fun keyCheckMessage(state: KeyCheckState): String? = when (state) {
    KeyCheckState.Idle -> null
    KeyCheckState.Running -> "Проверяем ключ…"
    is KeyCheckState.Done -> when (val result = state.result) {
        is KeyCheckResult.Valid -> "Ключ рабочий, доступно моделей: ${result.modelsAvailable}"
        KeyCheckResult.Invalid -> "Ключ не принят. Проверьте, что скопировали его целиком."
        KeyCheckResult.QuotaExceeded -> "Ключ рабочий, но квота исчерпана — попробуйте позже."
        KeyCheckResult.NoNetwork -> "Нет соединения с сервером."
        is KeyCheckResult.Unknown -> "Непонятный ответ: ${result.message}"
    }
}

@Composable
fun BudgetSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val spentToday by viewModel.spentTodayUsd.collectAsStateWithLifecycle()
    val spentMonth by viewModel.spentMonthUsd.collectAsStateWithLifecycle()
    var pinDialogOpen by remember { mutableStateOf(false) }

    SettingsScaffold(stringResource(R.string.settings_section_budget), onBack) {
        SettingsGroup("Лимиты") {
            SettingsRow(
                title = "Израсходовано сегодня",
                value = "\$${"%.2f".format(spentToday)}",
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_budget_daily_title),
                valueLabel = "\$${"%.2f".format(settings.dailyLimitUsd)}",
                value = settings.dailyLimitUsd.toFloat(),
                range = 0f..5f,
                steps = 19,
                onValueChange = { value -> viewModel.update { setDailyLimitUsd(value.toDouble()) } },
            )
            SettingsDivider()
            SettingsRow(
                title = "Израсходовано в этом месяце",
                value = "\$${"%.2f".format(spentMonth)}",
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_budget_monthly_title),
                valueLabel = "\$${"%.0f".format(settings.monthlyLimitUsd)}",
                value = settings.monthlyLimitUsd.toFloat(),
                range = 0f..100f,
                steps = 19,
                onValueChange = { value -> viewModel.update { setMonthlyLimitUsd(value.toDouble()) } },
            )
            SettingsSliderRow(
                title = "Лимит минут в день",
                valueLabel = "${settings.dailyLimitMinutes} мин",
                value = settings.dailyLimitMinutes.toFloat(),
                range = 0f..120f,
                steps = 11,
                onValueChange = { value -> viewModel.update { setDailyLimitMinutes(value.toInt()) } },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_budget_behavior_title),
                options = LimitBehavior.entries,
                selected = settings.limitBehavior,
                optionLabel = ::limitBehaviorTitle,
                onSelect = { value -> viewModel.update { setLimitBehavior(value) } },
            )
            SettingsNote(
                "Ориентир: минута голосового разговора стоит около \$0.021, " +
                    "десятиминутный урок — около \$0.21."
            )
        }

        SettingsDivider()
        SettingsGroup("Экономия") {
            SettingsSliderRow(
                title = "Авто-стоп при молчании",
                valueLabel = if (settings.idleAutoStopSeconds == 0) {
                    "выключен"
                } else {
                    "${settings.idleAutoStopSeconds} с"
                },
                value = settings.idleAutoStopSeconds.toFloat(),
                range = 0f..300f,
                steps = 11,
                onValueChange = { value -> viewModel.update { setIdleAutoStop(value.toInt()) } },
            )
        }

        SettingsDivider()
        SettingsGroup("Тарифы") {
            SettingsNote(
                "Тарифы Google меняются. Здесь их можно поправить, чтобы оценка расходов " +
                    "оставалась честной. Цены — доллары за миллион токенов."
            )
            PriceField("Аудио на вход", settings.priceAudioInPerMTok) { value ->
                viewModel.update { setPricing(audioIn = value) }
            }
            PriceField("Аудио на выход", settings.priceAudioOutPerMTok) { value ->
                viewModel.update { setPricing(audioOut = value) }
            }
            PriceField("Текст на вход", settings.priceTextInPerMTok) { value ->
                viewModel.update { setPricing(textIn = value) }
            }
            PriceField("Текст на выход", settings.priceTextOutPerMTok) { value ->
                viewModel.update { setPricing(textOut = value) }
            }
            PriceField("Курс доллара к рублю", settings.usdToRubRate) { value ->
                viewModel.update { setPricing(usdToRub = value) }
            }
        }

        SettingsDivider()
        SettingsGroup("Защита") {
            SettingsRow(
                title = stringResource(R.string.settings_budget_parental_pin_title),
                subtitle = if (settings.pinProtected) {
                    "PIN установлен: настройки ключа и лимитов защищены"
                } else {
                    "Без PIN любой может изменить ключ и лимиты"
                },
                value = if (settings.pinProtected) "Изменить" else "Задать",
                onClick = { pinDialogOpen = true },
            )
        }
    }

    if (pinDialogOpen) {
        PinDialog(
            hasPin = settings.pinProtected,
            onDismiss = { pinDialogOpen = false },
            onSave = { pin ->
                viewModel.setPin(pin)
                pinDialogOpen = false
            },
        )
    }
}

@Composable
private fun PriceField(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.replace(',', '.').toDoubleOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun PinDialog(hasPin: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val mismatch = pin.isNotEmpty() && repeat.isNotEmpty() && pin != repeat

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPin) "Изменить PIN" else "Задать PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = { Text("PIN (4–6 цифр)") },
                    singleLine = true,
                    visualTransformation = remember { PasswordVisualTransformation() },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it.filter(Char::isDigit).take(6) },
                    label = { Text("Повторите PIN") },
                    singleLine = true,
                    visualTransformation = remember { PasswordVisualTransformation() },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (mismatch) {
                    Text(
                        "PIN-коды не совпадают",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (hasPin) {
                    Text(
                        "Пустой PIN снимет защиту.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pin) },
                enabled = (pin.length >= 4 && pin == repeat) || (hasPin && pin.isEmpty()),
            ) { Text(stringResource(R.string.dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
