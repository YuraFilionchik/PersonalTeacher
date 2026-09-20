package com.example.personallangmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.example.personallangmaster.data.db.entity.DailyStatEntity
import com.example.personallangmaster.data.db.entity.UsageLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StatsDao {

    @Insert
    suspend fun insertUsage(entry: UsageLogEntity)

    @Insert
    suspend fun insertUsage(entries: List<UsageLogEntity>)

    /** Потрачено за период — на этом держатся дневной и месячный лимиты. */
    @Query("SELECT COALESCE(SUM(costUsd), 0) FROM usage_log WHERE profileId = :profileId AND at >= :since")
    suspend fun costSince(profileId: Long, since: Long): Double

    @Query("SELECT COALESCE(SUM(costUsd), 0) FROM usage_log WHERE profileId = :profileId AND at >= :since")
    fun observeCostSince(profileId: Long, since: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(tokens), 0) FROM usage_log WHERE profileId = :profileId AND at >= :since")
    suspend fun tokensSince(profileId: Long, since: Long): Long

    @Query("DELETE FROM usage_log WHERE at < :before")
    suspend fun deleteUsageOlderThan(before: Long)

    @Upsert
    suspend fun upsertDailyStat(stat: DailyStatEntity)

    @Query("SELECT * FROM daily_stat WHERE profileId = :profileId AND epochDay = :epochDay")
    suspend fun dailyStat(profileId: Long, epochDay: Long): DailyStatEntity?

    @Query("SELECT * FROM daily_stat WHERE profileId = :profileId AND epochDay = :epochDay")
    fun observeDailyStat(profileId: Long, epochDay: Long): Flow<DailyStatEntity?>

    @Query(
        "SELECT * FROM daily_stat WHERE profileId = :profileId AND epochDay >= :fromEpochDay " +
            "ORDER BY epochDay"
    )
    fun observeRange(profileId: Long, fromEpochDay: Long): Flow<List<DailyStatEntity>>
}
