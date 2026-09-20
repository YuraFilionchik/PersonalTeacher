package com.example.personallangmaster.domain

import com.example.personallangmaster.ai.text.GeminiTextClient
import com.example.personallangmaster.ai.text.LessonAnalysis
import com.example.personallangmaster.ai.text.LessonAnalysisSchema
import com.example.personallangmaster.ai.text.TextResult
import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.MistakeType
import com.example.personallangmaster.data.db.Speaker
import com.example.personallangmaster.data.db.UsageKind
import com.example.personallangmaster.data.db.VocabSource
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.dao.ContentDao
import com.example.personallangmaster.data.db.dao.LessonDao
import com.example.personallangmaster.data.db.dao.StatsDao
import com.example.personallangmaster.data.db.dao.VocabDao
import com.example.personallangmaster.data.db.entity.MistakeEntity
import com.example.personallangmaster.data.db.entity.PhonemeScoreEntity
import com.example.personallangmaster.data.db.entity.UsageLogEntity
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.data.prefs.ExplanationLanguage
import com.example.personallangmaster.data.prefs.ProgressionPace
import com.example.personallangmaster.data.prefs.SettingsRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import java.time.LocalDate

/** Чем закончился разбор — это же видит экран. */
sealed interface AnalysisResult {
    data class Success(val analysis: LessonAnalysis, val levelChangedTo: Cefr?) : AnalysisResult
    data class Failure(val reason: String) : AnalysisResult
}

/**
 * Разбор урока после его окончания.
 *
 * Это второй по важности вызов модели после самого разговора: именно здесь
 * ошибки превращаются в упражнения, слова — в карточки, а разговор — в оценку
 * уровня. Стоит доли цента, поэтому включён по умолчанию.
 */
class AnalyzeLessonUseCase(
    private val lessonDao: LessonDao,
    private val vocabDao: VocabDao,
    private val contentDao: ContentDao,
    private val statsDao: StatsDao,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val textClient: GeminiTextClient = GeminiTextClient(),
) {

    suspend operator fun invoke(lessonId: Long): AnalysisResult {
        val lesson = lessonDao.getById(lessonId)
            ?: return AnalysisResult.Failure("Урок не найден")

        val turns = lessonDao.getTurns(lessonId)
        if (turns.count { it.speaker == Speaker.USER } < MIN_USER_TURNS) {
            return AnalysisResult.Failure("Слишком короткий разговор для разбора")
        }

        val settings = settingsRepository.current()
        val apiKey = settingsRepository.apiKey()
            ?: return AnalysisResult.Failure("Ключ Gemini не задан")

        val profile = profileRepository.current()
        val transcript = buildString {
            appendLine("Student level: ${profile?.cefrOverall?.name ?: "unknown"}")
            appendLine("Lesson transcript:")
            turns.forEach { turn ->
                val who = if (turn.speaker == Speaker.USER) "STUDENT" else "TUTOR"
                appendLine("$who: ${turn.text}")
            }
        }

        val result = textClient.generateStructured(
            apiKey = apiKey,
            model = settings.textModelId,
            prompt = transcript,
            systemInstruction = LessonAnalysisSchema.systemInstruction(
                explanationInRussian = settings.explanationLanguage == ExplanationLanguage.RU
            ),
            schema = LessonAnalysisSchema.schema,
            parse = LessonAnalysisSchema::parse,
        )

        return when (result) {
            is TextResult.Failure -> AnalysisResult.Failure(result.reason)
            is TextResult.Success -> {
                val analysis = result.value
                persist(lessonId, lesson.profileId, analysis)
                logUsage(lesson.profileId, lessonId, result, settings)

                val newLevel = profile
                    ?.takeIf { !it.levelLocked }
                    ?.let { updateLevelIfNeeded(it.id, it.cefrOverall, settings.progressionPace) }

                AnalysisResult.Success(analysis, newLevel)
            }
        }
    }

    private suspend fun persist(lessonId: Long, profileId: Long, analysis: LessonAnalysis) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()

        lessonDao.getById(lessonId)?.let { lesson ->
            lessonDao.update(
                lesson.copy(
                    summaryRu = analysis.summary_ru.takeIf { it.isNotBlank() },
                    summaryEn = analysis.summary_en.takeIf { it.isNotBlank() },
                    cefrEstimate = Cefr.fromOrNull(analysis.cefr_estimate),
                    fluencyScore = analysis.fluency.takeIf { it > 0 },
                    accuracyScore = analysis.accuracy.takeIf { it > 0 },
                    vocabularyScore = analysis.vocabulary_range.takeIf { it > 0 },
                    status = LessonStatus.ANALYZED,
                )
            )
        }

        val mistakes = analysis.mistakes
            .filter { it.original.isNotBlank() && it.corrected.isNotBlank() }
            .map { mistake ->
                MistakeEntity(
                    lessonId = lessonId,
                    profileId = profileId,
                    type = MistakeType.entries.firstOrNull { it.name.equals(mistake.type, true) }
                        ?: MistakeType.GRAMMAR,
                    severity = mistake.severity.coerceIn(1, 3),
                    original = mistake.original,
                    corrected = mistake.corrected,
                    explanationRu = mistake.explanation_ru.takeIf { it.isNotBlank() },
                    grammarTopicId = mistake.grammar_topic?.takeIf { it.isNotBlank() },
                    createdAt = now,
                )
            }
        if (mistakes.isNotEmpty()) lessonDao.insertMistakes(mistakes)

        analysis.vocab
            .filter { it.term.isNotBlank() }
            .forEach { word ->
                if (vocabDao.findByTerm(profileId, word.term.trim()) == null) {
                    vocabDao.insertIgnoring(
                        VocabItemEntity(
                            profileId = profileId,
                            term = word.term.trim(),
                            translationRu = word.translation_ru.trim(),
                            exampleEn = word.example,
                            source = VocabSource.LESSON,
                            sourceLessonId = lessonId,
                            state = VocabState.NEW,
                            dueAtEpochDay = today,
                            createdAt = now,
                        )
                    )
                }
            }

        // Проблемный звук тянет оценку фонемы вниз: карта фонем строится именно так.
        analysis.pronunciation_notes
            .filter { it.phoneme.isNotBlank() }
            .forEach { note ->
                contentDao.upsertPhonemeScore(
                    PhonemeScoreEntity(
                        profileId = profileId,
                        phoneme = note.phoneme,
                        score = PROBLEM_PHONEME_SCORE,
                        attempts = 1,
                        lastPracticedAt = now,
                    )
                )
            }
    }

    private suspend fun logUsage(
        profileId: Long,
        lessonId: Long,
        result: TextResult.Success<LessonAnalysis>,
        settings: com.example.personallangmaster.data.prefs.AppSettings,
    ) {
        val now = System.currentTimeMillis()
        val entries = listOf(
            UsageLogEntity(
                profileId = profileId,
                at = now,
                kind = UsageKind.TEXT_IN,
                tokens = result.promptTokens.toLong(),
                costUsd = CostCalculator.costUsd(
                    result.promptTokens.toLong(),
                    settings.priceTextInPerMTok,
                ),
                lessonId = lessonId,
            ),
            UsageLogEntity(
                profileId = profileId,
                at = now,
                kind = UsageKind.TEXT_OUT,
                tokens = result.responseTokens.toLong(),
                costUsd = CostCalculator.costUsd(
                    result.responseTokens.toLong(),
                    settings.priceTextOutPerMTok,
                ),
                lessonId = lessonId,
            ),
        ).filter { it.tokens > 0 }

        if (entries.isNotEmpty()) statsDao.insertUsage(entries)
    }

    /** Пересчёт уровня по последним оценкам. Логика решения — в [LevelProgression]. */
    private suspend fun updateLevelIfNeeded(
        profileId: Long,
        current: Cefr,
        pace: ProgressionPace,
    ): Cefr? {
        val estimates = lessonDao.recentLevelEstimates(profileId, limit = LEVEL_WINDOW)
        val target = LevelProgression.next(current, estimates, pace) ?: return null

        profileRepository.applyAssessment(
            profileId = profileId,
            overall = target,
            speaking = target,
            grammar = target,
            vocab = target,
            now = System.currentTimeMillis(),
        )
        return target
    }

    private companion object {
        const val MIN_USER_TURNS = 2
        const val PROBLEM_PHONEME_SCORE = 40
        const val LEVEL_WINDOW = 6
    }
}
