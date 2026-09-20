package com.example.personallangmaster.data.repo

import com.example.personallangmaster.core.export.VocabCsv
import com.example.personallangmaster.core.export.VocabExportItem
import com.example.personallangmaster.core.srs.ReviewGrade
import com.example.personallangmaster.core.srs.Sm2Scheduler
import com.example.personallangmaster.data.db.ReviewMode
import com.example.personallangmaster.data.db.VocabSource
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.dao.VocabDao
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.data.db.entity.VocabReviewEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Словарь и очередь повторений.
 *
 * Весь модуль работает без сети: слова уже лежат в базе, планировщик считает
 * сроки локально, а озвучка и распознавание берутся у системы. Это сознательно —
 * повторять карточки в метро важнее, чем иметь на них красивый серверный отчёт.
 */
class VocabRepository(private val vocabDao: VocabDao) {

    fun observeAll(profileId: Long): Flow<List<VocabItemEntity>> = vocabDao.observeAll(profileId)

    fun observeDueCount(profileId: Long, today: Long = todayEpochDay()): Flow<Int> =
        vocabDao.observeDueCount(profileId, today)

    fun observeCountByState(profileId: Long, state: VocabState): Flow<Int> =
        vocabDao.observeCountByState(profileId, state)

    /** Очередь на сегодня: сначала самые просроченные. */
    suspend fun dueQueue(
        profileId: Long,
        today: Long = todayEpochDay(),
        limit: Int = DEFAULT_QUEUE_LIMIT,
    ): List<VocabItemEntity> = vocabDao.dueItems(profileId, today, limit)

    /**
     * Записывает оценку повторения и пересчитывает срок следующего показа.
     * Возвращает обновлённую карточку, чтобы экран сразу знал новый интервал.
     */
    suspend fun grade(
        item: VocabItemEntity,
        grade: ReviewGrade,
        mode: ReviewMode,
        latencyMs: Long = 0,
        today: Long = todayEpochDay(),
    ): VocabItemEntity {
        val scheduled = Sm2Scheduler.schedule(VocabSrs.toCard(item), grade, today)
        val updated = VocabSrs.apply(item, scheduled)

        vocabDao.update(updated)
        vocabDao.insertReview(
            VocabReviewEntity(
                itemId = item.id,
                reviewedAt = System.currentTimeMillis(),
                grade = grade.ordinal,
                mode = mode,
                latencyMs = latencyMs,
            )
        )
        return updated
    }

    /** «Отложить»: карточка уходит из сегодняшней очереди, но сроки не сбиваются. */
    suspend fun postpone(item: VocabItemEntity, days: Int = 1, today: Long = todayEpochDay()) {
        vocabDao.update(item.copy(dueAtEpochDay = today + days.coerceAtLeast(1)))
    }

    suspend fun setSuspended(item: VocabItemEntity, suspended: Boolean) {
        val state = if (suspended) {
            VocabState.SUSPENDED
        } else if (item.repetitions == 0) {
            VocabState.NEW
        } else {
            VocabState.REVIEW
        }
        vocabDao.update(item.copy(state = state))
    }

    /** Ручное добавление слова. Дубликаты не плодим: слово уже могло прийти из урока. */
    suspend fun addManual(
        profileId: Long,
        term: String,
        translationRu: String,
        example: String? = null,
        today: Long = todayEpochDay(),
    ): Boolean {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return false
        if (vocabDao.findByTerm(profileId, trimmed) != null) return false

        vocabDao.insertIgnoring(
            VocabItemEntity(
                profileId = profileId,
                term = trimmed,
                translationRu = translationRu.trim(),
                exampleEn = example?.trim()?.takeIf { it.isNotEmpty() },
                source = VocabSource.MANUAL,
                state = VocabState.NEW,
                dueAtEpochDay = today,
                createdAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun delete(itemId: Long) = vocabDao.delete(itemId)

    /** Выгрузка в формате, который понимает Anki. */
    fun toCsv(items: List<VocabItemEntity>): String = VocabCsv.exportToCsv(
        items.map { item ->
            VocabExportItem(
                term = item.term,
                translation = item.translationRu,
                definition = item.definitionEn,
                example = item.exampleEn,
                ipa = item.ipa,
                tags = item.tags,
            )
        }
    )

    private companion object {
        const val DEFAULT_QUEUE_LIMIT = 40

        fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
    }
}
