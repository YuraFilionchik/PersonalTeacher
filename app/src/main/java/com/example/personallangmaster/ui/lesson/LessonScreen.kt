package com.example.personallangmaster.ui.lesson

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.ai.live.LiveErrorKind
import com.example.personallangmaster.ai.live.LiveSessionState
import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.data.db.LessonMode
import com.example.personallangmaster.data.prefs.MicMode
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.ui.components.CorrectionCard
import com.example.personallangmaster.ui.components.MicButton
import com.example.personallangmaster.ui.components.MicButtonState
import com.example.personallangmaster.ui.components.MicGesture
import com.example.personallangmaster.ui.components.SubtitleLine
import com.example.personallangmaster.ui.components.WaveformVisualizer
import com.example.personallangmaster.data.db.Speaker as DbSpeaker
import com.example.personallangmaster.ui.components.Speaker as UiSpeaker

/**
 * Экран живого урока: субтитры, карточки поправок и большая кнопка микрофона.
 *
 * Пока урок не начат, экран показывает панель запуска с оценкой стоимости —
 * так решение «поговорить десять минут» остаётся осознанным.
 */
@Composable
fun LessonScreen(
    scenarioId: String? = null,
    onOpenSettings: () -> Unit = {},
    onOpenReview: (Long) -> Unit = {},
) {
    val container = LocalAppContainer.current
    val viewModel: LessonViewModel = viewModel(
        factory = LessonViewModel.factory(
            container.settingsRepository,
            container.profileRepository,
            container.lessonRepository,
            container.statsRepository,
            container.audioFileStore,
            container.ttsController,
            container.analysisScheduler,
            container.appScope,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val scenario = viewModel.state.value.scenario
            viewModel.startLesson(
                mode = if (scenario != null) LessonMode.SCENARIO else LessonMode.FREE_TALK,
                scenarioId = scenario?.id,
            )
        }
    }

    // Пока урок идёт, процесс держит передний сервис: иначе запись оборвётся
    // при погасшем экране.
    LaunchedEffect(state.isActive) {
        if (state.isActive) {
            LessonForegroundService.start(context)
        } else {
            LessonForegroundService.stop(context)
        }
    }

    LaunchedEffect(Unit) {
        LessonForegroundService.stopRequestFlow.collect { viewModel.endLesson() }
    }

    LaunchedEffect(scenarioId) { viewModel.prepareScenario(scenarioId) }

    val startLesson: (LessonMode) -> Unit = { mode ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.startLesson(mode, state.scenario?.id)
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    state.placement?.let { prompt ->
        PlacementDialog(
            prompt = prompt,
            onConfirm = { startLesson(LessonMode.PLACEMENT) },
            onDismiss = viewModel::dismissPlacement,
        )
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LessonHeader(state)

            Box(Modifier.weight(1f)) {
                if (state.isActive || state.subtitles.isNotEmpty()) {
                    SubtitleStream(state)
                } else {
                    StartPanel(
                        state = state,
                        onStart = startLesson,
                        onPlacement = viewModel::requestPlacement,
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            when {
                state.isActive -> LessonControls(state, viewModel)
                // После завершения урока экран не должен превращаться в тупик:
                // история остаётся на месте, а внизу появляются понятные действия.
                state.subtitles.isNotEmpty() -> FinishedBar(
                    state = state,
                    onRestart = {
                        viewModel.resetForNewLesson()
                        viewModel.startLesson(state.mode, state.scenario?.id)
                    },
                    onOpenReview = { viewModel.lastLessonId?.let(onOpenReview) },
                    onClearHistory = viewModel::resetForNewLesson,
                )
            }
        }
    }
}

@Composable
private fun LessonHeader(state: LessonUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = when (state.mode) {
                    LessonMode.FREE_TALK -> "Свободный разговор"
                    LessonMode.SCENARIO -> "Сценарий"
                    LessonMode.PLACEMENT -> "Тест уровня"
                    LessonMode.DRILL -> "Дрилл"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = statusText(state.sessionState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "%02d:%02d".format(state.elapsedSeconds / 60, state.elapsedSeconds % 60),
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.costUsd > 0) {
                Text(
                    text = CostCalculator.formatUsd(state.costUsd),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    state.budgetWarning?.let { warning ->
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SubtitleStream(state: LessonUiState) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.subtitles.size, state.subtitles.lastOrNull()?.text) {
        if (state.subtitles.isNotEmpty()) {
            listState.animateScrollToItem(state.subtitles.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.subtitles) { item ->
                SubtitleLine(
                    speaker = when (item.speaker) {
                        DbSpeaker.TUTOR -> UiSpeaker.TUTOR
                        DbSpeaker.USER -> UiSpeaker.USER
                    },
                    text = item.text,
                    isPartial = item.isPartial,
                )
            }
        }

        state.hints.forEach { hint ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(hint.textEn, style = MaterialTheme.typography.titleMedium)
                    hint.textRu?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonControls(state: LessonUiState, viewModel: LessonViewModel) {
    Column(Modifier.fillMaxWidth()) {
        state.corrections.forEach { correction ->
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically { it / 2 },
            ) {
                CorrectionCard(
                    original = correction.original,
                    corrected = correction.corrected,
                    explanation = correction.explanation,
                    onListen = { viewModel.speakCorrection(correction.corrected) },
                    onAddToVocab = { viewModel.addCorrectionToVocab(correction) },
                    onDismiss = { viewModel.dismissCorrection(correction.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        if (state.chatMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text("Написать тренеру") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = viewModel::sendDraft) {
                    Icon(Icons.Rounded.Send, contentDescription = "Отправить")
                }
            }
        }

        WaveformVisualizer(
            amplitudes = waveformOf(state),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::toggleChatMode) {
                Icon(Icons.Rounded.Chat, contentDescription = "Режим чата")
            }

            MicButton(
                state = micState(state),
                levelDbfs = if (state.sessionState is LiveSessionState.Speaking) {
                    state.tutorLevelDbfs
                } else {
                    state.micLevelDbfs
                },
                gesture = micGesture(state.micMode),
                onPress = viewModel::onMicPress,
                onRelease = viewModel::onMicRelease,
                onTap = viewModel::onMicTap,
            )

            IconButton(onClick = viewModel::endLesson) {
                Icon(Icons.Rounded.Stop, contentDescription = "Завершить урок")
            }
        }
    }
}

@Composable
private fun FinishedBar(
    state: LessonUiState,
    onRestart: () -> Unit,
    onOpenReview: () -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Урок завершён · %02d:%02d · %s".format(
                state.elapsedSeconds / 60,
                state.elapsedSeconds % 60,
                CostCalculator.formatUsd(state.costUsd),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRestart) { Text("Начать заново") }
            OutlinedButton(onClick = onOpenReview) { Text("Разбор урока") }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onClearHistory) { Text("Очистить экран") }
    }
}

@Composable
private fun StartPanel(
    state: LessonUiState,
    onStart: (LessonMode) -> Unit,
    onPlacement: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val error = state.sessionState as? LiveSessionState.Error

        if (error != null) {
            Text(
                text = errorTitle(error.kind),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            if (error.kind == LiveErrorKind.NO_KEY || error.kind == LiveErrorKind.INVALID_KEY) {
                Button(onClick = onOpenSettings) { Text("Открыть настройки") }
                Spacer(Modifier.height(8.dp))
            }
        } else if (state.scenario != null) {
            val scenario = state.scenario
            Text(
                text = scenario.titleRu,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = scenario.descriptionRu,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            // Цель проговариваем заранее: без неё ролевая игра превращается
            // в обычную беседу и не даёт ощущения выполненной задачи.
            Text(
                text = "Ваша задача: ${scenario.goal}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (scenario.vocabHints.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Пригодятся: ${scenario.vocabHints.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        } else {
            Text(
                text = "Готовы поговорить?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Десять минут разговора стоят примерно " +
                    CostCalculator.formatUsd(CostCalculator.estimateLessonCostUsd(10.0)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }

        val primaryMode = if (state.scenario != null) LessonMode.SCENARIO else LessonMode.FREE_TALK
        Button(onClick = { onStart(primaryMode) }) {
            Text(
                when {
                    error != null -> "Попробовать снова"
                    state.scenario != null -> "Начать сценарий"
                    else -> "Начать урок"
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        if (state.scenario == null) {
            // Не запускаем тест прямо отсюда: сначала диалог объясняет,
            // что именно произойдёт с уровнем профиля.
            OutlinedButton(onClick = onPlacement) {
                Text("Пройти тест уровня")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Тест — это пятиминутная беседа без поправок: тренер постепенно " +
                    "усложняет вопросы, а в конце по разбору ставится уровень.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/**
 * Объяснение теста уровня перед запуском.
 *
 * Тест переписывает уровень всего профиля — от него зависят и сложность
 * разговора, и подбор сценариев, — поэтому человек должен согласиться
 * осознанно, а не промахнуться мимо кнопки «Начать урок».
 */
@Composable
private fun PlacementDialog(
    prompt: PlacementPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (prompt.alreadyPassed) "Пройти тест заново" else "Тест уровня") },
        text = {
            Column {
                Text(
                    text = "${prompt.minutes} минут разговора без поправок: тренер начинает " +
                        "с бытовых тем и постепенно переходит к сложным. Говорите как " +
                        "получается — задача теста не оценить вас строго, а подобрать " +
                        "посильную сложность.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (prompt.levelLocked) {
                        "Уровень зафиксирован в настройках: результат теста будет показан " +
                            "в разборе, но профиль останется на ${prompt.currentLevel.name}."
                    } else {
                        "Сейчас ваш уровень — ${prompt.currentLevel.name}. " +
                            "После разбора он будет заменён оценкой теста, даже если она ниже."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Примерная стоимость: " +
                        CostCalculator.formatUsd(prompt.estimatedCostUsd),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Начать тест") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Не сейчас") } },
    )
}

private fun micState(state: LessonUiState): MicButtonState = when (state.sessionState) {
    is LiveSessionState.Ready -> MicButtonState.READY
    is LiveSessionState.Listening -> MicButtonState.LISTENING
    is LiveSessionState.Thinking -> MicButtonState.THINKING
    is LiveSessionState.Speaking -> MicButtonState.SPEAKING
    else -> MicButtonState.DISABLED
}

/** Удержание — только в режиме «удерживать»; остальные режимы работают одиночным тапом. */
private fun micGesture(mode: MicMode): MicGesture = when (mode) {
    MicMode.HOLD -> MicGesture.HOLD
    MicMode.TAP, MicMode.HANDS_FREE -> MicGesture.TAP
}

private fun statusText(state: LiveSessionState): String = when (state) {
    LiveSessionState.Idle -> "Не начат"
    LiveSessionState.Connecting -> "Подключение…"
    LiveSessionState.Ready -> "Можно говорить"
    LiveSessionState.Listening -> "Слушаю"
    LiveSessionState.Thinking -> "Думает…"
    LiveSessionState.Speaking -> "Говорит"
    is LiveSessionState.Reconnecting -> "Переподключение (${state.attempt})"
    is LiveSessionState.Error -> "Ошибка"
    LiveSessionState.Closed -> "Урок завершён"
}

private fun errorTitle(kind: LiveErrorKind): String = when (kind) {
    LiveErrorKind.NO_KEY -> "Нужен ключ Gemini"
    LiveErrorKind.INVALID_KEY -> "Ключ не принят"
    LiveErrorKind.QUOTA -> "Квота исчерпана"
    LiveErrorKind.NETWORK -> "Нет связи"
    LiveErrorKind.PROTOCOL -> "Сервер ответил неожиданно"
    LiveErrorKind.AUDIO_DEVICE -> "Микрофон недоступен"
    LiveErrorKind.BUDGET_LIMIT -> "Лимит расходов"
}

/** Простая волна по текущему уровню: полноценные амплитуды появятся из буфера аудио. */
private fun waveformOf(state: LessonUiState): FloatArray {
    val level = when (state.sessionState) {
        is LiveSessionState.Speaking -> state.tutorLevelDbfs
        else -> state.micLevelDbfs
    }
    val normalized = ((level + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
    return FloatArray(WAVEFORM_BARS) { index ->
        val phase = (index.toFloat() / WAVEFORM_BARS) * 2f
        normalized * (0.4f + 0.6f * kotlin.math.abs(kotlin.math.sin(phase * Math.PI)).toFloat())
    }
}

private const val WAVEFORM_BARS = 32
