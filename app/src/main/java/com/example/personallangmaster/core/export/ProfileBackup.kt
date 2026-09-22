package com.example.personallangmaster.core.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Формат резервной копии профиля.
 *
 * Версия 2 хранит всё, что имеет смысл переносить на другое устройство: сам
 * профиль, полный снимок настроек, словарь вместе с расписанием повторений и
 * накопленный прогресс. Истории уроков и транскриптов здесь нет — файл должен
 * оставаться компактным и не таскать за собой сырые диалоги.
 *
 * Все поля, кроме имени профиля, имеют значения по умолчанию: старый или
 * урезанный файл должен читаться, а не падать на отсутствующем ключе.
 */
@Serializable
data class ProfileBackup(
    val version: Int = BACKUP_VERSION,
    /** Когда сделан экспорт, epoch millis. */
    val exportedAt: Long = 0L,
    val profile: ProfileBackupData,
    val settings: SettingsBackup = SettingsBackup(),
    val vocab: List<VocabBackupItem> = emptyList(),
    val progress: ProgressBackup = ProgressBackup(),
)

@Serializable
data class ProfileBackupData(
    val name: String,
    val avatarSeed: Int = 0,
    val nativeLang: String = "ru",
    val targetLang: String = "en",
    val createdAt: Long = 0L,
    val cefrOverall: String = "A2",
    val cefrSpeaking: String = "A2",
    val cefrListening: String = "A2",
    val cefrGrammar: String = "A2",
    val cefrVocab: String = "A2",
    val levelLocked: Boolean = false,
    val interests: List<String> = emptyList(),
    val goals: String = "",
    val dailyGoalMinutes: Int = 10,
    val createdFromPlacement: Boolean = false,
    val cefrUpdatedAt: Long? = null,
)

/** Карточка словаря целиком, вместе с состоянием интервального повторения. */
@Serializable
data class VocabBackupItem(
    val term: String,
    val translationRu: String,
    val partOfSpeech: String? = null,
    val definitionEn: String? = null,
    val exampleEn: String? = null,
    val ipa: String? = null,
    val source: String = "LESSON",
    val tags: List<String> = emptyList(),
    val ease: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val state: String = "NEW",
    val dueAtEpochDay: Long = 0L,
    val lastReviewedEpochDay: Long? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class ProgressBackup(
    val streak: StreakBackup? = null,
    val dailyStats: List<DailyStatBackup> = emptyList(),
    val grammar: List<GrammarProgressBackup> = emptyList(),
    val phonemes: List<PhonemeScoreBackup> = emptyList(),
)

@Serializable
data class StreakBackup(
    val current: Int = 0,
    val longest: Int = 0,
    val lastActiveEpochDay: Long? = null,
    val freezesLeft: Int = 2,
)

@Serializable
data class DailyStatBackup(
    val epochDay: Long,
    val minutesSpoken: Double = 0.0,
    val lessonsCount: Int = 0,
    val wordsLearned: Int = 0,
    val reviewsDone: Int = 0,
    val mistakesFixed: Int = 0,
    val costUsd: Double = 0.0,
    val goalMet: Boolean = false,
)

@Serializable
data class GrammarProgressBackup(
    val topicId: String,
    val mastery: Int = 0,
    val lastPracticedAt: Long? = null,
    val mistakeCount: Int = 0,
)

@Serializable
data class PhonemeScoreBackup(
    val phoneme: String,
    val score: Int = 0,
    val attempts: Int = 0,
    val lastPracticedAt: Long? = null,
)

/**
 * Снимок настроек приложения. Перечисления лежат строками — так файл читается
 * глазами и переживает перестановку констант.
 *
 * [apiKey] хранится открытым текстом и попадает в файл только по явной галочке
 * при экспорте: на устройстве ключ лежит зашифрованным в Keystore, а Keystore
 * наружу не переносится.
 */
@Serializable
data class SettingsBackup(
    val personaId: String = "friendly_buddy",
    val tutorName: String = "Alex",
    val voiceName: String = "Puck",
    val accent: String = "AMERICAN",
    val speechRate: Int = 3,
    val verbosity: String = "MEDIUM",
    val customPromptExtra: String = "",

    val strictness: Int = 2,
    val correctGrammar: Boolean = true,
    val correctVocab: Boolean = true,
    val correctPronunciation: Boolean = true,
    val correctWordOrder: Boolean = true,
    val correctNaturalness: Boolean = true,
    val correctArticles: Boolean = true,
    val correctionLanguage: String = "MIXED",
    val explanationLanguage: String = "RU",
    val nativeLanguageUse: String = "ASK_ALLOWED",
    val initiative: String = "BALANCED",
    val lessonMinutes: Int = 10,
    val autoAnalyzeLesson: Boolean = true,

    val levelLocked: Boolean = false,
    val progressionPace: String = "NORMAL",

    val micMode: String = "HOLD",
    val vadThresholdDb: Double = -38.0,
    val silenceHangoverMs: Int = 800,
    val bargeInEnabled: Boolean = true,
    val noiseSuppression: Boolean = true,
    val audioOutput: String = "AUTO",
    val tutorVolume: Float = 1.0f,

    /** Ключ Gemini открытым текстом; null — ключ в файл не клали. */
    val apiKey: String? = null,
    val liveModelId: String? = null,
    val textModelId: String? = null,
    val cheapModelId: String? = null,
    val temperature: Float = 0.8f,
    val transcriptionEnabled: Boolean = true,
    val contextCompression: Boolean = true,
    val sessionResumption: Boolean = true,

    val dailyLimitUsd: Double = 0.5,
    val monthlyLimitUsd: Double = 10.0,
    val dailyLimitMinutes: Int = 30,
    val limitBehavior: String = "SOFT_STOP",
    val idleAutoStopSeconds: Int = 45,
    val parentPinHash: String = "",
    val priceTextInPerMTok: Double = 0.75,
    val priceTextOutPerMTok: Double = 4.50,
    val priceAudioInPerMTok: Double = 3.00,
    val priceAudioOutPerMTok: Double = 12.00,
    val usdToRubRate: Double = 0.0,

    val transcriptRetention: String = "FOREVER",
    val transcriptRetentionDays: Int = 90,
    val audioRecording: String = "NONE",
    val audioRetentionDays: Int = 14,

    val themeMode: String = "SYSTEM",
    val dynamicColor: Boolean = true,
    val subtitleMode: String = "ALWAYS",
    val subtitleFontScale: Float = 1.0f,
    val largeElements: Boolean = false,
    val waveStyle: String = "WAVE",

    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = 19 * 60,
    val reminderDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val reviewReminderEnabled: Boolean = true,
)

/** Версия формата резервной копии. Файлы более новых версий не читаем. */
const val BACKUP_VERSION = 2

object ProfileBackupUtils {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Экспортирует профиль в JSON-строку. */
    fun exportToJson(backup: ProfileBackup): String = json.encodeToString(backup)

    /**
     * Читает резервную копию. Возвращает null, если файл не разбирается или
     * сделан более новой версией приложения.
     */
    fun importFromJson(jsonString: String): ProfileBackup? = try {
        json.decodeFromString<ProfileBackup>(jsonString).takeIf { it.version <= BACKUP_VERSION }
    } catch (e: Exception) {
        null
    }
}
