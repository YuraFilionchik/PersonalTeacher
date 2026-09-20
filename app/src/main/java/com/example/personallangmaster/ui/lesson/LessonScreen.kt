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
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.ui.components.CorrectionCard
import com.example.personallangmaster.ui.components.MicButton
import com.example.personallangmaster.ui.components.MicButtonState
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
fun LessonScreen(onOpenSettings: () -> Unit = {}) {
    val container = LocalAppContainer.current
    val viewModel: LessonViewModel = viewModel(
        factory = LessonViewModel.factory(
            container.settingsRepository,
            container.profileRepository,
            container.lessonRepository,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startLesson() }

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
                        onStart = { mode ->
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED

                            if (granted) {
                                viewModel.startLesson(mode)
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onOpenSettings = onOpenSettings,
                    )
                }
            }

            if (state.isActive) {
                LessonControls(state, viewModel)
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

    LaunchedEffect(state.subtitles.size) {
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
                    onListen = { },
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
private fun StartPanel(
    state: LessonUiState,
    onStart: (LessonMode) -> Unit,
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

        Button(onClick = { onStart(LessonMode.FREE_TALK) }) {
            Text(if (error != null) "Попробовать снова" else "Начать урок")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onStart(LessonMode.PLACEMENT) }) {
            Text("Пройти тест уровня")
        }
    }
}

private fun micState(state: LessonUiState): MicButtonState = when (state.sessionState) {
    is LiveSessionState.Ready -> MicButtonState.READY
    is LiveSessionState.Listening -> MicButtonState.LISTENING
    is LiveSessionState.Thinking -> MicButtonState.THINKING
    is LiveSessionState.Speaking -> MicButtonState.SPEAKING
    else -> MicButtonState.DISABLED
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
