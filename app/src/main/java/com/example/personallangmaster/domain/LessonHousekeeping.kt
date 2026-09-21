package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.entity.LessonEntity
import java.util.Locale

/** Отбор уроков для массовых операций и подписи к ним. */
object LessonHousekeeping {

    /** Урок, который всё ещё ждёт разбора: только такие напоминают о себе. */
    fun needsAnalysis(lesson: LessonEntity): Boolean = lesson.status == LessonStatus.COMPLETED

    /** Несостоявшиеся уроки: их набирается больше всего, и чистят их пачкой. */
    fun failed(lessons: List<LessonEntity>): List<LessonEntity> =
        lessons.filter { it.status == LessonStatus.FAILED }

    /** Уроки, от которых остался файл записи. */
    fun withRecording(lessons: List<LessonEntity>): List<LessonEntity> =
        lessons.filter { !it.audioPath.isNullOrBlank() }

    /** «2,0 МБ» — размер записи в подписи пункта меню. */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 МБ"
        bytes < BYTES_IN_MB -> "%.0f КБ".format(RU, bytes / 1024.0)
        else -> "%.1f МБ".format(RU, bytes / BYTES_IN_MB.toDouble())
    }

    private const val BYTES_IN_MB = 1024L * 1024
    private val RU = Locale("ru")
}
