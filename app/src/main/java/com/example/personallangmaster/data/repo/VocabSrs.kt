package com.example.personallangmaster.data.repo

import com.example.personallangmaster.core.srs.SrsCard
import com.example.personallangmaster.core.srs.SrsState
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.entity.VocabItemEntity

/**
 * Мост между карточкой словаря в базе и моделью планировщика.
 *
 * Планировщик специально ничего не знает про Room, а Room — про SM-2:
 * весь перевод одного в другое собран здесь и покрыт тестами, потому что
 * ошибка в этом месте тихо ломает все сроки повторений.
 */
object VocabSrs {

    fun toCard(item: VocabItemEntity): SrsCard = SrsCard(
        ease = item.ease,
        intervalDays = item.intervalDays,
        repetitions = item.repetitions,
        lapses = item.lapses,
        state = item.state.toSrsState(),
        dueAtEpochDay = item.dueAtEpochDay,
        lastReviewedEpochDay = item.lastReviewedEpochDay,
    )

    fun apply(item: VocabItemEntity, card: SrsCard): VocabItemEntity = item.copy(
        ease = card.ease,
        intervalDays = card.intervalDays,
        repetitions = card.repetitions,
        lapses = card.lapses,
        state = card.state.toVocabState(),
        dueAtEpochDay = card.dueAtEpochDay,
        lastReviewedEpochDay = card.lastReviewedEpochDay,
    )

    /** Имена состояний в обеих моделях совпадают, но связь фиксируем явно. */
    fun VocabState.toSrsState(): SrsState = when (this) {
        VocabState.NEW -> SrsState.NEW
        VocabState.LEARNING -> SrsState.LEARNING
        VocabState.REVIEW -> SrsState.REVIEW
        VocabState.MATURE -> SrsState.MATURE
        VocabState.SUSPENDED -> SrsState.SUSPENDED
    }

    fun SrsState.toVocabState(): VocabState = when (this) {
        SrsState.NEW -> VocabState.NEW
        SrsState.LEARNING -> VocabState.LEARNING
        SrsState.REVIEW -> VocabState.REVIEW
        SrsState.MATURE -> VocabState.MATURE
        SrsState.SUSPENDED -> VocabState.SUSPENDED
    }
}
