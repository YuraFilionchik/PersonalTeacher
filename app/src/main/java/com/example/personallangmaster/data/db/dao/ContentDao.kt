package com.example.personallangmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.entity.ExerciseAttemptEntity
import com.example.personallangmaster.data.db.entity.ExerciseEntity
import com.example.personallangmaster.data.db.entity.GrammarProgressEntity
import com.example.personallangmaster.data.db.entity.GrammarTopicEntity
import com.example.personallangmaster.data.db.entity.MinimalPairEntity
import com.example.personallangmaster.data.db.entity.PhonemeEntity
import com.example.personallangmaster.data.db.entity.PhonemeScoreEntity
import com.example.personallangmaster.data.db.entity.PronunciationAttemptEntity
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import kotlinx.coroutines.flow.Flow

/**
 * Учебный контент: сценарии, грамматика, фонетика. Сид-таблицы наполняются из
 * `assets/ *.json` при первом запуске, поэтому вставка идёт через REPLACE —
 * обновлённый файл просто перезаписывает записи.
 */
@Dao
interface ContentDao {

    // --- Сценарии ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScenarios(scenarios: List<ScenarioEntity>)

    @Query("SELECT * FROM scenario ORDER BY level, titleRu")
    fun observeScenarios(): Flow<List<ScenarioEntity>>

    @Query("SELECT * FROM scenario WHERE level BETWEEN :from AND :to ORDER BY level, titleRu")
    fun observeScenariosForLevel(from: Cefr, to: Cefr): Flow<List<ScenarioEntity>>

    @Query("SELECT * FROM scenario WHERE id = :id")
    suspend fun getScenario(id: String): ScenarioEntity?

    @Query("SELECT COUNT(*) FROM scenario")
    suspend fun scenarioCount(): Int

    @Upsert
    suspend fun upsertScenario(scenario: ScenarioEntity)

    // --- Грамматика ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<GrammarTopicEntity>)

    @Query("SELECT * FROM grammar_topic ORDER BY cefr, titleRu")
    fun observeTopics(): Flow<List<GrammarTopicEntity>>

    @Query("SELECT * FROM grammar_topic WHERE id = :id")
    suspend fun getTopic(id: String): GrammarTopicEntity?

    @Query("SELECT COUNT(*) FROM grammar_topic")
    suspend fun topicCount(): Int

    @Upsert
    suspend fun upsertProgress(progress: GrammarProgressEntity)

    @Query("SELECT * FROM grammar_progress WHERE profileId = :profileId")
    fun observeProgress(profileId: Long): Flow<List<GrammarProgressEntity>>

    // --- Упражнения ---

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Query(
        "SELECT * FROM exercise WHERE profileId = :profileId AND used = 0 " +
            "AND (:topicId IS NULL OR topicId = :topicId) ORDER BY createdAt LIMIT :limit"
    )
    suspend fun unusedExercises(profileId: Long, topicId: String?, limit: Int = 10): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercise WHERE profileId = :profileId AND used = 0")
    suspend fun unusedExerciseCount(profileId: Long): Int

    @Query("UPDATE exercise SET used = 1 WHERE id = :exerciseId")
    suspend fun markExerciseUsed(exerciseId: Long)

    @Insert
    suspend fun insertAttempt(attempt: ExerciseAttemptEntity)

    // --- Фонетика ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhonemes(phonemes: List<PhonemeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMinimalPairs(pairs: List<MinimalPairEntity>)

    @Query("SELECT * FROM phoneme ORDER BY type, ipa")
    fun observePhonemes(): Flow<List<PhonemeEntity>>

    @Query("SELECT COUNT(*) FROM phoneme")
    suspend fun phonemeCount(): Int

    @Query("SELECT COUNT(*) FROM minimal_pair")
    suspend fun minimalPairCount(): Int

    @Query("SELECT * FROM minimal_pair WHERE phoneme1 = :phoneme OR phoneme2 = :phoneme")
    suspend fun pairsForPhoneme(phoneme: String): List<MinimalPairEntity>

    @Upsert
    suspend fun upsertPhonemeScore(score: PhonemeScoreEntity)

    @Query("SELECT * FROM phoneme_score WHERE profileId = :profileId ORDER BY score")
    fun observePhonemeScores(profileId: Long): Flow<List<PhonemeScoreEntity>>

    /** Самые слабые звуки профиля — фокус тренажёра и подсказка тренеру. */
    @Query("SELECT phoneme FROM phoneme_score WHERE profileId = :profileId AND attempts > 0 ORDER BY score LIMIT :limit")
    suspend fun weakestPhonemes(profileId: Long, limit: Int = 5): List<String>

    @Insert
    suspend fun insertPronunciationAttempt(attempt: PronunciationAttemptEntity)
}
