package com.example.personallangmaster.domain

/**
 * Постановка урока в очередь на разбор.
 *
 * Отдельный интерфейс нужен, чтобы экран урока не тащил за собой Context
 * и WorkManager: ViewModel просто говорит «этот урок надо разобрать»,
 * а кто и когда это сделает — забота фонового слоя.
 */
interface AnalysisScheduler {

    /**
     * @param notify показать уведомление, когда разбор будет готов. Ночной
     * доразбор старых уроков проходит молча.
     */
    fun schedule(lessonId: Long, notify: Boolean = true)

    companion object {
        /** Заглушка для превью и тестов. */
        val Noop: AnalysisScheduler = object : AnalysisScheduler {
            override fun schedule(lessonId: Long, notify: Boolean) = Unit
        }
    }
}
