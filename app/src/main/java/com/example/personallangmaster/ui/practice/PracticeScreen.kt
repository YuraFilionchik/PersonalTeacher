package com.example.personallangmaster.ui.practice

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
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personallangmaster.R
import com.example.personallangmaster.di.LocalAppContainer
import com.example.personallangmaster.ui.vocab.VocabListViewModel

/**
 * Раздел «Практика»: всё, чем можно заняться между разговорами.
 *
 * Карточки модулей, которые ещё не готовы, показываются приглушёнными —
 * так видно, куда движется приложение, и не создаётся ложных ожиданий.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    onOpenVocab: () -> Unit,
    onStartReview: () -> Unit,
    onOpenScenarios: () -> Unit,
    onOpenPronunciation: () -> Unit,
    onOpenGrammar: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: VocabListViewModel = viewModel(
        factory = VocabListViewModel.factory(
            container.vocabRepository,
            container.profileRepository,
            container.ttsController,
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_practice)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.dueCount > 0) {
                ModuleCard(
                    title = stringResource(R.string.vocab_due_today),
                    subtitle = "Карточек к повторению: ${state.dueCount}",
                    icon = Icons.Rounded.Style,
                    highlighted = true,
                    onClick = onStartReview,
                )
            }

            ModuleCard(
                title = stringResource(R.string.vocab_title),
                subtitle = if (state.items.isEmpty()) {
                    "Пока пусто — слова появятся после уроков"
                } else {
                    "Слов в словаре: ${state.items.size}"
                },
                icon = Icons.Rounded.MenuBook,
                onClick = onOpenVocab,
            )

            ModuleCard(
                title = "Ролевые сценарии",
                subtitle = "Кафе, собеседование, спор — разговор с целью",
                icon = Icons.Rounded.TheaterComedy,
                onClick = onOpenScenarios,
            )

            ModuleCard(
                title = stringResource(R.string.pronunciation_title),
                subtitle = "Минимальные пары, слабые звуки и карта фонем",
                icon = Icons.Rounded.RecordVoiceOver,
                onClick = onOpenPronunciation,
            )

            ModuleCard(
                title = stringResource(R.string.grammar_title),
                subtitle = "Темы и упражнения на материале ваших ошибок",
                icon = Icons.Rounded.Rule,
                onClick = onOpenGrammar,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = when {
                highlighted -> MaterialTheme.colorScheme.primaryContainer
                enabled -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
