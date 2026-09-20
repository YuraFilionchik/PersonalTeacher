package com.example.personallangmaster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.core.cost.CostCalculator
import com.example.personallangmaster.data.db.Cefr
import com.example.personallangmaster.data.db.entity.GrammarTopicEntity
import com.example.personallangmaster.data.db.entity.LessonEntity
import com.example.personallangmaster.data.repo.ContentRepository
import com.example.personallangmaster.data.repo.ProfileRepository
import com.example.personallangmaster.data.repo.StatsRepository
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

data class HomeUiState(
    val name: String = "",
    val level: Cefr = Cefr.A2,
    val goalMinutes: Int = 10,
    val minutesToday: Int = 0,
    val streak: Int = 0,
    val dueCards: Int = 0,
    val recommendedTopic: GrammarTopicEntity? = null,
    val lastLesson: LessonEntity? = null,
    val spentTodayUsd: Double = 0.0,
    val dailyLimitUsd: Double = 0.0,
) {
    val goalMet: Boolean get() = goalMinutes > 0 && minutesToday >= goalMinutes

    /** Осталось до дневного лимита — показываем, только когда лимит задан. */
    val budgetLeftUsd: Double? get() =
        if (dailyLimitUsd > 0) (dailyLimitUsd - spentTodayUsd).coerceAtLeast(0.0) else null

    val budgetLeftLabel: String get() = CostCalculator.formatUsd(budgetLeftUsd ?: 0.0)
}

/**
 * Главный экран: одно состояние ученика на сегодня.
 *
 * Здесь нет статистики ради статистики — только то, что подсказывает
 * следующее действие: сколько осталось до цели, что повторить, что подтянуть.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val profileRepository: ProfileRepository,
    private val statsRepository: StatsRepository,
    private val vocabRepository: VocabRepository,
    private val contentRepository: ContentRepository,
    private val lessonDao: com.example.personallangmaster.data.db.dao.LessonDao,
    private val settingsRepository: com.example.personallangmaster.data.prefs.SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        val profiles = profileRepository.activeProfile.filterNotNull()

        profiles
            .onEach { profile ->
                _state.update {
                    it.copy(
                        name = profile.name,
                        level = profile.cefrOverall,
                        goalMinutes = profile.dailyGoalMinutes,
                    )
                }
            }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile -> statsRepository.observeToday(profile.id) }
            .onEach { stat ->
                _state.update { it.copy(minutesToday = (stat?.minutesSpoken ?: 0.0).toInt()) }
            }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile -> statsRepository.observeStreak(profile.id) }
            .onEach { streak -> _state.update { it.copy(streak = streak?.current ?: 0) } }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile -> vocabRepository.observeDueCount(profile.id) }
            .onEach { due -> _state.update { it.copy(dueCards = due) } }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeCostSince(profile.id, StatsRepository.startOfToday())
            }
            .onEach { spent -> _state.update { it.copy(spentTodayUsd = spent) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _state.update { it.copy(dailyLimitUsd = settingsRepository.current().dailyLimitUsd) }
        }

        profiles
            .onEach { profile -> refreshSuggestions(profile.id) }
            .launchIn(viewModelScope)
    }

    private suspend fun refreshSuggestions(profileId: Long) {
        val month = System.currentTimeMillis() - 30 * StatsRepository.DAY_MILLIS
        val recommended = contentRepository.recommendedTopics(profileId, since = month, limit = 1)
        // Берём последний урок в любом состоянии: карточка одинаково нужна и
        // чтобы запустить разбор, и чтобы перечитать уже готовый.
        val lessons = lessonDao.getByStatus(
            profileId = profileId,
            status = com.example.personallangmaster.data.db.LessonStatus.COMPLETED,
            limit = 1,
        ) + lessonDao.getByStatus(
            profileId = profileId,
            status = com.example.personallangmaster.data.db.LessonStatus.ANALYZED,
            limit = 1,
        )

        _state.update {
            it.copy(
                recommendedTopic = recommended.firstOrNull()?.topic,
                lastLesson = lessons.maxByOrNull { lesson -> lesson.startedAt },
            )
        }
    }

    companion object {
        fun factory(
            profileRepository: ProfileRepository,
            statsRepository: StatsRepository,
            vocabRepository: VocabRepository,
            contentRepository: ContentRepository,
            lessonDao: com.example.personallangmaster.data.db.dao.LessonDao,
            settingsRepository: com.example.personallangmaster.data.prefs.SettingsRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(
                profileRepository, statsRepository, vocabRepository,
                contentRepository, lessonDao, settingsRepository,
            ) as T
        }
    }
}
