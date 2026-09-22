package com.example.personallangmaster.data.repo

import com.example.personallangmaster.core.export.DailyStatBackup
import com.example.personallangmaster.core.export.GrammarProgressBackup
import com.example.personallangmaster.core.export.PhonemeScoreBackup
import com.example.personallangmaster.core.export.ProfileBackupData
import com.example.personallangmaster.core.export.SettingsBackup
import com.example.personallangmaster.core.export.StreakBackup
import com.example.personallangmaster.core.export.VocabBackupItem
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.VocabSource
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.entity.DailyStatEntity
import com.example.personallangmaster.data.db.entity.GrammarProgressEntity
import com.example.personallangmaster.data.db.entity.PhonemeScoreEntity
import com.example.personallangmaster.data.db.entity.ProfileEntity
import com.example.personallangmaster.data.db.entity.StreakEntity
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.data.prefs.AppSettings

/**
 * Перекладывание сущностей базы и настроек в DTO резервной копии и обратно.
 *
 * Слой намеренно отдельный и без зависимостей от Android: именно здесь легко
 * ошибиться в одном поле из шестидесяти, поэтому он должен проверяться обычным
 * юнит-тестом на круговой обход.
 *
 * Неизвестное значение перечисления не роняет импорт: чужой или испорченный
 * файл должен дать настройку по умолчанию, а не пустой экран.
 */

// --- Профиль ---

fun ProfileEntity.toBackup(): ProfileBackupData = ProfileBackupData(
    name = name,
    avatarSeed = avatarSeed,
    nativeLang = nativeLang,
    targetLang = targetLang,
    createdAt = createdAt,
    cefrOverall = cefrOverall.name,
    cefrSpeaking = cefrSpeaking.name,
    cefrListening = cefrListening.name,
    cefrGrammar = cefrGrammar.name,
    cefrVocab = cefrVocab.name,
    levelLocked = levelLocked,
    interests = interests,
    goals = goals,
    dailyGoalMinutes = dailyGoalMinutes,
    createdFromPlacement = createdFromPlacement,
    cefrUpdatedAt = cefrUpdatedAt,
)

fun ProfileBackupData.toEntity(now: Long): ProfileEntity = ProfileEntity(
    name = name.trim().ifEmpty { "Ученик" },
    avatarSeed = avatarSeed,
    nativeLang = nativeLang,
    targetLang = targetLang,
    createdAt = createdAt.takeIf { it > 0 } ?: now,
    cefrOverall = Cefr.from(cefrOverall),
    cefrSpeaking = Cefr.from(cefrSpeaking),
    cefrListening = Cefr.from(cefrListening),
    cefrGrammar = Cefr.from(cefrGrammar),
    cefrVocab = Cefr.from(cefrVocab),
    levelLocked = levelLocked,
    interests = interests,
    goals = goals,
    dailyGoalMinutes = dailyGoalMinutes,
    createdFromPlacement = createdFromPlacement,
    cefrUpdatedAt = cefrUpdatedAt,
)

// --- Словарь ---

fun VocabItemEntity.toBackup(): VocabBackupItem = VocabBackupItem(
    term = term,
    translationRu = translationRu,
    partOfSpeech = partOfSpeech,
    definitionEn = definitionEn,
    exampleEn = exampleEn,
    ipa = ipa,
    source = source.name,
    tags = tags,
    ease = ease,
    intervalDays = intervalDays,
    repetitions = repetitions,
    lapses = lapses,
    state = state.name,
    dueAtEpochDay = dueAtEpochDay,
    lastReviewedEpochDay = lastReviewedEpochDay,
    createdAt = createdAt,
)

fun VocabBackupItem.toEntity(profileId: Long, now: Long): VocabItemEntity = VocabItemEntity(
    profileId = profileId,
    term = term,
    translationRu = translationRu,
    partOfSpeech = partOfSpeech,
    definitionEn = definitionEn,
    exampleEn = exampleEn,
    ipa = ipa,
    // Урок из чужой базы сюда не переезжает, связь со ставшим чужим id рвём.
    source = source.toEnumOrDefault(VocabSource.LESSON),
    sourceLessonId = null,
    tags = tags,
    ease = ease,
    intervalDays = intervalDays,
    repetitions = repetitions,
    lapses = lapses,
    state = state.toEnumOrDefault(VocabState.NEW),
    dueAtEpochDay = dueAtEpochDay,
    lastReviewedEpochDay = lastReviewedEpochDay,
    createdAt = createdAt.takeIf { it > 0 } ?: now,
)

// --- Прогресс ---

fun StreakEntity.toBackup(): StreakBackup = StreakBackup(
    current = current,
    longest = longest,
    lastActiveEpochDay = lastActiveEpochDay,
    freezesLeft = freezesLeft,
)

fun StreakBackup.toEntity(profileId: Long): StreakEntity = StreakEntity(
    profileId = profileId,
    current = current,
    longest = longest,
    lastActiveEpochDay = lastActiveEpochDay,
    freezesLeft = freezesLeft,
)

fun DailyStatEntity.toBackup(): DailyStatBackup = DailyStatBackup(
    epochDay = epochDay,
    minutesSpoken = minutesSpoken,
    lessonsCount = lessonsCount,
    wordsLearned = wordsLearned,
    reviewsDone = reviewsDone,
    mistakesFixed = mistakesFixed,
    costUsd = costUsd,
    goalMet = goalMet,
)

fun DailyStatBackup.toEntity(profileId: Long): DailyStatEntity = DailyStatEntity(
    profileId = profileId,
    epochDay = epochDay,
    minutesSpoken = minutesSpoken,
    lessonsCount = lessonsCount,
    wordsLearned = wordsLearned,
    reviewsDone = reviewsDone,
    mistakesFixed = mistakesFixed,
    costUsd = costUsd,
    goalMet = goalMet,
)

fun GrammarProgressEntity.toBackup(): GrammarProgressBackup = GrammarProgressBackup(
    topicId = topicId,
    mastery = mastery,
    lastPracticedAt = lastPracticedAt,
    mistakeCount = mistakeCount,
)

fun GrammarProgressBackup.toEntity(profileId: Long): GrammarProgressEntity = GrammarProgressEntity(
    profileId = profileId,
    topicId = topicId,
    mastery = mastery,
    lastPracticedAt = lastPracticedAt,
    mistakeCount = mistakeCount,
)

fun PhonemeScoreEntity.toBackup(): PhonemeScoreBackup = PhonemeScoreBackup(
    phoneme = phoneme,
    score = score,
    attempts = attempts,
    lastPracticedAt = lastPracticedAt,
)

fun PhonemeScoreBackup.toEntity(profileId: Long): PhonemeScoreEntity = PhonemeScoreEntity(
    profileId = profileId,
    phoneme = phoneme,
    score = score,
    attempts = attempts,
    lastPracticedAt = lastPracticedAt,
)

// --- Настройки ---

/** [rawApiKey] — расшифрованный ключ, если его решили положить в файл. */
fun AppSettings.toBackup(rawApiKey: String?): SettingsBackup = SettingsBackup(
    personaId = personaId,
    tutorName = tutorName,
    voiceName = voiceName,
    accent = accent.name,
    speechRate = speechRate,
    verbosity = verbosity.name,
    customPromptExtra = customPromptExtra,

    strictness = strictness,
    correctGrammar = correctGrammar,
    correctVocab = correctVocab,
    correctPronunciation = correctPronunciation,
    correctWordOrder = correctWordOrder,
    correctNaturalness = correctNaturalness,
    correctArticles = correctArticles,
    correctionLanguage = correctionLanguage.name,
    explanationLanguage = explanationLanguage.name,
    nativeLanguageUse = nativeLanguageUse.name,
    initiative = initiative.name,
    lessonMinutes = lessonMinutes,
    autoAnalyzeLesson = autoAnalyzeLesson,

    levelLocked = levelLocked,
    progressionPace = progressionPace.name,

    micMode = micMode.name,
    vadThresholdDb = vadThresholdDb,
    silenceHangoverMs = silenceHangoverMs,
    bargeInEnabled = bargeInEnabled,
    noiseSuppression = noiseSuppression,
    audioOutput = audioOutput.name,
    tutorVolume = tutorVolume,

    apiKey = rawApiKey,
    liveModelId = liveModelId,
    textModelId = textModelId,
    cheapModelId = cheapModelId,
    temperature = temperature,
    transcriptionEnabled = transcriptionEnabled,
    contextCompression = contextCompression,
    sessionResumption = sessionResumption,

    dailyLimitUsd = dailyLimitUsd,
    monthlyLimitUsd = monthlyLimitUsd,
    dailyLimitMinutes = dailyLimitMinutes,
    limitBehavior = limitBehavior.name,
    idleAutoStopSeconds = idleAutoStopSeconds,
    parentPinHash = parentPinHash,
    priceTextInPerMTok = priceTextInPerMTok,
    priceTextOutPerMTok = priceTextOutPerMTok,
    priceAudioInPerMTok = priceAudioInPerMTok,
    priceAudioOutPerMTok = priceAudioOutPerMTok,
    usdToRubRate = usdToRubRate,

    transcriptRetention = transcriptRetention.name,
    transcriptRetentionDays = transcriptRetentionDays,
    audioRecording = audioRecording.name,
    audioRetentionDays = audioRetentionDays,

    themeMode = themeMode.name,
    dynamicColor = dynamicColor,
    subtitleMode = subtitleMode.name,
    subtitleFontScale = subtitleFontScale,
    largeElements = largeElements,
    waveStyle = waveStyle.name,

    reminderEnabled = reminderEnabled,
    reminderMinuteOfDay = reminderMinuteOfDay,
    reminderDays = reminderDays.sorted(),
    reviewReminderEnabled = reviewReminderEnabled,
)

/**
 * Разворачивает снимок настроек. Ключ API здесь не трогаем — он шифруется
 * отдельно уже на устройстве.
 */
fun SettingsBackup.toSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        personaId = personaId,
        tutorName = tutorName,
        voiceName = voiceName,
        accent = accent.toEnumOrDefault(defaults.accent),
        speechRate = speechRate,
        verbosity = verbosity.toEnumOrDefault(defaults.verbosity),
        customPromptExtra = customPromptExtra,

        strictness = strictness,
        correctGrammar = correctGrammar,
        correctVocab = correctVocab,
        correctPronunciation = correctPronunciation,
        correctWordOrder = correctWordOrder,
        correctNaturalness = correctNaturalness,
        correctArticles = correctArticles,
        correctionLanguage = correctionLanguage.toEnumOrDefault(defaults.correctionLanguage),
        explanationLanguage = explanationLanguage.toEnumOrDefault(defaults.explanationLanguage),
        nativeLanguageUse = nativeLanguageUse.toEnumOrDefault(defaults.nativeLanguageUse),
        initiative = initiative.toEnumOrDefault(defaults.initiative),
        lessonMinutes = lessonMinutes,
        autoAnalyzeLesson = autoAnalyzeLesson,

        levelLocked = levelLocked,
        progressionPace = progressionPace.toEnumOrDefault(defaults.progressionPace),

        micMode = micMode.toEnumOrDefault(defaults.micMode),
        vadThresholdDb = vadThresholdDb,
        silenceHangoverMs = silenceHangoverMs,
        bargeInEnabled = bargeInEnabled,
        noiseSuppression = noiseSuppression,
        audioOutput = audioOutput.toEnumOrDefault(defaults.audioOutput),
        tutorVolume = tutorVolume,

        liveModelId = liveModelId ?: defaults.liveModelId,
        textModelId = textModelId ?: defaults.textModelId,
        cheapModelId = cheapModelId ?: defaults.cheapModelId,
        temperature = temperature,
        transcriptionEnabled = transcriptionEnabled,
        contextCompression = contextCompression,
        sessionResumption = sessionResumption,

        dailyLimitUsd = dailyLimitUsd,
        monthlyLimitUsd = monthlyLimitUsd,
        dailyLimitMinutes = dailyLimitMinutes,
        limitBehavior = limitBehavior.toEnumOrDefault(defaults.limitBehavior),
        idleAutoStopSeconds = idleAutoStopSeconds,
        parentPinHash = parentPinHash,
        priceTextInPerMTok = priceTextInPerMTok,
        priceTextOutPerMTok = priceTextOutPerMTok,
        priceAudioInPerMTok = priceAudioInPerMTok,
        priceAudioOutPerMTok = priceAudioOutPerMTok,
        usdToRubRate = usdToRubRate,

        transcriptRetention = transcriptRetention.toEnumOrDefault(defaults.transcriptRetention),
        transcriptRetentionDays = transcriptRetentionDays,
        audioRecording = audioRecording.toEnumOrDefault(defaults.audioRecording),
        audioRetentionDays = audioRetentionDays,

        themeMode = themeMode.toEnumOrDefault(defaults.themeMode),
        dynamicColor = dynamicColor,
        subtitleMode = subtitleMode.toEnumOrDefault(defaults.subtitleMode),
        subtitleFontScale = subtitleFontScale,
        largeElements = largeElements,
        waveStyle = waveStyle.toEnumOrDefault(defaults.waveStyle),

        reminderEnabled = reminderEnabled,
        reminderMinuteOfDay = reminderMinuteOfDay,
        reminderDays = reminderDays.toSet().takeIf { it.isNotEmpty() } ?: defaults.reminderDays,
        reviewReminderEnabled = reviewReminderEnabled,
    )
}

private inline fun <reified E : Enum<E>> String?.toEnumOrDefault(fallback: E): E =
    this?.let { raw -> enumValues<E>().firstOrNull { it.name == raw } } ?: fallback
