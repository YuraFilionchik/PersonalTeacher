package com.example.personallangmaster.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Отладочный экран: показывает системную инструкцию, которая уйдёт модели.
 *
 * Нужен именно сейчас, до подключения сети: по нему видно, как настройки
 * превращаются в поведение тренера, и это дешевле, чем проверять голосом.
 */
@Composable
fun PromptPreviewScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val prompt by viewModel.promptPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshPromptPreview() }

    SettingsScaffold("Итоговый промпт", onBack) {
        SettingsNote(
            "Так тренер видит вас перед началом урока. Память (прошлые уроки, ошибки, слова) " +
                "появится здесь после первых занятий."
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { viewModel.refreshPromptPreview() }) { Text("Обновить") }
            OutlinedButton(onClick = { copyToClipboard(context, prompt) }) { Text("Скопировать") }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = prompt.ifBlank { "Промпт ещё не собран" },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(12.dp),
            )
        }
        SettingsNote("Длина: ${prompt.length} символов (~${prompt.length / 4} токенов)")
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("prompt", text))
}
