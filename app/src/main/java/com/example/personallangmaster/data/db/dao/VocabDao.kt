package com.example.personallangmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.data.db.entity.VocabReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {

    /** Слово может прийти повторно из другого урока — тогда просто не дублируем его. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(item: VocabItemEntity): Long

    @Upsert
    suspend fun upsert(item: VocabItemEntity)

    @Update
    suspend fun update(item: VocabItemEntity)

    @Query("SELECT * FROM vocab_item WHERE profileId = :profileId AND term = :term LIMIT 1")
    suspend fun findByTerm(profileId: Long, term: String): VocabItemEntity?

    @Query(
        "SELECT * FROM vocab_item WHERE profileId = :profileId AND state != 'SUSPENDED' " +
            "AND dueAtEpochDay <= :todayEpochDay ORDER BY dueAtEpochDay, id LIMIT :limit"
    )
    suspend fun dueItems(profileId: Long, todayEpochDay: Long, limit: Int = 50): List<VocabItemEntity>

    @Query(
        "SELECT COUNT(*) FROM vocab_item WHERE profileId = :profileId " +
            "AND state != 'SUSPENDED' AND dueAtEpochDay <= :todayEpochDay"
    )
    fun observeDueCount(profileId: Long, todayEpochDay: Long): Flow<Int>

    /** Слова в активной проработке — подставляются в промпт, чтобы тренер их использовал. */
    @Query(
        "SELECT term FROM vocab_item WHERE profileId = :profileId AND state IN ('LEARNING', 'REVIEW') " +
            "ORDER BY lastReviewedEpochDay DESC LIMIT :limit"
    )
    suspend fun activeTerms(profileId: Long, limit: Int = 10): List<String>

    @Query("SELECT * FROM vocab_item WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeAll(profileId: Long): Flow<List<VocabItemEntity>>

    @Query("SELECT COUNT(*) FROM vocab_item WHERE profileId = :profileId AND state = :state")
    fun observeCountByState(profileId: Long, state: VocabState): Flow<Int>

    @Query("DELETE FROM vocab_item WHERE id = :itemId")
    suspend fun delete(itemId: Long)

    @Insert
    suspend fun insertReview(review: VocabReviewEntity)

    @Query("SELECT COUNT(*) FROM vocab_review WHERE reviewedAt >= :since")
    suspend fun reviewsSince(since: Long): Int
}
