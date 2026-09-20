package com.example.personallangmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.entity.ProfileEntity
import com.example.personallangmaster.data.db.entity.StreakEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    /** Активный профиль устройства. Пока профиль один — берём первый созданный. */
    @Query("SELECT * FROM profile ORDER BY createdAt LIMIT 1")
    fun observeActive(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile ORDER BY createdAt LIMIT 1")
    suspend fun getActive(): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query(
        """
        UPDATE profile SET
            cefrOverall = :overall,
            cefrSpeaking = :speaking,
            cefrGrammar = :grammar,
            cefrVocab = :vocab,
            cefrUpdatedAt = :updatedAt
        WHERE id = :profileId AND levelLocked = 0
        """
    )
    suspend fun updateLevels(
        profileId: Long,
        overall: Cefr,
        speaking: Cefr,
        grammar: Cefr,
        vocab: Cefr,
        updatedAt: Long,
    )

    @Query("DELETE FROM profile WHERE id = :profileId")
    suspend fun delete(profileId: Long)

    @Upsert
    suspend fun upsertStreak(streak: StreakEntity)

    @Query("SELECT * FROM streak WHERE profileId = :profileId")
    fun observeStreak(profileId: Long): Flow<StreakEntity?>

    @Query("SELECT * FROM streak WHERE profileId = :profileId")
    suspend fun observeStreakOnce(profileId: Long): StreakEntity?
}
