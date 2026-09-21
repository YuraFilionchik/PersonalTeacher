package com.example.personallangmaster.data.repo

import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.dao.LessonDao
import com.example.personallangmaster.data.db.dao.StatsDao
import com.example.personallangmaster.data.db.dao.VocabDao
import com.example.personallangmaster.data.db.entity.LessonEntity
import com.example.personallangmaster.domain.TranscriptFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Что делать с ошибками, словами и расходами удаляемого урока. */
enum class DeleteScope {
    /** Отвязать: результаты учёбы остаются, пропадает только связь с уроком. */
    KEEP_RESULTS,

    /** Удалить всё, что урок после себя оставил. */
    WITH_RESULTS,
}

/**
 * Действия над прошедшими уроками: удаление, чистка записей, пометка, состояние.
 *
 * Держится отдельно от [LessonRepository]: тот отвечает за запись урока в момент
 * разговора, а здесь всё, что происходит с уроком много позже — и ошибка здесь
 * стоит человеку потерянных данных, а не сорванной реплики.
 */
class LessonHistoryRepository(
    private val lessonDao: LessonDao,
    private val vocabDao: VocabDao,
    private val statsDao: StatsDao,
) {

    /**
     * Удаляет уроки вместе с записями и репликами.
     *
     * Ошибки, слова и расходы ссылаются на урок обычным полем без внешнего ключа,
     * поэтому решение по ним принимается явно: иначе они начнут указывать в пустоту.
     */
    suspend fun delete(lessonIds: List<Long>, scope: DeleteScope) {
        if (lessonIds.isEmpty()) return

        // Файл записи не должен пережить урок: ссылки на него уже не останется.
        deleteRecordings(lessonIds)

        when (scope) {
            DeleteScope.KEEP_RESULTS -> {
                lessonDao.unlinkMistakes(lessonIds)
                vocabDao.unlinkLessons(lessonIds)
                statsDao.unlinkLessons(lessonIds)
            }

            DeleteScope.WITH_RESULTS -> {
                lessonDao.deleteMistakesOfLessons(lessonIds)
                vocabDao.deleteOfLessons(lessonIds)
                statsDao.deleteOfLessons(lessonIds)
            }
        }

        lessonDao.deleteLessons(lessonIds)
    }

    /** Удаляет файлы записей и ссылки на них. Возвращает, сколько места освободилось. */
    suspend fun deleteRecordings(lessonIds: List<Long>): Long {
        if (lessonIds.isEmpty()) return 0

        val lessons = lessonDao.getByIds(lessonIds)
        val freed = withContext(Dispatchers.IO) {
            lessons.sumOf { lesson ->
                val path = lesson.audioPath
                if (path.isNullOrBlank()) return@sumOf 0L
                val file = File(path)
                // Размер читаем до удаления: после него длина всегда ноль.
                val size = runCatching { file.length() }.getOrDefault(0L)
                runCatching { file.delete() }
                size
            }
        }

        lessonDao.clearAudioPaths(lessonIds)
        return freed
    }

    /**
     * Размер записи каждого урока. Картой, а не суммой: размер нужен и в подписи
     * пункта меню отдельного урока, и в итоге по всему месяцу, а читать длину файла
     * при каждой перерисовке списка нельзя — это диск в главном потоке.
     */
    suspend fun recordingSizes(lessons: List<LessonEntity>): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            lessons.mapNotNull { lesson ->
                val path = lesson.audioPath
                if (path.isNullOrBlank()) return@mapNotNull null
                val size = runCatching { File(path).length() }.getOrDefault(0L)
                lesson.id to size
            }.toMap()
        }

    /** Урок закрыт без разбора: ошибки и слова из него не появятся. */
    suspend fun markSkipped(lessonId: Long) =
        lessonDao.setStatus(lessonId, LessonStatus.SKIPPED)

    /** Пустая пометка — это её отсутствие, а не пустая строка в карточке. */
    suspend fun setNote(lessonId: Long, note: String) =
        lessonDao.setNote(lessonId, note.trim().ifBlank { null })

    suspend fun transcript(lessonId: Long): String =
        TranscriptFormatter.plain(lessonDao.getTurns(lessonId))
}
