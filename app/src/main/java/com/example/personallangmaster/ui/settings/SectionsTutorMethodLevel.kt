package com.example.personallangmaster.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personallangmaster.R
import com.example.personallangmaster.ai.prompt.Personas
import com.example.personallangmaster.ai.prompt.StrictnessPolicy
import com.example.personallangmaster.ai.prompt.Voices
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.prefs.Accent
import com.example.personallangmaster.data.prefs.CorrectionLanguage
import com.example.personallangmaster.data.prefs.ExplanationLanguage
import com.example.personallangmaster.data.prefs.Initiative
import com.example.personallangmaster.data.prefs.NativeLanguageUse
import com.example.personallangmaster.data.prefs.ProgressionPace
import com.example.personallangmaster.data.prefs.Verbosity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TutorSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_section_tutor), onBack) {
        SettingsGroup("Характер") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_tutor_persona_title),
                subtitle = stringResource(R.string.settings_tutor_persona_subtitle),
                options = Personas.all,
                selected = Personas.byId(settings.personaId),
                optionLabel = { it.titleRu },
                optionDescription = { it.descriptionRu },
                onSelect = { persona ->
                    viewModel.update {
                        setPersona(persona.id)
                        setVoice(persona.defaultVoice)
                        setTutorName(persona.defaultTutorName)
                    }
                },
            )
            SettingsRow(
                title = stringResource(R.string.settings_tutor_name_title),
                subtitle = stringResource(R.string.settings_tutor_name_subtitle),
                value = settings.tutorName,
            )
            OutlinedTextField(
                value = settings.tutorName,
                onValueChange = { name -> viewModel.update { setTutorName(name) } },
                label = { Text("Имя тренера") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        SettingsDivider()
        SettingsGroup("Голос и речь") {
            SettingsRow(
                title = stringResource(R.string.settings_tutor_voice_title),
                subtitle = stringResource(R.string.settings_tutor_voice_subtitle),
                value = settings.voiceName,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Voices.all.forEach { voice ->
                    FilterChip(
                        selected = voice == settings.voiceName,
                        onClick = { viewModel.update { setVoice(voice) } },
                        label = { Text(voice) },
                    )
                }
            }
            SettingsChoiceRow(
                title = stringResource(R.string.settings_tutor_accent_title),
                subtitle = stringResource(R.string.settings_tutor_accent_subtitle),
                options = Accent.entries,
                selected = settings.accent,
                optionLabel = ::accentTitle,
                onSelect = { value -> viewModel.update { setAccent(value) } },
            )
            SettingsSliderRow(
                title = stringResource(R.string.settings_tutor_rate_title),
                valueLabel = speechRateTitle(settings.speechRate),
                value = settings.speechRate.toFloat(),
                range = 1f..5f,
                steps = 3,
                onValueChange = { value -> viewModel.update { setSpeechRate(value.toInt()) } },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_tutor_verbosity_title),
                subtitle = stringResource(R.string.settings_tutor_verbosity_subtitle),
                options = Verbosity.entries,
                selected = settings.verbosity,
                optionLabel = ::verbosityTitle,
                onSelect = { value -> viewModel.update { setVerbosity(value) } },
            )
        }

        SettingsDivider()
        SettingsGroup("Продвинутое") {
            SettingsNote(stringResource(R.string.settings_tutor_custom_prompt_subtitle))
            OutlinedTextField(
                value = settings.customPromptExtra,
                onValueChange = { text -> viewModel.update { setCustomPromptExtra(text) } },
                label = { Text(stringResource(R.string.settings_tutor_custom_prompt_title)) },
                placeholder = { Text("Например: чаще спрашивай про мою работу") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            SettingsNote(
                "Текст добавляется в конец системной инструкции. Посмотреть итоговый промпт " +
                    "можно в разделе «Отладка»."
            )
        }
    }
}

@Composable
fun MethodSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_section_method), onBack) {
        SettingsGroup(stringResource(R.string.settings_method_strictness_title)) {
            SettingsSliderRow(
                title = strictnessLevelTitle(settings.strictness),
                valueLabel = "${settings.strictness}/4",
                value = settings.strictness.toFloat(),
                range = StrictnessPolicy.MIN.toFloat()..StrictnessPolicy.MAX.toFloat(),
                steps = 3,
                onValueChange = { value -> viewModel.update { setStrictness(value.toInt()) } },
            )
            Text(
                text = strictnessLevelDescription(settings.strictness),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        SettingsDivider()
        SettingsGroup(stringResource(R.string.settings_method_targets_title)) {
            SettingsNote(stringResource(R.string.settings_method_targets_subtitle))
            SettingsSwitchRow(
                title = "Грамматика",
                checked = settings.correctGrammar,
                onCheckedChange = { value -> viewModel.update { setCorrectionTargets(grammar = value) } },
            )
            SettingsSwitchRow(
                title = "Словоупотребление",
                checked = settings.correctVocab,
                onCheckedChange = { value -> viewModel.update { setCorrectionTargets(vocab = value) } },
            )
            SettingsSwitchRow(
                title = "Произношение",
                checked = settings.correctPronunciation,
                onCheckedChange = { value ->
                    viewModel.update { setCorrectionTargets(pronunciation = value) }
                },
            )
            SettingsSwitchRow(
                title = "Порядок слов",
                checked = settings.correctWordOrder,
                onCheckedChange = { value -> viewModel.update { setCorrectionTargets(wordOrder = value) } },
            )
            SettingsSwitchRow(
                title = "Естественность фразы",
                checked = settings.correctNaturalness,
                onCheckedChange = { value ->
                    viewModel.update { setCorrectionTargets(naturalness = value) }
                },
            )
            SettingsSwitchRow(
                title = "Артикли и предлоги",
                checked = settings.correctArticles,
                onCheckedChange = { value -> viewModel.update { setCorrectionTargets(articles = value) } },
            )
        }

        SettingsDivider()
        SettingsGroup("Язык") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_method_lang_title),
                subtitle = stringResource(R.string.settings_method_lang_subtitle),
                options = CorrectionLanguage.entries,
                selected = settings.correctionLanguage,
                optionLabel = ::correctionLanguageTitle,
                onSelect = { value -> viewModel.update { setCorrectionLanguage(value) } },
            )
            SettingsChoiceRow(
                title = "Язык объяснений в приложении",
                subtitle = "На каком языке показывать разборы и правила",
                options = ExplanationLanguage.entries,
                selected = settings.explanationLanguage,
                optionLabel = ::explanationLanguageTitle,
                onSelect = { value -> viewModel.update { setExplanationLanguage(value) } },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_method_allow_ru_title),
                subtitle = stringResource(R.string.settings_method_allow_ru_subtitle),
                options = NativeLanguageUse.entries,
                selected = settings.nativeLanguageUse,
                optionLabel = ::nativeLanguageUseTitle,
                onSelect = { value -> viewModel.update { setNativeLanguageUse(value) } },
            )
        }

        SettingsDivider()
        SettingsGroup("Урок") {
            SettingsChoiceRow(
                title = stringResource(R.string.settings_method_initiative_title),
                subtitle = stringResource(R.string.settings_method_initiative_subtitle),
                options = Initiative.entries,
                selected = settings.initiative,
                optionLabel = ::initiativeTitle,
                onSelect = { value -> viewModel.update { setInitiative(value) } },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.settings_method_lesson_length_title),
                options = listOf(5, 10, 15, 20, 0),
                selected = settings.lessonMinutes,
                optionLabel = { minutes -> if (minutes == 0) "Без ограничения" else "$minutes мин" },
                onSelect = { value -> viewModel.update { setLessonMinutes(value) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_method_auto_analysis_title),
                subtitle = stringResource(R.string.settings_method_auto_analysis_subtitle),
                checked = settings.autoAnalyzeLesson,
                onCheckedChange = { value -> viewModel.update { setAutoAnalyze(value) } },
            )
        }
    }
}

@Composable
fun LevelSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var manualLevel by remember(profile?.cefrOverall) {
        mutableStateOf(profile?.cefrOverall ?: Cefr.A2)
    }

    SettingsScaffold(stringResource(R.string.settings_section_level), onBack) {
        SettingsGroup(stringResource(R.string.settings_level_cefr_title)) {
            SettingsRow(
                title = "Общий уровень",
                subtitle = profile?.cefrUpdatedAt?.let { "Обновлён по итогам уроков" }
                    ?: "Выбран при первом запуске",
                value = profile?.cefrOverall?.name ?: "—",
            )
            SettingsRow(title = "Speaking", value = profile?.cefrSpeaking?.name ?: "—")
            SettingsRow(title = "Grammar", value = profile?.cefrGrammar?.name ?: "—")
            SettingsRow(title = "Vocabulary", value = profile?.cefrVocab?.name ?: "—")
        }

        SettingsDivider()
        SettingsGroup("Управление уровнем") {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_level_lock_title),
                subtitle = stringResource(R.string.settings_level_lock_subtitle),
                checked = settings.levelLocked,
                onCheckedChange = viewModel::setLevelLocked,
            )
            if (settings.levelLocked) {
                SettingsChoiceRow(
                    title = "Зафиксированный уровень",
                    options = Cefr.entries,
                    selected = manualLevel,
                    optionLabel = { it.name },
                    onSelect = { level ->
                        manualLevel = level
                        viewModel.setManualLevel(level)
                    },
                )
                SettingsNote(
                    "Пока уровень зафиксирован, оценки после уроков показываются, " +
                        "но профиль не меняется."
                )
            }
            SettingsChoiceRow(
                title = stringResource(R.string.settings_level_pace_title),
                options = ProgressionPace.entries,
                selected = settings.progressionPace,
                optionLabel = ::progressionPaceTitle,
                enabled = !settings.levelLocked,
                onSelect = { value -> viewModel.update { setProgressionPace(value) } },
            )
        }

        SettingsDivider()
        SettingsGroup("Тест уровня") {
            SettingsRow(
                title = stringResource(R.string.settings_level_retest_title),
                subtitle = "Голосовое интервью на пять минут (появится вместе с живым уроком)",
                enabled = false,
            )
        }
    }
}

@Composable
private fun strictnessLevelTitle(level: Int): String = stringResource(
    when (level.coerceIn(0, 4)) {
        0 -> R.string.strictness_level_0_title
        1 -> R.string.strictness_level_1_title
        2 -> R.string.strictness_level_2_title
        3 -> R.string.strictness_level_3_title
        else -> R.string.strictness_level_4_title
    }
)

@Composable
private fun strictnessLevelDescription(level: Int): String = stringResource(
    when (level.coerceIn(0, 4)) {
        0 -> R.string.strictness_level_0_desc
        1 -> R.string.strictness_level_1_desc
        2 -> R.string.strictness_level_2_desc
        3 -> R.string.strictness_level_3_desc
        else -> R.string.strictness_level_4_desc
    }
)
