package com.example.personallangmaster.data.repo

import com.example.personallangmaster.core.export.SettingsBackup
import com.example.personallangmaster.core.export.VocabBackupItem
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.VocabSource
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.entity.ProfileEntity
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.data.prefs.Accent
import com.example.personallangmaster.data.prefs.AppSettings
import com.example.personallangmaster.data.prefs.AudioRecording
import com.example.personallangmaster.data.prefs.LimitBehavior
import com.example.personallangmaster.data.prefs.MicMode
import com.example.personallangmaster.data.prefs.ThemeMode
import com.example.personallangmaster.data.prefs.Verbosity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Маппинг резервной копии проверяется круговым обходом: ошибка в одном поле из
 * шестидесяти иначе всплывёт только у человека, потерявшего свои настройки.
 */
class BackupMappingTest {

    @Test
    fun `настройки переживают круговой обход`() {
        val original = AppSettings(
            tutorName = "Kate",
            accent = Accent.BRITISH,
            speechRate = 5,
            verbosity = Verbosity.DETAILED,
            customPromptExtra = "Больше про работу",
            strictness = 4,
            correctArticles = false,
            lessonMinutes = 25,
            micMode = MicMode.HANDS_FREE,
            vadThresholdDb = -45.0,
            tutorVolume = 0.6f,
            temperature = 1.2f,
            dailyLimitUsd = 1.5,
            limitBehavior = LimitBehavior.BLOCK,
            parentPinHash = "hash",
            usdToRubRate = 95.0,
            audioRecording = AudioRecording.USER_ONLY,
            themeMode = ThemeMode.DARK,
            subtitleFontScale = 1.4f,
            reminderEnabled = true,
            reminderMinuteOfDay = 8 * 60 + 30,
            reminderDays = setOf(1, 3, 5),
        )

        val restored = original.toBackup(rawApiKey = null).toSettings()

        // Ключ и флаг онбординга живут на устройстве, а не в файле.
        assertEquals(original, restored.copy(apiKeyEncrypted = original.apiKeyEncrypted))
    }

    @Test
    fun `ключ API попадает в снимок только по явной просьбе`() {
        val settings = AppSettings()

        assertNull(settings.toBackup(rawApiKey = null).apiKey)
        assertEquals("AIza-secret", settings.toBackup(rawApiKey = "AIza-secret").apiKey)
    }

    @Test
    fun `незнакомое значение перечисления откатывается к умолчанию`() {
        val restored = SettingsBackup(accent = "KLINGON", themeMode = "NEON").toSettings()

        assertEquals(Accent.AMERICAN, restored.accent)
        assertEquals(ThemeMode.SYSTEM, restored.themeMode)
    }

    @Test
    fun `профиль переживает круговой обход`() {
        val profile = ProfileEntity(
            id = 7,
            name = "Юра",
            avatarSeed = 3,
            createdAt = 1_000L,
            cefrOverall = Cefr.B2,
            cefrSpeaking = Cefr.B1,
            levelLocked = true,
            interests = listOf("музыка", "код"),
            goals = "Собеседования",
            dailyGoalMinutes = 20,
            createdFromPlacement = true,
            cefrUpdatedAt = 2_000L,
        )

        val restored = profile.toBackup().toEntity(now = 5_000L)

        // Идентификатор в новой базе свой: сравниваем всё остальное.
        assertEquals(profile.copy(id = 0), restored)
    }

    @Test
    fun `карточка словаря переживает круговой обход без связи с чужим уроком`() {
        val item = VocabItemEntity(
            id = 42,
            profileId = 1,
            term = "apple",
            translationRu = "яблоко",
            ipa = "ˈæpl",
            source = VocabSource.MANUAL,
            sourceLessonId = 99,
            tags = listOf("еда"),
            ease = 2.3,
            intervalDays = 6,
            repetitions = 3,
            lapses = 1,
            state = VocabState.REVIEW,
            dueAtEpochDay = 20_100,
            lastReviewedEpochDay = 20_094,
            createdAt = 1_000L,
        )

        val restored = item.toBackup().toEntity(profileId = 2, now = 5_000L)

        assertEquals(item.copy(id = 0, profileId = 2, sourceLessonId = null), restored)
    }

    @Test
    fun `карточка без даты создания получает текущую`() {
        val restored = VocabBackupItem(term = "pear", translationRu = "груша")
            .toEntity(profileId = 1, now = 5_000L)

        assertEquals(5_000L, restored.createdAt)
        assertEquals(VocabState.NEW, restored.state)
    }
}
