package com.example.personallangmaster.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personallangmaster.data.db.MistakeType
import com.example.personallangmaster.data.db.VocabState
import com.example.personallangmaster.data.db.dao.LessonDao

import com.example.personallangmaster.data.db.dao.UsageByKind
import com.example.personallangmaster.data.db.entity.LessonEntity
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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Сколько ошибок одного типа накопилось за период. */
data class MistakeTotal(
    val type: MistakeType,
    val total: Int,
)

data class ProgressUiState(
    val days: List<DayBar> = emptyList(),
    val minutesWeek: Int = 0,
    val lessonsWeek: Int = 0,
    val vocabByState: Map<VocabState, Int> = emptyMap(),
    val topMistakes: List<MistakeTotal> = emptyList(),
    val spentTodayUsd: Double = 0.0,
    val spentMonthUsd: Double = 0.0,
    val usageBreakdown: List<UsageByKind> = emptyList(),
    val recentLessons: List<LessonEntity> = emptyList(),
)

/**
 * Прогресс: дни, словарь, ошибки и расходы.
 *
 * Показываем только то, что можно честно измерить: минуты, карточки, число
 * ошибок и потраченные деньги. Никаких синтетических «баллов прогресса».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val profileRepository: ProfileRepository,
    private val statsRepository: StatsRepository,
    private val vocabRepository: VocabRepository,
    private val lessonDao: LessonDao,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    init {
        val profiles = profileRepository.activeProfile.filterNotNull()

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeRange(profile.id, LocalDate.now().toEpochDay() - DAYS + 1)
            }
            .onEach { stats ->
                val today = LocalDate.now()
                val byDay = stats.associateBy { it.epochDay }

                val bars = (0 until DAYS).map { offset ->
                    val date = today.minusDays((DAYS - 1 - offset).toLong())
                    val stat = byDay[date.toEpochDay()]
                    DayBar(
                        label = date.dayOfWeek
                            .getDisplayName(TextStyle.SHORT, Locale("ru"))
                            .replaceFirstChar(Char::uppercase),
                        minutes = stat?.minutesSpoken ?: 0.0,
                        isToday = date == today,
                    )
                }

                _state.update {
                    it.copy(
                        days = bars,
                        minutesWeek = stats.sumOf { stat -> stat.minutesSpoken }.toInt(),
                        lessonsWeek = stats.sumOf { stat -> stat.lessonsCount },
                    )
                }
            }
            .launchIn(viewModelScope)

        VocabState.entries.forEach { state ->
            profiles
                .flatMapLatest { profile -> vocabRepository.observeCountByState(profile.id, state) }
                .onEach { count ->
                    _state.update { current ->
                        current.copy(vocabByState = current.vocabByState + (state to count))
                    }
                }
                .launchIn(viewModelScope)
        }

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeCostSince(profile.id, StatsRepository.startOfToday())
            }
            .onEach { spent -> _state.update { it.copy(spentTodayUsd = spent) } }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeCostSince(profile.id, StatsRepository.startOfMonth())
            }
            .onEach { spent -> _state.update { it.copy(spentMonthUsd = spent) } }
            .launchIn(viewModelScope)

        profiles
            .flatMapLatest { profile ->
                statsRepository.observeUsageByKind(profile.id, StatsRepository.startOfMonth())
            }
            .onEach { usage -> _state.update { it.copy(usageBreakdown = usage) } }
            .launchIn(viewModelScope)

        profiles
            .onEach { profile -> refreshLessons(profile.id) }
            .launchIn(viewModelScope)
    }

    private fun refreshLessons(profileId: Long) {
        viewModelScope.launch {
            val month = System.currentTimeMillis() - 30 * StatsRepository.DAY_MILLIS
            _state.update {
                it.copy(
                    // Запрос группирует ещё и по теме, поэтому один тип приходит
                    // несколькими строками — складываем их, прежде чем показывать.
                    topMistakes = lessonDao.topMistakes(profileId, month, limit = 50)
                        .groupBy { frequency -> frequency.type }
                        .map { (type, rows) -> MistakeTotal(type, rows.sumOf { it.total }) }
                        .sortedByDescending { it.total }
                        .take(5),
                    recentLessons = lessonDao.getByStatus(
                        profileId = profileId,
                        status = com.example.personallangmaster.data.db.LessonStatus.ANALYZED,
                        limit = 10,
                    ),
                )
            }
        }
    }

    companion object {
        private const val DAYS = 7

        fun mistakeTitle(type: MistakeType): String = when (type) {
            MistakeType.GRAMMAR -> "Грамматика"
            MistakeType.VOCAB -> "Выбор слова"
            MistakeType.PRONUNCIATION -> "Произношение"
            MistakeType.WORD_ORDER -> "Порядок слов"
            MistakeType.ARTICLE -> "Артикли"
            MistakeType.TENSE -> "Времена"
            MistakeType.PREPOSITION -> "Предлоги"
            MistakeType.STYLE -> "Стиль"
        }

        fun vocabStateTitle(state: VocabState): String = when (state) {
            VocabState.NEW -> "Новые"
            VocabState.LEARNING -> "Учатся"
            VocabState.REVIEW -> "На повторении"
            VocabState.MATURE -> "Освоены"
            VocabState.SUSPENDED -> "Скрыты"
        }

        fun factory(
            profileRepository: ProfileRepository,
            statsRepository: StatsRepository,
            vocabRepository: VocabRepository,
            lessonDao: LessonDao,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProgressViewModel(profileRepository, statsRepository, vocabRepository, lessonDao) as T
        }
    }
}
