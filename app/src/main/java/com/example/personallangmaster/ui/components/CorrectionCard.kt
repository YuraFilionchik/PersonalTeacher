package com.example.personallangmaster.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.R
import com.example.personallangmaster.ui.theme.PersonalLangMasterTheme

/**
 * Карточка с исправлением ошибки. Цвет tertiary (учебный, не error).
 * Поддерживает жест смахивания (свайп) для скрытия.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionCard(
    original: String,
    corrected: String,
    explanation: String?,
    onListen: () -> Unit,
    onAddToVocab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction != null) {
                val alignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = alignment
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.lesson_card_dismiss),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = original,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    )
                }

                Text(
                    text = corrected,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                if (!explanation.isNullOrBlank()) {
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onListen,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.lesson_card_listen))
                    }

                    TextButton(
                        onClick = onAddToVocab,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.lesson_card_add_vocab))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CorrectionCardPreview() {
    PersonalLangMasterTheme {
        Surface {
            CorrectionCard(
                original = "Yesterday I go to the shop",
                corrected = "Yesterday I went to the shop",
                explanation = "Past Simple: 'вчера' указывает на завершенное действие в прошлом.",
                onListen = {},
                onAddToVocab = {},
                onDismiss = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
