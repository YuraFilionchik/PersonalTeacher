package com.example.personallangmaster.ui.vocab

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.R
import com.example.personallangmaster.di.LocalAppContainer

/** Весь словарь профиля: список, ручное добавление и выгрузка в Anki. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabListScreen(onBack: () -> Unit, onStartReview: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: VocabListViewModel = viewModel(
        factory = VocabListViewModel.factory(
            container.vocabRepository,
            container.profileRepository,
            container.ttsController,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var addDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vocab_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val csv = viewModel.exportCsv()
                            // Отправляем текстом: так словарь уходит в Anki, почту или заметки
                            // без файловых разрешений.
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/tab-separated-values"
                                putExtra(Intent.EXTRA_SUBJECT, "PersonalLangMaster: словарь")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(Intent.createChooser(intent, "Экспорт словаря"))
                        },
                        enabled = state.items.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Экспорт")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addDialogOpen = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Добавить слово")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Всего слов: ${state.items.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "На сегодня: ${state.dueCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.dueCount > 0) {
                    TextButton(onClick = onStartReview) { Text("Повторять") }
                }
            }

            if (state.items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Словарь пуст",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Слова добавляются автоматически во время уроков — " +
                            "или вручную кнопкой плюс.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { item ->
                        VocabItemCard(
                            item = item,
                            onSpeak = { viewModel.speak(item.term) },
                            onDelete = { viewModel.delete(item.id) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (addDialogOpen) {
        AddWordDialog(
            onDismiss = { addDialogOpen = false },
            onAdd = { term, translation, example ->
                viewModel.add(term, translation, example)
                addDialogOpen = false
            },
        )
    }
}

@Composable
private fun AddWordDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit,
) {
    var term by remember { mutableStateOf("") }
    var translation by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое слово") },
        text = {
            Column {
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text("Слово или фраза") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = translation,
                    onValueChange = { translation = it },
                    label = { Text("Перевод") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text("Пример (необязательно)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(term, translation, example) },
                enabled = term.isNotBlank() && translation.isNotBlank(),
            ) { Text(stringResource(R.string.dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
