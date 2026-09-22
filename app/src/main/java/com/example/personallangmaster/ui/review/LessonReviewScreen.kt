package com.example.personallangmaster.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.MistakeType
import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.domain.TranscriptFormatter

/**
 * Разбор урока: что получилось, что поправить и что пошло в словарь.
 *
 * Ошибки ученика показываем цветом tertiary, а не error: это учебная поправка,
 * а не сбой приложения, и красный здесь только демотивирует.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonReviewScreen(
    lessonId: Long,
    showTranscript: Boolean = false,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: LessonReviewViewModel = viewModel(
        factory = LessonReviewViewModel.factory(
            container.database.lessonDao(),
            container.analyzeLessonUseCase,
            container.lessonPlayer,
            container.ttsController,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var transcriptOpen by remember { mutableStateOf(showTranscript) }

    // Транскрипт открывают почитать, а не заплатить за разбор — открытие с этим
    // экраном не должно само по себе запускать платный вызов.
    LaunchedEffect(lessonId) { viewModel.load(lessonId, analyzeIfNeeded = !showTranscript) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Разбор урока") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Разбираем разговор…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            state.lesson?.let { lesson ->
                // Разобранным урок становится только после успешного разбора —
                // поэтому состояние видно прямо здесь, а не угадывается.
                if (lesson.status == LessonStatus.SKIPPED) {
                    // Это состояние выбрал сам человек («Закрыть без разбора») —
                    // карточка не должна выглядеть приглашением сделать то, от
                    // чего он только что отказался.
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Урок закрыт без разбора",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = state.error
                                    ?: "Ошибки и слова из него не появлялись — это было решение, " +
                                        "а не сбой.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.retry(lessonId, force = true) }) {
                                Text("Разобрать всё равно")
                            }
                        }
                    }
                } else if (lesson.status != LessonStatus.ANALYZED) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Урок ещё не разобран",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = state.error
                                    ?: "Нажмите, чтобы разобрать разговор — это займёт несколько секунд.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.retry(lessonId) }) {
                                Text("Разобрать урок")
                            }
                        }
                    }
                }

                lesson.summaryRu?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                if (state.praise.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                    ) {
                        Text(state.praise, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                state.levelChangedTo?.let { level ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                    ) {
                        Text(
                            "Уровень обновлён: ${level.name}",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (state.hasRecording) {
                    SectionTitle("Запись урока")
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.playing) {
                            OutlinedButton(onClick = viewModel::pausePlayback) {
                                Icon(Icons.Rounded.Pause, contentDescription = null)
                                Text("  Пауза")
                            }
                        } else {
                            OutlinedButton(onClick = viewModel::playLesson) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text("  Послушать урок")
                            }
                        }
                    }
                }

                SectionTitle("Оценки за урок")
                ScoreBar("Беглость", lesson.fluencyScore)
                ScoreBar("Точность", lesson.accuracyScore)
                ScoreBar("Словарный запас", lesson.vocabularyScore)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Уровень разговора", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        lesson.cefrEstimate?.name ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.vocabAdded > 0) {
                SectionTitle("В словарь добавлено слов: ${state.vocabAdded}")
            }

            if (state.mistakes.isNotEmpty()) {
                SectionTitle("Ошибки (${state.mistakes.size})")
                state.mistakes.forEach { row ->
                    MistakeCard(
                        row = row,
                        canPlay = state.hasRecording && row.audioOffsetMs != null,
                        onPlayMine = { viewModel.playMistake(row) },
                        onSpeakCorrect = { viewModel.speakCorrection(row.mistake.corrected) },
                    )
                }
            }

            if (state.nextFocus.isNotEmpty()) {
                SectionTitle("На что обратить внимание в следующий раз")
                state.nextFocus.forEach { focus ->
                    Text(
                        text = "• $focus",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }

            // Причину уже показала карточка «Урок ещё не разобран» — второй раз
            // повторять её незачем.
            val analyzed = state.lesson?.status == LessonStatus.ANALYZED
            state.error?.takeIf { analyzed }?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Разбор не получился: $error")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.retry(lessonId) }) {
                            Text("Попробовать снова")
                        }
                    }
                }
            }

            // Пустые реплики — след распознавания без результата: та же выборка,
            // что и в тексте, который уходит при «Поделиться транскриптом»,
            // иначе счётчик здесь и текст там расходятся.
            val visibleTurns = TranscriptFormatter.nonBlank(state.turns)
            if (visibleTurns.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(top = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { transcriptOpen = !transcriptOpen }
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Транскрипт (реплик: ${visibleTurns.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (transcriptOpen) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = if (transcriptOpen) "Свернуть" else "Развернуть",
                    )
                }

                if (transcriptOpen) {
                    visibleTurns.forEach { turn ->
                        val mine = turn.speaker == Speaker.USER
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                text = if (mine) "Я" else "Тренер",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (mine) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary,
                            )
                            Text(turn.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ScoreBar(title: String, value: Int?) {
    if (value == null) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text("$value", style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MistakeCard(
    row: MistakeRow,
    canPlay: Boolean,
    onPlayMine: () -> Unit,
    onSpeakCorrect: () -> Unit,
) {
    val mistake = row.mistake
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = typeTitle(mistake.type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(4.dp))
            Text(mistake.original, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "→ ${mistake.corrected}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            )
            mistake.explanationRu?.let { explanation ->
                Spacer(Modifier.height(4.dp))
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Как это прозвучало у вас — только если урок записывался
                // и фраза нашлась в транскрипте.
                if (canPlay) {
                    TextButton(onClick = onPlayMine) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Text(" Как я сказал")
                    }
                }
                TextButton(onClick = onSpeakCorrect) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = null)
                    Text(" Как правильно")
                }
            }
        }
    }
}

private fun typeTitle(type: MistakeType): String = when (type) {
    MistakeType.GRAMMAR -> "Грамматика"
    MistakeType.VOCAB -> "Слово не то"
    MistakeType.PRONUNCIATION -> "Произношение"
    MistakeType.WORD_ORDER -> "Порядок слов"
    MistakeType.ARTICLE -> "Артикль"
    MistakeType.TENSE -> "Время"
    MistakeType.PREPOSITION -> "Предлог"
    MistakeType.STYLE -> "Стиль"
}
