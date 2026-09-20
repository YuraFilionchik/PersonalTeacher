package com.example.personallangmaster.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBackupTest {

    @Test
    fun `exportToJson and importFromJson works correctly`() {
        val original = ProfileBackup(
            version = 1,
            name = "Test User",
            targetLang = "en",
            cefrOverall = "B1",
            interests = "Coding",
            goals = null,
            vocabItems = listOf(
                VocabBackupItem("apple", "яблоко", 2.5, 5, "REVIEW")
            ),
            stats = StatsBackup(10, 15, 120)
        )

        val jsonString = ProfileBackupUtils.exportToJson(original)
        assertTrue(jsonString.contains("Test User"))
        assertTrue(jsonString.contains("apple"))
        assertTrue(jsonString.contains("120"))

        val imported = ProfileBackupUtils.importFromJson(jsonString)
        assertNotNull(imported)
        assertEquals(original.name, imported?.name)
        assertEquals(1, imported?.vocabItems?.size)
        assertEquals("apple", imported?.vocabItems?.get(0)?.term)
        assertEquals(10, imported?.stats?.currentStreak)
    }

    @Test
    fun `importFromJson handles invalid json gracefully`() {
        val imported = ProfileBackupUtils.importFromJson("{ invalid json }")
        assertNull(imported)
    }

    @Test
    fun `importFromJson rejects unsupported versions`() {
        val jsonString = """{"version":2,"name":"Future User","targetLang":"en","cefrOverall":"B2"}"""
        val imported = ProfileBackupUtils.importFromJson(jsonString)
        assertNull(imported)
    }
    
    @Test
    fun `importFromJson accepts missing optional fields`() {
        val jsonString = """{"version":1,"name":"Minimal User","targetLang":"en","cefrOverall":"A1"}"""
        val imported = ProfileBackupUtils.importFromJson(jsonString)
        assertNotNull(imported)
        assertEquals("Minimal User", imported?.name)
        assertTrue(imported?.vocabItems?.isEmpty() == true)
        assertNull(imported?.stats)
    }
}
