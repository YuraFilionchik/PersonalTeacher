package com.example.personallangmaster.ui.progress

import com.example.personallangmaster.data.db.LessonMode
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.entity.LessonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Разметка месячной сетки: где начинается неделя, какие дни свои, и сколько
 * уроков в каждом дне — календарь на экране прогресса строится только этим.
 */
class LessonCalendarTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val month = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 21)

    private fun lessonAt(date: LocalDate, status: LessonStatus, hour: Int = 12) = LessonEntity(
        profileId = 1,
        startedAt = date.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli(),
        mode = LessonMode.FREE_TALK,
        personaId = "anna",
        strictness = 2,
        modelId = "test",
        status = status,
    )

    @Test
    fun `сетка начинается с понедельника и состоит из целых недель`() {
        val grid = LessonCalendar.buildMonth(month, emptyList(), today, zone)

        assertEquals(0, grid.size % 7)
        assertEquals(LocalDate.of(2026, 8, 31), grid.first().date)
        assertEquals(LocalDate.of(2026, 10, 4), grid.last().date)
    }

    @Test
    fun `дни соседних месяцев помечены чужими`() {
        val grid = LessonCalendar.buildMonth(month, emptyList(), today, zone)

        assertFalse(grid.first().inMonth)
        assertFalse(grid.last().inMonth)
        assertEquals(30, grid.count { it.inMonth })
    }

    @Test
    fun `уроки попадают в свой день и разобранные считаются отдельно`() {
        val day = LocalDate.of(2026, 9, 10)
        val grid = LessonCalendar.buildMonth(
            month,
            listOf(
                lessonAt(day, LessonStatus.ANALYZED),
                lessonAt(day, LessonStatus.COMPLETED, hour = 18),
                lessonAt(LocalDate.of(2026, 9, 11), LessonStatus.ANALYZED),
            ),
            today,
            zone,
        )

        val cell = grid.single { it.date == day }
        assertEquals(2, cell.total)
        assertEquals(1, cell.settled)
        assertEquals(1, grid.single { it.date == LocalDate.of(2026, 9, 11) }.total)
    }

    @Test
    fun `закрытый без разбора урок не считается ожидающим разбора`() {
        val day = LocalDate.of(2026, 9, 12)
        val grid = LessonCalendar.buildMonth(
            month,
            listOf(
                lessonAt(day, LessonStatus.SKIPPED),
                lessonAt(day, LessonStatus.COMPLETED, hour = 18),
            ),
            today,
            zone,
        )

        val cell = grid.single { it.date == day }
        assertEquals(2, cell.total)
        // SKIPPED — тоже решённый урок: календарь не должен звать разобрать то,
        // что человек уже осознанно закрыл.
        assertEquals(1, cell.settled)
    }

    @Test
    fun `день без уроков пустой, а не отрицательный`() {
        val grid = LessonCalendar.buildMonth(month, emptyList(), today, zone)

        assertTrue(grid.all { it.total == 0 && it.settled == 0 })
    }

    @Test
    fun `уроки чужого месяца не считаются даже в видимых хвостах недель`() {
        val grid = LessonCalendar.buildMonth(
            month,
            listOf(lessonAt(LocalDate.of(2026, 8, 31), LessonStatus.ANALYZED)),
            today,
            zone,
        )

        assertEquals(0, grid.sumOf { it.total })
    }

    @Test
    fun `сегодняшний день отмечен ровно один раз`() {
        val grid = LessonCalendar.buildMonth(month, emptyList(), today, zone)

        assertEquals(1, grid.count { it.isToday })
        assertEquals(today, grid.single { it.isToday }.date)
    }

    @Test
    fun `в другом месяце сегодняшнего дня нет`() {
        val grid = LessonCalendar.buildMonth(YearMonth.of(2026, 7), emptyList(), today, zone)

        assertTrue(grid.none { it.isToday })
    }
}
