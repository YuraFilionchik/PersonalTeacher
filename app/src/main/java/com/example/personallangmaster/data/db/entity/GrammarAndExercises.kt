package com.example.personallangmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.ExerciseKind

/**
 * Грамматическая тема. Базовый набор приезжает из `assets/grammar_topics.json`,
 * поэтому первичный ключ — строковый `id` из файла.
 */
@Entity(tableName = "grammar_topic")
data class GrammarTopicEntity(
    @PrimaryKey val id: String,
    val code: String,
    val titleRu: String,
    val titleEn: String,
    val cefr: Cefr,
    val explanationRu: String,
    val explanationEn: String,
    val isSeed: Boolean = true,
)

/** Прогресс профиля по конкретной теме. */
@Entity(
    tableName = "grammar_progress",
    primaryKeys = ["profileId", "topicId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("topicId")],
)
data class GrammarProgressEntity(
    val profileId: Long,
    val topicId: String,
    val mastery: Int = 0,
    val lastPracticedAt: Long? = null,
    val mistakeCount: Int = 0,
)

/**
 * Упражнение. Генерируется заранее в фоне, чтобы практика работала без сети;
 * `generatedFromMistakeId` связывает упражнение с реальной ошибкой ученика.
 */
@Entity(
    tableName = "exercise",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "used"]), Index("topicId")],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val topicId: String? = null,
    val kind: ExerciseKind,
    val promptText: String,
    val options: List<String> = emptyList(),
    val answer: String,
    val explanationRu: String? = null,
    val generatedFromMistakeId: Long? = null,
    val createdAt: Long,
    val used: Boolean = false,
)

/** Попытка выполнения упражнения. */
@Entity(
    tableName = "exercise_attempt",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("exerciseId")],
)
data class ExerciseAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val answeredAt: Long,
    val userAnswer: String,
    val correct: Boolean,
    val latencyMs: Long = 0,
)
