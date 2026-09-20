package com.example.personallangmaster.ui.grammar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.R
import com.example.personallangmaster.data.db.ExerciseKind
import com.example.personallangmaster.di.LocalAppContainer

/**
 * Грамматика: темы по вашим ошибкам, объяснения и упражнения на своём материале.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: GrammarViewModel = viewModel(
        factory = GrammarViewModel.factory(
            container.contentRepository,
            container.profileRepository,
            container.generateExercisesUseCase,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.grammar_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (state.openTopic != null) viewModel.closeTopic() else onBack() }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.openTopic == null -> TopicList(state, viewModel)
                state.practicing -> ExerciseRunner(state, viewModel)
                else -> TopicDetail(state, viewModel)
            }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            confirmButton = { TextButton(onClick = viewModel::clearMessage) { Text("Понятно") } },
            text = { Text(message) },
        )
    }
}

@Composable
private fun TopicList(state: GrammarUiState, viewModel: GrammarViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.recommended.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.grammar_recommended)) }
            items(state.recommended, key = { "rec_${it.topic.id}" }) { row ->
                TopicCard(row, highlighted = true) { viewModel.openTopic(row.topic) }
            }
        }

        item { SectionTitle("Все темы") }
        items(state.all, key = { it.topic.id }) { row ->
            TopicCard(row, highlighted = false) { viewModel.openTopic(row.topic) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TopicCard(row: TopicRow, highlighted: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.topic.titleRu,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(12.dp))
                // Уровень не переносим: иначе «A1» ломается на две строки.
                Text(
                    text = row.topic.cefr.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            if (row.mistakeCount > 0) {
                Text(
                    text = "Ошибок за месяц: ${row.mistakeCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (row.mastery > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { row.mastery / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TopicDetail(state: GrammarUiState, viewModel: GrammarViewModel) {
    val topic = state.openTopic ?: return

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(topic.titleRu, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "${topic.titleEn} · ${topic.cefr.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(topic.explanationRu, style = MaterialTheme.typography.bodyLarge)

        if (state.topicMistakes.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Ваши ошибки по этой теме",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            state.topicMistakes.take(5).forEach { mistake ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(mistake.original, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "→ ${mistake.corrected}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (state.finished) {
            Text(
                text = "Верно: ${state.correctCount} из ${state.total}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.generating) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(20.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Готовим упражнения по вашим ошибкам…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Button(onClick = viewModel::practice, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (state.exercises.isEmpty()) "Создать упражнения" else "Тренировать"
                )
            }
            if (state.exercises.isNotEmpty() && !state.finished) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Готово упражнений: ${state.exercises.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ExerciseRunner(state: GrammarUiState, viewModel: GrammarViewModel) {
    val exercise = state.current ?: return
    var input by remember(exercise.id) { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else state.index.toFloat() / state.total },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = exercise.promptText,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            if (exercise.kind == ExerciseKind.CHOICE) {
                exercise.options.forEach { option ->
                    OutlinedButton(
                        onClick = { viewModel.submit(option) },
                        enabled = state.answer == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) { Text(option) }
                }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(GrammarViewModel.placeholder(exercise.kind)) },
                    enabled = state.answer == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (state.answer == null) {
                    Button(
                        onClick = { viewModel.submit(input) },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Проверить") }
                }
            }

            state.answer?.let { answer ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (answer.correct) "Верно" else "Правильно: ${exercise.answer}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (answer.correct) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
                exercise.explanationRu?.let { explanation ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.answer != null) {
            Button(
                onClick = viewModel::next,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
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
