package com.example.personallangmaster.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.ai.prompt.Persona
import com.example.personallangmaster.ai.prompt.Personas
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.prefs.SettingsRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Шаги онбординга по порядку. */
enum class OnboardingStep { WELCOME, INTERESTS, TUTOR, LEVEL, API_KEY, PERMISSION, DONE }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    val interests: Set<String> = emptySet(),
    val goal: String = "",
    val persona: Persona = Personas.default,
    val voiceName: String = Personas.default.defaultVoice,
    val level: Cefr = Cefr.A2,
    val dailyGoalMinutes: Int = 10,
    val apiKey: String = "",
    val apiKeySaved: Boolean = false,
    val micGranted: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
) {
    /** Можно ли перейти к следующему шагу. */
    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.WELCOME -> name.isNotBlank()
            OnboardingStep.API_KEY -> apiKeySaved || apiKey.isNotBlank()
            else -> true
        }

    val progress: Float
        get() = (step.ordinal + 1f) / OnboardingStep.entries.size
}

/**
 * Онбординг: собирает минимум, без которого тренер не может быть личным —
 * имя, интересы, цель, характер тренера, уровень и ключ API.
 * Голосовой placement-тест появится отдельным этапом, пока уровень выбирается вручную.
 */
class OnboardingViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    fun toggleInterest(tag: String) = _state.update { current ->
        val updated = if (tag in current.interests) current.interests - tag else current.interests + tag
        current.copy(interests = updated)
    }

    fun onGoalChange(value: String) = _state.update { it.copy(goal = value) }

    fun onPersonaChange(persona: Persona) = _state.update {
        it.copy(persona = persona, voiceName = persona.defaultVoice)
    }

    fun onVoiceChange(voice: String) = _state.update { it.copy(voiceName = voice) }
    fun onLevelChange(level: Cefr) = _state.update { it.copy(level = level) }
    fun onDailyGoalChange(minutes: Int) = _state.update { it.copy(dailyGoalMinutes = minutes) }
    fun onApiKeyChange(value: String) = _state.update { it.copy(apiKey = value, apiKeySaved = false) }
    fun onMicPermissionResult(granted: Boolean) = _state.update { it.copy(micGranted = granted) }

    fun back() = _state.update { current ->
        val previous = OnboardingStep.entries.getOrNull(current.step.ordinal - 1) ?: current.step
        current.copy(step = previous)
    }

    fun next() {
        val current = _state.value
        if (!current.canContinue) return
        when (current.step) {
            OnboardingStep.API_KEY -> saveApiKeyAndAdvance()
            OnboardingStep.PERMISSION -> finish()
            else -> advance()
        }
    }

    /** Ключ можно ввести и позже — тогда живой урок просто не запустится. */
    fun skipApiKey() = advance()

    private fun advance() = _state.update { current ->
        val nextStep = OnboardingStep.entries.getOrNull(current.step.ordinal + 1) ?: current.step
        current.copy(step = nextStep)
    }

    private fun saveApiKeyAndAdvance() = viewModelScope.launch {
        val key = _state.value.apiKey.trim()
        if (key.isNotEmpty()) {
            settingsRepository.setApiKey(key)
            // Сырой ключ в состоянии больше не держим: он уже зашифрован в хранилище.
            _state.update { it.copy(apiKey = "", apiKeySaved = true) }
        }
        advance()
    }

    private fun finish() = viewModelScope.launch {
        val current = _state.value
        _state.update { it.copy(saving = true) }

        profileRepository.create(
            name = current.name,
            interests = current.interests.toList(),
            goals = current.goal,
            dailyGoalMinutes = current.dailyGoalMinutes,
            startLevel = current.level,
            now = System.currentTimeMillis(),
        )

        settingsRepository.setPersona(current.persona.id)
        settingsRepository.setTutorName(current.persona.defaultTutorName)
        settingsRepository.setVoice(current.voiceName)
        settingsRepository.setStrictness(current.persona.defaultStrictness)
        settingsRepository.setVerbosity(current.persona.defaultVerbosity)
        settingsRepository.setOnboardingCompleted(true)

        _state.update { it.copy(saving = false, step = OnboardingStep.DONE, finished = true) }
    }

    companion object {
        fun factory(
            profileRepository: ProfileRepository,
            settingsRepository: SettingsRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OnboardingViewModel(profileRepository, settingsRepository) as T
        }

        /** Интересы предлагаем списком — так ученику проще, чем писать с нуля. */
        val suggestedInterests = listOf(
            "Путешествия", "Работа и карьера", "IT и технологии", "Сериалы и кино",
            "Музыка", "Спорт", "Книги", "Готовка", "Игры", "Наука",
            "Бизнес", "Медицина", "Дети и семья", "Искусство",
        )
    }
}
