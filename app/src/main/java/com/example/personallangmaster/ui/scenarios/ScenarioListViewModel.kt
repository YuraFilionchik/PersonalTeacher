package com.example.personallangmaster.ui.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.entity.ScenarioEntity
import com.example.personallangmaster.data.repo.ContentRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.scenarioRange
import com.example.personallangmaster.domain.GenerateScenarioUseCase
import com.example.personallangmaster.domain.ScenarioResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScenarioListUiState(
    val suitable: List<ScenarioEntity> = emptyList(),
    val others: List<ScenarioEntity> = emptyList(),
    val categories: List<String> = listOf(ALL_CATEGORIES),
    val selectedCategory: String = ALL_CATEGORIES,
    val generating: Boolean = false,
    val error: String? = null,
)

/** Каталог сценариев с делением по уровню и фильтром по категории. */
class ScenarioListViewModel(
    private val contentRepository: ContentRepository,
    private val profileRepository: ProfileRepository,
    private val generateScenario: GenerateScenarioUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ScenarioListUiState())
    val state: StateFlow<ScenarioListUiState> = _state.asStateFlow()

    private var allScenarios: List<ScenarioEntity> = emptyList()
    private var level: Cefr = Cefr.A2

    init {
        viewModelScope.launch {
            level = profileRepository.current()?.cefrOverall ?: Cefr.A2
            regroup()
        }

        contentRepository.observeScenarios()
            .onEach { scenarios ->
                allScenarios = scenarios
                regroup()
            }
            .launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _state.update { it.copy(selectedCategory = category) }
        regroup()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun createScenario(description: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            when (val result = generateScenario(description)) {
                is ScenarioResult.Success -> {
                    _state.update { it.copy(generating = false) }
                    onCreated(result.scenario.id)
                }
                is ScenarioResult.Failure -> _state.update {
                    it.copy(generating = false, error = result.reason)
                }
            }
        }
    }

    private fun regroup() {
        val category = _state.value.selectedCategory
        val filtered = allScenarios.filter {
            category == ALL_CATEGORIES || it.category.equals(category, ignoreCase = true)
        }
        val (from, to) = scenarioRange(level)

        _state.update { current ->
            current.copy(
                suitable = filtered.filter { it.level.ordinal in from.ordinal..to.ordinal },
                others = filtered.filterNot { it.level.ordinal in from.ordinal..to.ordinal },
                categories = buildList {
                    add(ALL_CATEGORIES)
                    addAll(allScenarios.map { it.category }.distinct().sorted())
                },
            )
        }
    }

    companion object {
        fun factory(
            contentRepository: ContentRepository,
            profileRepository: ProfileRepository,
            generateScenario: GenerateScenarioUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScenarioListViewModel(contentRepository, profileRepository, generateScenario) as T
        }
    }
}
