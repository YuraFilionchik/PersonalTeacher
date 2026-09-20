package com.example.personallangmaster.data.repo

import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.dao.ProfileDao
import com.example.personallangmaster.data.db.entity.ProfileEntity
import com.example.personallangmaster.data.db.entity.StreakEntity
import kotlinx.coroutines.flow.Flow

/**
 * Профиль ученика. На устройстве он один: создаётся при онбординге и дальше
 * обновляется по итогам уроков.
 */
class ProfileRepository(private val profileDao: ProfileDao) {

    val activeProfile: Flow<ProfileEntity?> = profileDao.observeActive()

    suspend fun current(): ProfileEntity? = profileDao.getActive()

    suspend fun exists(): Boolean = profileDao.count() > 0

    /** Создаёт профиль и сразу заводит для него пустую серию дней. */
    suspend fun create(
        name: String,
        interests: List<String>,
        goals: String,
        dailyGoalMinutes: Int,
        startLevel: Cefr,
        now: Long,
    ): Long {
        val id = profileDao.insert(
            ProfileEntity(
                name = name.trim().ifEmpty { "Ученик" },
                createdAt = now,
                cefrOverall = startLevel,
                cefrSpeaking = startLevel,
                cefrListening = startLevel,
                cefrGrammar = startLevel,
                cefrVocab = startLevel,
                interests = interests,
                goals = goals.trim(),
                dailyGoalMinutes = dailyGoalMinutes,
            )
        )
        profileDao.upsertStreak(StreakEntity(profileId = id))
        return id
    }

    suspend fun update(profile: ProfileEntity) = profileDao.update(profile)

    /**
     * Применяет новую оценку уровня. Если ученик зафиксировал уровень вручную,
     * запрос ничего не меняет — это решается прямо в SQL.
     */
    suspend fun applyAssessment(
        profileId: Long,
        overall: Cefr,
        speaking: Cefr,
        grammar: Cefr,
        vocab: Cefr,
        now: Long,
    ) = profileDao.updateLevels(profileId, overall, speaking, grammar, vocab, now)

    fun observeStreak(profileId: Long): Flow<StreakEntity?> = profileDao.observeStreak(profileId)
}
