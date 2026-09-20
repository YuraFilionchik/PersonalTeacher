package com.example.personallangmaster.core.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Промежуточные DTO для сериализации профиля и его истории.
 */
@Serializable
data class ProfileBackup(
    val version: Int = 1,
    val name: String,
    val targetLang: String,
    val cefrOverall: String,
    val interests: String?,
    val goals: String?,
    val vocabItems: List<VocabBackupItem> = emptyList(),
    val stats: StatsBackup? = null,
)

@Serializable
data class VocabBackupItem(
    val term: String,
    val translation: String,
    val ease: Double,
    val intervalDays: Int,
    val state: String,
)

@Serializable
data class StatsBackup(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalMinutesSpoken: Int,
)

object ProfileBackupUtils {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Экспортирует профиль в JSON строку.
     */
    fun exportToJson(backup: ProfileBackup): String {
        return json.encodeToString(backup)
    }

    /**
     * Импортирует профиль из JSON строки с валидацией версии.
     */
    fun importFromJson(jsonString: String): ProfileBackup? {
        return try {
            val backup = json.decodeFromString<ProfileBackup>(jsonString)
            if (backup.version > 1) {
                null // Будущие версии пока не поддерживаем
            } else {
                backup
            }
        } catch (e: Exception) {
            null
        }
    }
}
