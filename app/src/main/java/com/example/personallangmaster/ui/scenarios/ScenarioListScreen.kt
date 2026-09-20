package com.example.personallangmaster.ui.scenarios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import com.example.personallangmaster.di.LocalAppContainer

/**
 * Каталог ролевых сценариев.
 *
 * Сверху — подходящие по уровню, ниже — остальные: слишком сложный сценарий
 * на старте отбивает желание говорить сильнее, чем скучный.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScenarioListScreen(onBack: () -> Unit, onStartScenario: (String) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ScenarioListViewModel = viewModel(
        factory = ScenarioListViewModel.factory(
            container.contentRepository,
            container.profileRepository,
            container.generateScenarioUseCase,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var createDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сценарии") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { createDialogOpen = true },
                icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                text = { Text("Свой сценарий") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.categories.forEach { category ->
                    FilterChip(
                        selected = category == state.selectedCategory,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(categoryTitle(category)) },
                    )
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                if (state.suitable.isNotEmpty()) {
                    item { SectionHeader("Подходят вашему уровню") }
                    items(state.suitable, key = { it.id }) { scenario ->
                        ScenarioCard(scenario, highlighted = true) { onStartScenario(scenario.id) }
                    }
                }
                if (state.others.isNotEmpty()) {
                    item { SectionHeader("Остальные") }
                    items(state.others, key = { it.id }) { scenario ->
                        ScenarioCard(scenario, highlighted = false) { onStartScenario(scenario.id) }
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (createDialogOpen) {
        CreateScenarioDialog(
            generating = state.generating,
            error = state.error,
            onDismiss = {
                createDialogOpen = false
                viewModel.clearError()
            },
            onCreate = { description ->
                viewModel.createScenario(description) { id ->
                    createDialogOpen = false
                    onStartScenario(id)
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ScenarioCard(
    scenario: ScenarioEntity,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
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
                    text = scenario.titleRu,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${scenario.level.name} · ${scenario.durationMins} мин",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = scenario.descriptionRu,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (scenario.isCustom) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ваш сценарий",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun CreateScenarioDialog(
    generating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!generating) onDismiss() },
        title = { Text("Свой сценарий") },
        text = {
            Column {
                Text(
                    "Опишите ситуацию своими словами — роли, цель и первую реплику " +
                        "тренер придумает сам.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Например: объясняю коллеге, почему сорвался срок") },
                    minLines = 2,
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (generating) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(20.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Придумываем сценарий…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(description) },
                enabled = description.isNotBlank() && !generating,
            ) { Text("Создать и начать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !generating) { Text("Отмена") }
        },
    )
}

/** Категории из сид-файла приходят кодами — показываем их по-русски. */
private fun categoryTitle(category: String): String = when (category.uppercase()) {
    ALL_CATEGORIES -> "Все"
    "EVERYDAY" -> "Быт"
    "TRAVEL" -> "Путешествия"
    "BUSINESS", "WORK" -> "Работа"
    "SOCIAL" -> "Общение"
    "HEALTH" -> "Здоровье"
    "CUSTOM" -> "Свои"
    else -> category.lowercase().replaceFirstChar(Char::uppercase)
}

const val ALL_CATEGORIES = "ALL"
