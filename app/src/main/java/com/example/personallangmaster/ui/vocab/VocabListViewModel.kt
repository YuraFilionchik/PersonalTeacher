package com.example.personallangmaster.ui.vocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.core.speech.TtsController
import com.example.personallangmaster.data.db.entity.VocabItemEntity
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.VocabRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class VocabListUiState(
    val items: List<VocabItemEntity> = emptyList(),
    val dueCount: Int = 0,
)

/** Список всех слов профиля: просмотр, ручное добавление, удаление и выгрузка. */
@OptIn(ExperimentalCoroutinesApi::class)
class VocabListViewModel(
    private val vocabRepository: VocabRepository,
    private val profileRepository: ProfileRepository,
    private val tts: TtsController,
) : ViewModel() {

    private val _state = MutableStateFlow(VocabListUiState())
    val state: StateFlow<VocabListUiState> = _state.asStateFlow()

    private var profileId: Long? = null

    init {
        val profiles = profileRepository.activeProfile.filterNotNull()

        profiles
            .onEach { profileId = it.id }
            .flatMapLatest { profile -> vocabRepository.observeAll(profile.id) }
            .onEach { items -> _state.update { it.copy(items = items) } }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile ->
                vocabRepository.observeDueCount(profile.id, LocalDate.now().toEpochDay())
            }
            .onEach { count -> _state.update { it.copy(dueCount = count) } }
            .launchIn(viewModelScope)
    }

    fun add(term: String, translation: String, example: String) {
        val profile = profileId ?: return
        viewModelScope.launch {
            vocabRepository.addManual(profile, term, translation, example.takeIf { it.isNotBlank() })
        }
    }

    fun delete(itemId: Long) {
        viewModelScope.launch { vocabRepository.delete(itemId) }
    }

    fun speak(term: String) = tts.speak(term)

    /** Готовая CSV-строка для отправки: файл никуда не пишем, разрешения не нужны. */
    fun exportCsv(): String = vocabRepository.toCsv(_state.value.items)

    companion object {
        fun factory(
            vocabRepository: VocabRepository,
            profileRepository: ProfileRepository,
            tts: TtsController,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                VocabListViewModel(vocabRepository, profileRepository, tts) as T
        }
    }
}
