package com.example.personallangmaster.ui.pronunciation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.personallangmaster.data.db.entity.PhonemeEntity
import com.example.personallangmaster.di.LocalAppContainer

/**
 * Тренажёр произношения: карта звуков и три режима дрилла.
 *
 * Карта показывает не «правильность», а освоенность: звук, который ещё ни разу
 * не встречался, серый, а не красный — пугать ученика нечем.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PronunciationScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PronunciationViewModel = viewModel(
        factory = PronunciationViewModel.factory(
            container.contentRepository,
            container.vocabRepository,
            container.profileRepository,
            container.ttsController,
            container.speechInput,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startSpeaking() }

    val requestSpeak = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startSpeaking() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pronunciation_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (state.mode != null) viewModel.exitDrill() else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.mode == null) {
                HubContent(state, viewModel)
            } else {
                DrillContent(state, viewModel, requestSpeak)
            }
        }
    }

    state.selectedPhoneme?.let { phoneme ->
        PhonemeDialog(
            phoneme = phoneme,
            score = state.scores[phoneme.ipa],
            onDismiss = { viewModel.selectPhoneme(null) },
            onTrain = { viewModel.startDrill(DrillMode.MINIMAL_PAIRS, phoneme.ipa) },
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("Понятно") } },
            text = { Text(message) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HubContent(state: PronunciationUiState, viewModel: PronunciationViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("Режимы")

        ModeCard(
            title = "Повтори за тренером",
            subtitle = "Слова и фразы из вашего словаря",
            onClick = { viewModel.startDrill(DrillMode.REPEAT) },
        )
        ModeCard(
            title = stringResource(R.string.pronunciation_minimal_pairs),
            subtitle = "ship или sheep — различить на слух",
            onClick = { viewModel.startDrill(DrillMode.MINIMAL_PAIRS) },
        )
        ModeCard(
            title = "Проблемные звуки",
            subtitle = "Фокус на том, что даётся хуже всего",
            onClick = { viewModel.startDrill(DrillMode.WEAK_SOUNDS) },
        )

        SectionTitle(stringResource(R.string.pronunciation_phonemes_map))
        Text(
            text = "Цвет показывает освоенность звука. Нажмите, чтобы увидеть подсказку.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        listOf("VOWEL", "DIPHTHONG", "CONSONANT").forEach { type ->
            val group = state.phonemes.filter { it.type.equals(type, ignoreCase = true) }
            if (group.isEmpty()) return@forEach

            Text(
                text = typeTitle(type),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.forEach { phoneme ->
                    PhonemeChip(
                        phoneme = phoneme,
                        score = state.scores[phoneme.ipa],
                        onClick = { viewModel.selectPhoneme(phoneme) },
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DrillContent(
    state: PronunciationUiState,
    viewModel: PronunciationViewModel,
    onSpeak: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else state.index.toFloat() / state.total },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        if (state.finished) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Готово", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Узнано верно: ${state.correctCount} из ${state.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = viewModel::exitDrill) { Text("К тренажёру") }
            }
            return
        }

        val item = state.current ?: return

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.mode) {
                DrillMode.MINIMAL_PAIRS -> {
                    Text(
                        "Какое слово прозвучало?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOfNotNull(item.text, item.counterpart).forEach { word ->
                            val answered = state.pairAnswer != null
                            val isPlayed = word == state.playedWord
                            OutlinedButton(
                                onClick = { if (!answered) viewModel.answerPair(word) },
                                enabled = !answered,
                            ) {
                                Text(
                                    text = word,
                                    color = when {
                                        !answered -> MaterialTheme.colorScheme.onSurface
                                        isPlayed -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                    if (state.pairAnswer != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (state.pairAnswer == state.playedWord) {
                                "Верно: ${state.playedWord}"
                            } else {
                                "Прозвучало: ${state.playedWord}"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.pairAnswer == state.playedWord) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        )
                    }
                }

                else -> {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    item.translation?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item.hintRu?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    when (val attempt = state.attempt) {
                        AttemptState.Idle -> FilledTonalButton(onClick = onSpeak) {
                            Icon(Icons.Rounded.Mic, contentDescription = null)
                            Spacer(Modifier.height(4.dp))
                            Text("Повторить вслух")
                        }

                        AttemptState.Listening -> Text(
                            "Слушаю…",
                            style = MaterialTheme.typography.titleMedium,
                        )

                        is AttemptState.Heard -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (attempt.correct) {
                                    "Похоже! Услышал: «${attempt.text}»"
                                } else {
                                    "Услышал «${attempt.text}» — попробуйте ещё раз"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (attempt.correct) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                                textAlign = TextAlign.Center,
                            )
                            // После удачной попытки повтор не нужен: иначе одно задание
                            // засчитывалось бы как несколько верных.
                            if (!attempt.correct) {
                                Spacer(Modifier.height(8.dp))
                                FilledTonalButton(onClick = onSpeak) {
                                    Icon(Icons.Rounded.Mic, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ещё раз")
                                }
                            }
                        }

                        is AttemptState.Failed -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                attempt.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onSpeak) { Text("Ещё раз") }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                IconButton(onClick = { viewModel.play(slow = false) }) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = "Прослушать")
                }
                IconButton(onClick = { viewModel.play(slow = true) }) {
                    Icon(Icons.Rounded.SlowMotionVideo, contentDescription = "Медленно")
                }
            }
            Button(onClick = viewModel::next) {
                Text(if (state.index == state.total - 1) "Завершить" else "Дальше")
            }
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
private fun ModeCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhonemeChip(phoneme: PhonemeEntity, score: Int?, onClick: () -> Unit) {
    val background = when {
        score == null -> MaterialTheme.colorScheme.surfaceVariant
        score >= 75 -> MaterialTheme.colorScheme.primaryContainer
        score >= 45 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = phoneme.ipa,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PhonemeDialog(
    phoneme: PhonemeEntity,
    score: Int?,
    onDismiss: () -> Unit,
    onTrain: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Звук ${phoneme.ipa}") },
        text = {
            Column {
                Text(phoneme.hintRu, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        score == null -> "Этот звук ещё не встречался в тренировках"
                        score >= 75 -> "Звучит уверенно"
                        score >= 45 -> "Получается через раз"
                        else -> "Пока даётся тяжело"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onTrain()
                }
            ) { Text("Тренировать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

private fun typeTitle(type: String): String = when (type.uppercase()) {
    "VOWEL" -> "Гласные"
    "DIPHTHONG" -> "Дифтонги"
    "CONSONANT" -> "Согласные"
    else -> type
}
