package com.example.personallangmaster.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBackupTest {

    private fun sample() = ProfileBackup(
        exportedAt = 1_700_000_000_000L,
        profile = ProfileBackupData(
            name = "Test User",
            targetLang = "en",
            cefrOverall = "B1",
            interests = listOf("Coding"),
            goals = "Работа",
        ),
        settings = SettingsBackup(tutorName = "Kate", strictness = 4),
        vocab = listOf(
            VocabBackupItem(
                term = "apple",
                translationRu = "яблоко",
                ease = 2.5,
                intervalDays = 5,
                state = "REVIEW",
            )
        ),
        progress = ProgressBackup(
            streak = StreakBackup(current = 10, longest = 15),
            dailyStats = listOf(DailyStatBackup(epochDay = 20_000, minutesSpoken = 12.0)),
            grammar = listOf(GrammarProgressBackup(topicId = "present_simple", mastery = 60)),
            phonemes = listOf(PhonemeScoreBackup(phoneme = "θ", score = 40, attempts = 7)),
        ),
    )

    @Test
    fun `экспорт и импорт сохраняют содержимое`() {
        val original = sample()

        val jsonString = ProfileBackupUtils.exportToJson(original)
        assertTrue(jsonString.contains("Test User"))
        assertTrue(jsonString.contains("apple"))

        val imported = ProfileBackupUtils.importFromJson(jsonString)
        assertEquals(original, imported)
    }

    @Test
    fun `ключ API не попадает в файл, если его не клали`() {
        val jsonString = ProfileBackupUtils.exportToJson(sample())
        assertTrue(jsonString.contains("\"apiKey\": null"))
    }

    @Test
    fun `битый файл не разбирается`() {
        assertNull(ProfileBackupUtils.importFromJson("{ invalid json }"))
    }

    @Test
    fun `файл более новой версии отвергается`() {
        val jsonString = """{"version":99,"profile":{"name":"Future User"}}"""
        assertNull(ProfileBackupUtils.importFromJson(jsonString))
    }

    @Test
    fun `минимальный файл читается на умолчаниях`() {
        val jsonString = """{"version":2,"profile":{"name":"Minimal User"}}"""
        val imported = ProfileBackupUtils.importFromJson(jsonString)

        assertNotNull(imported)
        assertEquals("Minimal User", imported?.profile?.name)
        assertEquals("A2", imported?.profile?.cefrOverall)
        assertTrue(imported?.vocab?.isEmpty() == true)
        assertNull(imported?.progress?.streak)
        assertEquals("Alex", imported?.settings?.tutorName)
    }
}
