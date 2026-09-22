package com.example.personallangmaster.ui.progress

import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.entity.LessonEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Одна клетка месячной сетки. */
data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val total: Int,
    /** Решённые уроки дня: разобранные и закрытые без разбора — по ним больше нечего спрашивать. */
    val settled: Int,
)

/** Раскладка месяца по клеткам: неделя начинается с понедельника. */
object LessonCalendar {

    fun buildMonth(
        month: YearMonth,
        lessons: List<LessonEntity>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarDay> {
        val byDate = lessons.groupBy { lessonDate(it, zone) }

        val first = month.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        val last = month.atEndOfMonth()
        val end = last.plusDays((DayOfWeek.SUNDAY.value - last.dayOfWeek.value).toLong())

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                val inMonth = YearMonth.from(date) == month
                // Хвосты соседних месяцев показываем только как числа: их уроки
                // считаются в своём месяце, иначе одни и те же точки появились бы дважды.
                val dayLessons = if (inMonth) byDate[date].orEmpty() else emptyList()
                CalendarDay(
                    date = date,
                    inMonth = inMonth,
                    isToday = date == today,
                    total = dayLessons.size,
                    // SKIPPED — тоже принятое решение, а не то, что ждёт разбора:
                    // календарь не должен звать разобрать урок, который уже закрыли осознанно.
                    settled = dayLessons.count {
                        it.status == LessonStatus.ANALYZED || it.status == LessonStatus.SKIPPED
                    },
                )
            }
            .toList()
    }

    fun lessonDate(lesson: LessonEntity, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(lesson.startedAt).atZone(zone).toLocalDate()
}
