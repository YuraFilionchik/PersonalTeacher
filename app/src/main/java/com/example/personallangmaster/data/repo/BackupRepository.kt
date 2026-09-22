package com.example.personallangmaster.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.personallangmaster.core.export.ProfileBackup
import com.example.personallangmaster.core.export.ProfileBackupUtils
import com.example.personallangmaster.core.export.ProgressBackup
import com.example.personallangmaster.data.db.AppDatabase
import com.example.personallangmaster.data.db.entity.StreakEntity
import com.example.personallangmaster.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Короткая сводка по файлу: то, что показываем перед импортом. */
data class BackupSummary(
    val name: String,
    val level: String,
    val exportedAt: Long,
    val vocabCount: Int,
    val dayCount: Int,
    val hasApiKey: Boolean,
)

/** Чем закончилась операция с файлом резервной копии. */
sealed interface BackupOutcome {
    data object Success : BackupOutcome

    /** Экспортировать нечего: профиль ещё не создан. */
    data object NoProfile : BackupOutcome

    /** Файл не разбирается или сделан более новой версией приложения. */
    data object BadFile : BackupOutcome

    data class Failed(val message: String) : BackupOutcome
}

/**
 * Перенос профиля между устройствами: файл JSON с профилем, настройками,
 * словарём и прогрессом.
 *
 * Импорт устроен как полная замена: старый профиль удаляется целиком, а вместе
 * с ним по внешним ключам уходят уроки, словарь и статистика. Сид-контент
 * (грамматика, фонемы, сценарии) не трогаем — он общий и переживает импорт,
 * иначе после восстановления приложение осталось бы без учебного материала
 * до следующего запуска.
 */
class BackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {

    private val backupDao by lazy { database.backupDao() }
    private val profileDao by lazy { database.profileDao() }

    /** Собирает снимок текущего состояния. null — профиля ещё нет. */
    suspend fun buildBackup(includeApiKey: Boolean): ProfileBackup? = withContext(Dispatchers.IO) {
        val profile = profileDao.getActive() ?: return@withContext null
        val settings = settingsRepository.current()
        val rawKey = if (includeApiKey) settingsRepository.apiKey() else null

        ProfileBackup(
            exportedAt = System.currentTimeMillis(),
            profile = profile.toBackup(),
            settings = settings.toBackup(rawKey),
            vocab = backupDao.vocabItems(profile.id).map { it.toBackup() },
            progress = ProgressBackup(
                streak = backupDao.streak(profile.id)?.toBackup(),
                dailyStats = backupDao.dailyStats(profile.id).map { it.toBackup() },
                grammar = backupDao.grammarProgress(profile.id).map { it.toBackup() },
                phonemes = backupDao.phonemeScores(profile.id).map { it.toBackup() },
            ),
        )
    }

    /** Пишет резервную копию в выбранный пользователем файл. */
    suspend fun exportTo(uri: Uri, includeApiKey: Boolean): BackupOutcome =
        withContext(Dispatchers.IO) {
            val backup = buildBackup(includeApiKey) ?: return@withContext BackupOutcome.NoProfile
            runCatching {
                // Режим "wt": если пользователь выбрал существующий файл, он
                // должен быть перезаписан, а не дополнен хвостом старого.
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(ProfileBackupUtils.exportToJson(backup).toByteArray(Charsets.UTF_8))
                } ?: error("не удалось открыть файл для записи")
                BackupOutcome.Success
            }.getOrElse { error ->
                Log.w(TAG, "Экспорт не удался: ${error.message}")
                BackupOutcome.Failed(error.message ?: "неизвестная ошибка")
            }
        }

    /** Читает и разбирает файл, ничего не меняя в базе. */
    suspend fun readBackup(uri: Uri): ProfileBackup? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: return@runCatching null
            ProfileBackupUtils.importFromJson(text)
        }.getOrElse { error ->
            Log.w(TAG, "Импорт не удался: ${error.message}")
            null
        }
    }

    fun summarize(backup: ProfileBackup): BackupSummary = BackupSummary(
        name = backup.profile.name,
        level = backup.profile.cefrOverall,
        exportedAt = backup.exportedAt,
        vocabCount = backup.vocab.size,
        dayCount = backup.progress.dailyStats.size,
        hasApiKey = !backup.settings.apiKey.isNullOrBlank(),
    )

    /**
     * Заменяет текущие данные содержимым резервной копии.
     *
     * База меняется одной транзакцией: наполовину восстановленный профиль
     * хуже, чем невосстановленный.
     */
    suspend fun restore(backup: ProfileBackup): BackupOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            database.withTransaction {
                backupDao.deleteAllProfiles()
                val profileId = profileDao.insert(backup.profile.toEntity(now))

                backupDao.insertVocabItems(backup.vocab.map { it.toEntity(profileId, now) })
                backupDao.insertStreak(
                    backup.progress.streak?.toEntity(profileId) ?: StreakEntity(profileId = profileId)
                )
                backupDao.insertDailyStats(backup.progress.dailyStats.map { it.toEntity(profileId) })
                backupDao.insertGrammarProgress(backup.progress.grammar.map { it.toEntity(profileId) })
                backupDao.insertPhonemeScores(backup.progress.phonemes.map { it.toEntity(profileId) })
            }

            // Профиль уже есть — заново гонять человека по онбордингу незачем.
            settingsRepository.replaceAll(
                settings = backup.settings.toSettings().copy(onboardingCompleted = true),
                rawApiKey = backup.settings.apiKey,
            )
            BackupOutcome.Success
        }.getOrElse { error ->
            Log.w(TAG, "Восстановление не удалось: ${error.message}")
            BackupOutcome.Failed(error.message ?: "неизвестная ошибка")
        }
    }

    private companion object {
        const val TAG = "BackupRepository"
    }
}
