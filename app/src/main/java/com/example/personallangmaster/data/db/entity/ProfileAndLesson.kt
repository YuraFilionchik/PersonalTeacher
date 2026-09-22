package com.example.personallangmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.LessonMode
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.MistakeType
import com.example.personallangmaster.data.db.Speaker

/**
 * Профиль ученика. На устройстве он один, но таблица рассчитана на несколько —
 * чтобы позже можно было добавить профили без миграции схемы.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarSeed: Int = 0,
    val nativeLang: String = "ru",
    val targetLang: String = "en",
    val createdAt: Long,
    val cefrOverall: Cefr = Cefr.A2,
    val cefrSpeaking: Cefr = Cefr.A2,
    val cefrListening: Cefr = Cefr.A2,
    val cefrGrammar: Cefr = Cefr.A2,
    val cefrVocab: Cefr = Cefr.A2,
    /** Уровень зафиксирован вручную: автокоррекция его не меняет. */
    val levelLocked: Boolean = false,
    val interests: List<String> = emptyList(),
    val goals: String = "",
    val dailyGoalMinutes: Int = 10,
    val createdFromPlacement: Boolean = false,
    val cefrUpdatedAt: Long? = null,
)

/** Один проведённый урок со сводной статистикой и результатом разбора. */
@Entity(
    tableName = "lesson",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("startedAt")],
)
data class LessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val mode: LessonMode,
    val scenarioId: String? = null,
    val personaId: String,
    val strictness: Int,
    val modelId: String,
    val durationSec: Int = 0,
    val userSpeakSec: Int = 0,
    val aiSpeakSec: Int = 0,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val costUsd: Double = 0.0,
    val summaryRu: String? = null,
    val summaryEn: String? = null,
    val cefrEstimate: Cefr? = null,
    val fluencyScore: Int? = null,
    val accuracyScore: Int? = null,
    val vocabularyScore: Int? = null,
    val audioPath: String? = null,
    val status: LessonStatus = LessonStatus.ACTIVE,
    /** Своя пометка от руки: «говорили про работу», «плохая связь». */
    val note: String? = null,
)

/** Реплика диалога. Хранится, если в настройках не отключено хранение транскриптов. */
@Entity(
    tableName = "turn",
    foreignKeys = [
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["lessonId", "index"])],
)
data class TurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long,
    val index: Int,
    val speaker: Speaker,
    val text: String,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val audioOffsetMs: Long? = null,
)

/**
 * Ошибка ученика. Источник — либо вызов `log_mistake` прямо во время урока,
 * либо разбор после него. Именно эта таблица питает грамматику и произношение.
 */
@Entity(
    tableName = "mistake",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "type", "createdAt"]), Index("lessonId")],
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: Long?,
    val profileId: Long,
    val type: MistakeType,
    val severity: Int = 2,
    val original: String,
    val corrected: String,
    val explanationRu: String? = null,
    val explanationEn: String? = null,
    val grammarTopicId: String? = null,
    val phoneme: String? = null,
    val createdAt: Long,
    val resolved: Boolean = false,
    val repeatCount: Int = 1,
)
