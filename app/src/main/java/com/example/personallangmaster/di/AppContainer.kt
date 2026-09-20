package com.example.personallangmaster.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.personallangmaster.core.crypto.KeyVault
import com.example.personallangmaster.core.speech.SpeechInput
import com.example.personallangmaster.core.speech.TtsController
import com.example.personallangmaster.data.db.AppDatabase
import com.example.personallangmaster.data.prefs.SettingsRepository
import com.example.personallangmaster.data.repo.LessonRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.VocabRepository
import com.example.personallangmaster.data.seed.SeedLoader
import com.example.personallangmaster.domain.AnalyzeLessonUseCase
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
    private val seedLoader: SeedLoader by lazy { SeedLoader(appContext, database.contentDao()) }

    /** Разовая инициализация на старте приложения: подгрузка учебного контента. */
    fun warmUp() {
        scope.launch { seedLoader.seedIfNeeded() }
    }
}

/** Доступ к контейнеру из Compose без прокидывания через все параметры. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer не предоставлен: оберните содержимое в CompositionLocalProvider")
}
