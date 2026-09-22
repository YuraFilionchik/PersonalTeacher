package com.example.personallangmaster.domain

/**
 * Какие уроки выбраны в истории.
 *
 * Пустой выбор и выключенный режим — одно и то же состояние: панель с действиями
 * над пустым списком ничего не значит и только мешает.
 */
data class LessonSelection(
    val active: Boolean = false,
    val ids: Set<Long> = emptySet(),
) {

    val count: Int get() = ids.size

    /** Долгое нажатие: включает режим и сразу выбирает урок, по которому нажали. */
    fun start(lessonId: Long): LessonSelection =
        LessonSelection(active = true, ids = setOf(lessonId))

    fun toggle(lessonId: Long): LessonSelection =
        of(if (lessonId in ids) ids - lessonId else ids + lessonId)

    fun clear(): LessonSelection = LessonSelection()

    /** Оставляет выбранными только те уроки, которые ещё есть в списке. */
    fun retain(existing: Set<Long>): LessonSelection = of(ids intersect existing)

    private fun of(next: Set<Long>): LessonSelection =
        if (next.isEmpty()) LessonSelection() else LessonSelection(active = true, ids = next)
}
