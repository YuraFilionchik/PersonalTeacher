package com.example.personallangmaster.domain

import com.example.personallangmaster.data.db.LessonMode
import com.example.personallangmaster.data.db.LessonStatus
import com.example.personallangmaster.data.db.entity.LessonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Отбор уроков для массовых операций и подписи размеров: на этом держатся
 * фильтр «Без разбора», «удалить все несостоявшиеся» и «очистить все записи».
 */
class LessonHousekeepingTest {

    private fun lesson(
        id: Long,
        status: LessonStatus,
        audioPath: String? = null,
    ) = LessonEntity(
        id = id,
        profileId = 1,
        startedAt = 0,
        mode = LessonMode.FREE_TALK,
        personaId = "anna",
        strictness = 2,
        modelId = "test",
        status = status,
        audioPath = audioPath,
    )

    @Test
    fun `разбора ждёт только завершённый урок`() {
        assertTrue(LessonHousekeeping.needsAnalysis(lesson(1, LessonStatus.COMPLETED)))
        assertFalse(LessonHousekeeping.needsAnalysis(lesson(2, LessonStatus.ANALYZED)))
        assertFalse(LessonHousekeeping.needsAnalysis(lesson(3, LessonStatus.FAILED)))
    }

    @Test
    fun `закрытый вручную урок больше не ждёт разбора`() {
        assertFalse(LessonHousekeeping.needsAnalysis(lesson(1, LessonStatus.SKIPPED)))
    }

    @Test
    fun `несостоявшиеся уроки отбираются отдельно от остальных`() {
        val lessons = listOf(
            lesson(1, LessonStatus.FAILED),
            lesson(2, LessonStatus.COMPLETED),
            lesson(3, LessonStatus.FAILED),
            lesson(4, LessonStatus.ANALYZED),
        )

        assertEquals(listOf(1L, 3L), LessonHousekeeping.failed(lessons).map { it.id })
    }

    @Test
    fun `урок с пустым путём к записи считается без записи`() {
        val lessons = listOf(
            lesson(1, LessonStatus.ANALYZED, audioPath = "/data/lesson_1.wav"),
            lesson(2, LessonStatus.ANALYZED, audioPath = null),
            lesson(3, LessonStatus.ANALYZED, audioPath = ""),
        )

        assertEquals(listOf(1L), LessonHousekeeping.withRecording(lessons).map { it.id })
    }

    @Test
    fun `размер записи читается человеком`() {
        assertEquals("0 МБ", LessonHousekeeping.formatSize(0))
        assertEquals("512 КБ", LessonHousekeeping.formatSize(524_288))
        assertEquals("2,0 МБ", LessonHousekeeping.formatSize(2_097_152))
    }
}
