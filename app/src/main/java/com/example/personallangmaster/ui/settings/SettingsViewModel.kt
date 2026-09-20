package com.example.personallangmaster.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.ai.prompt.TutorContext
import com.example.personallangmaster.ai.prompt.TutorPromptBuilder
import com.example.personallangmaster.ai.text.GeminiKeyChecker
import com.example.personallangmaster.ai.text.KeyCheckResult
import com.example.personallangmaster.core.crypto.PinHasher
import com.example.personallangmaster.data.db.AppDatabase
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.entity.ProfileEntity
import com.example.personallangmaster.data.prefs.AppSettings
import com.example.personallangmaster.data.prefs.SettingsRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Что происходит с проверкой ключа прямо сейчас. */
sealed interface KeyCheckState {
    data object Idle : KeyCheckState
    data object Running : KeyCheckState
    data class Done(val result: KeyCheckResult) : KeyCheckState
}

/**
 * Одна модель на весь экран настроек и все его подэкраны: настройки — единый
 * объект, дробить его по экранам смысла нет.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val database: AppDatabase,
    private val keyChecker: GeminiKeyChecker = GeminiKeyChecker(),
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val profile: StateFlow<ProfileEntity?> = profileRepository.activeProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _keyCheck = MutableStateFlow<KeyCheckState>(KeyCheckState.Idle)
    val keyCheck: StateFlow<KeyCheckState> = _keyCheck.asStateFlow()

    /** Разблокирован ли доступ к защищённым секциям в текущем сеансе. */
    private val _pinUnlocked = MutableStateFlow(false)
    val pinUnlocked: StateFlow<Boolean> = _pinUnlocked.asStateFlow()

    private val _promptPreview = MutableStateFlow("")
    val promptPreview: StateFlow<String> = _promptPreview.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val spentTodayUsd: StateFlow<Double> = profile.filterNotNull().flatMapLatest { p ->
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        database.statsDao().observeCostSince(p.id, startOfDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val spentMonthUsd: StateFlow<Double> = profile.filterNotNull().flatMapLatest { p ->
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        database.statsDao().observeCostSince(p.id, startOfMonth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val repository: SettingsRepository get() = settingsRepository

    /** Точечное изменение настроек: каждый экран вызывает свой метод репозитория. */
    fun update(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }

    // --- Уровень ---

    /** Замок уровня держим и в настройках (для UI), и в профиле (для SQL-защиты). */
    fun setLevelLocked(locked: Boolean) = viewModelScope.launch {
        settingsRepository.setLevelLocked(locked)
        profileRepository.current()?.let { profileRepository.update(it.copy(levelLocked = locked)) }
    }

    fun setManualLevel(level: Cefr) = viewModelScope.launch {
        profileRepository.current()?.let { current ->
            profileRepository.update(
                current.copy(
                    cefrOverall = level,
                    cefrSpeaking = level,
                    cefrListening = level,
                    cefrGrammar = level,
                    cefrVocab = level,
                    cefrUpdatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    // --- Ключ API ---

    fun saveApiKey(rawKey: String) = viewModelScope.launch {
        settingsRepository.setApiKey(rawKey)
        _keyCheck.value = KeyCheckState.Idle
    }

    fun checkApiKey() = viewModelScope.launch {
        _keyCheck.value = KeyCheckState.Running
        val key = settingsRepository.apiKey()
        _keyCheck.value = if (key.isNullOrBlank()) {
            KeyCheckState.Done(KeyCheckResult.Invalid)
        } else {
            KeyCheckState.Done(keyChecker.check(key))
        }
    }

    fun clearKeyCheck() {
        _keyCheck.value = KeyCheckState.Idle
    }

    // --- Родительский PIN ---

    fun setPin(pin: String) = viewModelScope.launch {
        settingsRepository.setParentPinHash(if (pin.isBlank()) "" else PinHasher.hash(pin))
        _pinUnlocked.value = true
    }

    fun tryUnlock(pin: String): Boolean {
        val stored = settings.value.parentPinHash
        val ok = stored.isBlank() || PinHasher.verify(pin, stored)
        if (ok) _pinUnlocked.value = true
        return ok
    }

    fun lock() {
        _pinUnlocked.value = false
    }

    /** Секция закрыта PIN-кодом и ещё не разблокирована в этом сеансе. */
    fun isLocked(): Boolean = settings.value.pinProtected && !_pinUnlocked.value

    // --- Отладка промпта ---

    fun refreshPromptPreview() = viewModelScope.launch {
        val currentSettings = settingsRepository.current()
        val currentProfile = profileRepository.current()
        val memory = withContext(Dispatchers.IO) {
            val profileId = currentProfile?.id
            if (profileId == null) {
                Memory()
            } else {
                Memory(
                    summaries = database.lessonDao().recentSummaries(profileId),
                    vocabulary = database.vocabDao().activeTerms(profileId),
                    phonemes = database.contentDao().weakestPhonemes(profileId),
                    mistakes = database.lessonDao()
                        .unresolvedMistakes(profileId, limit = 5)
                        .map { "${it.original} → ${it.corrected}" },
                )
            }
        }

        _promptPreview.value = TutorPromptBuilder.build(
            TutorContext(
                profile = currentProfile,
                settings = currentSettings,
                recentSummaries = memory.summaries,
                recurringMistakes = memory.mistakes,
                weakPhonemes = memory.phonemes,
                activeVocabulary = memory.vocabulary,
            )
        )
    }

    // --- Данные ---

    /** Полный сброс: настройки, ключ шифрования и вся база. */
    fun clearAllData(onDone: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        settingsRepository.clearAll()
        onDone()
    }

    private data class Memory(
        val summaries: List<String> = emptyList(),
        val mistakes: List<String> = emptyList(),
        val phonemes: List<String> = emptyList(),
        val vocabulary: List<String> = emptyList(),
    )

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            profileRepository: ProfileRepository,
            database: AppDatabase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(settingsRepository, profileRepository, database) as T
        }
    }
}
