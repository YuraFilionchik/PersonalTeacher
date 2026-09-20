package com.example.personallangmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personallangmaster.data.db.ReviewMode
import com.example.personallangmaster.data.db.VocabSource
import com.example.personallangmaster.data.db.VocabState

/**
 * Карточка словаря. Поля интервального повторения совпадают с моделью SM-2,
 * планировщик живёт отдельно в `core/srs`.
 */
@Entity(
    tableName = "vocab_item",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["profileId", "dueAtEpochDay"]), Index(value = ["profileId", "term"], unique = true)],
)
data class VocabItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val term: String,
    val translationRu: String,
    val partOfSpeech: String? = null,
    val definitionEn: String? = null,
    val exampleEn: String? = null,
    val ipa: String? = null,
    val source: VocabSource = VocabSource.LESSON,
    val sourceLessonId: Long? = null,
    val tags: List<String> = emptyList(),
    val ease: Double = 2.5,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val state: VocabState = VocabState.NEW,
    val dueAtEpochDay: Long,
    val lastReviewedEpochDay: Long? = null,
    val createdAt: Long,
)

/** Факт одного повторения — нужен для статистики и отладки планировщика. */
@Entity(
    tableName = "vocab_review",
    foreignKeys = [
        ForeignKey(
            entity = VocabItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class VocabReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val reviewedAt: Long,
    val grade: Int,
    val mode: ReviewMode,
    val latencyMs: Long = 0,
)
