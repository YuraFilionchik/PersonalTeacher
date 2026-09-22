package com.example.personallangmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.personallangmaster.data.db.entity.DailyStatEntity
import com.example.personallangmaster.data.db.entity.GrammarProgressEntity
import com.example.personallangmaster.data.db.entity.PhonemeScoreEntity
import com.example.personallangmaster.data.db.entity.StreakEntity
import com.example.personallangmaster.data.db.entity.VocabItemEntity

/**
 * Чтение и запись данных профиля целиком — для резервной копии.
 *
 * Обычные DAO отдают срезы под конкретный экран (что повторить сегодня, что
 * показать в отчёте), а здесь нужен полный список без фильтров и потоков.
 */
@Dao
interface BackupDao {

    // --- Чтение ---

    @Query("SELECT * FROM vocab_item WHERE profileId = :profileId ORDER BY createdAt")
    suspend fun vocabItems(profileId: Long): List<VocabItemEntity>

    @Query("SELECT * FROM daily_stat WHERE profileId = :profileId ORDER BY epochDay")
    suspend fun dailyStats(profileId: Long): List<DailyStatEntity>

    @Query("SELECT * FROM grammar_progress WHERE profileId = :profileId ORDER BY topicId")
    suspend fun grammarProgress(profileId: Long): List<GrammarProgressEntity>

    @Query("SELECT * FROM phoneme_score WHERE profileId = :profileId ORDER BY phoneme")
    suspend fun phonemeScores(profileId: Long): List<PhonemeScoreEntity>

    @Query("SELECT * FROM streak WHERE profileId = :profileId")
    suspend fun streak(profileId: Long): StreakEntity?

    // --- Запись ---

    /**
     * Слово могло встретиться в файле дважды: уникальный индекс по `term`
     * не должен ронять весь импорт, поэтому дубли просто отбрасываются.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVocabItems(items: List<VocabItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyStats(stats: List<DailyStatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrammarProgress(progress: List<GrammarProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhonemeScores(scores: List<PhonemeScoreEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStreak(streak: StreakEntity)

    /**
     * Снос старого профиля перед импортом. Внешние ключи с каскадом уносят
     * уроки, словарь, статистику и упражнения; сид-контент остаётся на месте.
     */
    @Query("DELETE FROM profile")
    suspend fun deleteAllProfiles()
}
