package com.example.personallangmaster.data.prefs

import com.example.personallangmaster.ai.ModelCatalog

/** Акцент тренера. Влияет на промпт и на подачу фонетики. */
enum class Accent { AMERICAN, BRITISH, NEUTRAL }

/** Насколько развёрнуто говорит тренер. */
enum class Verbosity { SHORT, MEDIUM, DETAILED }

/** Язык, на котором тренер даёт поправки голосом. */
enum class CorrectionLanguage { RU, EN, MIXED }

/** Язык объяснений грамматики в интерфейсе. */
enum class ExplanationLanguage { RU, EN }

/** Насколько свободно ученику разрешено переходить на русский. */
enum class NativeLanguageUse { ENGLISH_ONLY, ASK_ALLOWED, FREE }

/** Кто ведёт разговор. */
enum class Initiative { TUTOR, STUDENT, BALANCED }

/** Как работает кнопка микрофона. */
enum class MicMode { HOLD, TAP, HANDS_FREE }

/** Куда выводить звук тренера. */
enum class AudioOutput { AUTO, SPEAKER, HEADSET }

/** Что делать при достижении лимита расходов. */
enum class LimitBehavior { WARN, SOFT_STOP, BLOCK }

/** Сколько хранить транскрипты уроков. */
enum class TranscriptRetention { FOREVER, DAYS, NEVER }

/** Что записывать в аудио. */
enum class AudioRecording { NONE, USER_ONLY, FULL }

/** Когда показывать субтитры на экране урока. */
enum class SubtitleMode { ALWAYS, ON_TAP, USER_ONLY, NEVER }

/** Оформление приложения. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Стиль визуализации голоса. */
enum class WaveStyle { WAVE, PULSE, MINIMAL }

/** Темп усложнения материала. */
enum class ProgressionPace { CONSERVATIVE, NORMAL, AGGRESSIVE }

/**
 * Полный снимок настроек приложения — то, что на экране «Настройки» разложено
 * по секциям. Один неизменяемый объект удобно и отдавать в промпт-билдер,
 * и сравнивать при экспорте профиля.
 */
data class AppSettings(

    // --- Тренер ---
    val personaId: String = "friendly_buddy",
    val tutorName: String = "Alex",
    val voiceName: String = "Puck",
    val accent: Accent = Accent.AMERICAN,
    /** Темп речи 1..5, где 3 — обычный. */
    val speechRate: Int = 3,
    val verbosity: Verbosity = Verbosity.MEDIUM,
    val customPromptExtra: String = "",

    // --- Методика ---
    /** Строгость исправлений 0..4, см. StrictnessPolicy. */
    val strictness: Int = 2,
    val correctGrammar: Boolean = true,
    val correctVocab: Boolean = true,
    val correctPronunciation: Boolean = true,
    val correctWordOrder: Boolean = true,
    val correctNaturalness: Boolean = true,
    val correctArticles: Boolean = true,
    val correctionLanguage: CorrectionLanguage = CorrectionLanguage.MIXED,
    val explanationLanguage: ExplanationLanguage = ExplanationLanguage.RU,
    val nativeLanguageUse: NativeLanguageUse = NativeLanguageUse.ASK_ALLOWED,
    val initiative: Initiative = Initiative.BALANCED,
    /** Длительность урока по умолчанию в минутах, 0 — без ограничения. */
    val lessonMinutes: Int = 10,
    val autoAnalyzeLesson: Boolean = true,

    // --- Уровень ---
    val levelLocked: Boolean = false,
    val progressionPace: ProgressionPace = ProgressionPace.NORMAL,

    // --- Аудио ---
    val micMode: MicMode = MicMode.HOLD,
    /** Порог срабатывания VAD в dBFS: чем выше, тем меньше ловит шум. */
    val vadThresholdDb: Double = -38.0,
    val silenceHangoverMs: Int = 800,
    val bargeInEnabled: Boolean = true,
    val noiseSuppression: Boolean = true,
    val audioOutput: AudioOutput = AudioOutput.AUTO,
    val tutorVolume: Float = 1.0f,

    // --- Модель и ключ ---
    /** Ключ Gemini в зашифрованном виде: base64(IV + шифротекст) из KeyVault. */
    val apiKeyEncrypted: String = "",
    val liveModelId: String = ModelCatalog.DEFAULT_LIVE_MODEL,
    val textModelId: String = ModelCatalog.DEFAULT_TEXT_MODEL,
    val cheapModelId: String = ModelCatalog.DEFAULT_CHEAP_MODEL,
    val temperature: Float = 0.8f,
    val transcriptionEnabled: Boolean = true,
    val contextCompression: Boolean = true,
    val sessionResumption: Boolean = true,

    // --- Бюджет ---
    val dailyLimitUsd: Double = 0.5,
    val monthlyLimitUsd: Double = 10.0,
    val dailyLimitMinutes: Int = 30,
    val limitBehavior: LimitBehavior = LimitBehavior.SOFT_STOP,
    val idleAutoStopSeconds: Int = 45,
    val parentPinHash: String = "",
    /** Тарифы в долларах за миллион токенов — редактируемые, чтобы не протухли. */
    val priceTextInPerMTok: Double = 0.75,
    val priceTextOutPerMTok: Double = 4.50,
    val priceAudioInPerMTok: Double = 3.00,
    val priceAudioOutPerMTok: Double = 12.00,
    val usdToRubRate: Double = 0.0,

    // --- Данные и приватность ---
    val transcriptRetention: TranscriptRetention = TranscriptRetention.FOREVER,
    val transcriptRetentionDays: Int = 90,
    val audioRecording: AudioRecording = AudioRecording.NONE,
    val audioRetentionDays: Int = 14,

    // --- Внешний вид ---
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val subtitleMode: SubtitleMode = SubtitleMode.ALWAYS,
    val subtitleFontScale: Float = 1.0f,
    val largeElements: Boolean = false,
    val waveStyle: WaveStyle = WaveStyle.WAVE,

    // --- Напоминания ---
    val reminderEnabled: Boolean = false,
    /** Время напоминания в минутах от полуночи. */
    val reminderMinuteOfDay: Int = 19 * 60,
    /** Дни недели напоминания: 1 — понедельник, 7 — воскресенье. */
    val reminderDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val reviewReminderEnabled: Boolean = true,

    // --- Служебное ---
    val onboardingCompleted: Boolean = false,
) {
    /** Ключ введён — без него живой урок не запускается. */
    val hasApiKey: Boolean get() = apiKeyEncrypted.isNotBlank()

    /** Настройки закрыты родительским PIN. */
    val pinProtected: Boolean get() = parentPinHash.isNotBlank()
}
