package com.example.personallangmaster.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.personallangmaster.R
import com.example.personallangmaster.ai.prompt.Personas

/** Раздел настроек в списке и в поиске. */
private data class SectionItem(
    val route: SettingsRoute,
    val titleRes: Int,
    val icon: ImageVector,
    /** Ключи строк из этой секции — по ним работает поиск. */
    val searchableTitles: List<Int>,
)

private val sections = listOf(
    SectionItem(
        SettingsRoute.Tutor, R.string.settings_section_tutor, Icons.Rounded.Person,
        listOf(
            R.string.settings_tutor_persona_title,
            R.string.settings_tutor_voice_title,
            R.string.settings_tutor_accent_title,
            R.string.settings_tutor_rate_title,
            R.string.settings_tutor_verbosity_title,
            R.string.settings_tutor_name_title,
            R.string.settings_tutor_custom_prompt_title,
        ),
    ),
    SectionItem(
        SettingsRoute.Method, R.string.settings_section_method, Icons.Rounded.School,
        listOf(
            R.string.settings_method_strictness_title,
            R.string.settings_method_targets_title,
            R.string.settings_method_lang_title,
            R.string.settings_method_allow_ru_title,
            R.string.settings_method_initiative_title,
            R.string.settings_method_lesson_length_title,
            R.string.settings_method_auto_analysis_title,
        ),
    ),
    SectionItem(
        SettingsRoute.Level, R.string.settings_section_level, Icons.Rounded.TrendingUp,
        listOf(
            R.string.settings_level_cefr_title,
            R.string.settings_level_lock_title,
            R.string.settings_level_pace_title,
            R.string.settings_level_retest_title,
        ),
    ),
    SectionItem(
        SettingsRoute.Audio, R.string.settings_section_audio, Icons.Rounded.Mic,
        listOf(
            R.string.settings_audio_mic_mode_title,
            R.string.settings_audio_vad_sensitivity_title,
            R.string.settings_audio_barge_in_title,
            R.string.settings_audio_noise_suppression_title,
            R.string.settings_audio_output_device_title,
        ),
    ),
    SectionItem(
        SettingsRoute.Model, R.string.settings_section_model, Icons.Rounded.Key,
        listOf(
            R.string.settings_model_api_key_title,
            R.string.settings_model_live_model_title,
            R.string.settings_model_text_model_title,
        ),
    ),
    SectionItem(
        SettingsRoute.Budget, R.string.settings_section_budget, Icons.Rounded.Payments,
        listOf(
            R.string.settings_budget_daily_title,
            R.string.settings_budget_monthly_title,
            R.string.settings_budget_behavior_title,
            R.string.settings_budget_parental_pin_title,
        ),
    ),
    SectionItem(
        SettingsRoute.Data, R.string.settings_section_data, Icons.Rounded.Storage,
        listOf(
            R.string.settings_data_transcripts_title,
            R.string.settings_data_audio_recording_title,
            R.string.settings_data_clear_title,
        ),
    ),
    SectionItem(
        SettingsRoute.Appearance, R.string.settings_section_appearance, Icons.Rounded.Palette,
        listOf(
            R.string.settings_appearance_theme_title,
            R.string.settings_appearance_dynamic_color_title,
            R.string.settings_appearance_subtitles_title,
        ),
    ),
    SectionItem(
        SettingsRoute.Notifications, R.string.settings_section_notifications, Icons.Rounded.Notifications,
        listOf(
            R.string.settings_notifications_daily_title,
            R.string.settings_notifications_review_title,
        ),
    ),
)

/**
 * Корневой экран настроек: поиск по всем пунктам и список секций.
 * Значение справа в строке показывает, что выбрано сейчас, — чтобы не заходить внутрь ради проверки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenSection: (SettingsRoute) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    val matches = remember(query) {
        if (query.length < 2) {
            emptyList()
        } else {
            sections.flatMap { section ->
                section.searchableTitles
                    .map { titleRes -> section to context.getString(titleRes) }
                    .filter { (_, title) -> title.contains(query, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по настройкам") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (query.length >= 2) {
                if (matches.isEmpty()) {
                    SettingsNote("Ничего не найдено")
                } else {
                    matches.forEach { (section, title) ->
                        SettingsRow(
                            title = title,
                            subtitle = stringResource(section.titleRes),
                            icon = section.icon,
                            onClick = { onOpenSection(section.route) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                return@Column
            }

            sections.forEach { section ->
                SettingsRow(
                    title = stringResource(section.titleRes),
                    subtitle = sectionSummary(section.route, settings, profileName = profile?.name),
                    icon = section.icon,
                    onClick = { onOpenSection(section.route) },
                )
            }

            SettingsDivider()
            SettingsGroup("Отладка") {
                SettingsRow(
                    title = "Показать итоговый промпт",
                    subtitle = "Системная инструкция, собранная из текущих настроек",
                    icon = Icons.Rounded.Terminal,
                    onClick = { onOpenSection(SettingsRoute.PromptPreview) },
                )
            }

            SettingsNote(
                "PersonalLangMaster · ${profile?.name ?: "профиль не создан"} · " +
                    "уровень ${profile?.cefrOverall?.name ?: "—"}"
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Короткая сводка того, что выбрано в секции, — видна прямо в списке. */
@Composable
private fun sectionSummary(
    route: SettingsRoute,
    settings: com.example.personallangmaster.data.prefs.AppSettings,
    profileName: String?,
): String = when (route) {
    SettingsRoute.Tutor ->
        "${Personas.byId(settings.personaId).titleRu} · голос ${settings.voiceName}"
    SettingsRoute.Method -> strictnessTitle(settings.strictness)
    SettingsRoute.Level -> if (settings.levelLocked) "Зафиксирован вручную" else "Определяется автоматически"
    SettingsRoute.Audio -> micModeTitle(settings.micMode)
    SettingsRoute.Model ->
        if (settings.hasApiKey) "Ключ сохранён · ${settings.liveModelId}" else "Ключ не задан"
    SettingsRoute.Budget ->
        "Дневной лимит \$${"%.2f".format(settings.dailyLimitUsd)}" +
            if (settings.pinProtected) " · защищено PIN" else ""
    SettingsRoute.Data -> transcriptRetentionTitle(settings)
    SettingsRoute.Appearance -> themeTitle(settings)
    SettingsRoute.Notifications ->
        if (settings.reminderEnabled) {
            "Каждый день в %02d:%02d".format(
                settings.reminderMinuteOfDay / 60,
                settings.reminderMinuteOfDay % 60,
            )
        } else {
            "Выключены"
        }
    SettingsRoute.PromptPreview -> profileName.orEmpty()
}

@Composable
private fun strictnessTitle(level: Int): String = stringResource(
    when (level.coerceIn(0, 4)) {
        0 -> R.string.strictness_level_0_title
        1 -> R.string.strictness_level_1_title
        2 -> R.string.strictness_level_2_title
        3 -> R.string.strictness_level_3_title
        else -> R.string.strictness_level_4_title
    }
)
