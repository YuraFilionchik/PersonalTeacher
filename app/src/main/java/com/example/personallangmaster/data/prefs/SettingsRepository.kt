package com.example.personallangmaster.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.personallangmaster.core.crypto.KeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Единственный источник правды для настроек приложения.
 *
 * Читается как поток [AppSettings], пишется точечно — каждый экран настроек
 * вызывает свой `update…`. Ключ API проходит через [KeyVault]: наружу
 * репозиторий отдаёт расшифрованный ключ только по явному запросу.
 */
class SettingsRepository(
    private val context: Context,
    private val keyVault: KeyVault,
) {

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { error ->
            // Повреждённый файл настроек не должен мешать запуску: откатываемся к умолчаниям.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toSettings)

    suspend fun current(): AppSettings = settings.first()

    // --- Ключ API ---

    /** Сохраняет ключ в зашифрованном виде. Пустая строка стирает ключ. */
    suspend fun setApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        val payload = if (trimmed.isEmpty()) "" else keyVault.encrypt(trimmed).orEmpty()
        edit { it[Keys.apiKeyEncrypted] = payload }
    }

    /** Расшифрованный ключ — только для сетевого слоя, в UI не показываем. */
    suspend fun apiKey(): String? = keyVault.decrypt(current().apiKeyEncrypted)?.takeIf { it.isNotBlank() }

    // --- Тренер ---

    suspend fun setPersona(personaId: String) = edit { it[Keys.personaId] = personaId }
    suspend fun setTutorName(name: String) = edit { it[Keys.tutorName] = name }
    suspend fun setVoice(voiceName: String) = edit { it[Keys.voiceName] = voiceName }
    suspend fun setAccent(accent: Accent) = edit { it[Keys.accent] = accent.name }
    suspend fun setSpeechRate(rate: Int) = edit { it[Keys.speechRate] = rate.coerceIn(1, 5) }
    suspend fun setVerbosity(value: Verbosity) = edit { it[Keys.verbosity] = value.name }
    suspend fun setCustomPromptExtra(text: String) = edit { it[Keys.customPromptExtra] = text }

    // --- Методика ---

    suspend fun setStrictness(level: Int) = edit { it[Keys.strictness] = level.coerceIn(0, 4) }
    suspend fun setCorrectionTargets(
        grammar: Boolean? = null,
        vocab: Boolean? = null,
        pronunciation: Boolean? = null,
        wordOrder: Boolean? = null,
        naturalness: Boolean? = null,
        articles: Boolean? = null,
    ) = edit { prefs ->
        grammar?.let { prefs[Keys.correctGrammar] = it }
        vocab?.let { prefs[Keys.correctVocab] = it }
        pronunciation?.let { prefs[Keys.correctPronunciation] = it }
        wordOrder?.let { prefs[Keys.correctWordOrder] = it }
        naturalness?.let { prefs[Keys.correctNaturalness] = it }
        articles?.let { prefs[Keys.correctArticles] = it }
    }

    suspend fun setCorrectionLanguage(value: CorrectionLanguage) =
        edit { it[Keys.correctionLanguage] = value.name }

    suspend fun setExplanationLanguage(value: ExplanationLanguage) =
        edit { it[Keys.explanationLanguage] = value.name }

    suspend fun setNativeLanguageUse(value: NativeLanguageUse) =
        edit { it[Keys.nativeLanguageUse] = value.name }

    suspend fun setInitiative(value: Initiative) = edit { it[Keys.initiative] = value.name }
    suspend fun setLessonMinutes(minutes: Int) = edit { it[Keys.lessonMinutes] = minutes.coerceIn(0, 120) }
    suspend fun setAutoAnalyze(enabled: Boolean) = edit { it[Keys.autoAnalyzeLesson] = enabled }

    // --- Уровень ---

    suspend fun setLevelLocked(locked: Boolean) = edit { it[Keys.levelLocked] = locked }
    suspend fun setProgressionPace(value: ProgressionPace) = edit { it[Keys.progressionPace] = value.name }

    // --- Аудио ---

    suspend fun setMicMode(mode: MicMode) = edit { it[Keys.micMode] = mode.name }
    suspend fun setVadThreshold(db: Double) = edit { it[Keys.vadThresholdDb] = db.coerceIn(-60.0, -20.0) }
    suspend fun setSilenceHangover(ms: Int) = edit { it[Keys.silenceHangoverMs] = ms.coerceIn(200, 3000) }
    suspend fun setBargeIn(enabled: Boolean) = edit { it[Keys.bargeInEnabled] = enabled }
    suspend fun setNoiseSuppression(enabled: Boolean) = edit { it[Keys.noiseSuppression] = enabled }
    suspend fun setAudioOutput(value: AudioOutput) = edit { it[Keys.audioOutput] = value.name }
    suspend fun setTutorVolume(volume: Float) = edit { it[Keys.tutorVolume] = volume.coerceIn(0f, 1f) }

    // --- Модель ---

    suspend fun setLiveModel(id: String) = edit { it[Keys.liveModelId] = id }
    suspend fun setTextModel(id: String) = edit { it[Keys.textModelId] = id }
    suspend fun setCheapModel(id: String) = edit { it[Keys.cheapModelId] = id }
    suspend fun setTemperature(value: Float) = edit { it[Keys.temperature] = value.coerceIn(0f, 2f) }
    suspend fun setTranscription(enabled: Boolean) = edit { it[Keys.transcriptionEnabled] = enabled }
    suspend fun setContextCompression(enabled: Boolean) = edit { it[Keys.contextCompression] = enabled }
    suspend fun setSessionResumption(enabled: Boolean) = edit { it[Keys.sessionResumption] = enabled }

    // --- Бюджет ---

    suspend fun setDailyLimitUsd(value: Double) = edit { it[Keys.dailyLimitUsd] = value.coerceAtLeast(0.0) }
    suspend fun setMonthlyLimitUsd(value: Double) = edit { it[Keys.monthlyLimitUsd] = value.coerceAtLeast(0.0) }
    suspend fun setDailyLimitMinutes(value: Int) = edit { it[Keys.dailyLimitMinutes] = value.coerceAtLeast(0) }
    suspend fun setLimitBehavior(value: LimitBehavior) = edit { it[Keys.limitBehavior] = value.name }
    suspend fun setIdleAutoStop(seconds: Int) = edit { it[Keys.idleAutoStopSeconds] = seconds.coerceIn(0, 300) }
    suspend fun setParentPinHash(hash: String) = edit { it[Keys.parentPinHash] = hash }
    suspend fun setPricing(
        textIn: Double? = null,
        textOut: Double? = null,
        audioIn: Double? = null,
        audioOut: Double? = null,
        usdToRub: Double? = null,
    ) = edit { prefs ->
        textIn?.let { prefs[Keys.priceTextIn] = it }
        textOut?.let { prefs[Keys.priceTextOut] = it }
        audioIn?.let { prefs[Keys.priceAudioIn] = it }
        audioOut?.let { prefs[Keys.priceAudioOut] = it }
        usdToRub?.let { prefs[Keys.usdToRubRate] = it }
    }

    // --- Данные ---

    suspend fun setTranscriptRetention(value: TranscriptRetention, days: Int = 90) = edit { prefs ->
        prefs[Keys.transcriptRetention] = value.name
        prefs[Keys.transcriptRetentionDays] = days.coerceIn(1, 3650)
    }

    suspend fun setAudioRecording(value: AudioRecording, days: Int = 14) = edit { prefs ->
        prefs[Keys.audioRecording] = value.name
        prefs[Keys.audioRetentionDays] = days.coerceIn(1, 365)
    }

    // --- Внешний вид ---

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.themeMode] = value.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }
    suspend fun setSubtitleMode(value: SubtitleMode) = edit { it[Keys.subtitleMode] = value.name }
    suspend fun setSubtitleFontScale(scale: Float) = edit { it[Keys.subtitleFontScale] = scale.coerceIn(0.8f, 2.0f) }
    suspend fun setLargeElements(enabled: Boolean) = edit { it[Keys.largeElements] = enabled }
    suspend fun setWaveStyle(value: WaveStyle) = edit { it[Keys.waveStyle] = value.name }

    // --- Напоминания ---

    suspend fun setReminder(enabled: Boolean, minuteOfDay: Int? = null, days: Set<Int>? = null) =
        edit { prefs ->
            prefs[Keys.reminderEnabled] = enabled
            minuteOfDay?.let { prefs[Keys.reminderMinuteOfDay] = it.coerceIn(0, 24 * 60 - 1) }
            days?.let { set -> prefs[Keys.reminderDays] = set.map(Int::toString).toSet() }
        }

    suspend fun setReviewReminder(enabled: Boolean) = edit { it[Keys.reviewReminderEnabled] = enabled }

    // --- Служебное ---

    suspend fun setOnboardingCompleted(completed: Boolean) =
        edit { it[Keys.onboardingCompleted] = completed }

    /** Полный сброс настроек. Ключ шифрования тоже уничтожается. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        keyVault.wipe()
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            personaId = prefs[Keys.personaId] ?: defaults.personaId,
            tutorName = prefs[Keys.tutorName] ?: defaults.tutorName,
            voiceName = prefs[Keys.voiceName] ?: defaults.voiceName,
            accent = prefs[Keys.accent].toEnum(defaults.accent),
            speechRate = prefs[Keys.speechRate] ?: defaults.speechRate,
            verbosity = prefs[Keys.verbosity].toEnum(defaults.verbosity),
            customPromptExtra = prefs[Keys.customPromptExtra] ?: defaults.customPromptExtra,

            strictness = prefs[Keys.strictness] ?: defaults.strictness,
            correctGrammar = prefs[Keys.correctGrammar] ?: defaults.correctGrammar,
            correctVocab = prefs[Keys.correctVocab] ?: defaults.correctVocab,
            correctPronunciation = prefs[Keys.correctPronunciation] ?: defaults.correctPronunciation,
            correctWordOrder = prefs[Keys.correctWordOrder] ?: defaults.correctWordOrder,
            correctNaturalness = prefs[Keys.correctNaturalness] ?: defaults.correctNaturalness,
            correctArticles = prefs[Keys.correctArticles] ?: defaults.correctArticles,
            correctionLanguage = prefs[Keys.correctionLanguage].toEnum(defaults.correctionLanguage),
            explanationLanguage = prefs[Keys.explanationLanguage].toEnum(defaults.explanationLanguage),
            nativeLanguageUse = prefs[Keys.nativeLanguageUse].toEnum(defaults.nativeLanguageUse),
            initiative = prefs[Keys.initiative].toEnum(defaults.initiative),
            lessonMinutes = prefs[Keys.lessonMinutes] ?: defaults.lessonMinutes,
            autoAnalyzeLesson = prefs[Keys.autoAnalyzeLesson] ?: defaults.autoAnalyzeLesson,

            levelLocked = prefs[Keys.levelLocked] ?: defaults.levelLocked,
            progressionPace = prefs[Keys.progressionPace].toEnum(defaults.progressionPace),

            micMode = prefs[Keys.micMode].toEnum(defaults.micMode),
            vadThresholdDb = prefs[Keys.vadThresholdDb] ?: defaults.vadThresholdDb,
            silenceHangoverMs = prefs[Keys.silenceHangoverMs] ?: defaults.silenceHangoverMs,
            bargeInEnabled = prefs[Keys.bargeInEnabled] ?: defaults.bargeInEnabled,
            noiseSuppression = prefs[Keys.noiseSuppression] ?: defaults.noiseSuppression,
            audioOutput = prefs[Keys.audioOutput].toEnum(defaults.audioOutput),
            tutorVolume = prefs[Keys.tutorVolume] ?: defaults.tutorVolume,

            apiKeyEncrypted = prefs[Keys.apiKeyEncrypted] ?: defaults.apiKeyEncrypted,
            liveModelId = prefs[Keys.liveModelId] ?: defaults.liveModelId,
            textModelId = prefs[Keys.textModelId] ?: defaults.textModelId,
            cheapModelId = prefs[Keys.cheapModelId] ?: defaults.cheapModelId,
            temperature = prefs[Keys.temperature] ?: defaults.temperature,
            transcriptionEnabled = prefs[Keys.transcriptionEnabled] ?: defaults.transcriptionEnabled,
            contextCompression = prefs[Keys.contextCompression] ?: defaults.contextCompression,
            sessionResumption = prefs[Keys.sessionResumption] ?: defaults.sessionResumption,

            dailyLimitUsd = prefs[Keys.dailyLimitUsd] ?: defaults.dailyLimitUsd,
            monthlyLimitUsd = prefs[Keys.monthlyLimitUsd] ?: defaults.monthlyLimitUsd,
            dailyLimitMinutes = prefs[Keys.dailyLimitMinutes] ?: defaults.dailyLimitMinutes,
            limitBehavior = prefs[Keys.limitBehavior].toEnum(defaults.limitBehavior),
            idleAutoStopSeconds = prefs[Keys.idleAutoStopSeconds] ?: defaults.idleAutoStopSeconds,
            parentPinHash = prefs[Keys.parentPinHash] ?: defaults.parentPinHash,
            priceTextInPerMTok = prefs[Keys.priceTextIn] ?: defaults.priceTextInPerMTok,
            priceTextOutPerMTok = prefs[Keys.priceTextOut] ?: defaults.priceTextOutPerMTok,
            priceAudioInPerMTok = prefs[Keys.priceAudioIn] ?: defaults.priceAudioInPerMTok,
            priceAudioOutPerMTok = prefs[Keys.priceAudioOut] ?: defaults.priceAudioOutPerMTok,
            usdToRubRate = prefs[Keys.usdToRubRate] ?: defaults.usdToRubRate,

            transcriptRetention = prefs[Keys.transcriptRetention].toEnum(defaults.transcriptRetention),
            transcriptRetentionDays = prefs[Keys.transcriptRetentionDays] ?: defaults.transcriptRetentionDays,
            audioRecording = prefs[Keys.audioRecording].toEnum(defaults.audioRecording),
            audioRetentionDays = prefs[Keys.audioRetentionDays] ?: defaults.audioRetentionDays,

            themeMode = prefs[Keys.themeMode].toEnum(defaults.themeMode),
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
            subtitleMode = prefs[Keys.subtitleMode].toEnum(defaults.subtitleMode),
            subtitleFontScale = prefs[Keys.subtitleFontScale] ?: defaults.subtitleFontScale,
            largeElements = prefs[Keys.largeElements] ?: defaults.largeElements,
            waveStyle = prefs[Keys.waveStyle].toEnum(defaults.waveStyle),

            reminderEnabled = prefs[Keys.reminderEnabled] ?: defaults.reminderEnabled,
            reminderMinuteOfDay = prefs[Keys.reminderMinuteOfDay] ?: defaults.reminderMinuteOfDay,
            reminderDays = prefs[Keys.reminderDays]
                ?.mapNotNull(String::toIntOrNull)?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: defaults.reminderDays,
            reviewReminderEnabled = prefs[Keys.reviewReminderEnabled] ?: defaults.reviewReminderEnabled,

            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: defaults.onboardingCompleted,
        )
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(fallback: E): E =
        this?.let { raw -> enumValues<E>().firstOrNull { it.name == raw } } ?: fallback

    private object Keys {
        val personaId = stringPreferencesKey("persona_id")
        val tutorName = stringPreferencesKey("tutor_name")
        val voiceName = stringPreferencesKey("voice_name")
        val accent = stringPreferencesKey("accent")
        val speechRate = intPreferencesKey("speech_rate")
        val verbosity = stringPreferencesKey("verbosity")
        val customPromptExtra = stringPreferencesKey("custom_prompt_extra")

        val strictness = intPreferencesKey("strictness")
        val correctGrammar = booleanPreferencesKey("correct_grammar")
        val correctVocab = booleanPreferencesKey("correct_vocab")
        val correctPronunciation = booleanPreferencesKey("correct_pronunciation")
        val correctWordOrder = booleanPreferencesKey("correct_word_order")
        val correctNaturalness = booleanPreferencesKey("correct_naturalness")
        val correctArticles = booleanPreferencesKey("correct_articles")
        val correctionLanguage = stringPreferencesKey("correction_language")
        val explanationLanguage = stringPreferencesKey("explanation_language")
        val nativeLanguageUse = stringPreferencesKey("native_language_use")
        val initiative = stringPreferencesKey("initiative")
        val lessonMinutes = intPreferencesKey("lesson_minutes")
        val autoAnalyzeLesson = booleanPreferencesKey("auto_analyze_lesson")

        val levelLocked = booleanPreferencesKey("level_locked")
        val progressionPace = stringPreferencesKey("progression_pace")

        val micMode = stringPreferencesKey("mic_mode")
        val vadThresholdDb = doublePreferencesKey("vad_threshold_db")
        val silenceHangoverMs = intPreferencesKey("silence_hangover_ms")
        val bargeInEnabled = booleanPreferencesKey("barge_in_enabled")
        val noiseSuppression = booleanPreferencesKey("noise_suppression")
        val audioOutput = stringPreferencesKey("audio_output")
        val tutorVolume = floatPreferencesKey("tutor_volume")

        val apiKeyEncrypted = stringPreferencesKey("api_key_encrypted")
        val liveModelId = stringPreferencesKey("live_model_id")
        val textModelId = stringPreferencesKey("text_model_id")
        val cheapModelId = stringPreferencesKey("cheap_model_id")
        val temperature = floatPreferencesKey("temperature")
        val transcriptionEnabled = booleanPreferencesKey("transcription_enabled")
        val contextCompression = booleanPreferencesKey("context_compression")
        val sessionResumption = booleanPreferencesKey("session_resumption")

        val dailyLimitUsd = doublePreferencesKey("daily_limit_usd")
        val monthlyLimitUsd = doublePreferencesKey("monthly_limit_usd")
        val dailyLimitMinutes = intPreferencesKey("daily_limit_minutes")
        val limitBehavior = stringPreferencesKey("limit_behavior")
        val idleAutoStopSeconds = intPreferencesKey("idle_auto_stop_seconds")
        val parentPinHash = stringPreferencesKey("parent_pin_hash")
        val priceTextIn = doublePreferencesKey("price_text_in")
        val priceTextOut = doublePreferencesKey("price_text_out")
        val priceAudioIn = doublePreferencesKey("price_audio_in")
        val priceAudioOut = doublePreferencesKey("price_audio_out")
        val usdToRubRate = doublePreferencesKey("usd_to_rub_rate")

        val transcriptRetention = stringPreferencesKey("transcript_retention")
        val transcriptRetentionDays = intPreferencesKey("transcript_retention_days")
        val audioRecording = stringPreferencesKey("audio_recording")
        val audioRetentionDays = intPreferencesKey("audio_retention_days")

        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val subtitleMode = stringPreferencesKey("subtitle_mode")
        val subtitleFontScale = floatPreferencesKey("subtitle_font_scale")
        val largeElements = booleanPreferencesKey("large_elements")
        val waveStyle = stringPreferencesKey("wave_style")

        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderMinuteOfDay = intPreferencesKey("reminder_minute_of_day")
        val reminderDays = stringSetPreferencesKey("reminder_days")
        val reviewReminderEnabled = booleanPreferencesKey("review_reminder_enabled")

        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
    }
}
