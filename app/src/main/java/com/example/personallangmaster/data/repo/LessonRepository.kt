package com.example.personallangmaster.data.repo

import com.example.personallangmaster.data.db.LessonMode
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
import com.example.personallangmaster.data.db.entity.LessonEntity
import com.example.personallangmaster.data.db.entity.MistakeEntity
import com.example.personallangmaster.data.db.entity.TurnEntity
import com.example.personallangmaster.data.db.entity.UsageLogEntity
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import java.time.LocalDate

/**
 * Всё, что урок пишет в базу: реплики, ошибки, новые слова и расход токенов.
 *
 * Держится отдельно от сессии, чтобы запись в базу не мешала разговору:
 * вызовы короткие и не блокируют поток аудио.
 */
class LessonRepository(
    private val lessonDao: LessonDao,
    private val vocabDao: VocabDao,
    private val contentDao: ContentDao,
    private val statsDao: StatsDao,
) {

    suspend fun startLesson(
        profileId: Long,
        mode: LessonMode,
        scenarioId: String?,
        personaId: String,
        strictness: Int,
        modelId: String,
        now: Long = System.currentTimeMillis(),
    ): Long = lessonDao.insert(
        LessonEntity(
            profileId = profileId,
            startedAt = now,
            mode = mode,
            scenarioId = scenarioId,
            personaId = personaId,
            strictness = strictness,
            modelId = modelId,
            status = LessonStatus.ACTIVE,
        )
    )

    suspend fun finishLesson(
        lessonId: Long,
        durationSec: Int,
        userSpeakSec: Int,
        aiSpeakSec: Int,
        tokensIn: Long,
        tokensOut: Long,
        costUsd: Double,
        audioPath: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        val lesson = lessonDao.getById(lessonId) ?: return
        lessonDao.update(
            lesson.copy(
                endedAt = now,
                audioPath = audioPath ?: lesson.audioPath,
                durationSec = durationSec,
                userSpeakSec = userSpeakSec,
                aiSpeakSec = aiSpeakSec,
                tokensIn = tokensIn,
                tokensOut = tokensOut,
                costUsd = costUsd,
                status = LessonStatus.COMPLETED,
            )
        )
    }

    /**
     * Удаляет аудиозаписи уроков старше срока хранения.
     *
     * Файл и ссылка на него чистятся вместе: иначе в истории остаются уроки
     * с кнопкой «переслушать», которая ничего не находит.
     */
    suspend fun deleteAudioOlderThan(days: Int) {
        if (days <= 0) return
        val threshold = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        lessonDao.lessonsWithAudioBefore(threshold).forEach { lesson ->
            lesson.audioPath?.let { path -> runCatching { java.io.File(path).delete() } }
            lessonDao.update(lesson.copy(audioPath = null))
        }
    }

    suspend fun markFailed(lessonId: Long) {
        lessonDao.getById(lessonId)?.let { lesson ->
            lessonDao.update(lesson.copy(status = LessonStatus.FAILED, endedAt = System.currentTimeMillis()))
        }
    }

    /** Одна реплика урока вместе с её местом в аудиозаписи. */
    data class TurnRecord(
        val speaker: Speaker,
        val text: String,
        val startMs: Long,
        val audioOffsetMs: Long?,
    )

    /**
     * Дописывает одну реплику сразу, как только она закончилась.
     *
     * Раньше транскрипт копился в памяти и сохранялся в конце урока — и если
     * систему не устраивало фоновое приложение, урок пропадал целиком. Одна
     * короткая вставка на реплику стоит дёшево, а терять разговор нельзя.
     */
    suspend fun appendTurn(lessonId: Long, index: Int, turn: TurnRecord) {
        lessonDao.insertTurns(
            listOf(
                TurnEntity(
                    lessonId = lessonId,
                    index = index,
                    speaker = turn.speaker,
                    text = turn.text,
                    startMs = turn.startMs,
                    audioOffsetMs = turn.audioOffsetMs,
                )
            )
        )
    }

    /**
     * Закрывает уроки, оставшиеся в состоянии ACTIVE.
     *
     * Такое бывает, когда систему не устроило фоновое приложение и процесс
     * убили прямо во время разговора: реплики уже сохранены, а сам урок висит
     * незакрытым и не попадает ни в историю, ни в разбор.
     */
    suspend fun closeStuckLessons(profileId: Long) {
        lessonDao.getByStatus(profileId, LessonStatus.ACTIVE, limit = 20).forEach { lesson ->
            val turns = lessonDao.getTurns(lesson.id)
            val durationSec = (turns.maxOfOrNull { it.startMs } ?: 0L) / 1000

            lessonDao.update(
                lesson.copy(
                    endedAt = lesson.startedAt + durationSec * 1000,
                    durationSec = durationSec.toInt(),
                    status = if (turns.isEmpty()) LessonStatus.FAILED else LessonStatus.COMPLETED,
                )
            )
        }
    }

    /** Транскрипт целиком — на случай, если реплики почему-то не дописались по ходу. */
    suspend fun saveTurns(lessonId: Long, turns: List<TurnRecord>) {
        if (turns.isEmpty()) return
        lessonDao.insertTurns(
            turns.mapIndexed { index, turn ->
                TurnEntity(
                    lessonId = lessonId,
                    index = index,
                    speaker = turn.speaker,
                    text = turn.text,
                    startMs = turn.startMs,
                    audioOffsetMs = turn.audioOffsetMs,
                )
            }
        )
    }

    /** Ошибка от инструмента `log_mistake` прямо во время урока. */
    suspend fun logMistake(
        profileId: Long,
        lessonId: Long?,
        type: String?,
        original: String,
        corrected: String,
        explanation: String?,
        grammarTopic: String?,
        phoneme: String?,
        severity: Int?,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val parsedType = MistakeType.entries.firstOrNull { it.name.equals(type, true) }
            ?: MistakeType.GRAMMAR

        // Ту же ошибку не плодим: увеличиваем счётчик повторов.
        val existing = lessonDao.unresolvedMistakes(profileId, limit = 100)
            .firstOrNull { it.original.equals(original, true) && it.type == parsedType }

        if (existing != null) {
            lessonDao.insertMistake(
                existing.copy(
                    id = 0,
                    lessonId = lessonId,
                    createdAt = now,
                    repeatCount = existing.repeatCount + 1,
                )
            )
            return existing.id
        }

        return lessonDao.insertMistake(
            MistakeEntity(
                lessonId = lessonId,
                profileId = profileId,
                type = parsedType,
                severity = severity?.coerceIn(1, 3) ?: 2,
                original = original,
                corrected = corrected,
                explanationRu = explanation,
                grammarTopicId = grammarTopic,
                phoneme = phoneme,
                createdAt = now,
            )
        )
    }

    /** Новое слово от инструмента `save_vocab`. Повтор просто игнорируется. */
    suspend fun saveVocab(
        profileId: Long,
        lessonId: Long?,
        term: String,
        translationRu: String,
        example: String?,
        now: Long = System.currentTimeMillis(),
    ) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        if (vocabDao.findByTerm(profileId, trimmed) != null) return

        vocabDao.insertIgnoring(
            VocabItemEntity(
                profileId = profileId,
                term = trimmed,
                translationRu = translationRu.trim(),
                exampleEn = example,
                source = VocabSource.LESSON,
                sourceLessonId = lessonId,
                state = VocabState.NEW,
                dueAtEpochDay = LocalDate.now().toEpochDay(),
                createdAt = now,
            )
        )
    }

    suspend fun logUsage(
        profileId: Long,
        lessonId: Long?,
        promptTokens: Long,
        responseTokens: Long,
        promptCostUsd: Double,
        responseCostUsd: Double,
        now: Long = System.currentTimeMillis(),
    ) {
        val entries = buildList {
            if (promptTokens > 0) {
                add(
                    UsageLogEntity(
                        profileId = profileId,
                        at = now,
                        kind = UsageKind.LIVE_AUDIO_IN,
                        tokens = promptTokens,
                        costUsd = promptCostUsd,
                        lessonId = lessonId,
                    )
                )
            }
            if (responseTokens > 0) {
                add(
                    UsageLogEntity(
                        profileId = profileId,
                        at = now,
                        kind = UsageKind.LIVE_AUDIO_OUT,
                        tokens = responseTokens,
                        costUsd = responseCostUsd,
                        lessonId = lessonId,
                    )
                )
            }
        }
        if (entries.isNotEmpty()) statsDao.insertUsage(entries)
    }

    /** Сколько потрачено с начала суток — на этом держится дневной лимит. */
    suspend fun spentTodayUsd(profileId: Long, dayStartMillis: Long): Double =
        statsDao.costSince(profileId, dayStartMillis)

    /** Память тренера: то, что подставляется в системный промпт. */
    suspend fun tutorMemory(profileId: Long, since: Long): TutorMemory = TutorMemory(
        summaries = lessonDao.recentSummaries(profileId),
        mistakes = lessonDao.topMistakes(profileId, since)
            .mapNotNull { it.grammarTopicId ?: it.type.name },
        phonemes = contentDao.weakestPhonemes(profileId),
        vocabulary = vocabDao.activeTerms(profileId),
    )

    suspend fun scenario(id: String) = contentDao.getScenario(id)

    data class TutorMemory(
        val summaries: List<String> = emptyList(),
        val mistakes: List<String> = emptyList(),
        val phonemes: List<String> = emptyList(),
        val vocabulary: List<String> = emptyList(),
    )
}
