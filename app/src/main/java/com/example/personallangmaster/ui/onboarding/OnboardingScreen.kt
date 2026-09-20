package com.example.personallangmaster.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.ai.prompt.Personas
import com.example.personallangmaster.ai.prompt.Voices
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.di.LocalAppContainer

/**
 * Первый запуск: собираем то, без чего тренер не будет личным.
 * Экран намеренно линейный — назад можно, но никаких ветвлений.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.factory(
            container.profileRepository,
            container.settingsRepository,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onMicPermissionResult(granted) }

    // Переход дальше — эффект, а не действие во время композиции.
    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))

            AnimatedContent(targetState = state.step, label = "onboarding-step") { step ->
                Column {
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeStep(state, viewModel)
                        OnboardingStep.INTERESTS -> InterestsStep(state, viewModel)
                        OnboardingStep.TUTOR -> TutorStep(state, viewModel)
                        OnboardingStep.LEVEL -> LevelStep(state, viewModel)
                        OnboardingStep.API_KEY -> ApiKeyStep(state, viewModel)
                        OnboardingStep.PERMISSION -> PermissionStep(state)
                        OnboardingStep.DONE -> Unit
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step != OnboardingStep.WELCOME) {
                    TextButton(onClick = viewModel::back) { Text("Назад") }
                } else {
                    Spacer(Modifier)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.step == OnboardingStep.API_KEY) {
                        TextButton(onClick = viewModel::skipApiKey) { Text("Позже") }
                    }
                    Button(
                        onClick = {
                            if (state.step == OnboardingStep.PERMISSION && !state.micGranted) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    viewModel.onMicPermissionResult(true)
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@Button
                                }
                            }
                            viewModel.next()
                        },
                        enabled = state.canContinue && !state.saving,
                    ) {
                        Text(
                            when (state.step) {
                                OnboardingStep.PERMISSION ->
                                    if (state.micGranted) "Начать" else "Разрешить микрофон"
                                else -> "Далее"
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomeStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeader(
        title = "Привет!",
        subtitle = "Я буду вашим личным преподавателем английского. Как к вам обращаться?",
    )
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Имя") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeader(
        title = "О чём будем говорить?",
        subtitle = "Выберите темы, которые вам интересны — уроки будут строиться вокруг них.",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OnboardingViewModel.suggestedInterests.forEach { interest ->
            FilterChip(
                selected = interest in state.interests,
                onClick = { viewModel.toggleInterest(interest) },
                label = { Text(interest) },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = state.goal,
        onValueChange = viewModel::onGoalChange,
        label = { Text("Ваша цель") },
        placeholder = { Text("Например: свободно говорить на рабочих созвонах") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeader(
        title = "Какой тренер вам подойдёт?",
        subtitle = "Характер и голос можно поменять в любой момент в настройках.",
    )
    Personas.all.forEach { persona ->
        val selected = persona.id == state.persona.id
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .selectable(selected = selected) { viewModel.onPersonaChange(persona) },
            colors = CardDefaults.cardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = { viewModel.onPersonaChange(persona) })
                Column(Modifier.padding(start = 8.dp)) {
                    Text(persona.titleRu, style = MaterialTheme.typography.titleMedium)
                    Text(
                        persona.descriptionRu,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("Голос", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Voices.all.forEach { voice ->
            FilterChip(
                selected = voice == state.voiceName,
                onClick = { viewModel.onVoiceChange(voice) },
                label = { Text(voice) },
            )
        }
    }
}

@Composable
private fun LevelStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeader(
        title = "Ваш уровень",
        subtitle = "Выберите примерно — после первых уроков тренер уточнит его сам.",
    )
    Cefr.entries.forEach { level ->
        val selected = level == state.level
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected) { viewModel.onLevelChange(level) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = { viewModel.onLevelChange(level) })
            Column(Modifier.padding(start = 8.dp)) {
                Text(level.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    levelHint(level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "Цель на день: ${state.dailyGoalMinutes} мин",
        style = MaterialTheme.typography.titleSmall,
    )
    Slider(
        value = state.dailyGoalMinutes.toFloat(),
        onValueChange = { viewModel.onDailyGoalChange(it.toInt()) },
        valueRange = 5f..60f,
        steps = 10,
    )
}

private fun levelHint(level: Cefr): String = when (level) {
    Cefr.A1 -> "Знаю отдельные слова и простые фразы"
    Cefr.A2 -> "Могу объясниться в простых бытовых ситуациях"
    Cefr.B1 -> "Поддерживаю разговор на знакомые темы"
    Cefr.B2 -> "Говорю свободно, но с ошибками"
    Cefr.C1 -> "Говорю уверенно, шлифую детали"
}

@Composable
private fun ApiKeyStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepHeader(
        title = "Ключ Gemini API",
        subtitle = "Ключ нужен, чтобы тренер заговорил. Получите бесплатный ключ в Google AI " +
            "Studio (aistudio.google.com), раздел «Get API key». Ключ хранится только на этом " +
            "телефоне в зашифрованном виде и никуда больше не отправляется.",
    )
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = viewModel::onApiKeyChange,
        label = { Text("API-ключ") },
        singleLine = true,
        visualTransformation = remember { PasswordVisualTransformation() },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.apiKeySaved) {
        Spacer(Modifier.height(8.dp))
        Text("Ключ сохранён", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Примерная стоимость: десятиминутный разговор — около \$0.21. " +
            "Дневной лимит расходов можно задать в настройках.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionStep(state: OnboardingUiState) {
    StepHeader(
        title = "Доступ к микрофону",
        subtitle = "Без микрофона возможен только текстовый чат. Запись идёт лишь во время " +
            "урока и по умолчанию нигде не сохраняется.",
    )
    if (state.micGranted) {
        Text("Микрофон разрешён — всё готово к первому уроку.")
    }
}
