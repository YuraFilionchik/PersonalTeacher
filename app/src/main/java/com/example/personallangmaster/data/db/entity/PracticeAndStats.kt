package com.example.personallangmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.UsageKind

/** Фонема английского с подсказкой по артикуляции. Сид из `assets/phonemes.json`. */
@Entity(tableName = "phoneme")
data class PhonemeEntity(
    @PrimaryKey val id: String,
    val ipa: String,
    val type: String,
    val hintRu: String,
)

/** Минимальная пара для тренировки слуха и произношения. Сид из `assets/minimal_pairs.json`. */
@Entity(tableName = "minimal_pair", indices = [Index("phoneme1"), Index("phoneme2")])
data class MinimalPairEntity(
    @PrimaryKey val id: String,
    val word1: String,
    val word2: String,
    val phoneme1: String,
    val phoneme2: String,
    val translation1: String,
    val translation2: String,
)

/** Насколько хорошо профиль владеет конкретным звуком. Питает карту фонем. */
@Entity(
    tableName = "phoneme_score",
    primaryKeys = ["profileId", "phoneme"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PhonemeScoreEntity(
    val profileId: Long,
    val phoneme: String,
    val score: Int = 0,
    val attempts: Int = 0,
    val lastPracticedAt: Long? = null,
)

/** Попытка произнести фразу в тренажёре произношения. */
@Entity(
    tableName = "pronunciation_attempt",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class PronunciationAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val targetText: String,
    val phonemeFocus: String? = null,
    val recordedPath: String? = null,
    val score: Int? = null,
    val feedbackRu: String? = null,
    val createdAt: Long,
)

/** Ролевой сценарий разговора. Сид из `assets/scenarios.json`, свои сценарии — с `isCustom`. */
@Entity(tableName = "scenario", indices = [Index("category")])
data class ScenarioEntity(
    @PrimaryKey val id: String,
    val category: String,
    val level: Cefr,
    val titleRu: String,
    val titleEn: String,
    val descriptionRu: String,
    val durationMins: Int,
    val roleTutor: String,
    val roleUser: String,
    val goal: String,
    val successCriteria: List<String> = emptyList(),
    val openingLine: String,
    val vocabHints: List<String> = emptyList(),
    val isCustom: Boolean = false,
)

/** Запись о потраченных токенах. Основа экрана расходов и дневных лимитов. */
@Entity(
    tableName = "usage_log",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "at"])],
)
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val at: Long,
    val kind: UsageKind,
    val tokens: Long,
    val costUsd: Double,
    val lessonId: Long? = null,
)

/** Дневная сводка: заполняется по итогам уроков и повторений. */
@Entity(
    tableName = "daily_stat",
    primaryKeys = ["profileId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DailyStatEntity(
    val profileId: Long,
    val epochDay: Long,
    val minutesSpoken: Double = 0.0,
    val lessonsCount: Int = 0,
    val wordsLearned: Int = 0,
    val reviewsDone: Int = 0,
    val mistakesFixed: Int = 0,
    val costUsd: Double = 0.0,
    val goalMet: Boolean = false,
)

/** Серия дней подряд с выполненной целью. */
@Entity(
    tableName = "streak",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StreakEntity(
    @PrimaryKey val profileId: Long,
    val current: Int = 0,
    val longest: Int = 0,
    val lastActiveEpochDay: Long? = null,
    val freezesLeft: Int = 2,
)
