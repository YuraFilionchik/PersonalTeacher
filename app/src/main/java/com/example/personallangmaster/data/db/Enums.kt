package com.example.personallangmaster.data.db

/** Уровень владения языком по шкале CEFR. */
enum class Cefr { A1, A2, B1, B2, C1;

    companion object {
        fun fromOrNull(raw: String?): Cefr? = entries.firstOrNull { it.name.equals(raw, true) }
        fun from(raw: String?, fallback: Cefr = A2): Cefr = fromOrNull(raw) ?: fallback
    }
}

/** Режим проведения урока. */
enum class LessonMode { FREE_TALK, SCENARIO, PLACEMENT, DRILL }

/** Жизненный цикл урока: от активной сессии до разобранного. */
enum class LessonStatus { ACTIVE, COMPLETED, FAILED, ANALYZED }

/** Кто говорит в реплике. */
enum class Speaker { USER, TUTOR }

/** Категория ошибки ученика. */
enum class MistakeType {
    GRAMMAR, VOCAB, PRONUNCIATION, WORD_ORDER, ARTICLE, TENSE, PREPOSITION, STYLE
}

/** Откуда слово попало в словарь. */
enum class VocabSource { LESSON, MANUAL, EXERCISE }

/** Состояние карточки в интервальном повторении. */
enum class VocabState { NEW, LEARNING, REVIEW, MATURE, SUSPENDED }

/** Способ повторения карточки. */
enum class ReviewMode { RECOGNIZE, RECALL, SPEAK, LISTEN }

/** Тип упражнения по грамматике. */
enum class ExerciseKind { FILL_GAP, CHOICE, REORDER, TRANSLATE, SPEAK }

/** Тип израсходованных токенов — нужен для подсчёта стоимости. */
enum class UsageKind { LIVE_AUDIO_IN, LIVE_AUDIO_OUT, TEXT_IN, TEXT_OUT }
