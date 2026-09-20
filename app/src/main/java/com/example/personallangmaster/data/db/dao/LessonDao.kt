package com.example.personallangmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.MistakeType
import com.example.personallangmaster.data.db.entity.LessonEntity
import com.example.personallangmaster.data.db.entity.MistakeEntity
import com.example.personallangmaster.data.db.entity.TurnEntity
import kotlinx.coroutines.flow.Flow

/** Частая ошибка ученика — строка топа на экране прогресса. */
data class MistakeFrequency(
    val type: MistakeType,
    val grammarTopicId: String?,
    val total: Int,
    val lastAt: Long,
)

@Dao
interface LessonDao {

    @Insert
    suspend fun insert(lesson: LessonEntity): Long

    @Update
    suspend fun update(lesson: LessonEntity)

    @Query("SELECT * FROM lesson WHERE id = :lessonId")
    suspend fun getById(lessonId: Long): LessonEntity?

    @Query("SELECT * FROM lesson WHERE profileId = :profileId ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(profileId: Long, limit: Int = 50): Flow<List<LessonEntity>>

    @Query(
        "SELECT * FROM lesson WHERE profileId = :profileId AND status = :status " +
            "ORDER BY startedAt DESC LIMIT :limit"
    )
    suspend fun getByStatus(
        profileId: Long,
        status: LessonStatus,
        limit: Int = 20,
    ): List<LessonEntity>

    /** Сводки последних уроков — подставляются в системный промпт как память тренера. */
    @Query(
        "SELECT summaryEn FROM lesson WHERE profileId = :profileId AND summaryEn IS NOT NULL " +
            "ORDER BY startedAt DESC LIMIT :limit"
    )
    suspend fun recentSummaries(profileId: Long, limit: Int = 3): List<String>

    /** Последние оценки уровня — основа автокоррекции CEFR. */
    @Query(
        "SELECT cefrEstimate FROM lesson WHERE profileId = :profileId AND cefrEstimate IS NOT NULL " +
            "ORDER BY startedAt DESC LIMIT :limit"
    )
    suspend fun recentLevelEstimates(profileId: Long, limit: Int = 5): List<Cefr>

    @Insert
    suspend fun insertTurns(turns: List<TurnEntity>)

    @Query("SELECT * FROM turn WHERE lessonId = :lessonId ORDER BY `index`")
    fun observeTurns(lessonId: Long): Flow<List<TurnEntity>>

    @Query("SELECT * FROM turn WHERE lessonId = :lessonId ORDER BY `index`")
    suspend fun getTurns(lessonId: Long): List<TurnEntity>

    @Query("DELETE FROM turn WHERE lessonId IN (SELECT id FROM lesson WHERE startedAt < :before)")
    suspend fun deleteTurnsOlderThan(before: Long)

    /** Уроки, от которых остались аудиозаписи старше срока хранения. */
    @Query("SELECT * FROM lesson WHERE audioPath IS NOT NULL AND startedAt < :before")
    suspend fun lessonsWithAudioBefore(before: Long): List<LessonEntity>

    @Insert
    suspend fun insertMistakes(mistakes: List<MistakeEntity>): List<Long>

    @Insert
    suspend fun insertMistake(mistake: MistakeEntity): Long

    @Query("SELECT * FROM mistake WHERE lessonId = :lessonId ORDER BY severity DESC, id")
    fun observeMistakes(lessonId: Long): Flow<List<MistakeEntity>>

    /** Топ повторяющихся ошибок профиля за период — память тренера и вход для упражнений. */
    @Query(
        """
        SELECT type AS type, grammarTopicId AS grammarTopicId,
               COUNT(*) AS total, MAX(createdAt) AS lastAt
        FROM mistake
        WHERE profileId = :profileId AND createdAt >= :since AND resolved = 0
        GROUP BY type, grammarTopicId
        ORDER BY total DESC, lastAt DESC
        LIMIT :limit
        """
    )
    suspend fun topMistakes(profileId: Long, since: Long, limit: Int = 10): List<MistakeFrequency>

    @Query("SELECT * FROM mistake WHERE profileId = :profileId AND resolved = 0 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun unresolvedMistakes(profileId: Long, limit: Int = 50): List<MistakeEntity>

    @Query("UPDATE mistake SET resolved = 1 WHERE id IN (:ids)")
    suspend fun markResolved(ids: List<Long>)
}
