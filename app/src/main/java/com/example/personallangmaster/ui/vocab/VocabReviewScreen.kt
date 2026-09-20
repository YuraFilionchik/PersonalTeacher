package com.example.personallangmaster.ui.vocab

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.R
import com.example.personallangmaster.core.srs.ReviewGrade
import com.example.personallangmaster.data.db.ReviewMode
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.di.LocalAppContainer

/**
 * Повторение карточек. Работает без сети: озвучка и распознавание системные,
 * сроки считает локальный планировщик.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabReviewScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: VocabReviewViewModel = viewModel(
        factory = VocabReviewViewModel.factory(
            container.vocabRepository,
            container.profileRepository,
            container.statsRepository,
            container.ttsController,
            container.speechInput,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startSpeaking() }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vocab_due_today)) },
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
                .padding(innerPadding),
        ) {
            when {
                state.loading -> CenteredMessage { CircularProgressIndicator() }

                state.finished -> SessionSummary(state, onBack)

                else -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                    Text(
                        text = "${state.reviewed} из ${state.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )

                    state.current?.let { item ->
                        CardArea(
                            item = item,
                            state = state,
                            onReveal = viewModel::reveal,
                            onSpeakTerm = { slow -> viewModel.speakTerm(slow) },
                            onStartSpeaking = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED

                                if (granted) {
                                    viewModel.startSpeaking()
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )

                        Actions(
                            revealed = state.revealed,
                            onReveal = viewModel::reveal,
                            onGrade = viewModel::grade,
                            onPostpone = viewModel::postpone,
                            onSuspend = viewModel::suspendCurrent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardArea(
    item: VocabItemEntity,
    state: VocabReviewUiState,
    onReveal: () -> Unit,
    onSpeakTerm: (Boolean) -> Unit,
    onStartSpeaking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = modeTitle(state.mode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))

        when (state.mode) {
            // Видим английское слово, вспоминаем перевод.
            ReviewMode.RECOGNIZE -> {
                Prompt(item.term)
                if (state.revealed) Answer(item.translationRu)
            }

            // Видим перевод, вспоминаем английское слово.
            ReviewMode.RECALL -> {
                Prompt(item.translationRu)
                if (state.revealed) Answer(item.term)
            }

            // Видим перевод, произносим слово вслух.
            ReviewMode.SPEAK -> {
                Prompt(item.translationRu)
                Spacer(Modifier.height(16.dp))
                when (val speak = state.speak) {
                    SpeakState.Idle -> FilledTonalButton(onClick = onStartSpeaking) {
                        Icon(Icons.Rounded.Mic, contentDescription = null)
                        Spacer(Modifier.height(4.dp))
                        Text("Произнести")
                    }

                    SpeakState.Listening -> Text(
                        "Слушаю…",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    is SpeakState.Heard -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (speak.correct) "Узнал: «${speak.text}»"
                            else "Услышал «${speak.text}»",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (speak.correct) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                            textAlign = TextAlign.Center,
                        )
                        Answer(item.term)
                    }

                    is SpeakState.Failed -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            speak.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onStartSpeaking) { Text("Ещё раз") }
                        TextButton(onClick = onReveal) { Text("Показать слово") }
                    }
                }
            }

            // Слышим слово, вспоминаем значение.
            ReviewMode.LISTEN -> {
                IconButton(onClick = { onSpeakTerm(false) }) {
                    Icon(
                        Icons.Rounded.VolumeUp,
                        contentDescription = "Прослушать",
                        modifier = Modifier.padding(8.dp),
                    )
                }
                if (state.revealed) {
                    Prompt(item.term)
                    Answer(item.translationRu)
                }
            }
        }

        if (state.revealed) {
            item.exampleEn?.takeIf { it.isNotBlank() }?.let { example ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = example,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onSpeakTerm(false) }) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = "Прослушать")
                }
                IconButton(onClick = { onSpeakTerm(true) }) {
                    Icon(Icons.Rounded.SlowMotionVideo, contentDescription = "Медленно")
                }
            }
        }
    }
}

@Composable
private fun Prompt(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Answer(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Actions(
    revealed: Boolean,
    onReveal: () -> Unit,
    onGrade: (ReviewGrade) -> Unit,
    onPostpone: () -> Unit,
    onSuspend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (!revealed) {
            Button(
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Показать ответ") }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GradeButton(R.string.vocab_grade_again, Modifier.weight(1f)) {
                    onGrade(ReviewGrade.AGAIN)
                }
                GradeButton(R.string.vocab_grade_hard, Modifier.weight(1f)) {
                    onGrade(ReviewGrade.HARD)
                }
                GradeButton(R.string.vocab_grade_good, Modifier.weight(1f)) {
                    onGrade(ReviewGrade.GOOD)
                }
                GradeButton(R.string.vocab_grade_easy, Modifier.weight(1f)) {
                    onGrade(ReviewGrade.EASY)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onPostpone) { Text("Отложить") }
            TextButton(onClick = onSuspend) { Text("Больше не показывать") }
        }
    }
}

@Composable
private fun GradeButton(labelRes: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SessionSummary(state: VocabReviewUiState, onBack: () -> Unit) {
    CenteredMessage {
        if (state.total == 0) {
            Text(
                stringResource(R.string.vocab_empty),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Новые слова появятся здесь после урока.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "Повторено: ${state.reviewed}",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.forgotten == 0) {
                    "Все карточки вспомнились с первого раза."
                } else {
                    "Забытых: ${state.forgotten} — они вернутся завтра."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Готово") }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

private fun modeTitle(mode: ReviewMode): String = when (mode) {
    ReviewMode.RECOGNIZE -> "Узнать перевод"
    ReviewMode.RECALL -> "Вспомнить слово"
    ReviewMode.SPEAK -> "Произнести вслух"
    ReviewMode.LISTEN -> "Услышать и понять"
}

/** Карточка со словом из общего списка — используется на экране словаря. */
@Composable
fun VocabItemCard(item: VocabItemEntity, onSpeak: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.term, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.translationRu,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stateLabel(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onSpeak) {
                Icon(Icons.Rounded.VolumeUp, contentDescription = "Прослушать")
            }
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}

private fun stateLabel(item: VocabItemEntity): String = when (item.state.name) {
    "NEW" -> "Новое"
    "LEARNING" -> "Учится · интервал ${item.intervalDays} дн."
    "REVIEW" -> "На повторении · интервал ${item.intervalDays} дн."
    "MATURE" -> "Освоено · интервал ${item.intervalDays} дн."
    else -> "Скрыто"
}
