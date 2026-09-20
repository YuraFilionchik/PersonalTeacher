package com.example.personallangmaster.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.personallangmaster.core.crypto.KeyVault
import com.example.personallangmaster.core.speech.SpeechInput
import com.example.personallangmaster.core.speech.TtsController
import com.example.personallangmaster.data.db.AppDatabase
import com.example.personallangmaster.data.prefs.SettingsRepository
import com.example.personallangmaster.data.repo.ContentRepository
import com.example.personallangmaster.data.repo.LessonRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.StatsRepository
import com.example.personallangmaster.data.repo.VocabRepository
import com.example.personallangmaster.data.seed.SeedLoader
import com.example.personallangmaster.domain.AnalyzeLessonUseCase
import com.example.personallangmaster.domain.GenerateExercisesUseCase
import com.example.personallangmaster.domain.GenerateScenarioUseCase
import com.example.personallangmaster.work.Notifications
import com.example.personallangmaster.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ручной контейнер зависимостей: один экземпляр на приложение.
 *
 * Hilt здесь не нужен — граф зависимостей маленький и плоский, а явная сборка
 * читается лучше генерации. Если граф разрастётся, точка входа всего одна.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val keyVault: KeyVault by lazy { KeyVault() }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext, keyVault) }
    val profileRepository: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }
    val lessonRepository: LessonRepository by lazy {
        LessonRepository(
            lessonDao = database.lessonDao(),
            vocabDao = database.vocabDao(),
            contentDao = database.contentDao(),
            statsDao = database.statsDao(),
        )
    }
    val vocabRepository: VocabRepository by lazy { VocabRepository(database.vocabDao()) }
    val statsRepository: StatsRepository by lazy {
        StatsRepository(statsDao = database.statsDao(), profileDao = database.profileDao())
    }
    val contentRepository: ContentRepository by lazy {
        ContentRepository(contentDao = database.contentDao(), lessonDao = database.lessonDao())
    }

    // Системные озвучка и распознавание: повторения должны работать без сети и бесплатно.
    val ttsController: TtsController by lazy { TtsController(appContext) }
    val speechInput: SpeechInput by lazy { SpeechInput(appContext) }

    val analyzeLessonUseCase: AnalyzeLessonUseCase by lazy {
        AnalyzeLessonUseCase(
            lessonDao = database.lessonDao(),
            vocabDao = database.vocabDao(),
            contentDao = database.contentDao(),
            statsDao = database.statsDao(),
            settingsRepository = settingsRepository,
            profileRepository = profileRepository,
        )
    }
    val generateScenarioUseCase: GenerateScenarioUseCase by lazy {
        GenerateScenarioUseCase(
            settingsRepository = settingsRepository,
            profileRepository = profileRepository,
            contentRepository = contentRepository,
        )
    }
    val generateExercisesUseCase: GenerateExercisesUseCase by lazy {
        GenerateExercisesUseCase(
            settingsRepository = settingsRepository,
            profileRepository = profileRepository,
            contentRepository = contentRepository,
        )
    }
    private val seedLoader: SeedLoader by lazy { SeedLoader(appContext, database.contentDao()) }

    /** Разовая инициализация на старте: контент, канал уведомлений и расписание задач. */
    fun warmUp() {
        scope.launch {
            seedLoader.seedIfNeeded()
            Notifications.ensureChannel(appContext)
            WorkScheduler.sync(appContext, settingsRepository.current())
        }
    }

    /** Пересобрать расписание после изменения настроек напоминаний. */
    fun rescheduleReminders() {
        scope.launch { WorkScheduler.sync(appContext, settingsRepository.current()) }
    }
}

/** Доступ к контейнеру из Compose без прокидывания через все параметры. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer не предоставлен: оберните содержимое в CompositionLocalProvider")
}
