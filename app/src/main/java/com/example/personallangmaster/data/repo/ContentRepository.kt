package com.example.personallangmaster.data.repo

import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.ExerciseKind
import com.example.personallangmaster.data.db.dao.ContentDao
import com.example.personallangmaster.data.db.dao.LessonDao
import com.example.personallangmaster.data.db.entity.ExerciseAttemptEntity
import com.example.personallangmaster.data.db.entity.ExerciseEntity
import com.example.personallangmaster.data.db.entity.GrammarProgressEntity
import com.example.personallangmaster.data.db.entity.GrammarTopicEntity
import com.example.personallangmaster.data.db.entity.MinimalPairEntity
import com.example.personallangmaster.data.db.entity.MistakeEntity
import com.example.personallangmaster.data.db.entity.PhonemeEntity
import com.example.personallangmaster.data.db.entity.PhonemeScoreEntity
import com.example.personallangmaster.data.db.entity.PronunciationAttemptEntity
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import kotlinx.coroutines.flow.Flow

/**
 * Учебный контент: сценарии, грамматика, фонетика и упражнения.
 *
 * Справочная часть приезжает из `assets` при первом запуске и дальше не меняется,
 * а всё, что зависит от ученика — прогресс по темам, оценки звуков, сгенерированные
 * упражнения — накапливается здесь же.
 */
class ContentRepository(
    private val contentDao: ContentDao,
    private val lessonDao: LessonDao,
) {

    // --- Сценарии ---

    fun observeScenarios(): Flow<List<ScenarioEntity>> = contentDao.observeScenarios()

    suspend fun scenario(id: String): ScenarioEntity? = contentDao.getScenario(id)

    suspend fun saveScenario(scenario: ScenarioEntity) = contentDao.upsertScenario(scenario)

    // --- Грамматика ---

    fun observeTopics(): Flow<List<GrammarTopicEntity>> = contentDao.observeTopics()

    suspend fun topic(id: String): GrammarTopicEntity? = contentDao.getTopic(id)

    fun observeProgress(profileId: Long): Flow<List<GrammarProgressEntity>> =
        contentDao.observeProgress(profileId)

    /**
     * Темы, которые стоит подтянуть: сначала те, где ученик реально ошибается.
     *
     * Берём ошибки за период, считаем, по каким темам они повторяются, и
     * сортируем по частоте — так грамматика идёт от живой речи, а не от учебника.
     */
    suspend fun recommendedTopics(
        profileId: Long,
        since: Long,
        limit: Int = 5,
    ): List<RecommendedTopic> {
        val mistakes = lessonDao.topMistakes(profileId, since, limit = 20)
        val topics = mistakes.mapNotNull { frequency ->
            val topicId = frequency.grammarTopicId ?: return@mapNotNull null
            val topic = contentDao.getTopic(topicId) ?: return@mapNotNull null
            RecommendedTopic(topic = topic, mistakeCount = frequency.total)
        }
        return topics.sortedByDescending { it.mistakeCount }.take(limit)
    }

    suspend fun mistakesForTopic(
        profileId: Long,
        topicId: String,
        limit: Int = 20,
    ): List<MistakeEntity> = lessonDao.unresolvedMistakes(profileId, limit = 100)
        .filter { it.grammarTopicId == topicId }
        .take(limit)

    suspend fun updateMastery(profileId: Long, topicId: String, mastery: Int, now: Long) {
        contentDao.upsertProgress(
            GrammarProgressEntity(
                profileId = profileId,
                topicId = topicId,
                mastery = mastery.coerceIn(0, 100),
                lastPracticedAt = now,
            )
        )
    }

    // --- Упражнения ---

    suspend fun saveExercises(exercises: List<ExerciseEntity>) =
        contentDao.insertExercises(exercises)

    suspend fun pendingExercises(
        profileId: Long,
        topicId: String?,
        limit: Int = 10,
    ): List<ExerciseEntity> = contentDao.unusedExercises(profileId, topicId, limit)

    suspend fun pendingExerciseCount(profileId: Long): Int =
        contentDao.unusedExerciseCount(profileId)

    suspend fun recordAttempt(
        exerciseId: Long,
        answer: String,
        correct: Boolean,
        latencyMs: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        contentDao.insertAttempt(
            ExerciseAttemptEntity(
                exerciseId = exerciseId,
                answeredAt = now,
                userAnswer = answer,
                correct = correct,
                latencyMs = latencyMs,
            )
        )
        contentDao.markExerciseUsed(exerciseId)
    }

    // --- Фонетика ---

    fun observePhonemes(): Flow<List<PhonemeEntity>> = contentDao.observePhonemes()

    fun observePhonemeScores(profileId: Long): Flow<List<PhonemeScoreEntity>> =
        contentDao.observePhonemeScores(profileId)

    suspend fun weakestPhonemes(profileId: Long, limit: Int = 5): List<String> =
        contentDao.weakestPhonemes(profileId, limit)

    /**
     * Пары для звука.
     *
     * В сид-данных звук записан двумя способами: в `phonemes.json` без косых
     * черт (`ɪ`), в `minimal_pairs.json` — с ними (`/ɪ/`). Спрашиваем оба вида,
     * иначе половина карты фонем оказывается без единой пары.
     */
    suspend fun pairsFor(phoneme: String): List<MinimalPairEntity> {
        val bare = normalizePhoneme(phoneme)
        if (bare.isEmpty()) return emptyList()
        return contentDao.pairsForPhoneme("/$bare/")
            .ifEmpty { contentDao.pairsForPhoneme(bare) }
    }

    /**
     * Обновляет оценку звука скользящим средним.
     *
     * Одна неудачная попытка не должна обнулять звук, а одна удачная — объявлять
     * его освоенным: оценка движется постепенно, как и само произношение.
     */
    suspend fun recordPronunciation(
        profileId: Long,
        phoneme: String?,
        targetText: String,
        success: Boolean,
        previous: PhonemeScoreEntity?,
        now: Long = System.currentTimeMillis(),
    ) {
        contentDao.insertPronunciationAttempt(
            PronunciationAttemptEntity(
                profileId = profileId,
                targetText = targetText,
                phonemeFocus = phoneme,
                score = if (success) SUCCESS_SCORE else FAIL_SCORE,
                createdAt = now,
            )
        )

        val key = normalizePhoneme(phoneme.orEmpty())
        if (key.isEmpty()) return

        val attempts = (previous?.attempts ?: 0) + 1
        val previousScore = previous?.score ?: NEUTRAL_SCORE
        val target = if (success) SUCCESS_SCORE else FAIL_SCORE
        val updated = previousScore + ((target - previousScore) * SMOOTHING).toInt()

        contentDao.upsertPhonemeScore(
            PhonemeScoreEntity(
                profileId = profileId,
                // Оценки храним в одном виде — без косых черт, как в карте фонем.
                phoneme = key,
                score = updated.coerceIn(0, 100),
                attempts = attempts,
                lastPracticedAt = now,
            )
        )
    }

    data class RecommendedTopic(
        val topic: GrammarTopicEntity,
        val mistakeCount: Int,
    )

    companion object {
        /** Единый вид записи звука: `/θ/`, `θ` и ` θ ` — это один и тот же звук. */
        fun normalizePhoneme(value: String): String = value.trim().trim('/').trim()

        private const val SUCCESS_SCORE = 100
        private const val FAIL_SCORE = 20
        private const val NEUTRAL_SCORE = 50
        private const val SMOOTHING = 0.4
    }
}

/** Типы упражнений, которые умеет показывать экран практики. */
val supportedExerciseKinds = listOf(
    ExerciseKind.FILL_GAP,
    ExerciseKind.CHOICE,
    ExerciseKind.TRANSLATE,
)

/** Уровни, между которыми имеет смысл предлагать сценарии ученику. */
fun scenarioRange(level: Cefr): Pair<Cefr, Cefr> {
    val from = Cefr.entries.getOrElse(level.ordinal - 1) { Cefr.A1 }
    val to = Cefr.entries.getOrElse(level.ordinal + 1) { Cefr.C1 }
    return from to to
}
